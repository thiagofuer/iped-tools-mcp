# Design

## Context

Clientes LLM desktop no Windows (como Claude Desktop instalado via Microsoft Store / pacote MSIX) executam subprocessos MCP herdando o diretório de trabalho padrão do host ou container, que frequentemente aponta para `C:\Windows\System32`. Além disso, o protocolo MCP STDIO exige que o servidor responda prontamente às mensagens de inicialização (`initialize` e `tools/list`).

Veja `proposal.md` para a motivação detalhada e análise de causa raiz.

## Goals / Non-Goals

**Goals:**
- Garantir que o executável nativo do IPED Tools MCP inicie de forma confiável sob qualquer diretório de trabalho herdado, especialmente `C:\Windows\System32`.
- Evitar que o Quarkus tente inspecionar o diretório protegido `C:\Windows\System32\config`, eliminando `AccessDeniedException` e terminação precoce do processo.
- Reduzir o tempo de resposta da inicialização MCP STDIO para menos de 500 ms, eliminando timeouts do cliente Claude Desktop.
- Manter a integridade da sincronização em tempo de execução do caso pericial ativo.

**Non-Goals:**
- Não alterar a lógica forense ou de busca do IPED Core (`IPEDSource`, `IPEDSearcher`).
- Não alterar a interface gráfica Swing nem suas operações interativas.
- Não introduzir dependências externas adicionais.

## Decisions

### 1. Defesa em Duas Camadas para `user.dir`
- **Decisão:** Aplicar a proteção tanto na configuração do empacotador nativo (`-Duser.dir=$APPDIR`) quanto programaticamente no método `main` de `McpApplication`.
- **Racional:**
  - *Camada 1 (Launcher nativo):* A flag `-Duser.dir=$APPDIR` no `app/IPED-Tools-MCP.cfg` instrui a JVM no momento do boot do launcher jpackage a definir `user.dir` como a pasta de instalação da aplicação.
  - *Camada 2 (Código Java):* Caso o usuário execute o JAR diretamente (`java -jar`) ou o launcher seja invocado sem a flag, o código de `main` detecta se `user.dir` contém `system32` ou é somente-leitura e o redefine para `~/.iped-tools-mcp`.
- **Alternativas consideradas:**
  - *Desativar ConfigDiagnostic no Quarkus via properties:* Não elimina o comportamento padrão de scanning de arquivos de configuração em `./config`.

### 2. Pré-carregamento Assíncrono do Caso Ativo no Modo STDIO
- **Decisão:** Mover a chamada `IpedCoreService.getInstance().syncActiveCaseIfNeeded()` em modo STDIO (quando `--case` não é fornecido) para uma thread daemon em segundo plano (`iped-case-preloader`).
- **Racional:**
  - Permite que `Quarkus.run(args)` assuma a escuta do canal `stdin` imediatamente, completando o handshake MCP em menos de 0,5s.
  - Caso o cliente envie uma chamada de ferramenta enquanto o índice ainda está sendo aberto, o método interno `openCaseInternal` já é protegido por `synchronized`, aguardando de forma limpa e transparente o término da leitura do índice sem concorrência.
- **Alternativas consideradas:**
  - *Carregamento 100% preguiçoso (lazy):* Não iniciaria a leitura do índice até a primeira pergunta da IA, tornando a primeira resposta ligeiramente mais lenta. A thread em background aquece o cache de disco imediatamente sem atrasar o handshake.

## Risks / Trade-offs

- **[Risco] Chamada de ferramenta antes do término do pré-carregamento:**  
  *Mitigação:* `IpedCoreService.openCaseInternal` e `syncActiveCaseIfNeeded` utilizam monitor `synchronized`. Qualquer requisição concorrente de ferramenta aguarda o lock naturalmente sem falhar.
- **[Risco] Diferença entre ambiente de desenvolvimento e executável jpackage:**  
  *Mitigação:* A salvaguarda em `McpApplication.java` protege tanto a execução em desenvolvimento via `java -jar` quanto a execução do `.exe` distribuído.
