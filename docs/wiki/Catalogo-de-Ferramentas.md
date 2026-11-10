# Catálogo de Ferramentas Forenses (28 MCP Tools)

O **IPED Tools MCP** expõe 28 ferramentas periciais especializadas agrupadas em 9 domínios operacionais. Abaixo está a referência completa de cada ferramenta, seus objetivos e exemplos práticos de perguntas em português que o examinador pode fazer no chat.

---

## 1. Conectividade e Gestão de Casos

### `get_server_status`
* **Propósito:** Retorna o status operacional do servidor MCP, versão instalada do produto, versão do IPED Core e informações do caso ativo aberto.
* **Exemplo de Pergunta:** *"O servidor está ativo e qual versão do IPED está sendo utilizada?"*

### `check_connection`
* **Propósito:** Alias rápido para `get_server_status`, validando a conectividade imediata e prontidão para a investigação pericial.
* **Exemplo de Pergunta:** *"Verifique se a conexão com o caso IPED está funcionando corretamente."*

### `list_sources`
* **Propósito:** Lista todas as fontes de evidência indexadas no caso (discos rígidos, contêineres E01, extrações móveis UFED/GrayKey, pastas lógicas).
* **Exemplo de Pergunta:** *"Quais imagens forenses ou mídias foram processadas neste caso?"*

### `get_case_summary`
* **Propósito:** Apresenta um sumário executivo com a volumetria total de itens, distribuição por categorias, contagem de fontes e marcadores periciais prévios.
* **Exemplo de Pergunta:** *"Quantos arquivos no total existem neste caso e quais são as principais categorias encontradas?"*

### `open_case`
* **Propósito:** Carrega ou alterna dinamicamente o caso pericial ativo em tempo de execução sem reiniciar o processo ou reconfigurar o cliente de IA.
* **Exemplo de Pergunta:** *"Abra o caso pericial localizado em C:\casos_forenses\caso_operacao_02."*

---

## 2. Dicionário e Descoberta de Metadados

### `get_property_dictionary`
* **Propósito:** Retorna o catálogo canônico de metadados do IPED agrupados por domínios (`chats`, `browsers`, `emails`, `media`, `system`, `gps`, `ufed`, `ai`, `crypto`), informando tipos de dados e sintaxes de consulta.
* **Exemplo de Pergunta:** *"Quais propriedades de geolocalização e GPS estão disponíveis para busca?"*

### `list_available_properties`
* **Propósito:** Descobre dinamicamente os nomes exatos das propriedades que de fato foram indexadas para uma categoria específica presente no caso.
* **Exemplo de Pergunta:** *"Quais metadados específicos foram extraídos para a categoria 'whatsapp'?"*

---

## 3. Busca e Extração de Conteúdo

### `search_documents`
* **Propósito:** Executa consultas estruturadas no índice Apache Lucene do caso, suportando operadores booleanos, filtros de categoria, campos periciais e curingas.
* **Exemplo de Pergunta:** *"Busque mensagens de chat que contenham o termo 'pagamento' ou 'transferência'."*

### `get_document_metadata`
* **Propósito:** Recupera metadados técnicos de um ou mais itens, com agrupamento semântico (`basic`, `communication`, `geo`, `forensic`, `extra`), extração sem truncamento (`raw: true`) ou filtragem por campos (`keys`).
* **Exemplo de Pergunta:** *"Mostre todos os metadados técnicos e dados forenses do item ID 15420."*

### `get_document_text`
* **Propósito:** Extrai o conteúdo textual completo de um arquivo processado pelo IPED/OCR com paginação controlada (`offset` e `max_chars`).
* **Exemplo de Pergunta:** *"Leia os primeiros 2000 caracteres do documento ID 8412."*

### `list_categories`
* **Propósito:** Lista todas as categorias de evidências catalogadas pelo IPED no caso e a contagem de itens em cada uma.
* **Exemplo de Pergunta:** *"Quais tipos de evidência foram classificados e quantos itens cada categoria possui?"*

### `list_bookmarks`
* **Propósito:** Lista todos os marcadores periciais existentes no arquivo `bookmarks.iped` do caso e o quantitativo de itens marcados.
* **Exemplo de Pergunta:** *"Quais marcadores periciais já foram criados neste caso?"*

### `add_to_bookmark`
* **Propósito:** Adiciona um ou mais itens a um marcador pericial existente ou cria um novo marcador de auditoria no caso.
* **Exemplo de Pergunta:** *"Adicione os itens 1250 e 1251 ao marcador 'Contratos Suspeitos'."*

