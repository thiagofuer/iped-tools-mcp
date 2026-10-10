# Empacotamento Nativo e Distribuição

O **IPED Tools MCP** é distribuído sem dependências externas de Java no host. O pipeline de empacotamento utiliza a ferramenta nativa `jpackage` do JDK 21 para embutir um runtime Java Liberica 21 LTS enxuto, gerando binários autônomos de alta performance para o Windows.

---

## 📦 1. Geração do Pacote Portátil (`.zip`)

O script `scripts/package_app.ps1` orquestra a compilação Maven, o download do runtime Java embutido e a montagem da estrutura nativa:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\package_app.ps1
```

### Artefatos Gerados:
* `dist/IPED-Tools-MCP/` — Diretório com o executável e bibliotecas descompactadas.
* `dist/IPED-Tools-MCP-1.0.0-windows-x64-portable.zip` — Pacote portátil compactado.
* `dist/SHA256SUMS.txt` — Manifesto de hashes criptográficos SHA-256 gerado automaticamente.

---

## 💿 2. Geração do Instalador Windows (`.msi`)

Para gerar o instalador formal do Windows com atalhos e integração ao sistema:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\package_msi.ps1
```

### Pré-requisitos do Instalador:
* **WiX Toolset v3.11:** O script verifica e utiliza automaticamente o WiX Toolset localizado em `tools/wix311/` no próprio repositório.
* **Artefato Gerado:** `dist/IPED-Tools-MCP-1.0.0.msi` e atualização do `dist/SHA256SUMS.txt`.

---

## 🚀 3. Publicação Automatizada da Wiki Oficial

A documentação desta wiki é mantida em arquivos Markdown na pasta `docs/wiki/` do repositório principal e publicada na aba **Wiki** do GitHub através de uma GitHub Action (`.github/workflows/publish-wiki.yml`).

### Como Funciona o Pipeline:
1. **Gatilho de Publicação:** O workflow dispara automaticamente a cada `push` na branch canônica `main` que contenha alterações em `docs/wiki/**`.
2. **Disparo Manual:** Mantenedores também podem forçar a sincronização via `workflow_dispatch` na interface de Actions do GitHub.
3. **Espelhamento Unidirecional:** O workflow clona o repositório `iped-tools-mcp.wiki.git`, sincroniza exatamente o conteúdo de `docs/wiki/` (removendo páginas deletadas) e envia o commit de atualização.
4. **Segurança e Privilégios Mínimos:** O pipeline utiliza exclusivamente o token temporário padrão `${{ secrets.GITHUB_TOKEN }}` com permissão restrita `contents: write`, sem necessidade de PAT (*Personal Access Token*).

### ⚠️ Etapa Manual Única: Inicialização da Wiki no GitHub
O repositório `.wiki.git` só passa a existir fisicamente no GitHub após a criação da **primeira página** via interface web:
1. No repositório do GitHub, acesse a aba **Wiki**.
2. Clique em **Create the first page**, insira um título provisório (ex: `Home`) e clique em **Save Page**.
3. A partir desse momento, o `.wiki.git` estará acessível e todas as publicações subsequentes serão 100% automatizadas pelo workflow da branch `main`.

> 💡 **Permissões de Workflow:** Caso o workflow retorne erro de permissão ao tentar realizar o push no repositório da wiki, certifique-se de que nas configurações do repositório (**Settings > Actions > General > Workflow permissions**) a opção **"Read and write permissions"** esteja selecionada.
