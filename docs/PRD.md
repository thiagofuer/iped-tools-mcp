# PRD — IPED Tools MCP

**Versão:** 1.0.1  
**Data:** 2026-09-27  
**Status:** Aprovado, Implementado e Validado (v1.0.1)  
**Autor:** Thiago Sampaio Figueiredo

---

## 1. Visão do Produto

**IPED Tools MCP** é um aplicativo desktop standalone que permite a peritos criminais, analistas forenses e investigadores conectar qualquer LLM (local ou remota) a um caso processado pelo IPED para realizar análises forenses assistidas por inteligência artificial.

O produto elimina a necessidade de conhecimento técnico em programação, configuração de servidores ou uso de terminal. O usuário instala o aplicativo, seleciona a pasta do caso IPED e começa a perguntar. O sistema conta com memorização de sessão e sincronização bidirecional em tempo real entre a interface gráfica e o cliente de inteligência artificial.

### 1.1 Objetivo Estratégico: Paridade Funcional com o Módulo de Análise do IPED (`AppMain`)
Além de simplificar o acesso aos dados, o **IPED Tools MCP tem como objetivo central fornecer ao LLM as mesmas ferramentas, visões e capacidades investigativas que a interface gráfica do módulo de análise do IPED (`iped.app.ui.AppMain` / `App.java`) disponibiliza aos seus usuários humanos**. 

Ao espelhar programaticamente o ecossistema analítico da interface pericial do IPED, a IA ganha autonomia para:
* **Explorar Relações Forenses:** Descobrir subitens extraídos (anexos, zips), contêineres pais, itens duplicados por hash MD5/SHA-256 e referências cruzadas entre evidências.
* **Consultar Detecções de IA Integradas:** Filtrar itens por categorias de inteligência artificial geradas no processamento (armas, drogas, nudez, pornografia, CSAM, transcrições de áudio).
* **Executar Buscas por Similaridade:** Realizar buscas por imagens semelhantes (vetores/embeddings), rostos semelhantes (reconhecimento facial) e documentos textuais correlatos.
* **Navegar na Árvore de Diretórios:** Percorrer a estrutura física e lógica do sistema de arquivos e partições da evidência.
* **Reconstruir Linhas do Tempo (Timelines):** Realizar análises cronológicas contextuais em torno de datas e horas críticas de fatos criminosos.
* **Inspecionar Evidências Visualmente (Multimodal):** Analisar miniaturas (*thumbnails*) e fotos diretamente no contexto do modelo multimodal.
* **Mapear Vínculos e Grafos:** Identificar conexões entre interlocutores e fluxos de comunicação.
* **Operar Triagem e Gestão de Laudo:** Marcar itens de interesse pericial (*checked*), criar marcadores (*bookmarks*) e exportar evidências selecionadas para compor o laudo pericial.

---

## 2. Problema

Hoje, para usar IA na análise de um caso IPED, o perito precisa:

1. Iniciar o `iped-webapi` via terminal com argumentos e arquivos de configuração JSON
2. Ter Python instalado e criar um virtualenv com dependências (`mcp`, `httpx`)
3. Configurar manualmente o `mcp_config.json` do cliente LLM
4. Manter dois processos rodando simultaneamente (webapi + bridge MCP)

Esse fluxo é inviável para o público-alvo (peritos e analistas sem background em desenvolvimento de software).

---

## 3. Público-Alvo

| Persona | Descrição | Necessidade Principal |
|---|---|---|
| **Perito Criminal Federal/Estadual** | Utiliza IPED diariamente para processar celulares, HDs, pendrives. Conhecimento técnico em forense digital, mas não em programação. | Interrogar o caso com linguagem natural sem aprender Lucene ou APIs. |
| **Analista de Inteligência** | Trabalha com grandes volumes de dados extraídos. Precisa encontrar padrões rapidamente. | Buscar informações específicas (contatos, transações, mensagens) de forma eficiente. |
| **Delegado / Promotor** | Precisa de respostas rápidas sobre o conteúdo de um caso sem dominar o IPED. | Obter respostas em linguagem natural sobre a evidência sem navegar pela interface do IPED. |

