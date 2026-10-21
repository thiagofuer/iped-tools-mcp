# Proposal

## Why

`get_top_contacts` e `get_communications_graph` não refletem as propriedades reais do caso (issue #4): o dono do aparelho aparece como o contato nº 1, `incoming_count` ≈ `total_interactions` e `outgoing_count` ≈ 0. A investigação no índice do caso de teste mostrou três causas independentes:

1. **Bug de direção:** `"OUTGOING".contains("IN")` é verdadeiro, então toda mensagem enviada também é classificada como recebida.
2. **Propriedades ignoradas:** chamadas UFED usam `ufed:Direction` (não `Communication:Direction`); as subpropriedades estruturadas `Communication:From/To:id|name|phoneNumber` não são usadas; cerca de 45% das mensagens de IM (mensagens de grupo) não têm direção explícita e hoje são tratadas como recebidas.
3. **Canal impreciso:** o canal é deduzido do texto de `category`, de modo que o app mais volumoso do caso (Viber, `ufed:SourceApplication`) vira "chat messages"; WhatsApp/Telegram vêm de `accountType`.

Além disso, a ferramenta deve responder às perguntas do examinador sobre vínculos (com quem, como, com que frequência, quando e por qual meio) e entregar dados suficientes para a LLM montar um gráfico de vínculos ou um relatório, de forma comparável à aba **Vínculos** do IPED (`iped-app/.../graph`, `iped-engine/.../graph/GraphTask`), cujo grafo também é derivado de `Communication:From/To` e não depende de `Communication:Direction`.

## What Changes

- Novo modelo único de eventos de comunicação, derivado somente do índice Lucene (somente leitura), compartilhado por todas as ferramentas de comunicação: resolve canal (`ufed:SourceApplication` → `accountType` → `contentType`), direção (`Communication:Direction` → `ufed:Direction`, nunca assumida como recebida), partes (subpropriedades `:id/:name/:phoneNumber` antes do parsing de texto), data, grupo e id do item (evidência).
- **Sem noção de "dono do aparelho":** a análise não usa `get_device_and_owner_info`, nem `ufed:PhoneOwner`, nem `Conversation:Account` para afirmar quem é o dono. O lado local de cada evento é representado como ponto local por canal, sem atribuição de identidade.
- `get_top_contacts` passa a ranquear interlocutores (contraparte de cada evento com direção explícita), com contagens por direção (incluindo `unknown`), por canal e por mês, primeira/última interação, dias ativos e amostra de evidências.
- `get_communications_graph` passa a devolver nós tipados e arestas dirigidas com agregados por canal e por mês, até 5 `evidence_ids` por aresta, diagnósticos de cobertura/truncamento e `mermaid` opcional.
- Nova ferramenta `get_contact_relationship` para detalhar a relação entre dois contatos (linha do tempo mensal, canais, direções, grupos em comum e evidências), tratando identificadores ambíguos como candidatos e não como adivinhação.
- Filtros opcionais `channel`, `start_date` e `end_date` nas três ferramentas.
- Limites padrão: 20 contatos, 50 arestas e 5 `evidence_ids` por aresta.
- **BREAKING:** o nó sintético `Dispositivo_Local` do grafo é removido e substituído por pontos locais por canal (`local:<canal>`, `identity_resolved: false`); o campo `channels` passa a conter o app real (ex.: `Viber`) em vez de rótulos genéricos. Os demais campos existentes são mantidos.
- Total de ferramentas MCP sobe de 27 para 28; documentação, wiki e script de teste STDIO são atualizados.

## Capabilities

### New Capabilities
- `communication-event-model`: extração e normalização de eventos de comunicação a partir das propriedades do índice (canal, direção explícita, identidade das partes, data, grupo, evidência), com diagnósticos de cobertura, usada pelas ferramentas de vínculo.

### Modified Capabilities
- `communications-graph`: requisitos de `get_top_contacts` e `get_communications_graph` passam a exigir direção explícita sem inferência de dono, canal real, agregados temporais, evidências, truncamento explícito e filtros; adiciona `get_contact_relationship`.
- `user-documentation`: o catálogo de ferramentas deve descrever o contrato de saída das ferramentas de comunicação (direção `unknown`, truncamento, canais) e listar a nova ferramenta.

## Impact

- **Código:** `IpedCoreService` (coleta e agregação de eventos, remoção do uso de `getDeviceAndOwnerInfo` nas análises de comunicação), `CommunicationsGraphTool` (parâmetros e nova tool); possível extração do modelo de eventos para classe própria em `service/`.
- **Componentes IPED/Lucene:** `IPEDSearcher`, `IPEDSource.getReader().document(...)`; campos `Communication:*`, `ufed:Direction`, `ufed:SourceApplication`, `accountType`, `contentType`, `isGroupMessage`, `Conversation:*`. libphonenumber (já presente via `iped-engine`) para normalização de telefones.
- **Cadeia de custódia:** nenhuma escrita; Neo4j/`graph.db` e os CSVs de `iped/neo4j` não são acessados.
- **Testes e docs:** testes unitários puros para canal/direção/identidade/agregação e testes de integração com caso local (`McpToolsTest`, `IpedCoreServiceTest`); `scripts/test_stdio_mcp.ps1`; `docs/wiki/Catalogo-de-Ferramentas.md` e referências a "27 ferramentas" (README, wiki, `CHANGELOG.md`).
- **Forensic value:** o examinador e a LLM passam a ter quem/como/quando/frequência/meio com rastreio até o item, e a cobertura de direção fica explícita, evitando conclusões sobre quem iniciou a conversa sem base nas propriedades.
