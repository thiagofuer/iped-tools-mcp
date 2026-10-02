package br.com.ipedtools.mcp.tools;

import java.util.List;
import java.util.Map;

import br.com.ipedtools.mcp.service.IpedCoreService;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class DocumentMetadataTool {

    @Tool(
        name = "get_document_metadata",
        description = """
            Retrieves forensic metadata for a list of document IDs, supporting both token-efficient summarized mode (default)
            and full-fidelity un-truncated extraction ('raw: true') with surgical key filtering ('keys').
            
            OUTPUT MODES:
            - Standard mode ('raw: false'): Properties are grouped into semantic blocks ('basic', 'communication', 'geo', 'forensic', 'extra')
              with token-safety limits (strings truncated at 500 chars, lists truncated at 10 items).
            - Full-fidelity mode ('raw: true'): Returns all indexed properties in a flat dictionary without truncation or semantic grouping.
              Use when investigating detailed registry values, mobile configs, complete participant lists, or EXIF metadata.
            
            SURGICAL KEY FILTERING:
            - Pass 'keys' (e.g. ['Hardware-Wallet-*', 'ai:*', 'image:*']) to retrieve ONLY the requested fields.
              Combine with 'raw: true' to inspect critical fields at full resolution without wasting context tokens on unrelated properties.
            
            SEMANTIC BLOCKS (Standard mode):
            - 'basic': File name, path, category, mime type, size, timestamps, deleted/carved flags.
            - 'communication': Chat & email attributes (Communication:*, Conversation:*, GroupID, sender/recipient accounts).
            - 'geo': Coordinates and locations (common:geo:locations, latitude, longitude, ufed:coordinate_id).
            - 'forensic': Hashes, hashDb:*, CSAM alerts, neural detectors (ai:csamDetector:*), and NSFW scores (nsfw_nudity_score).
            - 'extra': Media EXIF, audio transcription, Cellebrite UFED, age estimation (faceAge:*), and hardware wallets (Hardware-Wallet-*).
            """
    )
    public List<Map<String, Object>> getDocumentMetadata(
            @ToolArg(name = "doc_ids", description = "List of integer document IDs to retrieve (e.g. [101, 102, 105])")
            List<Integer> docIds,
            @ToolArg(name = "raw", description = "When true, returns un-truncated properties without 500-char or 10-item limits and without semantic grouping. Default is false.", required = false, defaultValue = "false")
            boolean raw,
            @ToolArg(name = "keys", description = "Optional list of property keys or wildcard patterns (e.g. ['Hardware-Wallet-*', 'ai:*', 'Communication:From']). If specified, only matching keys are returned.", required = false)
            List<String> keys,
            @SuppressWarnings("unused")
            @ToolArg(name = "source_id", description = "Optional ID of the evidence source", required = false)
            String sourceId
    ) {
        IpedCoreService service = IpedCoreService.getInstance();
        if (!service.isCaseOpen()) {
            return List.of(Map.of("error", "Nenhum caso está aberto no momento."));
        }
        return service.getDocumentMetadataBatch(docIds, raw, keys);
    }
}
