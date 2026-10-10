# Tasks

## 1. Estrutura da wiki (scaffolding)

- [x] 1.1 Criar `docs/wiki/` com `Home.md`, `_Sidebar.md` e `_Footer.md` seguindo o mapa de páginas do design (D1); verificar que o `_Sidebar.md` lista todas as páginas previstas com rótulos acentuados em pt-BR e links no formato `[texto](Nome-da-Pagina)` (D2)
- [x] 1.2 Escrever no `_Footer.md` o aviso de que a fonte é `docs/wiki/` (com link para a pasta no repositório) e que edições na aba Wiki são sobrescritas; verificar o texto renderizado na pré-visualização Markdown

## 2. Primeiros passos

- [x] 2.1 Escrever `Requisitos.md` (Windows x64, caso processado pelo IPED, cliente MCP compatível) e verificar que não cita clientes fora dos cinco suportados
- [x] 2.2 Escrever `Download-e-Verificacao.md` com o link `www.mcp.ipedtools.com.br` e o comando `Get-FileHash -Algorithm SHA256` comparado ao `SHA256SUMS.txt`; verificar executando o comando contra `dist/IPED-Tools-MCP-1.0.0-windows-x64-portable.zip` e conferindo com `dist/SHA256SUMS.txt`
- [x] 2.3 Escrever `Instalacao.md` cobrindo MSI (caminho padrão `C:\Program Files\IPED Tools MCP\`) e ZIP portátil (caminho fictício); verificar que `IPED-Tools-MCP.exe --version` documentado retorna a versão em `dist/IPED-Tools-MCP/`

## 3. Configurador gráfico e clientes de IA (GUI/Sync)

- [x] 3.1 Escrever `Configurador-Grafico.md` explicando seleção do caso, perfis de cliente, cópia 1-click, opção de fixar `--case` e sincronização do caso ativo (`~/.iped-tools-mcp/active_case.txt` e `open_case`); verificar conferindo os nomes dos perfis com `MainWindow.java`
- [x] 3.2 Escrever `Cliente-Claude-Desktop.md`, `Cliente-Cursor.md` e `Cliente-Antigravity.md` com o JSON exatamente no formato emitido pela `MainWindow` (perfil "Claude Desktop / Cursor / Antigravity") e o local do arquivo de configuração de cada cliente; verificar comparando com o snippet gerado pela GUI
- [x] 3.3 Escrever `Cliente-LM-Studio.md` com os modos formulário e `ng-mcp.json`, destacando o uso air-gapped com modelo local; verificar comparando com os dois perfis LM Studio da GUI
- [x] 3.4 Escrever `Cliente-Claude-Code.md` com o comando de registro do servidor via `--stdio`; verificar que o comando documentado segue a sintaxe atual do `claude mcp add`
- [x] 3.5 Validar de forma automatizada o comando `--stdio` documentado contra um caso de exemplo, executando `scripts\test_stdio_mcp.ps1 -CasePath <caso de teste>` (caso resolvido via `local-test.properties`/`IPED_TEST_CASE_PATH`) e verificar que o handshake e o `tools/list` passam

## 4. Uso e referência de ferramentas (MCP tools)

- [x] 4.1 Escrever `Fluxo-de-Investigacao.md` com o prompt `start_case` e o ciclo triagem → `add_to_bookmark`/`set_item_checked` → revisão no IPED Desktop; verificar conferindo o nome do prompt em `ForensicPrompts.java`
- [x] 4.2 Escrever `Catalogo-de-Ferramentas.md` com as 27 ferramentas nas 9 categorias, propósito e uma pergunta-exemplo em pt-BR por ferramenta; verificar com um comando PowerShell que extrai os nomes das `@Tool` em `src/main/java` e confirma que cada um aparece na página (e nenhum nome extra)
- [x] 4.3 Escrever `Consultas-Lucene-e-Propriedades.md` (sintaxe, escape, exemplos de `search_documents`, domínios de `get_property_dictionary`); verificar executando ao menos duas consultas de exemplo contra o caso de teste via `scripts\test_stdio_mcp.ps1` ou testes Maven existentes

## 5. Boas práticas e solução de problemas

- [x] 5.1 Escrever `Boas-Praticas-Forenses.md` (somente leitura, air-gapped, isolamento de streams, únicas escritas permitidas, não-metas); verificar coerência com a seção de princípios do README atual e com a spec `mcp-server-core`
- [x] 5.2 Escrever `Solucao-de-Problemas.md` (servidor não inicia, nenhuma ferramenta listada, caminho do executável com escape, nenhum caso ativo, logs em stderr, `--version`); verificar reproduzindo o cenário "nenhum caso ativo" com o executável de `dist/`

## 6. Documentação de desenvolvimento

- [x] 6.1 Migrar os diagramas mermaid de arquitetura e dual-mode para `Dev-Arquitetura.md` junto com a tabela de opções de CLI; verificar que a pré-visualização Markdown renderiza os dois diagramas
- [x] 6.2 Escrever `Dev-Compilacao-e-Testes.md` e `Dev-Empacotamento.md` com links para `CONTRIBUTING.md` e `CHANGELOG.md`; verificar executando `mvn test` e confirmando que os comandos documentados correspondem aos scripts em `scripts/`
- [x] 6.3 Adicionar ao `CONTRIBUTING.md` a orientação de atualizar `docs/wiki/` no mesmo PR que altera ferramentas ou a GUI; verificar a seção no arquivo

## 7. Publicação automática (Packaging)

- [x] 7.1 Criar `.github/workflows/publish-wiki.yml` conforme D3 (gatilhos `push` em `main` com `paths: docs/wiki/**` e `workflow_dispatch`, `permissions: contents: write`, `concurrency`, clone do `.wiki.git` com erro explícito se inexistente, `rsync --delete`, commit apenas se houver mudanças); verificar com `actionlint` (ou validação de YAML) e revisão de que não há PAT referenciado
- [x] 7.2 Documentar no `Dev-Empacotamento.md` (seção "Publicação da Wiki") a etapa manual de criar a primeira página pela web e o ajuste de "Workflow permissions" caso o push falhe; verificar o texto contra os cenários da spec `wiki-publishing`

## 8. README enxuto

- [x] 8.1 Reescrever o `README.md` conforme D5 (badges, descrição, download oficial + SHA-256, personas, quickstart de ~5 passos, links para Wiki/CHANGELOG/CONTRIBUTING/LICENSE) e remover Goose e Ollama da lista de clientes; verificar que todos os cenários do requisito "Repository Hygiene and Governance Documentation" (delta) são atendidos

## 9. Validação integrada

- [x] 9.1 Auditar sanitização em `docs/wiki/` e `README.md` com busca por letras de unidade, nomes de pastas e identificadores fora do padrão fictício (`C:\casos_forenses\caso_operacao_01`, `C:\Program Files\IPED Tools MCP\`, caminho portátil fictício); verificar resultado vazio
- [x] 9.2 Verificar todos os links internos de `docs/wiki/` com um script que confere se cada `](Nome-da-Pagina)` corresponde a um arquivo existente; verificar zero links quebrados
- [x] 9.3 Rodar `openspec validate create-user-wiki --strict` e verificar que passa sem erros
- [ ] 9.4 Após o merge em `main` (com a primeira página criada manualmente), confirmar que a execução da Action publica a aba Wiki com a `Home`, o sidebar e todas as páginas; reexecutar via `workflow_dispatch` e verificar que não é criado commit vazio
