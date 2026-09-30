# FEAT-07: Triagem Forense, Anotações Periciais e Exportação de Evidências

**Código:** FEAT-07  
**Status:** Especificado (SDD)  
**Módulo IPED de Referência:** `iped.app.ui.App` (`exportToZip`, `checkBox`, `FILTRO_SELECTED`, `updateCaseData`, `bookmarksTree`)  
**Data:** 2026-09-27  

---

## 1. Contexto e Motivação Forense

O trabalho pericial culmina na **produção do laudo pericial** e na **entrega dos elementos de prova**:
* **Triagem / Marcação de Itens (*Checked*):** No IPED Desktop, cada linha possui um checkbox. O perito marca os itens relevantes para que constem no relatório final do caso.
* **Anotações Periciais (*Comments*):** O perito insere observações técnicas associadas aos marcadores ou aos itens (ex: *"Comprovante de pagamento da propina citado na quebra de sigilo"*).
* **Exportação Física de Arquivos:** O perito precisa frequentemente extrair os arquivos originais (planilhas, áudios, vídeos, bancos de dados) para uma pasta fora do caso IPED, a fim de anexá-los a um pen drive ou enviá-los à autoridade policial/judicial.

Permitir que a LLM execute essas ações permite um fluxo de trabalho onde o perito e a IA realizam a triagem cooperativa: a IA identifica as evidências críticas, marca os itens, redige anotações e exporta os arquivos para a pasta de trabalho do perito.

---

## 2. Engenharia e APIs Internas do IPED Core

```java
// 1. Marcação de Itens (Checked / Unchecked)
ipedSource.setChecked(itemId, true);
ipedSource.saveCheckedState(); // persiste no arquivo de estado do caso

// 2. Anotação em Marcador
ipedSource.getBookmarks().setComment(bookmarkId, "Anotação gerada pela IA...");
ipedSource.getBookmarks().saveState(true);

// 3. Exportação de Arquivos Originais
File targetDir = new File(destinationPath);
for (int itemId : itemIds) {
    IItem item = ipedSource.getItem(itemId);
    File outFile = new File(targetDir, item.getName());
    try (InputStream in = item.getStream();
         OutputStream out = new FileOutputStream(outFile)) {
        in.transferTo(out);
    }
}
```

---

## 3. Especificação das Ferramentas MCP (`@Tool`)

### 3.1 `set_item_checked`
```java
@Tool(name = "set_item_checked",
      description = "Marks or unmarks an item as checked (relevant) for the final forensic report, matching IPED Desktop's checkmark column.")
public ActionResponse setItemChecked(
    @ToolArg(name = "item_id", description = "The item numeric ID.") int itemId,
    @ToolArg(name = "checked", description = "True to mark as relevant/checked, false to unmark.") boolean checked
)
```

### 3.2 `export_evidence_items`
```java
@Tool(name = "export_evidence_items",
      description = "Exports the original decoded file(s) for the specified item IDs to a designated folder on disk or ZIP archive.")
public ExportResponse exportEvidenceItems(
    @ToolArg(name = "item_ids", description = "List of item numeric IDs to export.") List<Integer> itemIds,
    @ToolArg(name = "destination_folder", description = "Absolute destination directory path on the local system.") String destinationFolder,
    @ToolArg(name = "create_zip", description = "If true, bundles all exported items into a single ZIP archive (default false).") Boolean createZip
)
```

---

## 4. Prompt Engineering & Workflow Guidance

* **Perguntas-Gatilho do Usuário:**
  * *"Marque esses 3 comprovantes como selecionados para o laudo."*
  * *"Exporte os vídeos suspeitos para a pasta D:\Exportacao_OperacaoX."*
  * *"Salve esses arquivos no meu desktop para que eu possa ouvi-los."*
* **Regras de Segurança:**
  * Validar se o caminho de destino (`destination_folder`) é gravável antes de iniciar a extração.
  * Preservar os nomes e extensões originais dos arquivos, tratando eventuais caracteres inválidos no Windows.
