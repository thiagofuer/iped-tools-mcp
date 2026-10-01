# Design: Restauração do Subsistema GUI e Higienização de Build

## Context

No commit `69a1719`, a flag `--win-console` foi adicionada aos argumentos do `jpackage` em `scripts/package_app.ps1` e `scripts/package_msi.ps1`. O objetivo era facilitar a exibição de `--version` e `--help` no terminal. No entanto, no ecossistema Windows NT (especialmente Windows 11 com o *Windows Terminal* como emulador padrão), binários com subsistema de console forçam a abertura de uma janela de prompt/terminal a cada inicialização por duplo clique, mesmo quando a aplicação chama `FreeConsole()` logo após carregar a JVM.

Para detalhes de motivação, consulte [proposal.md](proposal.md).

## Goals / Non-Goals

**Goals:**
- Restaurar o subsistema nativo `IMAGE_SUBSYSTEM_WINDOWS_GUI` no `IPED-Tools-MCP.exe` removendo `--win-console` dos scripts `package_app.ps1` e `package_msi.ps1`.
- Garantir que o duplo clique no executável abra exclusivamente e de forma limpa a interface visual Swing (`MainWindow`), sem janelas de console anexas.
- Preservar a comunicação do servidor MCP via STDIO (`--stdio`), garantindo que clientes como Claude Desktop, LM Studio e Cursor continuem enviando e recebendo mensagens JSON-RPC 2.0 através dos pipes do processo.
- Assegurar que os testes automatizados de integração do binário nativo (`scripts/test_native_exe.ps1`) continuem passando (incluindo testes de `--version` e chamadas MCP).
- Adicionar rotina de higienização ao final de `package_app.ps1` que remova o diretório intermediário `target/dist-build` e desmarque o atributo *ReadOnly* do binário final, permitindo que comandos como `mvn clean package` funcionem sem erro.

**Non-Goals:**
- Criação de múltiplos executáveis (`IPED-Tools-MCP-cli.exe` vs `IPED-Tools-MCP.exe`), mantendo um binário unificado simples para distribuição e facilidade de suporte pericial.
- Alterações no protocolo JSON-RPC 2.0 ou no catálogo de ferramentas MCP.

## Decisions

### Decisão 1: Remoção de `--win-console` no `jpackage`
- **Escolha**: Omitir a flag `--win-console` em `scripts/package_app.ps1` e `scripts/package_msi.ps1`.
- **Justificativa**: Por padrão, sem `--win-console`, o `jpackage` gera executáveis com `IMAGE_SUBSYSTEM_WINDOWS_GUI`. No Windows, o subsistema GUI nunca instancia console ao ser invocado pelo shell gráfico (`explorer.exe`), resolvendo o problema na raiz no nível do cabeçalho PE do Windows.
- **Alternativas consideradas**:
  - *Esconder console com `ShowWindow(SW_HIDE)` via JNA*: Rejeitado porque o Windows Terminal abre e exibe a janela por frações de segundo antes do Java iniciar, além de poder deixar abas órfãs.
  - *Dual Executable com `--add-launcher`*: Rejeitado por aumentar a complexidade de atalhos e documentação sem necessidade prática para o fluxo pericial predominante.

### Decisão 2: Manutenção do suporte a Pipes e Console Attach
- **Escolha**: Manter a lógica existente em `McpApplication.java` (`attachConsoleOnWindows()` e detecção de pipes `fileType == 3`).
- **Justificativa**: 
  - Quando clientes MCP executam subprocessos com `--stdio`, eles passam handles redirecionados (pipes), os quais funcionam nativamente tanto em processos CUI quanto GUI.
  - Scripts como `test_native_exe.ps1` invocam `& $exe --version | Out-String`, onde o PowerShell redireciona a saída padrão em pipe e captura o texto normalmente.
  - Chamadas manuais diretas no console utilizam `AttachConsole(-1)` para enviar a saída ao console pai quando aplicável.

### Decisão 3: Limpeza de `target/dist-build` e Reset de `IsReadOnly`
- **Escolha**: Ao final de `scripts/package_app.ps1`, executar:
  ```powershell
  if (Test-Path (Join-Path $targetAppDir "IPED-Tools-MCP.exe")) {
      (Get-Item (Join-Path $targetAppDir "IPED-Tools-MCP.exe")).IsReadOnly = $false
  }
  if (Test-Path $buildDestDir) {
      Remove-Item -Recurse -Force $buildDestDir
  }
  ```
- **Justificativa**: O `jpackage` deixa arquivos intermediários marcados como *ReadOnly* na pasta de destino intermediária. Removendo esse diretório no script de empacotamento, o ambiente permanece limpo e o Maven não falha em `mvn clean`.

## Risks / Trade-offs

- **[Risco] Invocação de `--version` em prompt CMD sem redirecionamento**: Em um prompt clássico do CMD (`cmd.exe`), executar um binário GUI direto pode liberar a linha de comando enquanto a saída assíncrona é impressa.
  - *Mitigação*: `attachConsoleOnWindows()` em `McpApplication.java` anexa ao console pai e redireciona para `CONOUT$`. Além disso, scripts de automação utilizam PowerShell com pipes (`| Out-String` ou `| Out-Host`), que capturam a saída de forma síncrona.
