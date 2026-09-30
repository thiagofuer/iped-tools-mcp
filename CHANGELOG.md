# Changelog

Todas as alterações notáveis neste projeto serão documentadas neste arquivo.

O formato é baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.0.0/),
e este projeto adere ao [Versionamento Semântico](https://semver.org/lang/pt-BR/).

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