---

## 4. Decisões Técnicas Consolidadas

| Decisão | Escolha | Justificativa |
|---|---|---|
| **Linguagem** | Java 21 | Compatibilidade com libs IPED (Java 11 retrocompat), ecossistema maduro, comunidade forense familiarizada. |
| **Framework** | Quarkus 3.x (JVM mode) | Extensão `quarkus-mcp-server` simplifica implementação MCP. Suporte a STDIO e SSE. |
| **GUI** | Swing | Já incluso no JRE, sem dependências extras. Suficiente para tela de configuração simples. |
| **Acesso ao índice** | Direto via libs IPED core (IPEDSearcher, Lucene) | Elimina dependência do webapi como processo separado. |
| **Empacotamento** | jpackage + jlink (JRE mínimo embutido) | Instalador .msi auto-contido. Usuário não precisa instalar Java. |
| **Protocolo MCP** | JSON-RPC 2.0 sobre STDIO | Padrão universal suportado por Claude Desktop, LM Studio, Cursor, VS Code, etc. |
| **GraalVM nativo** | NÃO | Incompatibilidade com reflexão dinâmica do Lucene. Risco de manutenção inaceitável. |
| **Versões IPED** | 4.x ou superior | Formatos de índice anteriores não serão suportados no MVP. |

---

## 5. Funcionalidades — MVP e v1.0.1

### 5.1 Modo GUI (Configurador, Monitoramento & Diagnóstico)

Ativado quando o usuário executa o aplicativo sem flags (duplo clique no `.exe`).

| ID | Funcionalidade | Descrição |
|---|---|---|
| **GUI-01** | Seleção de caso | Botão "Procurar..." abre um file chooser para o usuário apontar a pasta do caso IPED processado. Valida se contém índice Lucene válido. |
| **GUI-02** | Status do caso | Exibe: nome/caminho do caso, total de itens indexados, categorias encontradas, marcadores existentes. |
| **GUI-03** | Geração de configuração MCP | Gera parâmetros simplificados (apenas `--stdio` para configuração única agnóstica de caso) ou bloco JSON completo, com opção de fixar `--case`. |
| **GUI-04** | Copiar para clipboard | Botões dedicados para copiar caminho do executável (`Command`) e argumentos (`Arguments`) com 1 clique. |
| **GUI-05** | Indicador de status MCP | Mostra se o caso está carregado e pronto para consumo pelo servidor STDIO. |
| **GUI-06** | Log de atividade | Painel de texto com scroll mostrando log das operações, carregamentos e trocas de casos em tempo real. |
| **GUI-07** | Memorização e Restauração | Ao iniciar, restaura automaticamente o último caso salvo em `~/.iped-tools-mcp/active_case.txt`. |
| **GUI-08** | Sincronização Bidirecional | Timer de background na GUI (1.5s) detecta quando o LLM troca de caso via `open_case` e atualiza a tela em tempo real sem intervenção do usuário. |

### 5.2 Modo STDIO (Servidor MCP)

Ativado quando o cliente LLM (Claude Desktop, LM Studio, etc.) inicia o processo com a flag `--stdio`.

