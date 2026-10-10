# IPED Tools MCP — Wiki Oficial

Bem-vindo à documentação oficial do **IPED Tools MCP**, o servidor nativo baseado na especificação [Model Context Protocol (MCP)](https://modelcontextprotocol.io/) que conecta Grandes Modelos de Linguagem (LLMs) e assistentes de Inteligência Artificial diretamente a casos periciais processados pelo **IPED (Indexador e Processador de Evidências Digitais)**.

---

## 🧭 Navegação Rápida

| Seção | O que você encontrará |
|---|---|
| **[Requisitos](Requisitos)** | Compatibilidade de sistema, versões do IPED suportadas e clientes homologados. |
| **[Download e Verificação](Download-e-Verificacao)** | Obtenção dos pacotes oficiais e conferência de integridade criptográfica SHA-256. |
| **[Instalação](Instalacao)** | Passo a passo para instalação via instalador Windows (`.msi`) ou pacote portátil (`.zip`). |
| **[Configurador Gráfico](Configurador-Grafico)** | Como utilizar a interface visual para alternar o caso ativo e gerar configurações 1-click. |
| **[Clientes de IA](Cliente-Claude-Desktop)** | Guias específicos para [Claude Desktop](Cliente-Claude-Desktop), [Cursor](Cliente-Cursor), [Antigravity](Cliente-Antigravity), [LM Studio (Air-Gapped)](Cliente-LM-Studio) e [Claude Code](Cliente-Claude-Code). |
| **[Fluxo de Investigação](Fluxo-de-Investigacao)** | Metodologia forense assistida por IA, uso do prompt `start_case` e ciclo de auditoria. |
| **[Catálogo de Ferramentas](Catalogo-de-Ferramentas)** | Documentação completa das 27 ferramentas periciais disponíveis com exemplos de comandos. |
| **[Consultas Lucene e Propriedades](Consultas-Lucene-e-Propriedades)** | Sintaxe de pesquisa, escape de caracteres especiais e catálogo de metadados. |
| **[Boas Práticas Forenses](Boas-Praticas-Forenses)** | Princípios de preservação probatória, modo somente-leitura e operação desconectada. |
| **[Solução de Problemas](Solucao-de-Problemas)** | Diagnóstico de falhas comuns, logs de execução e resolução de incidentes. |
| **[Desenvolvimento e Engenharia](Dev-Arquitetura)** | [Arquitetura interna](Dev-Arquitetura), [compilação e testes](Dev-Compilacao-e-Testes) e [empacotamento nativo](Dev-Empacotamento). |

---

## 🔒 Garantias Forenses Fundamentais

* **Acesso Estritamente Somente-Leitura (Read-Only):** Índices Apache Lucene e bancos de dados SQLite do caso nunca sofrem alterações.
* **Operação Desconectada (Air-Gapped):** Comunicação realizada exclusivamente por entrada e saída padrão local (`stdin`/`stdout`), sem portas de rede abertas.
* **Preservação de Evidências:** Apenas o arquivo de conferência pericial (`bookmarks.iped`) pode receber inclusões para auditoria no IPED Desktop.
