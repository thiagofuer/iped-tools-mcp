# Changelog

Todas as alterações notáveis neste projeto serão documentadas neste arquivo.

O formato é baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.0.0/),
e este projeto adere ao [Versionamento Semântico](https://semver.org/lang/pt-BR/).

---

## [Unreleased]

### Adicionado
- **Modelo único de eventos de comunicação (`CommunicationAnalytics`, `CommunicationEventExtractor`):**
  - Eventos derivados somente do índice Lucene (somente leitura) a partir de itens com `Communication:From` ou `Communication:To`, com leitura seletiva dos campos armazenados e processamento em *streaming*.
  - Canal resolvido por `ufed:SourceApplication` → `accountType` → `contentType` (ex.: `Viber`, `WhatsApp`, `Telegram`, `Phone`, `Email`), com `unknown` quando não determinável.
  - Identidade das partes pelas subpropriedades estruturadas `Communication:From/To:id|name|phoneNumber` antes do parsing de texto, normalização de telefones com libphonenumber, e-mails em minúsculas, união de aliases e descarte de remetentes de sistema (`System Message`, `status@broadcast`).
  - Mensagens de grupo vinculadas ao nó de grupo (o `Communication:To:id` do chat não vira pessoa).
  - Diagnósticos de cobertura em todas as respostas (`events_analyzed`, direção explícita, canal resolvido e data).
- **Nova ferramenta MCP `get_contact_relationship`:** detalha a relação entre dois contatos (totais, canais, direções, histograma mensal, grupos em comum e `evidence_ids`), devolvendo `candidates` quando a referência é ambígua.
- **Filtros `channel`, `start_date` e `end_date`** em `get_top_contacts`, `get_communications_graph` e `get_contact_relationship`; `include_mermaid` opcional em `get_communications_graph`.
- **Catálogo de propriedades do caso (issue #2):** as propriedades passam a vir do índice do caso, com contagem exata de itens, tipo (`metadataTypes.txt`) e grupo (prefixos do IPED mais o grupo `AI`), junto com a semântica curada das propriedades conhecidas do IPED (significado, escala, valores e exemplos de consulta). Os nomes curados são verificados por teste contra as constantes do IPED.
  - **Nova ferramenta `list_properties`:** grupos do caso e, com `group` ou `pattern`, as propriedades com tipo, contagem, descrição e exemplo de consulta escapado; `category` restringe as contagens à categoria.
  - **Nova ferramenta `describe_property`:** significado, escala, mínimo e máximo (números e datas) ou valores mais frequentes (texto) e exemplos de consulta; com `query`, a distribuição é calculada só sobre os itens da consulta. Propriedade inexistente é informada como ausente (`present: false`), com sugestão de nomes parecidos, em vez de zero itens.
  - **Aviso de propriedade inexistente em `search_documents`:** `warnings` com `property_not_in_case` quando a consulta cita uma propriedade que não existe no caso.
  - **Parâmetro `groups` em `get_document_metadata`** para selecionar grupos do catálogo.
- **Paginação em `search_documents`:** novo parâmetro `offset` e campos `has_more` e `next_offset` na resposta, para percorrer todos os itens de uma consulta em páginas de até 100. A ordem é a do índice do caso, a mesma em todas as chamadas, e a nota da resposta indica as posições exibidas e como pedir a próxima página. A descrição da ferramenta e o prompt `start_case` orientam a não concluir ausência de evidência enquanto houver páginas não lidas. Chamadas sem `offset` continuam funcionando como antes.
- **Rastreabilidade e transparência:** até 5 `evidence_ids` por contato e por aresta (mais antigo, mais recente e amostra determinística), campos `truncated`, `total_eligible_edges`, `unlinked_events` e `unknown_direction_total`.

### Modificado
- **BREAKING — `get_communications_graph`:** o nó sintético `Dispositivo_Local` foi removido; o lado local de cada evento com direção explícita passa a ser o nó `local:<canal>` (`type: LOCAL_ENDPOINT`, `identity_resolved: false`) com os identificadores locais observados. Os nós passam a ter `type` (`ACCOUNT`, `GROUP`, `LOCAL_ENDPOINT`) e os ids são chaves canônicas (ex.: `tel:15550101181`).
- **BREAKING — `channels`:** passa a conter o aplicativo real (ex.: `Viber`) em vez de rótulos genéricos (`chat messages`, `calls`).
- **BREAKING — `get_document_metadata`:** os blocos `basic`, `communication`, `geo`, `forensic` e `extra` dão lugar aos grupos do catálogo (`Basic`, `Communication`, `AI`, `Image`, `UFED`...). A lista de permissões manual foi removida: `nudityScore`, `nudityClass`, `ocrCharCount`, `Regex:*`, `language:*`, `pdf:*` e `office:*` deixam de ser descartados no modo padrão.
- **BREAKING — ferramentas removidas (issue #2):** `list_ai_filters`, `query_ai_detections`, `get_property_dictionary` e `list_available_properties`. Consultavam propriedades que o IPED não grava (`hasWeapon`, `hasDrug`, `isNudity`, `hasFace`, `hasOCR`), usavam a escala errada de `nudityScore`, listavam nomes divergentes (`Message-AttachmentCount` em vez de `Message:AttachmentCount`) e amostravam só os 100 primeiros itens. O servidor passa a expor 26 ferramentas.

  | Ferramenta removida | Como fazer agora |
  |---|---|
  | `list_ai_filters` | `list_properties(group="AI")` |
  | `query_ai_detections` | `describe_property` para a escala e `search_documents` com a propriedade (ex.: `nudityScore:[650 TO *]`, `faceAge\:labels:child`, `ufed\:mediaClasses:Weapons`) |
  | `get_property_dictionary` | `list_properties(group=...)` ou `list_properties(pattern=...)` e `describe_property(name=...)` |
  | `list_available_properties` | `list_properties(category=...)`, com contagens exatas sobre todos os itens da categoria |
- **BREAKING — `get_device_and_owner_info` substituída por `get_device_and_accounts` (issue #3):** a ferramenta devolve só identificadores registrados pela extração e pelos parsers do IPED, cada um com a origem (item, app, propriedade e tipo de fonte) e a quantidade de apps e itens que o sustentam. São eles: entradas de informações do aparelho (IMEI, IMSI, ICCID, MSISDN, modelo), telefones, e-mails, IDs de mensageiro e usernames das contas de usuário, e perfis de usuário e hives do registro em computadores. Nenhum valor é rotulado como do dono, e mídias sem identificadores estruturados recebem resposta explícita com sugestões. O total de ferramentas continua 26.

  | Campo removido | Onde está agora |
  |---|---|
  | `likely_owners`, `likely_owner_names` | Não há substituto: o servidor não aponta o dono. Compare `account_identifiers` (ordenados por `app_count`) e cite os `item_id`. |
  | `owner_phone_numbers`, `owner_emails` | `account_identifiers.phone` e `account_identifiers.email`, com as fontes de cada valor |
  | `primary_accounts`, `user_accounts` | `sources` de cada identificador (item, app e propriedade) |
  | `device_properties` | `device_information` (`name`, `value`, `item_id`) |
  | `user_profile_dirs` | `user_profiles` (com `item_id`) |
  | `system_info` (`registered_owner`, `computer_name`...) | Removido: o IPED não grava essas propriedades. Leia os hives listados em `registry_hives` com `get_document_text`. |
- **`list_properties` com o filtro `identity`:** a semântica curada marca as propriedades de identificação (`phoneNumber`, `userAccount`, `userName`, `emailAddress`, `ufed:PhoneNumber`, `ufed:Username`, `ufed:UserID`, `ufed:EmailAddress`, `ufed:EntryName`, `ufed:EntryValue`); cada propriedade listada informa a marca, e `list_properties(identity=true)` lista só essas.
- **`search_documents` e prompt `start_case`:** deixam de listar nomes fixos de categorias e filtros de IA e orientam a descoberta por `list_categories`, `list_properties` e `describe_property`.
- **`get_top_contacts` e `get_communications_graph` não usam mais `get_device_and_owner_info`, `ufed:PhoneOwner` nem `Conversation:Account`:** nenhuma identidade é rotulada como dono do aparelho.

### Corrigido
- **Telefone do dono tirado de regex (issue #3):** a ferramenta de identificação do aparelho apontava como telefone do dono o primeiro valor de `Regex:PHONE` de arquivos de configuração e trazia valores sem sentido (IDs de usuário do Android, pastas de apps) como prováveis donos. Agora só contas estruturadas e informações do aparelho são fonte, e a conclusão sobre quem usa o aparelho fica com quem analisa.
- **Faixas em propriedades numéricas do processamento (issue #7):** consultas como `nudityScore:[500 TO 1000]`, `ocrCharCount:[1 TO *]` e `audio\:transcriptConfidence:[0.5 TO *]` retornavam 0. Os tipos de `metadataTypes.txt` agora são carregados antes da abertura do índice, e as faixas viram consultas numéricas. Limites decimais escritos com ponto (`0.5`) são convertidos para a localidade do caso. Quando uma faixa não encontra itens, `search_documents` devolve `warnings` dizendo se a propriedade não existe no caso, se é textual ou quais são seus valores mínimo e máximo.
- **Direção das comunicações (issue #4):** `"OUTGOING".contains("IN")` fazia toda mensagem enviada ser contada também como recebida. A direção agora vem somente de `Communication:Direction` ou `ufed:Direction` (comparação exata); chamadas UFED passam a ter direção, e eventos sem direção explícita ficam como `unknown` em vez de serem tratados como recebidos.

---

## [1.0.1] - 2026-10-10

### Adicionado
- **Prompt Forense MCP `start_case` (`ForensicPrompts`):**
  - Implementação do prompt pericial `start_case` estabelecendo o protocolo de engajamento entre o LLM e o caso IPED ativo.
  - Diretiva de *Dynamic Language Mirroring*: saudação inicial padronizada em português brasileiro, adaptando-se fluidamente ao idioma de comunicação do perito.
  - Salvaguardas periciais de conformidade forense: restrição estrita de leitura (read-only no filesystem), preservação de cadeia de custódia e orientação ativa para registro de evidências em marcadores (`add_to_bookmark`) e triagem (`set_item_checked`).
- **Extração de Metadados Brutos e Filtragem Cirúrgica (`DocumentMetadataTool`):**
  - Parâmetro `raw: true` em `get_document_metadata` para extração fidedigna de propriedades Lucene sem truncamento de strings (500 caracteres) ou de listas (10 itens).
  - Parâmetro `keys` com suporte a padrões glob/wildcards case-insensitive (ex: `Hardware-Wallet-*`, `ai:*`, `Communication:*`) para inspeção de campos críticos com economia de tokens de contexto.
- **Integração com Tarefas Python e JavaScript do IPED:**
  - Preservação e roteamento semântico de propriedades geradas por tarefas auxiliares do IPED (`SearchHardwareWallets.py`, `CSAMDetectorTask.py`, `AgeEstimationTask.py`, `NSFWNudityDetectTask.py`).
  - Novo domínio pericial `crypto` catalogando artefatos de hardware wallets (`Hardware-Wallet-Found`, `Hardware-Wallet-VendorName`, `Hardware-Wallet-DeviceName`, etc.).
  - Domínio pericial `ai` enriquecido com modelos de deep learning e redes neurais (`ai:csamDetector:csam`, `faceAge:count:Child`, `nsfw_nudity_score`).
  - Novos filtros em `query_ai_detections` e `list_ai_filters`: `crypto_wallets`, `age_estimation`, `nsfw` e busca híbrida de `csam` (hashes conhecidos + rede neural).
- **Descoberta Centralizada de Fontes de Evidência:**
  - Nova ferramenta MCP `list_sources` expondo as raízes do caso pericial e contêineres analisados.

### Modificado
- **Identificação Generalizada de Dispositivos (`get_device_and_owner_info`):**
  - Suporte híbrido transparente para extrações móveis (UFED/GrayKey) e imagens de computadores (.E01/raw/vmdk).
  - Descoberta e estruturação de múltiplos contêineres forenses sob a chave `evidences`, com classificação automática do caso (`mobile`, `computer` ou `hybrid`).
  - Extração profunda de números de telefone e e-mails utilizando padrões regex nativos do IPED (`Regex:PHONE`, `Regex:EMAIL`, `phone`, `cellPhone`).
- **Atualização do Ecossistema Quarkus:**
  - Atualização do Quarkus para a versão `3.39.3`.
- **Expansão da Cobertura de Testes Automatizados:**
  - Suíte de testes unitários expandida para 52 testes com 100% de aprovação em `IpedCoreServiceTest`, `McpToolsTest` e `VersionInfoTest`.

### Corrigido
- **Compatibilidade de Inicialização com Claude Desktop e Windows MSIX:**
  - Salvaguarda automática da propriedade `user.dir` no método `main` de `McpApplication`, redirecionando para `~/.iped-tools-mcp` quando o processo herda diretório restrito do sistema (`C:\Windows\System32`), prevenindo falhas fatais com `AccessDeniedException` ao acessar `System32\config`.
  - Inicialização assíncrona do leitor de casos IPED no modo `--stdio` via thread daemon (`iped-case-preloader`), liberando o loop de eventos STDIN do Quarkus em menos de 1,5 segundos para evitar estouro de timeout de conexão em clientes MCP.
  - Injeção da opção JVM `-Duser.dir=$APPDIR` no arquivo de configuração nativo `IPED-Tools-MCP.cfg` e script de empacotamento `package_app.ps1`.
- **Subsistema GUI no Executável Nativo Windows:**
  - Remoção da flag `--win-console` no `package_app.ps1` e `package_msi.ps1`, restaurando o subsistema `IMAGE_SUBSYSTEM_WINDOWS_GUI` e prevenindo a abertura de janela fantasma de terminal ao executar o aplicativo por duplo-clique.
  - Preservação dos descritores de stream `stdin`/`stdout` para comunicação JSON-RPC quando executado via clientes MCP.
- **Empacotamento e Permissões no Windows:**
  - Encerramento preventivo de instâncias ativas do `IPED-Tools-MCP.exe` antes da substituição de arquivos no `package_app.ps1`, evitando travamento de arquivos por bloqueio de DLLs no Windows.
  - Higienização pós-build desmarcando o atributo `ReadOnly` dos binários em `dist/` e purga da pasta temporária `target/dist-build`.
  - Correção na configuração do plugin Maven Surefire (`@{argLine}`) no `pom.xml`.

---

## [1.0.0] - 2026-09-29

### Adicionado
- **Arquitetura de Servidor MCP Nativo:**
  - Servidor construído sobre o ecossistema Quarkus 3.17.8 e Java 21 LTS de alta performance.
  - Implementação completa do protocolo **Model Context Protocol (MCP)** versão `2024-11-05` via transporte STDIO (JSON-RPC 2.0).
  - Leitor in-process direto sobre índices Apache Lucene e bancos SQLite do IPED (`IpedCoreService`), sem necessidade de servidor HTTP webapi intermediário.

- **Catálogo de 27 Ferramentas Periciais Forenses (MCP Tools):**
  - **Gestão de Casos e Conectividade:** `get_server_status`, `check_connection`, `get_case_summary`, `open_case` (com suporte a troca a quente de casos).
  - **Dicionário e Descoberta de Metadados:** `get_property_dictionary`, `list_available_properties`.
  - **Busca e Extração Forense:** `search_documents`, `get_document_metadata`, `get_document_text`, `list_categories`, `list_bookmarks`, `add_to_bookmark`.
  - **Inteligência de Dispositivos e Contatos:** `get_device_and_owner_info`, `get_top_contacts`, `get_communications_graph`.
  - **Análise Temporal e Cronológica:** `get_timeline`, `get_events_around_time`.
  - **Navegação e Estrutura de Evidências:** `list_folder_contents`, `get_item_relations`.
  - **Triagem Pericial:** `set_item_checked` (marcação de itens conferidos na evidência).
  - **Investigação Multimodal e Similaridade:** `get_item_thumbnail`, `search_similar_images` (pHash), `search_similar_faces` (reconhecimento facial), `search_similar_documents` (MoreLikeThis).
  - **Filtros e Detecções de IA:** `list_ai_filters`, `query_ai_detections`.

- **Interface Gráfica Integrada (Desktop Swing):**
  - Painel de controle `MainWindow` com indicação visual de status do servidor, caso ativo, uso de memória JVM e ferramentas carregadas.
  - Seletor de casos IPED com validação de pasta de índice.
  - Janela modal interativa "ℹ Sobre" apresentando detalhes de versão, build, links para [www.ipedtools.com.br](https://www.ipedtools.com.br) e repositório GitHub.

- **Infraestrutura de Versionamento Semântico:**
  - `pom.xml` consolidado como fonte única da verdade para a versão do produto (`1.0.0`).
  - Geração automática de `version.properties` durante o ciclo de build do Maven (`maven.build.timestamp`).
  - Classe utilitária `VersionInfo` provendo metadados de versão, data de compilação e versão do IPED Core.
  - Suporte completo às flags de linha de comando `--version` e `-v` com saída em texto limpo e encerramento imediato.
  - Exposição de `server_version` nas ferramentas de diagnóstico MCP e publicidade de versão no handshake `initialize`.

- **Pipeline de Empacotamento e Distribuição Automatizado:**
  - Script `scripts/package_app.ps1` com geração de runtime Java 21 enxuto via `jlink`, imagem nativa Windows `.exe` via `jpackage` e pacote portátil `.zip` (`IPED-Tools-MCP-1.0.0-windows-x64-portable.zip`).
  - Script `scripts/package_msi.ps1` para geração do instalador pericial Windows `.msi` (`IPED-Tools-MCP-1.0.0.msi`), com download automatizado do WiX Toolset 3.11 sob demanda.
  - Geração automática de manifesto criptográfico de integridade forense `dist/SHA256SUMS.txt`.

- **Governança do Repositório e Garantias Forenses:**
  - Arquivo `.gitignore` robusto protegendo contra commit acidental de evidências periciais (`*.iped`, `active_case.txt`, bancos locais) e binários pesados (`dist/`, `target/`, `tools/`).
  - `README.md` detalhado com arquitetura, catálogo de ferramentas e guia de uso com Claude Desktop.
  - `CONTRIBUTING.md` formalizando o modelo GitFlow tradicional, regras SemVer e fluxo de PRs.
  - Bateria de testes de integração automatizados (`scripts/test_stdio_mcp.ps1` com 25 testes do protocolo e `scripts/test_native_exe.ps1` para o binário nativo).

[Unreleased]: https://github.com/thiagofuer/iped-tools-mcp/compare/v1.0.1...HEAD
[1.0.1]: https://github.com/thiagofuer/iped-tools-mcp/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/thiagofuer/iped-tools-mcp/releases/tag/v1.0.0
