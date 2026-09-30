# FEAT-06: Navegação Hierárquica do Sistema de Arquivos (Evidence Tree)

**Código:** FEAT-06  
**Status:** Especificado (SDD)  
**Módulo IPED de Referência:** `iped.app.ui.App` (`tree`, `evidencePanel`, `treeListener`, `recursiveTreeList`)  
**Data:** 2026-09-27  

---

## 1. Contexto e Motivação Forense

No IPED Desktop, o painel lateral esquerdo apresenta a **Árvore de Evidências** (`tree`), permitindo ao perito percorrer fisicamente o sistema de arquivos original da imagem forense:
* Discos e partições (ex: `Disco 0 -> Partição NTFS 2 [OS] -> C: -> Users -> ...`)
* Pastas do sistema operacional (`AppData`, `System32`, `Prefetch`, `Startup`)
* Estrutura de aplicativos específicos (ex: diretórios de bancos de dados do WhatsApp, Telegram, navegadores).

Hoje, no MCP, a busca por caminhos é restrita a filtros de texto no campo `path`. Com a ferramenta de navegação hierárquica, a LLM pode navegar por pastas como se estivesse abrindo pastas no Explorer do Windows, inspecionando subdiretórios e listando arquivos com contagem e tipagem.

---

## 2. Engenharia e APIs Internas do IPED Core

O IPED armazena o caminho absoluto e relativo de cada item no campo `path` e indexa nós pai e filhos:

```java
// Listagem não-recursiva de arquivos de uma pasta
String exactDirQuery = "path:\"" + folderPath + "/*\" AND NOT path:\"" + folderPath + "/*/*\"";

// Listagem recursiva (inclui subpastas)
String recursiveDirQuery = "path:\"" + folderPath + "/*\"";

IPEDSearcher searcher = new IPEDSearcher(ipedSource, exactDirQuery);
SearchResult results = searcher.search();

// Agrupamento de subpastas imediatas
Set<String> subfolders = new TreeSet<>();
for (int id : results.getItemIds()) {
    Document doc = ipedSource.getReader().document(id);
    String p = doc.get("path");
    // extrai próximo segmento de diretório
}
```

---

## 3. Especificação das Ferramentas MCP (`@Tool`)

### 3.1 `list_folder_contents`
```java
@Tool(name = "list_folder_contents",
      description = "Lists files and subdirectories located within a specific directory path in the forensic evidence tree.")
public FolderContentsResponse listFolderContents(
    @ToolArg(name = "folder_path", description = "The directory path (e.g. 'C:/Users/Target/Downloads' or '/data/data/com.whatsapp').") String folderPath,
    @ToolArg(name = "recursive", description = "If true, includes all files in all nested subdirectories (default false).") Boolean recursive,
    @ToolArg(name = "limit", description = "Maximum items to return (default 50).") Integer limit
)
```

---

## 4. Prompt Engineering & Workflow Guidance

* **Perguntas-Gatilho do Usuário:**
  * *"O que tinha dentro da pasta Downloads do usuário?"*
  * *"Quais arquivos existem no diretório de instalação do programa suspeito?"*
  * *"Liste o conteúdo da pasta AppData/Roaming."*
* **Workflow Guidance:**
  * Usar `list_folder_contents` com `recursive=false` para explorar a estrutura de pastas progressivamente, evitando poluir o contexto com milhares de subarquivos desnecessários.
