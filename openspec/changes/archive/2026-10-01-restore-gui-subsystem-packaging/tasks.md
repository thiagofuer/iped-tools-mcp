# Tasks: Restauração do Subsistema GUI e Higienização de Build

## 1. Ajuste dos Scripts de Empacotamento e Configuração

- [x] 1.1 Remover a flag `--win-console` dos argumentos do `jpackage` em `scripts/package_app.ps1` e verificar ausência de referências a console launcher
- [x] 1.2 Remover a flag `--win-console` dos argumentos do `jpackage` em `scripts/package_msi.ps1` e verificar integridade da lista de argumentos
- [x] 1.3 Implementar etapa de higienização ao final de `scripts/package_app.ps1` para remover o atributo `IsReadOnly` do executável final em `dist/` e purgar a pasta intermediária `target/dist-build`
- [x] 1.4 Adicionar prefixo `@{argLine}` na configuração de `argLine` do `maven-surefire-plugin` em `pom.xml` e verificar eliminação do aviso do Quarkus

## 2. Empacotamento Nativo e Validação Pericial

- [x] 2.1 Executar a limpeza e compilação do runner JAR com `mvn clean package -DskipTests` e verificar saída `BUILD SUCCESS` sem erros de deleção de arquivos
- [x] 2.2 Executar o script `scripts/package_app.ps1` e verificar geração de `dist/IPED-Tools-MCP/IPED-Tools-MCP.exe`, pacote `.zip` e manifesto `SHA256SUMS.txt`, confirmando a ausência de `target/dist-build`
- [x] 2.3 Executar a suíte de testes de integração do executável nativo `scripts/test_native_exe.ps1` e verificar passagem dos 10 testes funcionais STDIO MCP e flag `--version`
- [x] 2.4 Verificar que o cabeçalho PE de `dist/IPED-Tools-MCP/IPED-Tools-MCP.exe` reporta subsistema Windows GUI (2) e que a abertura do executável exibe a GUI diretamente sem janela de console
