# Arquitetura e Fluxo de Execução

O **IPED Tools MCP** é construído sobre o ecossistema **Java 21 (LTS)** e **Quarkus 3.x**, operando como um processo nativo do Windows que integra diretamente as bibliotecas internas do IPED Core e do Apache Lucene sem intermediários de rede.

---

## 🏛 Diagrama de Arquitetura do Sistema

```mermaid
graph TD
    subgraph LLM_Client ["Clientes de IA / MCP"]
        Claude["Claude Desktop / Claude Code"]
        LMStudio["LM Studio (Local / Air-Gapped)"]
        Antigravity["Antigravity / Agentes"]
        Codex["Codex (app da OpenAI)"]
    end

    subgraph Product ["IPED Tools MCP (Executável Nativo Windows)"]
        Launcher["McpApplication (Main Entrypoint)"]

        subgraph GUI_Layer ["Modo GUI (Swing Desktop)"]
            MainWindow["MainWindow (JFrame)"]
            SelfTest["StdioSelfTest (teste local em processo filho --stdio)"]
            ConfigGen["Gerador de Configuração 1-Click"]
        end

        subgraph MCP_Layer ["Modo Headless STDIO (Quarkus MCP Engine)"]
            StdioHandler["STDIO Transport (stdin / stdout)"]
            McpDispatcher["Quarkus MCP Dispatcher"]
            Tools["Ferramentas Forenses (@Tool)"]
        end

        subgraph Core_Service ["Camada de Serviço Forense"]
            IpedService["IpedCoreService (Singleton In-Process)"]
        end

        subgraph IPED_Core ["Bibliotecas IPED Core (Em Memória)"]
            IPEDSource["iped.engine.data.IPEDSource"]
            IPEDSearcher["iped.engine.search.IPEDSearcher"]
            Bookmarks["iped.engine.data.BitmapBookmarks"]
        end

        subgraph Case_Files ["Estrutura em Disco do Caso IPED"]
            LuceneIndex["<caso>/iped/index/ (Apache Lucene 9.2)"]
            BookmarksFile["<caso>/iped/bookmarks.iped"]
            ConfFiles["<caso>/iped/conf/"]
        end
    end

    LLM_Client -->|"JSON-RPC 2.0 via pipes stdin/stdout"| Launcher
    Launcher -->|"--stdio detectado"| StdioHandler
    Launcher -->|"Sem parâmetros ou duplo-clique"| MainWindow

    MainWindow --> SelfTest
    MainWindow --> ConfigGen
    SelfTest -->|"Executa o comando gerado com --stdio (MCP via pipes)"| Launcher

    StdioHandler <--> McpDispatcher
    McpDispatcher --> Tools
    Tools --> IpedService

    IpedService --> IPEDSource
    IpedService --> IPEDSearcher
    IpedService --> Bookmarks

    IPEDSource --> LuceneIndex
    Bookmarks --> BookmarksFile
```

---

## 🔄 Fluxo de Vida e Execução em Modo Duplo (Dual-Mode)

O ponto de entrada `McpApplication.main(args)` inspeciona os argumentos de execução e roteia o ciclo de vida da aplicação:

```mermaid
sequenceDiagram
    autonumber
    actor Trigger as Invocador (Usuário ou Cliente LLM)
    participant Main as McpApplication.main(args)
    participant Swing as MainWindow (Swing GUI)
    participant Quarkus as Quarkus MCP Bootstrap
    participant Service as IpedCoreService

    Trigger->>Main: Executa IPED-Tools-MCP.exe
    alt args contém "--stdio"
        Note over Main: Modo Servidor MCP Headless
        Main->>Main: Redireciona System.out para stderr (stream isolation)
        opt Informado "--case [caminho]" (configuração antiga)
            Main->>Main: Ignora o argumento e registra um aviso no log
        end
        Main->>Quarkus: Inicia Quarkus MCP Server (stdin/stdout)
        Note over Quarkus: initialize e tools/list respondem sem caso aberto
        Quarkus->>Service: open_case(case_path) pedido pelo modelo
        Service->>Service: Abre o índice do caso só neste processo
        Note over Service: O caso fica aberto até outro open_case ou o fim do processo, sem estado compartilhado com outros processos
    else Sem argumentos ou com duplo-clique
        Note over Main: Modo Configurador Gráfico
        Main->>Swing: EventQueue.invokeLater(() -> new MainWindow())
        Note over Swing: Não abre caso no próprio processo. O teste local executa o servidor --stdio num processo filho
    end
```