| ID | Ferramenta MCP | Descrição |
|---|---|---|
| **MCP-01** | `get_server_status` | Retorna status do servidor e se um caso está carregado. Workflow rule: chamar primeiro. |
| **MCP-02** | `get_case_summary` | Resumo executivo do caso: fontes, total de itens, categorias, bookmarks. |
| **MCP-03** | `list_sources` | Lista fontes de evidência (casos, imagens de disco, extrações mobile) carregadas. |
| **MCP-04** | `get_device_and_owner_info` | Identifica proprietário do dispositivo: contas, telefones, e-mails, IMEI, modelo. |
| **MCP-07** | `search_documents` | Busca geral com sintaxe Lucene. Descrição inclui catálogo completo de categorias IPED. |
| **MCP-08** | `get_document_metadata` | Metadados limpos e token-eficientes de documentos por ID. |
| **MCP-09** | `get_document_text` | Texto extraído de documento com paginação por offset/max_chars. |
| **MCP-10** | `list_categories` | Lista todas as categorias de evidência indexadas no caso. |
| **MCP-11** | `list_bookmarks` | Lista todos os bookmarks/marcadores existentes no caso. |
| **MCP-12** | `add_to_bookmark` | Adiciona evidências a um bookmark para inclusão no laudo pericial. |
| **MCP-13** | `open_case` | Abre ou alterna dinamicamente o caso IPED ativo em tempo de execução via caminho de pasta. |

### 5.3 Princípios das Descrições de Ferramentas (Prompt Engineering)

Todas as ferramentas devem seguir o padrão de enriquecimento contextual:

1. **Workflow Guidance**: Indicar na descrição quando e em que ordem chamar a ferramenta.
2. **Regras Negativas**: Indicar explicitamente o que NÃO fazer (ex: "NEVER search for 'invalid source'").
3. **Catálogo de Valores**: Listar na descrição todos os valores válidos de filtros (categorias IPED, tipos de artefato).
4. **Exemplos de Parâmetros**: Incluir exemplos concretos de sintaxe nos parâmetros (ex: Lucene queries).
5. **Perguntas-Gatilho**: Listar exemplos de perguntas do usuário que devem acionar cada ferramenta.

---

## 6. Fluxos de Usuário

### 6.1 Primeiro Uso (Setup Único no Cliente LLM)

```
Usuário baixa IPED Tools MCP (ou executa o instalador .msi)
  └─► Executa o aplicativo nativo: IPED-Tools-MCP.exe
       └─► Abre a janela GUI do IPED Tools MCP
            └─► Clica em "Procurar..." e seleciona o caso: D:\meu_caso_iped\
                 └─► Aplicativo valida o índice e exibe total de itens e categorias
                      └─► Salva o caso em ~/.iped-tools-mcp/active_case.txt
                           └─► No painel do LM Studio / Claude:
                                ├─► Copia o comando: C:\...\IPED-Tools-MCP.exe
                                └─► Copia o argumento: --stdio
                                     └─► Configuração salva UMA ÚNICA VEZ no cliente LLM
```

### 6.2 Uso Recorrente e Troca Dinâmica de Casos

```
Cenário A: Perito inicia o cliente LLM (LM Studio / Claude Desktop)
  └─► O cliente LLM inicia em background: IPED-Tools-MCP.exe --stdio
       └─► Servidor MCP lê automaticamente o caso ativo (~/.iped-tools-mcp/active_case.txt)
            └─► LLM carrega as ferramentas e responde sobre as evidências do caso atual.

Cenário B: Troca de caso pela GUI
  └─► O perito clica em "Procurar..." na GUI e seleciona: E:\novo_caso\
       └─► A GUI atualiza active_case.txt e exibe as novas métricas.
            └─► Na próxima pergunta no chat, o servidor MCP detecta a troca e recarrega o novo caso.

Cenário C: Troca de caso via Prompt no Chat
  └─► Usuário digita no chat: "Por favor, abra o caso E:\novo_caso"
       └─► LLM executa a ferramenta MCP open_case(case_path="E:\novo_caso")
            └─► O servidor MCP abre o índice e atualiza active_case.txt.
                 └─► A GUI (aberta na tela) detecta a mudança via timer (1.5s) e atualiza automaticamente.
```

---

## 7. Arquitetura de Alto Nível

