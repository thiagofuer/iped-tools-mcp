# IPED Tools MCP

[![Version](https://img.shields.io/badge/version-1.0.0-blue.svg)](pom.xml)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Quarkus](https://img.shields.io/badge/Quarkus-3.17.8-red.svg)](https://quarkus.io/)
[![MCP](https://img.shields.io/badge/MCP-2024--11--05-green.svg)](https://modelcontextprotocol.io/)
[![License](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Distribution](https://img.shields.io/badge/Download-ipedtools.com.br-brightgreen.svg)](https://www.ipedtools.com.br)

**IPED Tools MCP** é um servidor nativo baseado na especificação **Model Context Protocol (MCP)** que conecta Inteligências Artificiais e LLMs (como Claude Desktop, Claude Code, Cursor, Goose, ChatGPT e modelos locais) diretamente a casos processados pelo **IPED (Indexador e Processador de Evidências Digitais)**.

O projeto permite que assistentes inteligentes realizem investigações forenses profundas, buscas estruturadas em índices Lucene, análises cronológicas, grafos de comunicação, triagem de itens e cruzamento multimodal com integridade pericial.

---

## 🌐 Distribuição Oficial e Código-Fonte

- **Download Oficial de Executáveis e Instaladores:** [https://www.ipedtools.com.br](https://www.ipedtools.com.br)
- **Repositório de Código-Fonte:** [https://github.com/thiagofuer/iped-tools-mcp](https://github.com/thiagofuer/iped-tools-mcp)

Os binários compilados para Windows (pacote portátil `.zip` e instalador `.msi` com runtime Java 21 embutido) e os manifestos criptográficos `SHA256SUMS.txt` são distribuídos através do portal oficial [www.ipedtools.com.br](https://www.ipedtools.com.br).

---

## 🏛 Arquitetura do Sistema

```
┌────────────────────────────────────────────────────────┐
│               Clientes LLM / MCP                       │
│    (Claude Desktop, Cursor, Goose, CLI, Agentes)      │
└───────────────────────────┬────────────────────────────┘
                            │ JSON-RPC 2.0 (STDIO)
                            ▼
┌────────────────────────────────────────────────────────┐
│                    IPED Tools MCP                      │
│  ┌───────────────────┐        ┌─────────────────────┐  │
│  │    Swing GUI      │        │ Quarkus MCP Server  │  │
│  │  (Monitor / Tray) │        │ (27 Forensic Tools) │  │
│  └───────────────────┘        └──────────┬──────────┘  │
│                                          │             │
│  ┌───────────────────────────────────────▼──────────┐  │
│  │               IpedCoreService                    │  │
│  │   Leitura direta in-process do Apache Lucene     │  │
│  │   MMapDirectory / SleuthKit / BitmapBookmarks    │  │
│  └───────────────────┬──────────────────────────────┘  │
└──────────────────────┼─────────────────────────────────┘
                       │ Acesso direto somente-leitura
                       ▼
┌────────────────────────────────────────────────────────┐
│                   Caso IPED em Disco                   │
│   D:\caso_forense\iped\ (index, bookmarks.iped, etc.)  │
└────────────────────────────────────────────────────────┘
```

- **Acesso In-Process de Alta Performance:** Comunicação direta com os índices Apache Lucene e metadados SQLite do IPED sem necessidade de subir o servidor HTTP webapi legado.
- **Isolamento e Segurança:** Operação em modo estritamente somente-leitura sobre a evidência (exceto ao registrar marcadores forenses em arquivo de bookmarks dedicado).
- **Dual Mode (GUI & STDIO):** Pode ser executado em modo silencioso (`--stdio`) como subprocesso do cliente MCP ou com interface gráfica Swing (`MainWindow`) com monitoramento em tempo real.

---

## 🧰 Catálogo de Ferramentas Forenses (27 MCP Tools)

IPED Tools MCP disponibiliza **27 ferramentas especializadas** para condução de perícias e análises forenses digitais:

### 1. Conectividade e Gestão de Casos
- `get_server_status`: Retorna o estado do servidor MCP, versão instalada (`1.0.0`) e detalhes do caso aberto.
- `check_connection`: Verificação rápida de liveness e status de prontidão pericial.
- `get_case_summary`: Estatísticas completas do caso (volume de itens indexados, categorias, marcadores, metadados).
- `open_case`: Carrega ou alterna dinamicamente o caso pericial ativo em tempo de execução sem reiniciar o processo.

### 2. Dicionário e Descoberta de Metadados
- `get_property_dictionary`: Dicionário semântico e catálogo estruturado de propriedades forenses por domínio (chats, emails, chamadas, web, mídias, etc.).
- `list_available_properties`: Descobre dinamicamente os nomes exatos de campos e propriedades indexadas no caso para uma categoria específica.

### 3. Busca e Extração de Conteúdo
- `search_documents`: Executa consultas estruturadas em sintaxe Lucene no índice pericial (`category:whatsapp`, `date:[...]`, `has_attachment:true`).
- `get_document_metadata`: Recupera metadados técnicos forenses completos de um ou múltiplos itens (hashes MD5/SHA256, EXIF, permissões, caminhos, etc.).
- `get_document_text`: Extrai texto completo processado pelo OCR ou parsers nativos do IPED.
- `list_categories`: Lista todas as categorias de evidências presentes no caso e seus quantitativos.
- `list_bookmarks`: Lista os marcadores periciais do caso e total de documentos marcados.
- `add_to_bookmark`: Adiciona um ou mais itens a um marcador pericial existente ou cria um novo marcador.

### 4. Inteligência de Comunicações e Dispositivos
- `get_device_and_owner_info`: Identifica dados do proprietário do dispositivo, contas vinculadas, IMEI, números de telefone e contas de nuvem.
- `get_top_contacts`: Ranking de contatos mais frequentes em mensagens, chamadas e aplicativos de comunicação.
- `get_communications_graph`: Extrai o grafo relacional de comunicações (nós e arestas de interlocutores, volume de mensagens e chamadas trocadas).

### 5. Análise Cronológica e Temporal
- `get_timeline`: Linha do tempo unificada de eventos do caso (mensagens, arquivos criados, conexões, acessos web).
- `get_events_around_time`: Janela de correlação temporal em torno de um instante-chave (+/- N minutos).

### 6. Navegação e Árvore de Evidências
- `list_folder_contents`: Navega pela árvore de diretórios virtuais ou lógicos das mídias periciadas no caso.
- `get_item_relations`: Mapeia hierarquia pericial completa (item pai, itens filhos contidos e cópias duplicadas por hash).

### 7. Triagem Pericial
- `set_item_checked`: Marca ou desmarca o status de conferência/triagem pericial de um item evidência.

### 8. Análise Multimodal e Similaridade
- `get_item_thumbnail`: Retorna a miniatura visual de imagens e vídeos codificada em base64 com metadados periciais.
- `search_similar_images`: Busca reversa por imagens visualmente semelhantes utilizando assinaturas perceptuais (pHash/perceptual hash).
- `search_similar_faces`: Busca por faces similares às detectadas no item de referência via reconhecimento facial.
- `search_similar_documents`: Busca por documentos textualmente semelhantes via vetores semânticos / Lucene MoreLikeThis.

### 9. Filtros de Inteligência Artificial e Reconhecimento
- `list_ai_filters`: Lista as categorias de detecções por IA disponíveis no caso (faces, nudez/conteúdo sensível, documentos de identidade, armas, placas, veículos, drogas, etc.).
- `query_ai_detections`: Consulta itens classificados por filtros específicos de Inteligência Artificial com limiar de confiança configurável.

---

## 🚀 Instalação e Uso

### Configuração no Claude Desktop

Adicione a seguinte configuração ao arquivo `claude_desktop_config.json` do seu ambiente:

```json
{
  "mcpServers": {
    "iped-tools": {
      "command": "C:\\Program Files\\IPED Tools MCP\\IPED-Tools-MCP.exe",
      "args": [
        "--stdio",
        "--case",
        "D:\\casos_forenses\\caso_operacao_01"
      ]
    }
  }
}
```

> **Dica:** Caso utilize a versão portátil ZIP ou execute a partir dos fontes, você pode apontar para o `IPED-Tools-MCP.exe` na pasta descompactada ou invocar diretamente o Java:
> ```json
> {
>   "command": "java",
>   "args": [
>     "-jar", "C:\\caminho\\para\\iped-tools-mcp-1.0.0-runner.jar",
>     "--stdio",
>     "--case", "D:\\casos_forenses\\caso_operacao_01"
>   ]
> }
> ```

---

## 💻 Opções de Linha de Comando (CLI)

O executável suporta os seguintes parâmetros de linha de comando:

| Flag | Descrição |
|---|---|
| `--stdio` | Inicia o servidor em modo STDIO MCP (comunicação via JSON-RPC 2.0 através da entrada e saída padrão). |
| `--case <caminho>` | Define o caminho da pasta do caso IPED a ser pré-carregada na inicialização. |
| `--version`, `-v` | Exibe a versão do produto (`1.0.0`), data do build, versão Java e versão do IPED Core, saindo imediatamente. |
| `--help`, `-h` | Exibe a ajuda detalhada com a sintaxe de uso e parâmetros suportados. |

---

## 🛠 Compilação e Empacotamento

### Pré-requisitos
- **Java Development Kit (JDK):** Versão 21 (LTS) de 64 bits.
- **Apache Maven:** Versão 3.8.0 ou superior.
- **PowerShell:** Versão 5.1 ou superior (para execução dos scripts de empacotamento).
- **WiX Toolset v3.11:** Necessário apenas para geração do instalador `.msi` (o script `scripts/package_msi.ps1` faz o download automático se não estiver presente).

### 1. Compilação do Runner JAR
```powershell
mvn clean package -DskipTests
```
O artefato compilado é gerado em `target/iped-tools-mcp-1.0.0-runner.jar`.

### 2. Execução dos Testes Automatizados
```powershell
# Execução dos testes unitários Maven
mvn test

# Testes de integração ponta a ponta do protocolo STDIO MCP (usa local-test.properties ou -CasePath)
powershell -ExecutionPolicy Bypass -File scripts\test_stdio_mcp.ps1 -CasePath "C:\casos_forenses\caso_operacao_01"

# Testes de integração do executável nativo Windows
powershell -ExecutionPolicy Bypass -File scripts\test_native_exe.ps1 -CasePath "C:\casos_forenses\caso_operacao_01"
```

### 3. Empacotamento Nativo Windows (.exe e .zip portátil)
```powershell
powershell -ExecutionPolicy Bypass -File scripts\package_app.ps1
```
Gera a aplicação nativa em `dist/IPED-Tools-MCP/` e o pacote portátil em `dist/IPED-Tools-MCP-1.0.0-windows-x64-portable.zip`, atualizando o manifesto `dist/SHA256SUMS.txt`.

### 4. Empacotamento do Instalador MSI
```powershell
powershell -ExecutionPolicy Bypass -File scripts\package_msi.ps1
```
Gera o instalador pericial Windows em `dist/IPED-Tools-MCP-1.0.0.msi` com integração ao Painel de Controle e Menu Iniciar.

---

## 🛡 Integridade Criptográfica

Para ambientes de perícia oficial e cadeia de custódia digital, todos os pacotes distribuídos possuem hashes SHA-256 publicados no arquivo `dist/SHA256SUMS.txt`. Para verificar a integridade do pacote baixado:

```powershell
Get-FileHash -Algorithm SHA256 "dist\IPED-Tools-MCP-1.0.0-windows-x64-portable.zip"
```

---

## 📄 Governança e Contribuição

- O fluxo de desenvolvimento segue o **GitFlow Tradicional** (`main`, `develop`, `feature/*`, `release/*`, `hotfix/*`).
- O versionamento segue rigorosamente a especificação **Semantic Versioning 2.0.0**.
- Para detalhes sobre padrões de código, fluxo de PRs e diretrizes de contribuição, consulte [CONTRIBUTING.md](CONTRIBUTING.md).
- O histórico de alterações de cada versão pode ser consultado em [CHANGELOG.md](CHANGELOG.md).

---

## ⚖ Licença

Distribuído sob a licença **GPLv3** (GNU General Public License v3.0). Consulte o arquivo `LICENSE` para mais informações.
