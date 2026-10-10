# Design

## Context

Ver `proposal.md` (Why). Estado atual relevante:

- O repositório não tem `.github/` nem `docs/`; todo o conteúdo de usuário está no `README.md`.
- O repositório `iped-tools-mcp.wiki.git` ainda não existe (o GitHub só o cria após a primeira página salva pela interface web).
- O configurador gráfico (`MainWindow`) já gera configuração para três perfis: "LM Studio (Formulário Visual)", "LM Studio (Arquivo ng-mcp.json)" e "Claude Desktop / Cursor / Antigravity (JSON)", com opção de fixar ou não o `--case`.
- O fluxo de branches é GitFlow; `main` contém apenas versões lançadas.
- São 27 ferramentas MCP (anotadas com `@Tool` nas classes de `tools/`) e um prompt (`start_case`).

## Goals / Non-Goals

**Goals:**
- Wiki navegável pela aba Wiki do GitHub, gerada exclusivamente a partir de `docs/wiki/`.
- Publicação sem dependências de terceiros e sem segredos adicionais.
- README reduzido a vitrine + quickstart, sem perder as garantias exigidas pela spec de governança (download oficial, personas).

**Non-Goals:**
- Tradução para inglês (change futura).
- GitHub Pages, geradores de site estático ou busca customizada.
- Documentação de Goose, Ollama ou outros clientes.
- Versionamento múltiplo da wiki (a aba Wiki mostra apenas a versão atual da `main`; versões anteriores ficam acessíveis em `docs/wiki/` de cada tag).
- Alterações no código Java ou no gerador de configuração da GUI.

## Decisions

### D1. Mapa de páginas e nomes de arquivo ASCII

Nomes de arquivo sem acento, palavras separadas por hífen (o GitHub Wiki deriva o título do nome do arquivo); rótulos acentuados ficam no `_Sidebar.md` e no H1 de cada página.

```
docs/wiki/
  Home.md
  _Sidebar.md
  _Footer.md
  Requisitos.md
  Download-e-Verificacao.md
  Instalacao.md
  Configurador-Grafico.md            (GUI + caso ativo compartilhado)
  Cliente-Claude-Desktop.md
  Cliente-Cursor.md
  Cliente-Antigravity.md
  Cliente-LM-Studio.md               (formulário + ng-mcp.json + air-gapped)
  Cliente-Claude-Code.md
  Fluxo-de-Investigacao.md           (start_case, ciclo triagem -> marcadores -> IPED Desktop)
  Catalogo-de-Ferramentas.md         (27 tools, agrupadas nas 9 categorias atuais)
  Consultas-Lucene-e-Propriedades.md
  Boas-Praticas-Forenses.md
  Solucao-de-Problemas.md
  Dev-Arquitetura.md                 (mermaid migrado do README)
  Dev-Compilacao-e-Testes.md
  Dev-Empacotamento.md
  images/                            (capturas da GUI, opcional)
```

*Alternativa considerada:* nomes acentuados (`Instalação.md`). Rejeitada por gerar URLs com percent-encoding e risco de normalização Unicode (NFC/NFD) entre Windows e o runner Linux.

### D2. Links no formato de wiki

Links internos usam `[texto](Nome-da-Pagina)` (sem `.md`) e imagens usam `images/arquivo.png`. Isso otimiza a leitura na aba Wiki, que é o canal oficial.

*Alternativa:* links `Nome.md` que funcionam na aba Code mas quebram ou se comportam de forma inconsistente na Wiki. Rejeitada; a aba Code é canal secundário.

### D3. Workflow `.github/workflows/publish-wiki.yml` com git puro