```
┌────────────────────────────────────────────────────────────────────────┐
│                         IPED Tools MCP                                 │
│                                                                        │
│  ┌──────────────────────┐              ┌────────────────────────────┐  │
│  │      Swing GUI       │              │     Quarkus MCP Server     │  │
│  │                      │              │                            │  │
│  │  • Seletor de Caso   │              │  @Tool get_server_status   │  │
│  │  • Status e Métricas │              │  @Tool get_case_summary    │  │
│  │  • Copiar Cmd & Args │              │  @Tool list_sources        │  │
│  │  • Log em Tempo Real │              │  @Tool get_device_and_...  │  │
│  │  • Timer Sync (1.5s) │              │  @Tool search_documents    │  │
│  │                      │              │  @Tool get_document_meta   │  │
│  └──────────┬───────────┘              │  @Tool get_document_text   │  │
│             │                          │  @Tool list_categories     │  │
│             │                          │  @Tool list_bookmarks      │  │
│             │    ┌────────────────┐    │  @Tool add_to_bookmark     │  │
│             └───►│active_case.txt │◄───┤  @Tool open_case           │  │
│                  └───────┬────────┘    │                            │  │
│                          │             └─────────────┬──────────────┘  │
│                          ▼                           ▼                 │
│             ┌────────────────────────────────────────────────┐         │
│             │               IpedCoreService                  │         │
│             │                                                │         │
│             │  • IPEDSearcher (Lucene 9.2 queries)           │         │
│             │  • Marcadores (BitmapBookmarks R/W)            │         │
│             │  • IPEDSource (case metadata & readers)        │         │
│             │  • syncActiveCaseIfNeeded()                    │         │
│             └───────────────────────┬────────────────────────┘         │
│                                     │                                  │
│             ┌───────────────────────▼────────────────────────┐         │
│             │           Índice Lucene do Caso                │         │
│             │       (leitura direta in-memory, sem HTTP)     │         │
│             └────────────────────────────────────────────────┘         │
│                                                                        │
│  JRE 21 Liberica nativo embutido com flags de reflexão                 │
└────────────────────────────────────────────────────────────────────────┘
```

### 7.1 Camadas

| Camada | Responsabilidade | Componentes |
|---|---|---|
| **Apresentação** | Interface com o perito (GUI) e com o cliente LLM (STDIO) | Swing JFrame, Quarkus MCP STDIO transport |
| **Ferramentas MCP** | Lógica forense de alto nível, prompt engineering nas descrições | Classes `@Tool` (11 ferramentas ativas + `open_case`) |
| **Sincronização de Estado** | Desacoplamento e sincronização entre GUI e cliente LLM | `active_case.txt` em `~/.iped-tools-mcp/` |
| **Serviço IPED Core** | Abstração sobre as APIs internas do IPED para leitura do índice | Wrapper sobre IPEDSearcher, Marcadores, CategoryManager |
| **Dados** | Índice Lucene persistido em disco pelo IPED | Acesso read-only (exceto marcadores) |

---

## 8. Requisitos Não-Funcionais

| Requisito | Meta | Justificativa |
|---|---|---|
| **Tempo de startup** | < 5 segundos para carregar caso | Peritos não toleram espera longa para iniciar análise. |
| **Consumo de memória** | < 512 MB RAM | Máquinas de perícia frequentemente rodam IPED + outros softwares simultaneamente. |
| **Tamanho do instalador** | ~330 MB (.msi completo com JRE 21 embutido) | Auto-contido: não exige instalação prévia de Java nem download externo. |
| **Offline** | 100% funcional sem internet | Laudos periciais são produzidos em redes isoladas (air-gapped). O produto não requer internet. O LLM pode ser local (LM Studio / Ollama). |
| **Segurança** | Acesso somente local via pipes STDIO | Dados forenses são sigilosos. Nenhuma porta de rede ou listener HTTP é aberto. |
| **Integridade** | Read-only exceto marcadores | Nenhuma operação MCP altera o índice ou as evidências originais. |

---

## 9. Estrutura do Projeto (Maven)

