# Guia de Contribuição e Governança do Repositório

Obrigado pelo seu interesse em contribuir com o **IPED Tools MCP**! Este documento estabelece o modelo de governança, convenções de versionamento, fluxo de branches e padrões de código para manter o projeto seguro, estável e com alta qualidade técnica.

---

## 🌿 Modelo de Branching: GitFlow Tradicional

O projeto adota rigorosamente a estratégia do **GitFlow Tradicional**. Nenhuma alteração deve ser enviada diretamente para as branches principais sem revisão.

```
       (hotfix/1.0.1)
           ┌───────┐
           │       ▼
main ──────●───────●───────● (v1.1.0)
            \     ▲       ▲
             \   / (release/1.1.0)
develop ──────●─●───────●─●
               \       /
                └──●──┘ (feature/nova-ferramenta)
```

### 1. Branches Principais (Permanentes)

| Branch | Descrição | Regras de Proteção |
|---|---|---|
| `main` | Código de produção estável. Cada commit em `main` corresponde a uma versão oficial distribuída e deve possuir uma tag semântica (ex: `v1.0.0`). | **Bloqueada para push direto.** Apenas aceita merges vindos de `release/*` ou `hotfix/*`. |
| `develop` | Linha de desenvolvimento contínuo e integração de novas funcionalidades concluídas. | **Bloqueada para push direto.** Atualizada exclusivamente via Pull Requests aprovados a partir de branches de feature ou fixes. |

### 2. Branches de Suporte (Temporárias)

- **`feature/<nome-descritivo>`**:
  - *Origem:* `develop`
  - *Destino:* `develop`
  - *Propósito:* Desenvolvimento de novas ferramentas MCP, novos recursos da GUI ou melhorias de performance.
  - *Exemplo:* `feature/audio-transcription-tool`, `feature/graph-layout-tuning`

- **`release/<versao>`**:
  - *Origem:* `develop`
  - *Destino:* `main` e `develop`
  - *Propósito:* Preparação final de uma nova versão. Permite testes integrados, ajustes na documentação (`CHANGELOG.md`), atualização do número de versão no `pom.xml` e correções pontuais de bugs de lançamento.
  - *Exemplo:* `release/1.1.0`

- **`hotfix/<versao>`**:
  - *Origem:* `main`
  - *Destino:* `main` e `develop`
  - *Propósito:* Correção urgente de falhas críticas encontradas em produção.
  - *Exemplo:* `hotfix/1.0.1`

---

## 🏷 Padrão de Versionamento (Semantic Versioning 2.0.0)

O projeto adota a convenção de **Versionamento Semântico** no formato `MAJOR.MINOR.PATCH`:

1. **MAJOR (ex: 2.0.0):** Alterações que introduzem incompatibilidades com versões anteriores na API das ferramentas MCP, no protocolo de comunicação ou nas dependências fundamentais.
2. **MINOR (ex: 1.1.0):** Adição de novas ferramentas MCP, novas funcionalidades na interface gráfica ou expansões de suporte mantendo compatibilidade retroativa.
3. **PATCH (ex: 1.0.1):** Correções de bugs, ajustes de compatibilidade e melhorias internas de estabilidade sem adição de novas funcionalidades na API.

### Fonte Única da Verdade
- O arquivo [`pom.xml`](pom.xml) é a **única fonte da verdade** para a versão do produto.
- Em desenvolvimento contínuo na branch `develop`, a versão deve conter o sufixo `-SNAPSHOT` (ex: `1.1.0-SNAPSHOT`).
- Nas branches `release/*` ou `hotfix/*`, o sufixo `-SNAPSHOT` é removido (ex: `1.0.0`) antes da compilação e do merge final em `main`.

---

## 🔒 Salvaguarda Forense e Controle de Binários

Por se tratar de uma ferramenta voltada para perícia digital e investigação forense:

1. **Jamais versione evidências periciais:** Nunca adicione arquivos de casos reais, bancos SQLite locais (`*.mv.db`), arquivos de estado (`active_case.txt`, `*.iped`, `bookmarks.iped`) ao controle de versão.
2. **Jamais versione binários compilados:** Diretórios como `target/`, `dist/` e ferramentas externas como `tools/wix311` (104 MB) estão devidamente ignorados no `.gitignore` e não devem ser incluídos em commits.
3. **WiX Toolset:** O script `scripts/package_msi.ps1` é auto-suficiente e realiza o download sob demanda caso o ambiente não possua o binário localmente.

---

## 🧪 Critérios de Aceitação e Testes

Antes de submeter um Pull Request, certifique-se de que todos os passos a seguir passaram com sucesso no seu ambiente:

1. **Testes Unitários Maven:**
   ```powershell
   mvn clean test
   ```
   Todos os testes devem passar (atualmente 52/52 testes com 0 falhas).

2. **Verificação da Flag `--version`:**
   ```powershell
   mvn package -DskipTests
   java -jar target/iped-tools-mcp-*-runner.jar --version
   ```
   Deve exibir a versão correta e sair com código de retorno 0.

3. **Testes de Integração MCP STDIO:**
   ```powershell
   powershell -ExecutionPolicy Bypass -File scripts\test_stdio_mcp.ps1 -CasePath "caminho_para_caso_teste"
   ```
   Todos os testes automatizados do protocolo MCP devem ser validados.

---

## 📝 Padrão de Mensagens de Commit

Utilizamos o padrão de **Conventional Commits**:

- `feat:` Nova funcionalidade ou nova ferramenta MCP (ex: `feat: adiciona ferramenta search_similar_faces`)
- `fix:` Correção de bug (ex: `fix: corrige parsing de timestamp UTC na timeline`)
- `docs:` Alterações puramente em documentação (ex: `docs: atualiza README com novos endpoints`)
- `test:` Adição ou modificação de testes (ex: `test: adiciona suite de testes para VersionInfo`)
- `refactor:` Alteração de código sem impacto externo de funcionalidade
- `chore:` Tarefas de manutenção, scripts de build, dependências ou governança

---

## 🤝 Processo de Contribuição e Pull Request

Adotamos o fluxo padrão **Fork & Pull Request** do GitHub para contribuições da comunidade:

1. **Faça um Fork:** Crie um fork do repositório [IPEDToolsMCP](https://github.com/thiagofuer/iped-tools-mcp) para sua conta pessoal no GitHub.
2. **Clone localmente:** Clone o seu fork para a sua máquina:
   ```bash
   git clone https://github.com/SEU-USUARIO/iped-tools-mcp.git
   cd iped-tools-mcp
   ```
3. **Crie uma branch:** A partir da branch `develop`, crie uma branch com nome descritivo:
   ```bash
   git checkout -b feature/sua-feature develop
   # ou
   git checkout -b fix/seu-fix develop
   ```
4. **Implemente e Teste:** Escreva o código mantendo o estilo do projeto e valide a suíte de testes (`mvn clean test`).
5. **Faça o Push:** Envie a sua branch para o seu fork no GitHub:
   ```bash
   git push origin feature/sua-feature
   ```
6. **Abra o Pull Request:** Abra um Pull Request no GitHub tendo como destino (**base branch**) a branch `develop` do repositório oficial (`upstream`). Descreva com clareza a motivação, o que foi alterado e as evidências de teste.
