package br.com.ipedtools.mcp.tools;

import java.io.IOException;
import java.util.Map;

import br.com.ipedtools.mcp.service.IpedCoreService;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SimilaritySearchTool {

    @Tool(
        name = "search_similar_images",
        description = """
            Searches for visually similar images across the case using IPED's perceptual image hashing and feature vectors.
            
            WHAT THIS TOOL DOES:
            - Compares perceptual image features (color channels, luminance, texture vectors) of the reference item against the case index.
            - Finds crops, resized copies, recompressed JPEGs, or color-adjusted variants of a reference photograph.
            - Returns matching items ranked in descending order by visual similarity score (1.0 to 100.0, or 100.0 for identical hashes).
            
            FORENSIC WORKFLOW GUIDANCE:
            - Use when tracking the dissemination of a specific illicit image, memes, forged documents, or logos across suspects' devices.
            """
    )
    public Map<String, Object> searchSimilarImages(
            @ToolArg(name = "item_id", description = "The numerical ID of the reference image item", required = true)
            int itemId,
            @ToolArg(name = "min_score", description = "Minimum similarity score threshold (default: 1.0, scale 1.0 to 100.0)", required = false)
            Float minScore,
            @ToolArg(name = "limit", description = "Maximum number of similar images to return (default: 50, max: 500)", required = false)
            Integer limit
    ) {
        IpedCoreService service = IpedCoreService.getInstance();
        if (!service.isCaseOpen()) {
            return Map.of("error", "Nenhum caso está aberto no momento.");
        }
        try {
            float min = minScore != null && minScore > 0 ? minScore : 1.0f;
            int maxLimit = limit != null && limit > 0 ? limit : 50;
            return service.searchSimilarImages(itemId, min, maxLimit);
        } catch (IllegalArgumentException e) {
            return Map.of("reference_id", itemId, "error", e.getMessage());
        } catch (IOException e) {
            return Map.of("reference_id", itemId, "error", "Erro ao executar busca de imagens similares: " + e.getMessage());
        }
    }

    @Tool(
        name = "search_similar_faces",
        description = """
            Searches for occurrences of the same human face across all case media using IPED's 128-dimensional deep facial embeddings (FaceNet/dlib).
            
            WHAT THIS TOOL DOES:
            - Compares face encodings extracted from the reference photo against all face vectors indexed in the case.
            - Returns matching photos containing the same person, ranked by facial similarity score (threshold: 0 to 100, default min: 50).
            
            FORENSIC WORKFLOW GUIDANCE:
            - Use when identifying an unknown suspect or victim appearing in multiple photos, video frames, or messaging chats.
            """
    )
    public Map<String, Object> searchSimilarFaces(
            @ToolArg(name = "item_id", description = "The numerical ID of the reference photo containing a face", required = true)
            int itemId,
            @ToolArg(name = "min_score", description = "Minimum facial similarity score (default: 50, range: 1 to 100)", required = false)
            Float minScore,
            @ToolArg(name = "limit", description = "Maximum number of face matches to return (default: 50, max: 500)", required = false)
            Integer limit
    ) {
        IpedCoreService service = IpedCoreService.getInstance();
        if (!service.isCaseOpen()) {
            return Map.of("error", "Nenhum caso está aberto no momento.");
        }
        try {
            float min = minScore != null && minScore > 0 ? minScore : 50.0f;
            int maxLimit = limit != null && limit > 0 ? limit : 50;
            return service.searchSimilarFaces(itemId, min, maxLimit);
        } catch (IllegalArgumentException e) {
            return Map.of("reference_id", itemId, "error", e.getMessage());
        } catch (IOException e) {
            return Map.of("reference_id", itemId, "error", "Erro ao executar busca de faces similares: " + e.getMessage());
        }
    }

    @Tool(
        name = "search_similar_documents",
        description = """
            Searches for text documents with statistically similar phrasing, vocabulary, or structure (MoreLikeThis / term vector analysis).
            
            WHAT THIS TOOL DOES:
            - Extracts top representative terms from the reference document's indexed content.
            - Locates other documents in the case sharing significant term overlap, ranked by relevance score.
            
            FORENSIC WORKFLOW GUIDANCE:
            - Use when tracking contract revisions, draft copies of leaked memos, fraudulent invoices, or phishing email templates.
            """
    )
    public Map<String, Object> searchSimilarDocuments(
            @ToolArg(name = "item_id", description = "The numerical ID of the reference document", required = true)
            int itemId,
            @ToolArg(name = "match_percent", description = "Minimum percentage of representative terms that must match (default: 50, range: 10 to 100)", required = false)
            Integer matchPercent,
            @ToolArg(name = "limit", description = "Maximum number of similar documents to return (default: 50, max: 500)", required = false)
            Integer limit
    ) {
        IpedCoreService service = IpedCoreService.getInstance();
        if (!service.isCaseOpen()) {
            return Map.of("error", "Nenhum caso está aberto no momento.");
        }
        try {
            int percent = matchPercent != null && matchPercent > 0 ? matchPercent : 50;
            int maxLimit = limit != null && limit > 0 ? limit : 50;
            return service.searchSimilarDocuments(itemId, percent, maxLimit);
        } catch (IllegalArgumentException e) {
            return Map.of("reference_id", itemId, "error", e.getMessage());
        } catch (IOException e) {
            return Map.of("reference_id", itemId, "error", "Erro ao executar busca de documentos similares: " + e.getMessage());
        }
    }
}
