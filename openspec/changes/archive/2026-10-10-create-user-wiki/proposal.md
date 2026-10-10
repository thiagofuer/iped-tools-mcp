# Proposal

## Why

Peritos, assistentes técnicos e analistas que baixam o IPED Tools MCP não têm hoje um guia de instalação, configuração e uso: o `README.md` (334 linhas) mistura vitrine, arquitetura, build e um trecho curto de configuração que cobre apenas Claude Desktop e LM Studio. Sem um guia passo a passo, o perito tende a errar a configuração do cliente de IA, a ignorar o fluxo de caso ativo compartilhado e a desconhecer as salvaguardas forenses (somente-leitura, air-gapped, marcadores auditáveis no IPED Desktop), o que compromete a adoção e o uso metodologicamente correto da ferramenta.

## What Changes

- Criação de uma wiki de usuário em **pt-BR** mantida como Markdown versionado em `docs/wiki/` (fonte única de verdade), cobrindo: requisitos, download e verificação SHA-256, instalação (MSI e portátil), configurador gráfico e sincronização do caso ativo, configuração de clientes de IA, prompt `start_case` e fluxo de investigação, catálogo das 27 ferramentas, sintaxe Lucene e dicionário de propriedades, boas práticas forenses, solução de problemas/FAQ e seção de desenvolvimento (arquitetura, build, testes, empacotamento).
- Guias de configuração para exatamente cinco clientes: **Claude Desktop, Cursor, Antigravity, LM Studio** (destacado como caminho oficial para laboratórios air-gapped) e **Claude Code**.
- Nova GitHub Action que publica automaticamente `docs/wiki/` no repositório `iped-tools-mcp.wiki.git` (aba Wiki) a cada push na branch `main` que altere `docs/wiki/**`, alinhada ao GitFlow (a Wiki reflete sempre a versão lançada).
- Enxugamento do `README.md`: passa a conter nome, badges, descrição curta, personas, download oficial com verificação de integridade, quickstart de poucos passos e links para Wiki, `CHANGELOG.md`, `CONTRIBUTING.md` e `LICENSE`. Arquitetura, catálogo de ferramentas, CLI, build, testes e empacotamento migram para a wiki.
- Remoção, no README, da menção a clientes sem guia na wiki (Goose, Ollama) até que ganhem página própria.

## Capabilities

### New Capabilities
- `user-documentation`: estrutura, idioma, cobertura de conteúdo e regras de sanitização da wiki de usuário mantida em `docs/wiki/`, incluindo os guias de configuração dos clientes de IA suportados.
- `wiki-publishing`: publicação automatizada e unidirecional de `docs/wiki/` para a Wiki do GitHub via GitHub Actions, com gatilho, autenticação e pré-condições definidos.

### Modified Capabilities
- `version-and-release-governance`: o requisito "Repository Hygiene and Governance Documentation" passa a exigir que o `README.md` seja uma vitrine enxuta que direciona à Wiki para guias de uso e desenvolvimento, sem duplicar o catálogo de ferramentas nem os guias de configuração.

## Impact

- **Novos arquivos**: `docs/wiki/*.md` (incluindo `Home.md`, `_Sidebar.md`, `_Footer.md`), `docs/wiki/images/` (opcional, capturas da GUI) e `.github/workflows/publish-wiki.yml` (o repositório ainda não possui `.github/`).
- **Arquivos alterados**: `README.md` (redução substancial de conteúdo).
- **Sem alteração** em código Java, componentes IPED (`IPEDSource`, `IPEDSearcher`, `BitmapBookmarks`), protocolo MCP, empacotamento ou testes Maven.
- **Etapa manual única**: criar a primeira página da Wiki pela interface web do GitHub para que o `.wiki.git` passe a existir antes da primeira execução da Action.
- **Valor forense**: documentação pública reforça o uso correto das salvaguardas de cadeia de custódia e operação offline; todos os exemplos devem usar caminhos fictícios (ex.: `C:\casos_forenses\caso_operacao_01`).
