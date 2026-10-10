# Changelog

Todas as alterações notáveis neste projeto serão documentadas neste arquivo.

O formato é baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.0.0/),
e este projeto adere ao [Versionamento Semântico](https://semver.org/lang/pt-BR/).

---

## [Unreleased]

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
