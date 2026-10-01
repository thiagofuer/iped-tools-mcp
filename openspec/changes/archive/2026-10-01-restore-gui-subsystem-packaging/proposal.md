# Proposal: Restaurar Subsistema GUI no Empacotamento Nativo Windows

## Why

Atualmente, ao executar o `IPED-Tools-MCP.exe` por duplo-clique no Windows Explorer ou via atalho na Área de Trabalho/Menu Iniciar, o Windows abre previamente uma janela preta de terminal/console antes de carregar a interface gráfica (Swing). Isso ocorre porque o `jpackage` foi configurado com a flag `--win-console`, marcando o binário PE com o subsistema CUI (Console User Interface). Para peritos criminais e operadores que utilizam o configurador visual, essa janela de terminal em segundo plano prejudica a experiência de uso e a confiabilidade visual da ferramenta pericial.

Além disso, o diretório intermediário `target/dist-build` gerado pelo `jpackage` retém o executável com atributo *Read-Only*, o que impede a execução limpa de `mvn clean package` no Windows sem intervenção manual.

## What Changes

- **Remoção de `--win-console` nos scripts de empacotamento**: Remover a flag `--win-console` dos scripts `scripts/package_app.ps1` e `scripts/package_msi.ps1`, restaurando o subsistema padrão `IMAGE_SUBSYSTEM_WINDOWS_GUI`.
- **Inicialização Gráfica Limpa**: Garantir que o duplo-clique no executável nativo inicialize imediatamente a interface Swing (`MainWindow`) sem alocação ou piscar de janela de console no Windows 10/11.
- **Preservação do Modo MCP STDIO**: Preservar o funcionamento pleno do modo servidor MCP (`--stdio`) acionado por clientes LLM (Claude Desktop, LM Studio, Cursor, Ollama), uma vez que tais ferramentas inicializam o executável via pipes anônimos redirecionados (`stdin`/`stdout`), independentes do subsistema GUI/CUI.
- **Limpeza e Reset de Atributos do Build**: Ajustar `scripts/package_app.ps1` para remover o atributo somente-leitura dos artefatos gerados e limpar o diretório temporário `target/dist-build`, garantindo idempotência e compatibilidade com o `mvn clean`.

## Capabilities

### New Capabilities
*(Nenhuma nova capacidade introduzida)*

### Modified Capabilities
- `native-packaging`: Atualiza os requisitos de empacotamento nativo Windows para exigir subsistema GUI sem console fantasma ao iniciar e limpeza automatizada de artefatos intermediários com atributos de proteção.

## Impact

- **Código e Scripts Afetados**:
  - `scripts/package_app.ps1` (remoção de `--win-console` e rotina de sanitização pós-criação)
  - `scripts/package_msi.ps1` (remoção de `--win-console`)
  - `src/main/java/br/com/ipedtools/mcp/McpApplication.java` (simplificação de manipulações de console Win32 desnecessárias no modo GUI)
- **Compatibilidade**: 100% retrocompatível com Claude Desktop, LM Studio, Cursor e execuções via linha de comando (`test_native_exe.ps1`).
