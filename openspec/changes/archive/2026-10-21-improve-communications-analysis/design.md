# Design

## Context

Os dois tools atuais ([`CommunicationsGraphTool`](../../../src/main/java/br/com/ipedtools/mcp/tools/CommunicationsGraphTool.java)) delegam para `IpedCoreService.getTopContacts/getCommunicationsGraph`, que usam `collectCommunicationEvents` (consulta ampla por `category`, `from:*`, `to:*`) e `parseContact` (parsing de texto). Ver `proposal.md` - Why para as causas (direção, propriedades ignoradas, canal).

Fatos verificados no índice do caso de teste (764.455 itens, 10.703 com `Communication:From`):

- `Communication:Direction` existe em 6.022 itens (`incoming` 5.842, `outgoing` 180); ~4.590 IMs não têm direção e são mensagens de grupo.
- Chamadas UFED trazem `Communication:To` e `ufed:Direction` (48/25), em geral sem `From`.
- Canal: `ufed:SourceApplication` (Viber 1.993, Native Messages 130, Snapchat, Signal, Facebook) para UFED; `accountType` (WhatsApp, Telegram) para parsers nativos.
- Subpropriedades estruturadas `Communication:From:id|name|phoneNumber` e `Communication:To:id|name`; em mensagens de grupo `Communication:To:id` é o id do chat, não de uma pessoa.
- A aba Vínculos do IPED monta `From -> To/CC/BCC` por item (`GraphTask.processCommunicationMetadata`) sem usar direção; dados em Neo4j embarcado (4.4.4), que o SearchApp mantém travado enquanto o caso está aberto.

Restrições do projeto: acesso somente leitura ao caso, offline, stdout reservado ao JSON-RPC, Java 21.

## Goals / Non-Goals

**Goals:**
- Uma única definição de evento de comunicação, testável sem caso real, usada por `get_top_contacts`, `get_communications_graph` e `get_contact_relationship`.
- Respostas que permitam à LLM explicar com quem, como, com que frequência, quando e por qual meio, com rastreio até o item e com a confiança (cobertura) explícita.
- Nenhuma afirmação sobre quem é o dono do aparelho.

**Non-Goals:**
- Reproduzir o layout ou as interações visuais da aba Vínculos.
- Ler `graph.db` (Neo4j) ou os CSVs de `iped/neo4j`; busca de caminhos entre entidades; Cypher livre.
- Alterar `get_device_and_owner_info` ou o resumo inicial do caso.
- Inferir direção a partir de identidade (dono) ou de heurística de volume.

## Decisions

### D1. Fonte única: índice Lucene
As dimensões necessárias (data, canal, direção, id do item) só coexistem no índice. O grafo do IPED (CSV/Neo4j) não tem data e agrupa Viber/WhatsApp/SMS em `message`.
**Alternativas:** (a) Neo4j embarcado: lock com o SearchApp aberto, escrita em `iped/neo4j/logs`, Neo4j 4.4 sem suporte oficial a Java 21; (b) CSVs `iped/neo4j/csv`: formato interno, sem datas nem app. Descartadas.

### D2. Estrutura em duas camadas
- `CommunicationEventExtractor` (novo, `service/`): consulta `Communication\:From:* OR Communication\:To:*`, carrega somente os campos necessários do documento e produz `CommunicationEvent` em *streaming* (`Consumer`), sem reter todos os eventos em memória.
- `CommunicationAnalytics` (novo, `service/`, sem dependência de Lucene): resolução de canal/direção/identidade (funções puras) e agregadores de contatos, grafo e relação. É a camada coberta por testes unitários.
- `IpedCoreService` mantém os métodos públicos `getTopContacts`, `getCommunicationsGraph` e ganha `getContactRelationship`, delegando às novas classes. `ContactIdentity`, `CommunicationEvent`, `parseContact` e o caminho de `getDeviceAndOwnerInfo` saem das análises de comunicação.
**Alternativa:** manter tudo em `IpedCoreService` (3,2 mil linhas): dificulta testes sem caso real.