---

## 💻 Opções de Linha de Comando (CLI)

O executável nativo suporta os seguintes parâmetros formais:

| Parâmetro | Descrição | Comportamento Operacional |
|---|---|---|
| `--stdio` | Inicia o servidor em modo STDIO MCP. | Desativa a interface gráfica, isola o fluxo `stdout` para JSON-RPC e aguarda requisições na entrada padrão. |
| `--case <caminho>` | Não suportado. | Ignorado com um aviso no log, para não quebrar configurações antigas. O servidor sempre inicia sem caso; o caso é aberto na conversa com `open_case`, só naquele processo. |
| Qualquer outro (inclusive `--version` e `--help`) ou nenhum | Abre o configurador gráfico. | O executável não imprime informações no terminal: o lançador nativo do jpackage não tem console e executa a JVM como processo filho, então a saída se perderia. A versão aparece no título da janela e no "Sobre", e no protocolo MCP (`serverInfo.version` e `get_server_status`). |

A análise dos argumentos fica em `McpApplication.parseArguments`, que devolve o modo (STDIO ou configurador) e se houve `--case` legado.

---

## 🩺 Logs e Diagnóstico

O pacote `br.com.ipedtools.mcp.diagnostics` cuida do registro para suporte:

| Classe | Papel |
|---|---|
| `LogFiles` | Grava o log em `%USERPROFILE%\.iped-tools-mcp\logs\`, um arquivo por modo (`iped-tools-mcp-stdio.log` e `iped-tools-mcp-gui.log`). Faz rotação a cada 5 MB, guardando 5 arquivos. Usa UTF-8, gravação imediata e o PID em cada linha. O handler é instalado no logger raiz no início do `main` e continua ativo depois que o Quarkus configura o log. O log em arquivo do próprio Quarkus não é usado, porque ele repassa os eventos anteriores à sua configuração e duplicaria as linhas de início. O handler grava a partir do nível INFO. Se a pasta não puder ser gravada, o log segue só no `stderr`. |
| `StartupDiagnostics` | Cabeçalho de cada início: versões, modo, argumentos, codificações, `iped.root`, `java.library.path` e as pastas do PATH ligadas a Python, JEP ou IPED, com as DLLs encontradas. |
| `ShutdownLog` | Registra o encerramento do servidor STDIO pelo `ShutdownEvent` do Quarkus, que roda antes de o log ser encerrado. |
| `CrashDiagnostics` | Registra exceções não capturadas e, no configurador, o encerramento. Copia para a pasta de logs os `hs_err_pid*.log` que a JVM deixou na pasta temporária, até 5 por início e sem repetir os já coletados. |

Outros pontos:
* **Etapas da abertura do caso** (`IpedCoreService.caseStep`): cada etapa registra início e fim, com duração. Uma falha fica registrada como `last_error`, devolvido por `get_server_status`.
* **Log do IPED:** as classes do IPED que usam a API log4j2 passam pela ponte `log4j2-jboss-logmanager` e chegam aos mesmos destinos. O `log4j-core` não faz parte do pacote.
* **Conteúdo do caso fica fora do log:** o MCP não registra texto de consultas nem valores devolvidos. A categoria `iped.engine.search.SimilarDocumentSearch`, que registraria os termos do documento de referência, fica em nível WARN no `application.properties`.
