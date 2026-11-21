# IPED Tools MCP — Wiki Oficial

Bem-vindo à documentação oficial do **IPED Tools MCP**, o servidor nativo baseado na especificação [Model Context Protocol (MCP)](https://modelcontextprotocol.io/) que conecta Grandes Modelos de Linguagem (LLMs) e assistentes de Inteligência Artificial diretamente a casos periciais processados pelo **IPED (Indexador e Processador de Evidências Digitais)**.

---

## 🧭 Navegação Rápida

### 📦 Instalação e Configuração
Siga nesta ordem: instale, configure o seu aplicativo de IA e passe à investigação.

| Etapa | O que você encontrará |
|---|---|
| **Instalação** | [Requisitos](Requisitos) (sistema, versões do IPED e clientes homologados), [Download e Verificação](Download-e-Verificacao) (pacotes oficiais e conferência SHA-256) e [Instalação](Instalacao) (instalador `.msi` ou pacote portátil `.zip`). |
| **Configuração do cliente de IA** | [Configurador Gráfico](Configurador-Grafico) (testar o MCP e gerar a configuração 1-click) e os guias de [Claude Code](Cliente-Claude-Code), [Claude Desktop](Cliente-Claude-Desktop), [Cursor](Cliente-Cursor), [Antigravity](Cliente-Antigravity) e [LM Studio (offline)](Cliente-LM-Studio). |
| **➡️ Próximo passo** | [Como Investigar com o MCP](Como-Investigar-com-o-MCP). |

### 🔍 Investigação

| Página | O que você encontrará |
|---|---|
| **[Como Investigar com o MCP](Como-Investigar-com-o-MCP)** | Passo a passo da investigação com o seu aplicativo de IA, a metodologia que a IA segue e como personalizá-la. |
| **[Catálogo de Ferramentas](Catalogo-de-Ferramentas)** | As ferramentas periciais disponíveis, com exemplos de perguntas. |
| **[Consultas Lucene e Propriedades](Consultas-Lucene-e-Propriedades)** | Sintaxe de pesquisa, escape de caracteres especiais e catálogo de metadados. |
| **[Boas Práticas Forenses](Boas-Praticas-Forenses)** | Princípios de preservação probatória, modo somente-leitura e operação desconectada. |

### 🆘 Ajuda e 🛠️ Desenvolvimento

| Página | O que você encontrará |
|---|---|
| **[Solução de Problemas](Solucao-de-Problemas)** | Diagnóstico de falhas comuns, logs de execução e resolução de incidentes. |
| **[Desenvolvimento e Engenharia](Dev-Arquitetura)** | [Arquitetura interna](Dev-Arquitetura), [compilação e testes](Dev-Compilacao-e-Testes) e [empacotamento nativo](Dev-Empacotamento). |

---

## 🔒 Garantias Forenses Fundamentais

* **Acesso Estritamente Somente-Leitura (Read-Only):** Índices Apache Lucene e bancos de dados SQLite do caso nunca sofrem alterações.
* **Operação Desconectada (Air-Gapped):** Comunicação realizada exclusivamente por entrada e saída padrão local (`stdin`/`stdout`), sem portas de rede abertas.
* **Preservação de Evidências:** Apenas o arquivo de conferência pericial (`bookmarks.iped`) pode receber inclusões para auditoria no IPED Desktop.
