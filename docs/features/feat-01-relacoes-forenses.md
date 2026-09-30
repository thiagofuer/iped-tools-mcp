# FEAT-01: Relações Forenses de Itens (Subitens, Pais, Duplicatas e Referências)

**Código:** FEAT-01  
**Status:** Especificado (SDD)  
**Módulo IPED de Referência:** `iped.app.ui.App` (`subItemTable`, `parentItemTable`, `duplicatesTable`, `referencesTable`, `referencedByTable`)  
**Data:** 2026-09-27  

---

## 1. Contexto e Motivação Forense

Na análise de evidências digitais, um documento ou artefato raramente existe de forma isolada. No IPED Desktop:
* **Item Pai (Parent):** O perito precisa saber de qual contêiner um arquivo foi extraído (ex: um PDF extraído de um anexo de e-mail, ou um arquivo `.db-wal` extraído de um backup de WhatsApp).
* **Subitens (Children):** Ao encontrar um arquivo ZIP, um instalador APK ou uma mensagem de e-mail, o perito precisa saber quais subitens foram descompactados ou pertencem a ele.
* **Duplicatas por Hash (Duplicates):** Se um arquivo suspeito (ex: comprovante de transação, foto de arma) foi encontrado em um dispositivo, o perito precisa saber imediatamente se o **mesmo arquivo** (com mesmo hash MD5/SHA-256) também estava presente no notebook de outro investigado ou em outro pendrive do caso multi-fontes.
* **Referências Cruzadas (References / Referenced By):** Relacionamentos de atalhos (`.lnk`), históricos de navegação apontando para arquivos em cache e referências de bancos de dados.

Hoje, no MCP, a LLM precisa fazer queries Lucene manuais complexas para tentar descobrir essas relações. Esta feature fornece à IA uma visão relacional forense completa em 1 única chamada.

---

## 2. Engenharia e APIs Internas do IPED Core

O IPED armazena e expõe essas relações por meio de índices e campos específicos:

```java
// Identificação do Pai e Subitens
IItem item = ipedSource.getItem(itemId);
Long parentId = item.getParentId(); // ID do contêiner pai

// Subitens indexados com campo 'parent'
String querySubItems = "parent:" + itemId;
SearchResult subResults = ipedSearcher.search(querySubItems);

// Duplicatas por Hash (MD5 ou SHA-256)
String hash = item.getHash(); // ou doc.get("hash")
if (hash != null && !hash.isBlank()) {
    String queryDup = "hash:" + hash + " AND NOT id:" + itemId;
    SearchResult dupResults = ipedSearcher.search(queryDup);
}

// Referências (campos 'references' e 'referencedBy')
String[] refs = doc.getValues("references");
String[] refBy = doc.getValues("referencedBy");
```

---

## 3. Especificação da Ferramenta MCP (`@Tool`)

### Assinatura Proposta
```java
@Tool(name = "get_item_relations",
      description = "Retrieves forensic relationships for a given item ID: its parent container, sub-items, exact hash duplicates across the case, and cross-references.")
public ItemRelationsResult getItemRelations(
    @ToolArg(name = "item_id", description = "The unique numeric ID of the item.") int itemId
)
```

### Esquema do Retorno JSON
```json
{
  "item_id": 14205,
  "name": "comprovante_pix.pdf",
  "hash_md5": "e4d909c290d0fb1ca068ffaddf22cbd0",
  "parent": {
    "item_id": 14200,
    "name": "Mensagem WhatsApp de Fulano para Sicrano",
    "category": "chat messages",
    "path": "/WhatsApp/Databases/msgstore.db"
  },
  "sub_items_count": 0,
  "sub_items": [],
  "duplicates_count": 2,
  "duplicates": [
    {
      "item_id": 89211,
      "source_name": "Notebook_Dell_Inspiron",
      "name": "comprovante_antigo.pdf",
      "path": "C:/Users/alvo/Downloads/comprovante_antigo.pdf",
      "category": "documents"
    },
    {
      "item_id": 104523,
      "source_name": "Pendrive_Sandisk_32GB",
      "name": "doc_financeiro.pdf",
      "path": "F:/Backups/doc_financeiro.pdf",
      "category": "documents"
    }
  ],
  "references": [],
  "referenced_by": [
    {
      "item_id": 15022,
      "name": "comprovante_pix.lnk",
      "category": "system/recent"
    }
  ]
}
```

---

## 4. Prompt Engineering & Workflow Guidance

* **Workflow Guidance:** Instruir a LLM a chamar `get_item_relations` sempre que encontrar um arquivo de alto interesse (comprovante, contrato, arquivo compactado, imagem incriminatória) para verificar se ele se repete em outros dispositivos e quem o transmitiu.
* **Perguntas-Gatilho do Usuário:**
  * *"De onde veio esse arquivo PDF?"*
  * *"Esse mesmo arquivo foi encontrado em outros computadores ou celulares apreendidos?"*
  * *"Quais arquivos estavam dentro deste arquivo ZIP?"*
  * *"Existem duplicatas deste arquivo no caso?"*