- Gatilhos: `push` em `main` com `paths: ['docs/wiki/**']` e `workflow_dispatch`.
- `permissions: contents: write`; `concurrency: publish-wiki` para evitar pushes simultâneos.
- Passos: checkout → `git clone https://x-access-token:${{ secrets.GITHUB_TOKEN }}@github.com/${{ github.repository }}.wiki.git` (se falhar, `::error::` com instrução de criar a primeira página pela web) → `rsync -a --delete --exclude .git docs/wiki/ wiki/` → `git add -A` → commit somente se `git status --porcelain` não for vazio, com mensagem referenciando o SHA de origem → `git push`.

*Alternativa:* actions de terceiros (ex.: `Andrew-Chen-Wang/github-wiki-action`). Rejeitada para evitar dependência externa na cadeia de publicação de um produto forense e manter o workflow auditável em ~30 linhas.

### D4. Conteúdo derivado do código, não reinventado

- Os snippets de configuração na wiki reproduzem exatamente o formato emitido pelo gerador da `MainWindow` (com `--stdio` e sem `--case` como padrão recomendado; `--case` documentado como opção avançada).
- O catálogo de ferramentas é construído a partir das anotações `@Tool` atuais, reaproveitando os textos do README como base, acrescidos de uma pergunta-exemplo em pt-BR por ferramenta.
- O caminho padrão de instalação documentado é `C:\Program Files\IPED Tools MCP\IPED-Tools-MCP.exe`; o portátil usa caminho fictício (ex.: `C:\Ferramentas\IPED-Tools-MCP\IPED-Tools-MCP.exe`).

### D5. README enxuto preservando requisitos de governança

O README mantém: título e badges, descrição curta, distribuição oficial (`www.mcp.ipedtools.com.br`) e verificação SHA-256, a tabela "Personas e Perfis de Uso" (exigida pela spec de governança), quickstart de ~5 passos e links para Wiki, `CHANGELOG.md`, `CONTRIBUTING.md` e `LICENSE`. Remove: arquitetura, fluxo dual-mode, princípios detalhados, catálogo, prompts, CLI, build, testes, empacotamento. A lista de clientes passa a citar apenas os cinco suportados.

## Risks / Trade-offs

- [Alguém edita direto na aba Wiki e perde a alteração] → `_Footer.md` em todas as páginas avisa que a fonte é `docs/wiki/` com link para o arquivo no repositório.
- [Wiki desatualiza em relação às ferramentas] → tarefa de verificação que compara os nomes das `@Tool` no código com o catálogo; recomendação no `CONTRIBUTING.md` de atualizar `docs/wiki/` no mesmo PR que altera tools ou a GUI.
- [Alterações de documentação feitas em `develop` só aparecem na Wiki após a release] → comportamento intencional (Wiki = versão lançada); correções urgentes seguem `hotfix/*` ou `workflow_dispatch` após merge na `main`.
- [Interface dos clientes de IA muda (menus do LM Studio, Cursor)] → guias focam no conteúdo da configuração e indicam a versão do cliente usada na redação.
- [Capturas de tela vazarem dados de casos reais] → capturas apenas com caso de exemplo fictício; revisão explícita no checklist do PR.
- [`GITHUB_TOKEN` sem permissão de push na wiki em configurações restritas da organização] → mensagem de erro clara; fallback documentado de ajustar "Workflow permissions" para leitura e escrita nas configurações do repositório.
- [Links no formato wiki quebram na aba Code] → aceito (D2).

## Migration Plan

1. Mesclar a change em `develop` (wiki ainda não publicada).
2. Antes ou logo após o merge da release em `main`: criar manualmente a página `Home` pela interface web do GitHub (habilitando Wikis em Settings → Features, se necessário).
3. Merge em `main` dispara a Action, que sobrescreve a `Home` provisória com o conteúdo de `docs/wiki/`.
4. Rollback: reverter o commit em `main` e reexecutar o workflow, ou desabilitar a aba Wiki em Settings; o README continua apontando para conteúdo válido em `docs/wiki/` na árvore do repositório.

## Open Questions

- Incluir capturas de tela da GUI já nesta change ou em uma iteração seguinte (não altera a estrutura de páginas nem o workflow).