---

## 4. Inteligência de Comunicações e Dispositivos

### `get_device_and_owner_info`
* **Propósito:** Extrai informações do proprietário do dispositivo periciado, contas vinculadas, identificadores de hardware (IMEI), números de telefone e e-mails associados.
* **Exemplo de Pergunta:** *"Quem é o proprietário identificado deste celular e quais são seus números de telefone e IMEI?"*

### `get_top_contacts`
* **Propósito:** Gera o ranking dos interlocutores mais frequentes em mensagens, chamadas e e-mails, com contagens por direção, por canal e por mês, primeira e última interação, dias ativos e `evidence_ids`.
* **Parâmetros:** `limit` (padrão 20, máximo 200), `channel`, `start_date`, `end_date`.
* **Exemplo de Pergunta:** *"Quais são os 10 contatos com maior volume de interação neste telefone?"*
* **Exemplo de Pergunta:** *"Com quem houve mais conversas pelo Viber em 2024?"*

### `get_communications_graph`
* **Propósito:** Extrai o grafo de rede de comunicações: nós tipados e arestas dirigidas e ponderadas, com agregados por canal e por mês e até 5 `evidence_ids` por aresta. Opcionalmente devolve um diagrama Mermaid (`include_mermaid: true`) com exatamente os nós e arestas retornados.
* **Parâmetros:** `focal_contact`, `min_interactions` (padrão 5), `limit_edges` (padrão 50, máximo 200), `channel`, `start_date`, `end_date`, `include_mermaid`.
* **Exemplo de Pergunta:** *"Construa o grafo de comunicação entre os principais interlocutores identificados no caso."*
* **Exemplo de Pergunta:** *"Monte um diagrama de vínculos em torno do número +1 555-010-1181."*

### `get_contact_relationship`
* **Propósito:** Detalha como dois contatos se relacionam: total de interações, mensagens, chamadas e e-mails, quebra por canal e por direção, histograma mensal, primeira e última interação, grupos em comum e `evidence_ids`. Quando uma referência corresponde a várias identidades, devolve os `candidates` sem escolher nenhum.
* **Parâmetros:** `contact_a`, `contact_b` (telefone, e-mail, conta, `contact_key` ou nome), `channel`, `start_date`, `end_date`, `evidence_limit` (padrão 5, máximo 20).
* **Exemplo de Pergunta:** *"Como Carlos Teste e Bruno Exemplo se comunicavam, por quais aplicativos e em quais meses?"*

### Como interpretar os resultados de comunicação
As três ferramentas acima usam o mesmo modelo de eventos, derivado somente das propriedades indexadas pelo IPED (`Communication:From`, `Communication:To` e subpropriedades `:id`, `:name` e `:phoneNumber`), em modo somente leitura.

* **Direção explícita:** `incoming` e `outgoing` vêm somente de `Communication:Direction` ou, em chamadas UFED, de `ufed:Direction`. Sem essas propriedades, o evento fica com direção `unknown` e **não** é contado como recebido nem enviado. Ele aparece em `unknown_direction_total`, `unknown_direction_count` e `group_activity`. Mensagens de grupo de WhatsApp costumam não ter direção, então não use eventos `unknown` para concluir quem iniciou uma conversa.
* **Sem presunção de dono:** nenhuma identidade é rotulada como dono do aparelho. O lado local de cada canal é o nó `local:<canal>` (`type: LOCAL_ENDPOINT`, `identity_resolved: false`), que apenas lista os identificadores locais observados (`observed_local_identifiers`). Os demais nós são `ACCOUNT` (pessoa ou conta) e `GROUP` (grupo, com `participants_observed`).
* **Canais:** o canal é o aplicativo real, resolvido nesta ordem: `ufed:SourceApplication` (ex.: `Viber`, `Signal`), `accountType` (ex.: `WhatsApp`, `Telegram`) e `contentType`. Chamadas sem aplicativo aparecem como `Phone`, e-mails como `Email`, e o que não pode ser determinado como `unknown`.
* **Identidades:** telefones são normalizados (ex.: `+1 555-010-1181` e `15550101181` viram a mesma identidade, `tel:15550101181`), e-mails ficam em minúsculas e as variantes aparecem em `aliases`. Remetentes de sistema (`System Message`, `status@broadcast`) são descartados. Use `contact_key` (ou o `id` do nó) para chamar `get_contact_relationship` sem ambiguidade.
* **Truncamento:** `truncated: true` indica que o limite cortou o resultado. Compare `total_contacts_discovered` com `returned_count`, ou `total_eligible_edges` com `returned_edges`. Ausência na lista não significa ausência de vínculo. `unlinked_events` conta eventos sem direção e com apenas uma parte identificada, que não geram aresta.
* **Cobertura:** `diagnostics` informa `events_analyzed` e quantos eventos (e o percentual) têm direção explícita, canal resolvido e data. Quando há filtro de data, `events_without_date_excluded` mostra quantos eventos sem data ficaram de fora.
* **Evidências:** cada contato e cada aresta trazem até 5 `evidence_ids` (o mais antigo, o mais recente e uma amostra determinística), que podem ser abertos com `get_document_metadata`.

