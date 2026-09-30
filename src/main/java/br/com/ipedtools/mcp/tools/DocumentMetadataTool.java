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
            Retrieves structured, token-efficient forensic metadata for a list of document IDs.
            Properties are grouped into semantic blocks:
            - 'basic': File name, path, category, mime type, size, timestamps (created, modified, accessed), deleted/carved flags.
            - 'communication': Chat & email attributes (Communication:Direction, Communication:From, Communication:To, Communication:Date, GroupID, sender/recipient accounts).
            - 'geo': Coordinates and geographical locations (common:geo:locations, latitude, longitude, ufed:coordinate_id).
            - 'forensic': Hashes (MD5, SHA-256), hash database matches (hashDb:*), and CSAM alerts.
            - 'extra': Media EXIF (image:make, image:model, video:duration), audio transcription (audio:transcription), and Cellebrite UFED attributes.
            
            WHEN TO USE:
            - Call this tool after 'search_documents' to inspect metadata for candidate items
              before deciding whether to read their full text with 'get_document_text'.
            - Use the communication and geo blocks to analyze message directions, contacts, and geographical movement.
            - Low-level internal Lucene engine noise and binary buffers are automatically filtered out.
            """
    )
    public List<Map<String, Object>> getDocumentMetadata(
            @ToolArg(name = "doc_ids", description = "List of integer document IDs to retrieve (e.g. [101, 102, 105])", required = true)
            List<Integer> docIds,
            @ToolArg(name = "source_id", description = "Optional ID of the evidence source", required = false, defaultValue = "")
            String sourceId
    ) {
        IpedCoreService service = IpedCoreService.getInstance();
        if (!service.isCaseOpen()) {
            return List.of(Map.of("error", "Nenhum caso está aberto no momento."));
        }
        return service.getDocumentMetadataBatch(docIds);
    }
}
