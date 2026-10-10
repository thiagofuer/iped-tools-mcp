# Tasks

## 1. Proteção do Diretório de Trabalho (user.dir)

- [x] 1.1 Implementar salvaguarda de `user.dir` no método `main` de `McpApplication.java` redirecionando para `~/.iped-tools-mcp` quando o diretório herdado apontar para `System32` ou não tiver permissão de escrita, e verificar via teste de unidade ou execução simulada.

## 2. Inicialização Assíncrona do Modo STDIO

- [x] 2.1 Refatorar a sincronização de caso ativo em `McpApplication.java` durante inicialização `--stdio` sem parâmetro `--case`, movendo a execução para uma thread daemon em segundo plano (`iped-case-preloader`) para liberar `Quarkus.run` imediatamente.
- [x] 2.2 Verificar que chamadas concorrentes às ferramentas MCP aguardam o término da carga do caso através da sincronização existente em `IpedCoreService.openCaseInternal`.

## 3. Configuração do Empacotamento Nativo

- [x] 3.1 Adicionar a opção JVM `-Duser.dir=$APPDIR` nos argumentos do `jpackage` em `scripts/package_app.ps1`.
- [x] 3.2 Atualizar o arquivo de configuração de runtime `dist/IPED-Tools-MCP/app/IPED-Tools-MCP.cfg` incluindo `java-options=-Duser.dir=$APPDIR`.

## 4. Validação e Testes Integrados

- [x] 4.1 Executar compilação com `mvn clean package -DskipTests` e verificar geração do runner JAR sem erros.
- [x] 4.2 Executar script de teste automatizado simulando subprocesso spawned com `cwd = C:\Windows\System32` e validar que o processo não encerra com `AccessDeniedException`.
- [x] 4.3 Validar handshake MCP completo (`initialize`, `notifications/initialized`, `tools/list`) respondendo em menos de 1 segundo e retornando todas as 27 ferramentas.
