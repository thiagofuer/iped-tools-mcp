# Requisitos de Sistema e Compatibilidade

Antes de instalar e utilizar o **IPED Tools MCP**, certifique-se de que o seu ambiente atenda aos pré-requisitos descritos abaixo.

---

## 💻 Sistema Operacional e Hardware

* **Sistema Operacional:** Microsoft Windows 10 ou Windows 11 (arquitetura de 64 bits - `x64`).
* **Memória RAM:**
  * Mínimo: 8 GB de RAM.
  * Recomendado: 16 GB de RAM ou superior (especialmente ao operar com grandes índices Lucene ou modelos locais de IA).
* **Espaço em Disco:**
  * ~400 MB livres para a instalação do executável e runtime Java embutido.
  * Espaço adicional suficiente para a leitura dos casos IPED e seus respectivos índices.

> ℹ️ **Nota sobre Java:** Não é necessário instalar o Java (JRE/JDK) previamente na máquina. Tanto o instalador `.msi` quanto o pacote portátil `.zip` incluem um runtime Java 21 LTS (BellSoft Liberica JRE) isolado e pré-configurado.

---

## 📂 Requisitos do Caso IPED

O IPED Tools MCP conecta-se a casos digitais processados previamente pelo **IPED (Indexador e Processador de Evidências Digitais)**:

* **Versões do IPED:** Compatível com casos gerados a partir do IPED 3.18 até versões recentes (índices Apache Lucene 9.x).
* **Estrutura Obrigatória da Pasta:** O caso selecionado deve conter a subpasta `iped/` com a estrutura padrão:
  * `iped/index/` — Diretório com os índices do Apache Lucene.
  * `iped/conf/` — Arquivos de configuração do processamento.
  * `iped/bookmarks.iped` — Arquivo de marcadores periciais (criado automaticamente caso ainda não exista).
* **Permissões de Arquivo:**
  * Permissão de **leitura** em todo o caso e índices.
  * Permissão de **escrita** apenas caso deseje adicionar marcadores periciais (`add_to_bookmark`) ou conferir itens na triagem (`set_item_checked`).

---

## 🤖 Clientes de IA Homologados

O servidor implementa o protocolo **Model Context Protocol (MCP)** sobre STDIO (entrada e saída padrão). As seguintes ferramentas e interfaces de IA são oficialmente suportadas e documentadas nesta wiki:

1. **[Claude Desktop](Cliente-Claude-Desktop)** — Cliente desktop da Anthropic.
2. **[Cursor](Cliente-Cursor)** — IDE com suporte nativo ao protocolo MCP.
3. **[Antigravity](Cliente-Antigravity)** — Ambiente de desenvolvimento assistido por IA.
4. **[LM Studio](Cliente-LM-Studio)** — Recomendado para **laboratórios periciais isolados (air-gapped)** utilizando modelos de linguagem 100% locais (offline).
5. **[Claude Code](Cliente-Claude-Code)** — Interface de linha de comando para agentes periciais.