### D3. Resolução de canal, direção e data
- Canal: `ufed:SourceApplication` → `accountType` → derivado de `contentType` (`message/x-whatsapp-*`, `message/x-telegram-*`, `message/x-skype-*`, `application/x-ufed-call` → chamada, `Emails` → e-mail) → `unknown`. O valor do app é preservado (ex.: `Viber`, `Native Messages`); campo `kind` separado (`message|call|email`).
- Direção: comparação exata, sem diferenciar caixa, de `Communication:Direction` e depois `ufed:Direction` contra `incoming`/`outgoing`; qualquer outra coisa é `UNKNOWN`. O campo registra `direction_source`. Corrige o `contains("IN")`.
- Data: `Communication:Date` → `timeStamp` → `created`, normalizada para ISO-8601 pela rotina existente (`normalizeIsoDate`); sem fallback para a hora atual.

### D4. Identidade das partes
1. Preferir `:id`, `:phoneNumber`, `:name` estruturados; senão, parsing do valor bruto (formatos `Nome (ID:+55…)`, `<email>`, `…@s.whatsapp.net`).
2. Chave canônica: telefone via libphonenumber (já presente em `iped-engine`; usada pelo `GraphTask`) quando houver `+` ou região conhecida, senão apenas dígitos; e-mail em minúsculas; demais contas por id em minúsculas com o canal como escopo para evitar colisão entre apps.
3. Identidades que compartilham chave de telefone ou e-mail são unidas, e os valores originais viram `aliases`.
4. Em eventos de grupo, `Communication:To:id` não vira identidade de pessoa: o destinatário é o nó de grupo (nome e `Conversation:id` quando existir).
5. Nomes vêm primeiro de `:name`; a agenda (`category:contacts`) é apenas fonte secundária de nome, sem alterar chave. O limite atual de 500 contatos sobe para 5.000 (o caso de teste tem 3.496).
**Alternativa:** unificar por nome: gera falsos positivos entre homônimos; usado só como último recurso e marcado em `identity_basis`.

### D5. Sem dono: lado local por canal
Eventos com direção explícita têm um lado local e um interlocutor: em `INCOMING` o remetente é o interlocutor; em `OUTGOING` são os destinatários. O lado local é o nó `local:<canal>` (`identity_resolved: false`) com `observed_local_identifiers` (até 5, com contagem) para informar, sem afirmar, as contas vistas naquele lado.
Eventos `UNKNOWN` com ambas as partes identificadas geram aresta `remetente -> destinatário` (inclusive grupos); com uma só parte, entram em `unlinked_events` e na seção de atividade de grupo quando aplicável, e nunca em `incoming_count`/`outgoing_count`.
**Alternativas:** (a) nó único de dono (como hoje): atribui identidade sem base; (b) lado local pelo `From` visto em eventos de saída: mistura contas de apps diferentes e some quando a mensagem de entrada não traz `To`.

### D6. Agregação e evidências
Para cada contato e aresta: `total`, `messages`, `calls`, `emails`, contagens por direção, mapa por canal, histograma `YYYY-MM`, `first/last`, `active_days`. `evidence_ids`: menor e maior timestamp mais 3 itens escolhidos deterministicamente pelo menor hash do id do item (reservatório fixo de tamanho 3), totalizando no máximo 5. Limites: padrão 20/50/5; máximos 200/200/20.

