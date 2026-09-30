# FEAT-02: Buscas por Similaridade Forense (Imagens, Faces e Documentos Semelhantes)

**Código:** FEAT-02  
**Status:** Especificado (SDD)  
**Módulo IPED de Referência:** `iped.app.ui.App` (`SimilarImagesSearch`, `SimilarFacesSearch`, `SimilarDocumentSearch`, botões `butSimSearch`, `butFaceSearch`)  
**Data:** 2026-09-27  

---

## 1. Contexto e Motivação Forense

O IPED possui motores avançados de busca por similaridade que vão muito além de strings e metadados:
* **Similaridade de Imagens (`SimilarImagesSearch`):** Permite encontrar imagens com conteúdo visual semelhante mesmo que tenham sido redimensionadas, salvas em formatos diferentes (JPEG vs PNG), recortadas ou tenham sofrido compressão.
* **Similaridade Facial (`SimilarFacesSearch`):** Quando o perito localiza uma foto de um investigado ou de uma vítima, o IPED consegue varrer todo o acervo (milhares de fotos e vídeos) agrupando todas as mídias onde aquela mesma face aparece com alta confiança.
* **Similaridade de Documentos (`SimilarDocumentSearch`):** Permite encontrar minutas, contratos ou relatórios que compartilham grande parte do texto (utilizando algoritmos de similaridade estatística / MoreLikeThis / MinHash).

Fornecer essas ferramentas via MCP permite que a LLM auxilie o perito em investigações de identificação humana, fraudes contratuais e localização de fotos correlatas.

---

## 2. Engenharia e APIs Internas do IPED Core

```java
// 1. Busca por Imagens Semelhantes
SimilarImagesSearch imageSearch = new SimilarImagesSearch(ipedSource, refItem);
imageSearch.setMinScore(0.75f); // limiar de similaridade
List<SimilarItemResult> similarImages = imageSearch.search(limit);

// 2. Busca por Rostos / Faces Semelhantes
SimilarFacesSearch faceSearch = new SimilarFacesSearch(ipedSource, refItem);
faceSearch.setMinScore(0.80f);
List<SimilarFaceResult> similarFaces = faceSearch.search(limit);

// 3. Documentos Similares (MoreLikeThis / MinHash)
SimilarDocumentSearch docSearch = new SimilarDocumentSearch(ipedSource, refItemId);
SearchResult similarDocs = docSearch.search(limit);
```

---

## 3. Especificação das Ferramentas MCP (`@Tool`)

### 3.1 `search_similar_images`
```java
@Tool(name = "search_similar_images",
      description = "Finds images that are visually similar to a given reference image item ID. Useful for finding variations, crops, or re-compressed versions of a photo.")
public SimilarItemsResponse searchSimilarImages(
    @ToolArg(name = "item_id", description = "The ID of the reference image.") int itemId,
    @ToolArg(name = "min_similarity", description = "Minimum similarity score between 0.0 and 1.0 (default 0.75).") Float minSimilarity,
    @ToolArg(name = "limit", description = "Maximum number of results to return (default 20).") Integer limit
)
```

### 3.2 `search_similar_faces`
```java
@Tool(name = "search_similar_faces",
      description = "Searches the case for all photos containing the same face as in the reference photo item ID. Essential for facial recognition and identifying a suspect across all images.")
public SimilarFacesResponse searchSimilarFaces(
    @ToolArg(name = "item_id", description = "The ID of the image containing the target face.") int itemId,
    @ToolArg(name = "min_confidence", description = "Confidence threshold between 0.0 and 1.0 (default 0.80).") Float minConfidence,
    @ToolArg(name = "limit", description = "Maximum number of matches (default 30).") Integer limit
)
```

### 3.3 `search_similar_documents`
```java
@Tool(name = "search_similar_documents",
      description = "Finds text documents (PDFs, DOCX, TXT) that have similar content or phrasing to a reference document ID (MoreLikeThis).")
public SimilarDocumentsResponse searchSimilarDocuments(
    @ToolArg(name = "item_id", description = "The reference document item ID.") int itemId,
    @ToolArg(name = "limit", description = "Maximum number of results (default 15).") Integer limit
)
```

---

## 4. Prompt Engineering & Workflow Guidance

* **Workflow Guidance:** 
  * Se o perito perguntar *"Onde mais essa pessoa aparece no celular?"*, a LLM deve primeiro identificar o `item_id` da foto da pessoa e em seguida chamar `search_similar_faces`.
  * Se o perito perguntar *"Existem versões dessa mesma foto em outras pastas?"*, chamar `search_similar_images`.
* **Regras Negativas:** 
  * NÃO realizar busca textual por nomes próprios para encontrar fotos de pessoas quando uma foto de referência estiver disponível; use `search_similar_faces`.
