# Backlog de Tarefas — IPED Tools MCP

**Versão:** 1.0.1  
**Data:** 2026-09-27  
**Status:** Concluído e Validado (v1.0.1)  
**Referências:** [PRD.md](file:///C:/Users/thiag/Downloads/workspace/IPEDToolsMCP/docs/PRD.md) | [DESIGN.md](file:///C:/Users/thiag/Downloads/workspace/IPEDToolsMCP/docs/DESIGN.md)  

---

## Estrutura do Backlog e Fases

```text
Fase 1: Setup do Projeto Maven & Dependências Quarkus + IPED Core [CONCLUÍDA]
Fase 2: Camada de Serviço In-Process (IpedCoreService) [CONCLUÍDA]
Fase 3: Implementação das Ferramentas MCP (@Tool) com Prompt Engineering [CONCLUÍDA]
Fase 4: Ponto de Entrada Dual-Mode (CLI / Dispatcher) [CONCLUÍDA]
Fase 5: Interface Gráfica Swing (MainWindow) [CONCLUÍDA]
Fase 6: Testes Integrados e Validação [CONCLUÍDA]
Fase 7: Pipeline de Empacotamento (Uber-JAR + jlink + jpackage MSI) [CONCLUÍDA]
Fase 8: Sincronização Dinâmica de Casos e Aprimoramento da UX [CONCLUÍDA]
```

---

## Fase 1: Setup do Projeto Maven & Dependências

- [x] **TASK-01**: Criar o arquivo `pom.xml` raiz com Quarkus BOM 3.x, Java 21 e extensão `quarkus-mcp-server-stdio`.
  - Configurar dependências do `iped-engine` e `iped-api` (localmente ou via system scope / local repo).
  - Incluir dependência do `org.apache.lucene:lucene-core:9.2.0`.
- [x] **TASK-02**: Criar `src/main/resources/application.properties` com configurações de STDIO seguro:
  - `quarkus.mcp.server.stdio.enabled=true`
  - `quarkus.mcp.server.stdio.null-system-out=true`
  - `quarkus.log.console.stderr=true`
  - `quarkus.banner.enabled=false`
  - `quarkus.package.jar.type=uber-jar`
- [x] **TASK-03**: Configurar compilação no Maven e verificar se o build inicial passa com sucesso.

---

## Fase 2: Camada de Serviço In-Process (`IpedCoreService`)

- [x] **TASK-04**: Implementar classe singleton `IpedCoreService.java`.
  - Método `openCase(File caseDir)`: validação com `IPEDSource.checkIfIsCaseFolder`, inicialização de `Configuration` e instanciação de `IPEDSource`.
  - Método `closeCase()`: liberação ordenada dos leitores Lucene e thread pools.
- [x] **TASK-05**: Implementar busca Lucene:
  - Método `executeSearch(String queryText, int limit)` utilizando `IPEDSearcher`.
  - Retornar IDs de documentos indexados e contagem total de hits.
- [x] **TASK-06**: Implementar extração de metadados:
  - Método `getDocumentMetadata(int itemId)`: leitura de campos do Lucene via `source.getLuceneId(id)` e higienização para redução de tokens.
  - Inclusão de marcadores associados e status de checagem do item.
- [x] **TASK-07**: Implementar extração de texto:
  - Método `getDocumentText(int itemId, int offset, int maxChars)` utilizando `StandardParser`, `ParsingTask` e Tika `ToTextContentHandler`.
  - Paginação segura com indicador de truncamento para proteger a janela de contexto da LLM.
- [x] **TASK-08**: Implementar operações de marcadores e categorias:
  - Método `listCategories()` via `source.getLeafCategories()`.
  - Método `listBookmarks()` via `source.getBookmarks()`.
  - Método `addBookmark(String name, List<Integer> docIds)` com persistência síncrona `saveState(true)`.

---

## Fase 3: Implementação das Ferramentas MCP (`@Tool`)

- [x] **TASK-09**: Implementar `ServerStatusTool.java` (`get_server_status`).
  - Incluir workflow guidance na descrição ("Call this tool first...").
- [x] **TASK-10**: Implementar `CaseSummaryTool.java` (`get_case_summary`).
  - Total de itens (`*:*`), categorias ativas e marcadores existentes.
- [x] **TASK-11**: Implementar `SourcesTool.java` (`list_sources`).
  - Listagem de fontes de dados e caminhos absolutos.
- [x] **TASK-12**: Implementar `DeviceOwnerTool.java` (`get_device_and_owner_info`).
  - Extração de contas (`category:"user accounts"`), telefones, e-mails, modelo e IMEI (`category:"device information"`).
  - Incluir regras negativas explícitas na descrição ("NEVER search for 'proprietario' using text search").
- [x] **TASK-13**: Implementar `DocumentSearchTool.java` (`search_documents`).
  - Documentar catálogo completo de categorias IPED na anotação `@Tool` (WhatsApp, Telegram, e-mails, etc.).
  - Incluir exemplos de sintaxe Lucene.
- [x] **TASK-14**: Implementar `DocumentMetadataTool.java` (`get_document_metadata`).
  - Suporte a consultas individuais e em lote (batch).
- [x] **TASK-15**: Implementar `DocumentTextTool.java` (`get_document_text`).
  - Paginação por offset/max_chars.
- [x] **TASK-16**: Implementar `CategoryListTool.java` (`list_categories`) e `BookmarkTools.java` (`list_bookmarks`, `add_to_bookmark`).

---

## Fase 4: Ponto de Entrada Dual-Mode (CLI / Dispatcher)

- [x] **TASK-17**: Implementar `McpApplication.java` (`public static void main(String[] args)`).
  - Parser de argumentos CLI (`--stdio`, `--case <path>`, `--gui`, `--help`).
  - Roteamento condicional:
    - Se `--stdio`: inicializa `IpedCoreService` com o caso informado e dispara o runner Quarkus MCP.
    - Se sem argumentos: dispara `MainWindow` do Swing no `EventQueue.invokeLater`.

---

## Fase 5: Interface Gráfica Swing (`MainWindow`)

- [x] **TASK-18**: Construir layout Swing com Look and Feel nativo do sistema operacional (Windows LookAndFeel).
  - Campo de texto e botão "Procurar..." com `JFileChooser` para seleção da pasta do caso.
- [x] **TASK-19**: Implementar validação visual e resumo do caso carregado:
  - Validação da estrutura `iped/index`, contagem de itens e lista de categorias.
- [x] **TASK-20**: Implementar painel de geração de configuração:
  - Área de texto com o JSON formatado para Claude Desktop, LM Studio e Cursor.
  - Botão "Copiar para Clipboard" com notificação visual de sucesso.
- [x] **TASK-21**: Implementar console de log em tempo real (JTextArea com scroll) capturando mensagens informativas de diagnóstico.

---

## Fase 6: Testes Integrados e Validação

- [x] **TASK-22**: Testar execução do `IpedCoreService` contra caso de teste do IPED 4.x indexado.
- [x] **TASK-23**: Testar comunicação STDIO via JSON-RPC utilizando script mock de validação (`scripts/test_stdio_mcp.ps1` - 10/10 testes passando).
- [x] **TASK-24**: Testar integração ponta a ponta com **LM Studio** e **Claude Desktop**.
  - Validar perguntas reais ("Resumo do caso", "Quem é o dono do aparelho?", "Busque mensagens suspeitas").

---

## Fase 7: Pipeline de Empacotamento e Distribuição

- [x] **TASK-25**: Configurar geração do Uber-JAR no Maven (`target/iped-tools-mcp-1.0.0-SNAPSHOT-runner.jar`).
- [x] **TASK-26**: Criar script para geração de runtime Java enxuto via `jlink` (`target/runtime` - 60.8MB com JRE 21 embutido).
- [x] **TASK-27**: Configurar `jpackage` para gerar o instalador do Windows (`.msi`) com atalho na Área de Trabalho e Menu Iniciar (`dist/IPED-Tools-MCP-1.0.0.msi` e pacote portátil `dist/IPED-Tools-MCP-portable-v1.0.0.zip`).
- [x] **TASK-28**: Testar execução nativa isolada (`dist/IPED-Tools-MCP/IPED-Tools-MCP.exe`) sem depender de Java instalado no sistema (`scripts/test_native_exe.ps1` 100% aprovado).

---

## Fase 8: Sincronização Dinâmica de Casos e Aprimoramento da UX

- [x] **TASK-29**: Implementar persistência de estado do caso ativo em disco (`~/.iped-tools-mcp/active_case.txt`).
- [x] **TASK-30**: Implementar restauração e recarga automática do caso no startup da GUI (`MainWindow.java`).
- [x] **TASK-31**: Implementar detecção e recarga em tempo real na instância STDIO (`IpedCoreService.syncActiveCaseIfNeeded()`).
- [x] **TASK-32**: Implementar ferramenta MCP `open_case` ([`OpenCaseTool.java`](file:///C:/Users/thiag/Downloads/workspace/IPEDToolsMCP/src/main/java/br/com/ipedtools/mcp/tools/OpenCaseTool.java)) permitindo alternância dinâmica de casos via chat na LLM.
- [x] **TASK-33**: Implementar timer de sincronização reversa na GUI (STDIO/chat -> GUI) a cada 1.5s para refletir comandos da LLM na tela sem intervenção do perito.
- [x] **TASK-34**: Reformulação da aba/painel de configuração na GUI com suporte a configuração única (`--stdio`), botões de cópia rápida para `Command` e `Arguments`, opção de fixar caso (`--case`) e orientações sobre formatação de caminhos no LM Studio.
- [x] **TASK-35**: Configurar parâmetros de JVM (`--add-opens`, `-Djava.security.manager=allow`) no pacote nativo `app/IPED-Tools-MCP.cfg` para total compatibilidade com `RegexTask` / `BigDecimal.intVal` do IPED no Java 21.
- [x] **TASK-36**: Automação e validação do pipeline de empacotamento (`package_app.ps1`, `package_msi.ps1`) gerando o portátil nativo `dist/IPED-Tools-MCP/IPED-Tools-MCP.exe` e instalador `dist/IPED-Tools-MCP-1.0.0.msi`.
- [x] **TASK-37**: Teste de regressão ponta a ponta (`scripts/test_native_exe.ps1`) validando inicialização nativa, recuperação de dados e alternância dinâmica entre casos no disco (100% aprovado).
