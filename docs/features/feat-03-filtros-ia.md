# FEAT-03: Consulta e Filtros de Inteligência Artificial do IPED

**Código:** FEAT-03  
**Status:** Especificado (SDD)  
**Módulo IPED de Referência:** `iped.app.ui.App` (`aiFiltersTree`, `aiFiltersPanel`, `AIFiltersTreeListener`)  
**Data:** 2026-09-27  

---

## 1. Contexto e Motivação Forense

O IPED possui uma infraestrutura nativa robusta de **Inteligência Artificial Forense** que roda durante a fase de indexação (Deep Learning / redes convolucionais e modelos ONNX/PyTorch integrados):
* **Detecção de Armas de Fogo e Facas:** Identifica imagens e quadros de vídeo contendo revólveres, pistolas, fuzis e facas.
* **Detecção de Drogas e Entorpecentes:** Identifica porções, pacotes, comprimidos e plantas ilícitas.
* **Classificação de Nudez e Pornografia:** Classificação de conteúdo adulto por score de probabilidade.
* **Detecção de CSAM (Abuso Sexual Infantil):** Integração com modelos e bases conhecidas para proteção à infância.
* **Detecção de Faces e Pessoas:** Itens onde pelo menos uma face humana foi detectada.
* **Transcrições Automáticas de Áudio (Whisper / VOSK):** Mensagens de voz de WhatsApp e gravações transcritas para texto.

No IPED Desktop, o perito visualiza a árvore `aiFiltersTree`. O MCP deve permitir que a LLM consulte diretamente quais filtros foram gerados e filtre as evidências por essas predições.

---

## 2. Engenharia e APIs Internas do IPED Core

O IPED registra as predições nos campos do documento Lucene ou em estruturas de marcadores/filtros:

```java
// Consulta a campos gerados por tarefas de IA
// Exemplo de campos indexados pelo IPED:
// hasWeapon:true, weaponScore:[0.85 TO 1.0]
// hasDrug:true, drugScore:[0.70 TO 1.0]
// isNudity:true, nudityScore:[0.90 TO 1.0]
// hasFace:true
// audioTranscript:*

// Construção de queries direcionadas para cada modelo
String luceneFilter = switch(filterType.toLowerCase()) {
    case "weapons" -> "hasWeapon:true OR category:\"weapons\"";
    case "drugs" -> "hasDrug:true OR category:\"drugs\"";
    case "nudity" -> "isNudity:true OR category:\"nudity\"";
    case "faces" -> "hasFace:true";
    case "audio_transcripts" -> "hasAudioTranscript:true OR audioTranscript:*";
    default -> throw new IllegalArgumentException("Filtro desconhecido: " + filterType);
};

IPEDSearcher searcher = new IPEDSearcher(ipedSource, luceneFilter);
SearchResult results = searcher.search();
```

---

## 3. Especificação das Ferramentas MCP (`@Tool`)

### 3.1 `list_ai_filters`
```java
@Tool(name = "list_ai_filters",
      description = "Lists all AI and Computer Vision detection filters available in this IPED case (e.g. weapons, drugs, nudity, faces, audio transcripts) along with item counts.")
public List<AIFilterSummary> listAiFilters()
```

### 3.2 `query_ai_detections`
```java
@Tool(name = "query_ai_detections",
      description = "Queries evidence items flagged by IPED's automated AI detection models (weapons, drugs, nudity, faces, audio transcripts).")
public AiDetectionResult queryAiDetections(
    @ToolArg(name = "filter_type", description = "Type of detection: 'weapons', 'drugs', 'nudity', 'faces', 'audio_transcripts'.") String filterType,
    @ToolArg(name = "min_score", description = "Optional minimum confidence score (0.0 to 1.0).") Float minScore,
    @ToolArg(name = "limit", description = "Maximum number of items to return (default 20).") Integer limit
)
```

---

## 4. Prompt Engineering & Workflow Guidance

* **Perguntas-Gatilho do Usuário:**
  * *"Foram encontradas fotos de armas neste celular?"*
  * *"Há evidências visuais de drogas ou entorpecentes?"*
  * *"O IPED detectou conteúdo impróprio ou nudez?"*
  * *"Quais mensagens de áudio foram transcritas?"*
* **Workflow Guidance:**
  * Quando o usuário fizer perguntas sobre armamentos ou substâncias ilícitas, a LLM deve chamar prioritariamente `query_ai_detections(filter_type="weapons")` em vez de apenas buscar o termo textual *"arma"*, pois criminosos frequentemente não escrevem a palavra no nome do arquivo.