---

## 5. Análise Cronológica e Temporal

### `get_timeline`
* **Propósito:** Constrói uma linha do tempo unificada de eventos (mensagens, arquivos criados, chamadas, acessos) dentro de um intervalo de datas ISO-8601.
* **Exemplo de Pergunta:** *"Monte a linha do tempo de eventos ocorridos entre 2024-05-01 e 2024-05-03."*

### `get_events_around_time`
* **Propósito:** Cria uma janela de correlação temporal em torno de um instante crítico (+/- N minutos) para reconstrução circunstancial do momento dos fatos.
* **Exemplo de Pergunta:** *"O que aconteceu no aparelho 30 minutos antes e depois das 14:15 do dia 10 de maio de 2024?"*

---

## 6. Navegação e Árvore de Evidências

### `list_folder_contents`
* **Propósito:** Navega pela estrutura hierárquica de pastas lógicas ou virtuais das mídias periciadas no caso.
* **Exemplo de Pergunta:** *"Liste os arquivos e subpastas localizados no diretório '/Users/alvo/Downloads'."*

### `get_item_relations`
* **Propósito:** Mapeia a genealogia pericial completa do item: elemento pai, subitens extraídos e duplicatas idênticas por hash SHA-256 no caso.
* **Exemplo de Pergunta:** *"O arquivo ID 5020 possui duplicatas idênticas em outras mídias periciadas?"*

---

## 7. Triagem Pericial

### `set_item_checked`
* **Propósito:** Marca ou desmarca o status de conferência/triagem pericial de um item (equivalente à caixa de seleção *checkbox* do IPED Desktop).
* **Exemplo de Pergunta:** *"Marque o item 7890 como conferido na triagem pericial."*

---

## 8. Análise Multimodal e Similaridade

### `get_item_thumbnail`
* **Propósito:** Recupera a miniatura gráfica de imagens e vídeos codificada em Base64 JPEG como bloco multimodal do protocolo MCP.
* **Exemplo de Pergunta:** *"Exiba a miniatura visual da foto ID 25830."*

### `search_similar_images`
* **Propósito:** Realiza busca reversa por imagens visualmente semelhantes através de hashes perceptuais (pHash).
* **Exemplo de Pergunta:** *"Localize imagens visualmente parecidas com a foto ID 1200."*

### `search_similar_faces`
* **Propósito:** Localiza outras fotos no caso que contenham rostos similares ao rosto identificado na foto de referência via redes de reconhecimento facial.
* **Exemplo de Pergunta:** *"Encontre outras fotos contendo a mesma face presente na imagem ID 3450."*

### `search_similar_documents`
* **Propósito:** Encontra documentos textualmente semelhantes ao arquivo de referência utilizando o algoritmo Lucene MoreLikeThis.
* **Exemplo de Pergunta:** *"Busque relatórios ou minutas com conteúdo semelhante ao documento ID 8900."*

---

## 9. Filtros de Inteligência Artificial e Reconhecimento

### `list_ai_filters`
* **Propósito:** Lista as categorias de detecções neurais e de IA computadas pelo IPED disponíveis no caso (armas, drogas, nudez, faces, transcrições de áudio, CSAM, carteiras cripto, estimativa de idade).
* **Exemplo de Pergunta:** *"Quais filtros de IA e modelos de visão computacional foram processados neste caso?"*

### `query_ai_detections`
* **Propósito:** Consulta itens classificados por filtros de redes neurais específicos com pontuações de confiança e limiares de detecção.
* **Exemplo de Pergunta:** *"Mostre as fotos classificadas pelo filtro de armas com confiança acima de 80%."*
