package br.com.ipedtools.mcp.tools;

import java.io.IOException;
import java.util.Map;

import br.com.ipedtools.mcp.service.IpedCoreService;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class AiDetectionTool {

    @Tool(
        name = "list_ai_filters",
        description = """
            Lists available machine learning and automated AI detection categories in the case along with item counts.
            
            WHAT THIS TOOL DOES:
            - Discovers which AI detection models were executed during case indexing (e.g. weapons, drugs, adult content/nudity, faces, audio transcripts, CSAM hash hits, OCR).
            - Returns filter types, human-readable descriptions, Lucene query syntax, and total detection counts in the active case.
            
            FORENSIC WORKFLOW GUIDANCE:
            - Call this tool first to understand what automated AI insights exist in the case before querying specific categories.
            """
    )
    public Map<String, Object> listAiFilters() {
        IpedCoreService service = IpedCoreService.getInstance();
        if (!service.isCaseOpen()) {
            return Map.of("error", "Nenhum caso está aberto no momento.");
        }
        return service.listAiFilters();
    }

    @Tool(
        name = "query_ai_detections",
        description = """
            Queries evidence items detected by automated machine learning models (weapons, drugs, nudity, faces, audio transcripts, CSAM, OCR).
            
            WHAT THIS TOOL DOES:
            - Executes specialized queries against pre-computed AI classification and computer vision fields.
            - Supports optional confidence/score thresholds for granular triage.
            - Returns matched items with forensic metadata (id, name, path, category, size, hash, triage selection state, detection scores).
            
            FORENSIC WORKFLOW GUIDANCE:
            - Supported filter_type values:
              * 'weapons': Firearms and ammunition detections.
              * 'drugs': Illicit drugs and paraphernalia detections.
              * 'nudity': Explicit/adult content detected by DIE (supports min_score from 0.0 to 1.0).
              * 'faces': Media containing detected human faces (supports min_score as min face count).
              * 'audio_transcripts': Voice notes and audio files transcribed by speech-to-text AI.
              * 'csam': Hits against child sexual abuse material known hash databases.
              * 'ocr': Scanned images and documents with text extracted via OCR.
            """
    )
    public Map<String, Object> queryAiDetections(
            @ToolArg(name = "filter_type", description = "AI detection filter: 'weapons', 'drugs', 'nudity', 'faces', 'audio_transcripts', 'csam', 'ocr'", required = true)
            String filterType,
            @ToolArg(name = "min_score", description = "Optional minimum confidence score threshold (e.g. 0.7 for nudity or audio transcription confidence)", required = false)
            Float minScore,
            @ToolArg(name = "limit", description = "Maximum number of items to return (default: 50, max: 500)", required = false)
            Integer limit,
            @ToolArg(name = "offset", description = "Pagination offset (default: 0)", required = false)
            Integer offset
    ) {
        IpedCoreService service = IpedCoreService.getInstance();
        if (!service.isCaseOpen()) {
            return Map.of("error", "Nenhum caso está aberto no momento.");
        }
        try {
            int maxLimit = limit != null && limit > 0 ? limit : 50;
            int safeOffset = offset != null && offset >= 0 ? offset : 0;
            return service.queryAiDetections(filterType, minScore, maxLimit, safeOffset);
        } catch (IllegalArgumentException e) {
            return Map.of("filter_type", filterType, "error", e.getMessage());
        } catch (IOException e) {
            return Map.of("filter_type", filterType, "error", "Erro ao consultar detecções de IA: " + e.getMessage());
        }
    }
}
