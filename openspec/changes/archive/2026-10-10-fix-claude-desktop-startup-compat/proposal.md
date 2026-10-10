# Proposal

## Why

Ao executar o IPED Tools MCP como servidor MCP STDIO no Claude Desktop no Windows (particularmente quando instalado via Microsoft Store / pacote MSIX), o cliente de IA fecha inesperadamente a conexão (`Server transport closed unexpectedly, this is likely due to the process exiting early`), impedindo que as 27 ferramentas MCP fiquem disponíveis no chat.

A investigação diagnóstica revelou duas causas raízes críticas:
1. **Conflito de Diretório de Trabalho (`C:\Windows\System32\config`):** Processos filhos iniciados a partir de containers MSIX no Windows herdam `C:\Windows\System32` como diretório de trabalho padrão (`user.dir`). O framework Quarkus, ao inicializar, varre a pasta `./config` em busca de arquivos locais de configuração. Ao tentar ler `C:\Windows\System32\config` (diretório restrito de registros do sistema operacional), a JVM dispara `java.nio.file.AccessDeniedException: C:\Windows\System32\config`, encerrando o processo imediatamente com código 1.
2. **Timeout de Handshake STDIO por Carga Síncrona de Índice:** O método `main` executava `IpedCoreService.syncActiveCaseIfNeeded()` de forma bloqueante na thread principal antes de invocar `Quarkus.run(args)`. Em casos periciais volumosos (centenas de milhares de documentos), o carregamento do índice Lucene consome 2 a 3 segundos, excedendo a janela de timeout de inicialização do cliente MCP do Claude Desktop.

## What Changes

- **Proteção Programática de `user.dir`:** Adicionar no ponto de entrada de `McpApplication.main` uma verificação e redirecionamento de `user.dir` para um diretório seguro do usuário (`~/.iped-tools-mcp`) caso o diretório corrente seja o `System32` ou não possua permissões de escrita/leitura.
- **Configuração da JVM no Empacotador Nativo:** Injetar a opção de runtime `-Duser.dir=$APPDIR` nos argumentos do `jpackage` em `scripts/package_app.ps1` e no arquivo de configuração do launcher `app/IPED-Tools-MCP.cfg`.
- **Inicialização Assíncrona do Caso Ativo no Modo STDIO:** Tornar o pré-carregamento do caso ativo assíncrono em uma thread daemon separada (`iped-case-preloader`), permitindo que o `Quarkus.run(args)` escute e responda ao handshake inicial JSON-RPC (`initialize` e `tools/list`) em menos de 500 ms, enquanto o índice é mapeado em segundo plano.

## Capabilities

### Modified Capabilities
- `mcp-server-core`: Atualização dos requisitos de inicialização headless do servidor MCP STDIO para garantir tempo de resposta de handshake inferior a 1 segundo e isolamento contra diretórios de trabalho herdados restritos do Windows (`System32`).
- `native-packaging`: Atualização do requisito "JVM Reflection and Security Configuration" para incluir o parâmetro de inicialização `-Duser.dir=$APPDIR` na configuração do launcher nativo Windows gerado pelo `jpackage`.

## Impact

- **Código-fonte Afetado:**
  - `src/main/java/br/com/ipedtools/mcp/McpApplication.java`: Salvaguarda de `user.dir` e pré-carregador assíncrono em background.
  - `scripts/package_app.ps1`: Adição de `--java-options -Duser.dir=$APPDIR` na chamada do `jpackage`.
- **Compatibilidade:** Compatibilidade retroativa total preservada; nenhuma alteração nas assinaturas das ferramentas MCP nem na integridade estrita de leitura do IPED Core.
- **Ambientes Alvo:** Resolve falhas de conectividade com Claude Desktop (instaladores MSIX e Win32), Cursor e outros clientes MCP no Windows 10/11.