### D7. Contrato de saída (resumo)
- `get_top_contacts`: mantém `rank, identifier, name, total_interactions, messages_count, calls_count, incoming_count, outgoing_count, channels, first_interaction, last_interaction`; acrescenta `aliases`, `unknown_direction_count`, `by_channel`, `by_month`, `active_days`, `evidence_ids`, `is_group`; raiz com `unknown_direction_total`, `group_activity`, `filters`, `diagnostics`, `total_*`, `returned_count`, `truncated`.
- `get_communications_graph`: nós `id, label, name, aliases, type (ACCOUNT|GROUP|LOCAL_ENDPOINT), total_interactions, is_focal, identity_resolved`; arestas `source, target, weight, channels, by_channel, messages_count, calls_count, first_interaction, last_interaction, by_month, evidence_ids`; raiz com `graph_source: "lucene-index"`, `unlinked_events`, `truncated`, `diagnostics`; `mermaid` se pedido.
- **BREAKING:** `Dispositivo_Local` removido e `channels` com app real. `phone_or_account` é mantido.
- `diagnostics`: `events_analyzed`, `with_explicit_direction` (`count`, `percent`), `with_resolved_channel`, `with_timestamp`.

### D8. `get_contact_relationship`
Parâmetros: `contact_a`, `contact_b` obrigatórios, `channel`, `start_date`, `end_date`, `evidence_limit` (padrão 5). Resolução: igualdade normalizada por identificador/alias; depois substring sem caixa em nomes. Mais de uma identidade: devolve `candidates` (até 10) sem escolher. Um evento casa se `a` e `b` são remetente/destinatário em lados opostos, ou membros do mesmo grupo (grupos em comum). Quando uma referência é um identificador observado do lado local do canal, eventos com direção explícita e lado local sem identidade casam como `endpoint_inferred: true`.

### D9. Filtros e Mermaid
`channel` (igualdade sem caixa com o app), `start_date`/`end_date` (ISO, inclusivos); eventos sem data são excluídos quando há filtro de data e contados em `events_without_date_excluded`. `include_mermaid` gera `flowchart LR` com ids `n0..nk`, rótulos escapados e peso nas arestas, somente com nós e arestas retornados.

### D10. Testes e documentação
Testes unitários puros sobre `CommunicationAnalytics` (canal, direção incluindo `Outgoing`, identidade/alias, grupos, agregação, evidências, truncamento) com eventos sintéticos; testes de integração em `McpToolsTest`/`IpedCoreServiceTest` com `@EnabledIf("isCaseAvailable")`; `scripts/test_stdio_mcp.ps1` passa a chamar as três ferramentas; wiki, README e `CHANGELOG.md` passam de 27 para 28 ferramentas; `ForensicPrompts` cita a nova ferramenta.

## Risks / Trade-offs

- [Telefones sem `+` e sem região confiável podem não unir variantes] → usar a região do `GraphConfig`/locale quando disponível e registrar `identity_basis`; validar nos casos secundário e terciário.
- [Mensagens individuais recebidas sem `To` deixam o lado local sem identidade] → é o motivo de `local:<canal>`; `observed_local_identifiers` mostra o que foi visto.
- [Grande volume de grupos sem direção deixa `unknown` alto] → reportado em `diagnostics` e `group_activity` em vez de escondido.
- [Casos grandes (milhões de mensagens)] → *streaming* sem lista completa, carga seletiva de campos armazenados, e tempo medido na validação.
- [Mudança de contrato (BREAKING) em clientes que dependiam de `Dispositivo_Local` ou de `channels` genéricos] → campos antigos mantidos quando possível e documentado no `CHANGELOG.md`.
- [`ufed:SourceApplication` ausente em alguns extratores] → cai em `accountType`/`contentType`, e por último `unknown`, com a cobertura de canal informada.

## Migration Plan

Sem migração de dados nem de configuração. Entrega em um único release (contrato das ferramentas atualizado e `CHANGELOG.md` com seção própria); reversão é reverter o commit, pois o estado do caso e os bookmarks não são tocados.

## Open Questions

- Validar a hipótese da issue #4 (campo de direção ausente) em um caso de WhatsApp nativo do IPED (HTML por conversa); o desenho já trata direção ausente como `UNKNOWN`, então não bloqueia a implementação.
