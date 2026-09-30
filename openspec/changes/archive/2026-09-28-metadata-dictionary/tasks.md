# Tasks: Metadata Dictionary and Structured Forensic Properties

## 1. Service Layer: Dicionário e Sanitização Semântica

- [x] 1.1 Implementar catálogo de domínios forenses (`chats`, `browsers`, `emails`, `media`, `system`, `gps`, `ufed`, `ai`) com nomes de campos, tipos, descrições e exemplos de sintaxe Lucene com escape em `IpedCoreService.java`.
- [x] 1.2 Implementar método `listAvailableProperties(String category)` em `IpedCoreService.java` que inspeciona dinamicamente os campos populados no índice Lucene para itens da categoria informada.
- [x] 1.3 Refatorar método `sanitizeProperties` em `IpedCoreService.java` substituindo a lista estática `ALLOWED_METADATA_KEYS` por whitelist de prefixos (`Communication:`, `Conversation:`, `common:`, `image:`, `video:`, `audio:`, `ufed:`, `p2p:`, `hashDb:`, `meta:`) e particionamento em blocos (`basic`, `communication`, `geo`, `forensic`, `extra`).

## 2. Ferramentas MCP (@Tool)

- [x] 2.1 Criar a classe `PropertyDictionaryTool.java` registrando `@Tool get_property_dictionary` e `@Tool list_available_properties` com prompt engineering detalhado e validações de argumentos.
- [x] 2.2 Atualizar `DocumentMetadataTool.java` para refletir o schema de retorno agrupado e orientar o LLM sobre o acesso a campos de comunicação e geolocalização.

## 3. Testes e Validação Integrada

- [x] 3.1 Executar compilação do projeto com `mvn clean test-compile` e verificar ausência de regressões.
- [x] 3.2 Executar script de testes STDIO JSON-RPC validando as novas ferramentas e a extração de campos `Communication:*` e `common:geo:locations` em casos de teste locais.

## 4. Reempacotamento e Verificação Nativa

- [x] 4.1 Reempacotar a aplicação nativa Windows via `scripts/package_app.ps1` e `scripts/package_msi.ps1`.
- [x] 4.2 Validar execução do executável nativo `IPED-Tools-MCP.exe` via `scripts/test_native_exe.ps1` garantindo estabilidade no ambiente autônomo do perito.
