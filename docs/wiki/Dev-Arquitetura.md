# Arquitetura e Fluxo de Execução

O **IPED Tools MCP** é construído sobre o ecossistema **Java 21 (LTS)** e **Quarkus 3.x**, operando como um processo nativo do Windows que integra diretamente as bibliotecas internas do IPED Core e do Apache Lucene sem intermediários de rede.

---

## 🏛 Diagrama de Arquitetura do Sistema

```mermaid
graph TD
    subgraph LLM_Client ["Clientes de IA / MCP"]
        Claude["Claude Desktop / Claude Code"]
        CursorApp["Cursor / IDEs"]
        LMStudio["LM Studio (Local / Air-Gapped)"]
        Antigravity["Antigravity / Agentes"]
    end

    subgraph Product ["IPED Tools MCP (Executável Nativo Windows)"]
        Launcher["McpApplication (Main Entrypoint)"]

        subgraph GUI_Layer ["Modo GUI (Swing Desktop)"]
            MainWindow["MainWindow (JFrame)"]
            CaseSelector["JFileChooser (Seletor de Caso)"]
            StatsPanel["Painel de Estatísticas & Métricas"]
            ConfigGen["Gerador de Configuração 1-Click"]
            GuiTimer["Timer de Sincronização (1.5s)"]
        end

        subgraph State_Sync ["Estado Compartilhado"]
            ActiveCaseFile["~/.iped-tools-mcp/active_case.txt"]
        end

        subgraph MCP_Layer ["Modo Headless STDIO (Quarkus MCP Engine)"]
            StdioHandler["STDIO Transport (stdin / stdout)"]
            McpDispatcher["Quarkus MCP Dispatcher"]
            Tools["27 Ferramentas Forenses (@Tool)"]
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

    MainWindow --> CaseSelector
    MainWindow --> StatsPanel
    MainWindow --> ConfigGen
    MainWindow -->|"Grava caso ativo"| ActiveCaseFile
    GuiTimer -->|"Sondagem periódica (1.5s)"| ActiveCaseFile
    GuiTimer -.->|"Atualiza UI se caso alterado via chat"| MainWindow

    StdioHandler <--> McpDispatcher
    McpDispatcher --> Tools
    Tools --> IpedService
    IpedService -->|"Lê caso ativo compartilhado"| ActiveCaseFile

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
    participant State as active_case.txt

    Trigger->>Main: Executa IPED-Tools-MCP.exe
    alt args contém "--stdio"
        Note over Main: Modo Servidor MCP Headless
        Main->>Main: Redireciona System.out para stderr (stream isolation)
        alt Informado "--case [caminho]"
            Main->>Service: IpedCoreService.openCase(caminho)
        else Sem "--case" (Configuração Recomendada)
            Main->>State: Lê caso ativo salvo em disco
            opt Caso ativo existente
                Main->>Service: IpedCoreService.openCase(caminhoSalvo)
            end
        end
        Main->>Quarkus: Inicia Quarkus MCP Server (stdin/stdout)
        Note over Quarkus: Responde a requisições JSON-RPC e sincroniza caso ativo
    else Sem argumentos ou com duplo-clique
        Note over Main: Modo Configurador Gráfico
        Main->>State: Lê último caso ativo
        Main->>Swing: EventQueue.invokeLater(() -> new MainWindow())
        Swing->>Service: Carrega caso e exibe métricas/estatísticas
        Note over Swing: Inicia Timer (1.5s) para refletir comandos do chat na tela
    end
```

---

## 💻 Opções de Linha de Comando (CLI)

O executável nativo suporta os seguintes parâmetros formais:

| Parâmetro | Descrição | Comportamento Operacional |
|---|---|---|
| `--stdio` | Inicia o servidor em modo STDIO MCP. | Desativa a interface gráfica, isola o fluxo `stdout` para JSON-RPC e aguarda requisições na entrada padrão. |
| `--case <caminho>` | Especifica um caso IPED fixo na inicialização. | Opcional. Quando omitido, o servidor utiliza o caso salvo em `~/.iped-tools-mcp/active_case.txt`. |
| `--version`, `-v` | Exibe os dados de versão e licença. | Imprime versão, data do build, runtime Java e versão do IPED Core na saída padrão e finaliza imediatamente com código 0. |
| `--help`, `-h` | Exibe a ajuda da linha de comando. | Lista todos os parâmetros suportados com exemplos de uso. |