```
IPEDToolsMCP/
├── docs/                                  # Artefatos SDD
│   ├── PRD.md                             # Este documento (requisitos e produto)
│   ├── DESIGN.md                          # Arquitetura detalhada e decisões técnicas
│   └── TASKS.md                           # Backlog de tarefas e status de implementação
│
├── scripts/                               # Scripts de automação PowerShell
│   ├── package_app.ps1                    # Build da distribuição portátil nativa via jpackage
│   ├── package_msi.ps1                    # Geração do instalador Windows .msi
│   ├── test_stdio_mcp.ps1                 # Testes de integração STDIO / JSON-RPC
│   └── test_native_exe.ps1                # Teste ponta a ponta do binário nativo .exe
│
├── src/
│   └── main/
│       ├── java/
│       │   └── br/com/ipedtools/mcp/
│       │       ├── McpApplication.java    # Entrypoint (dual-mode: GUI ou STDIO)
│       │       ├── gui/
│       │       │   └── MainWindow.java    # Swing GUI (configurador & live sync)
│       │       ├── tools/
│       │       │   ├── ServerStatusTool.java   # @Tool get_server_status
│       │       │   ├── CaseSummaryTool.java    # @Tool get_case_summary
│       │       │   ├── SourcesTool.java        # @Tool list_sources
│       │       │   ├── DeviceOwnerTool.java    # @Tool get_device_and_owner_info
│       │       │   ├── DocumentSearchTool.java # @Tool search_documents
│       │       │   ├── DocumentMetadataTool.java # @Tool get_document_metadata
│       │       │   ├── DocumentTextTool.java   # @Tool get_document_text
│       │       │   ├── CategoryListTool.java   # @Tool list_categories
│       │       │   ├── BookmarkTools.java      # @Tool list_bookmarks, add_to_bookmark
│       │       │   └── OpenCaseTool.java       # @Tool open_case (troca dinâmica)
│       │       └── service/
│       │           └── IpedCoreService.java    # Wrapper direto sobre IPEDSearcher / Lucene
│       └── resources/
│           └── application.properties          # Configurações Quarkus STDIO
│
├── dist/                                  # Binários gerados para distribuição
│   ├── IPED-Tools-MCP/                    # Pacote portátil com JRE 21 embutido
│   │   ├── IPED-Tools-MCP.exe             # Executável nativo Windows
│   │   └── app/IPED-Tools-MCP.cfg         # Configurações JVM e add-opens
│   └── IPED-Tools-MCP-1.0.0.msi           # Instalador do Windows
│
├── pom.xml                                # Maven + Quarkus BOM + IPED core dependencies
└── README.md                              # Instruções de build e desenvolvimento
```

---

## 10. Dependências Externas (Maven)

| Dependência | Versão | Propósito |
|---|---|---|
| `io.quarkus:quarkus-bom` | 3.x (LTS mais recente) | BOM do Quarkus |
| `io.quarkiverse.mcp:quarkus-mcp-server` | latest | Extensão MCP Server (STDIO transport) |
| `iped-engine` | 4.x | IPEDSearcher, IPEDSource, Marcadores |
| `iped-utils` | 4.x | Utilitários IPED |
| `org.apache.lucene:lucene-core` | (versão usada pelo IPED 4.x) | Leitura de índice |
| `com.fasterxml.jackson.core:jackson-databind` | (via Quarkus) | JSON serialization |

> **Nota:** As libs `iped-engine` e `iped-utils` serão referenciadas como dependências locais (system scope ou repositório Maven local) a partir do IPED 4.x source.

---

## 11. Fora do Escopo (v1.0)

| Item | Motivo |
|---|---|
| Transporte SSE/HTTP | MVP usa apenas STDIO. SSE pode ser adicionado em v2. |
| Exportação de relatórios | O IPED já faz isso. O MCP foca em consulta e marcação. |
| Processamento de novas evidências | O MCP é somente para análise de casos já processados. |
| Suporte a IPED < 4.0 | Formatos de índice antigos aumentariam complexidade. |
| Integração direta com LLM (inferência local) | O produto é um servidor MCP; o LLM é responsabilidade do cliente. |
| Atualização automática (auto-update) | Será avaliado para v2. |

---

## 12. Métricas de Sucesso

| Métrica | Meta v1.0 |
|---|---|
| Tempo do download à primeira pergunta respondida | < 10 minutos |
| Perguntas respondidas corretamente pela LLM usando as ferramentas | > 85% |
| Crashes ou erros fatais por sessão | 0 |
| Downloads no primeiro mês (ipedtools.com.br) | > 100 |
| Feedback positivo de peritos beta-testers | > 80% satisfação |

---

## 13. Roadmap e Features Derivadas do Módulo de Análise (`AppMain`)

Para atingir a paridade funcional com o módulo de análise do IPED (`iped.app.ui.AppMain`), as seguintes features forenses foram mapeadas e estão documentadas detalhadamente em [`docs/features/`](file:///C:/Users/thiag/Downloads/workspace/IPEDToolsMCP/docs/features/):

| Código | Feature | Descrição | Status SDD |
|---|---|---|---|
| **FEAT-01** | [Relações Forenses de Itens](file:///C:/Users/thiag/Downloads/workspace/IPEDToolsMCP/docs/features/feat-01-relacoes-forenses.md) | Subitens, contêiner pai, duplicatas por hash (MD5/SHA256) e referências cruzadas. | Documentado |
| **FEAT-02** | [Buscas por Similaridade](file:///C:/Users/thiag/Downloads/workspace/IPEDToolsMCP/docs/features/feat-02-similaridade-multimodal.md) | Busca de imagens semelhantes, reconhecimento/agrupamento facial e documentos similares. | Documentado |
| **FEAT-03** | [Filtros de Inteligência Artificial](file:///C:/Users/thiag/Downloads/workspace/IPEDToolsMCP/docs/features/feat-03-filtros-ia.md) | Consulta a detecções de IA (armas, drogas, nudez, pornografia, CSAM, transcrições). | Documentado |
| **FEAT-04** | [Linha do Tempo Forense (Timeline)](file:///C:/Users/thiag/Downloads/workspace/IPEDToolsMCP/docs/features/feat-04-linha-do-tempo.md) | Reconstrução cronológica de eventos em torno de marcos temporais do fato investigado. | Documentado |
| **FEAT-05** | [Inspeção Multimodal & Miniaturas](file:///C:/Users/thiag/Downloads/workspace/IPEDToolsMCP/docs/features/feat-05-miniaturas-multimodal.md) | Extração de thumbnails e imagens em Base64 para modelos de visão (multimodais). | Documentado |
| **FEAT-06** | [Árvore do Sistema de Arquivos](file:///C:/Users/thiag/Downloads/workspace/IPEDToolsMCP/docs/features/feat-06-arvore-evidencias.md) | Navegação hierárquica por pastas, diretórios, volumes e discos físicos/lógicos. | Documentado |
| **FEAT-07** | [Triagem Forense e Exportação](file:///C:/Users/thiag/Downloads/workspace/IPEDToolsMCP/docs/features/feat-07-exportacao-triagem.md) | Marcação de itens (*checked*), comentários periciais e exportação de evidências para laudo. | Documentado |
| **FEAT-08** | [Análise de Vínculos e Grafos](file:///C:/Users/thiag/Downloads/workspace/IPEDToolsMCP/docs/features/feat-08-grafo-comunicacoes.md) | Grafo de contatos e comunicações (quem falou com quem, ligações, mensagens). | Documentado |
| **FEAT-09** | [Dicionário Forense de Metadados](file:///C:/Users/thiag/Downloads/workspace/IPEDToolsMCP/docs/features/feat-09-dicionario-metadados-propriedades.md) | Catálogo de propriedades estruturadas dos decodificadores do IPED (WhatsApp, Web, EXIF, EVTX, UFED) e eliminação do filtro restritivo de metadados. | Documentado |
