package br.com.ipedtools.mcp.tools;

import java.io.IOException;
import java.util.Map;

import br.com.ipedtools.mcp.service.IpedCoreService;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class DocumentSearchTool {

    @Tool(
        name = "search_documents",
        description = """
            Searches for documents, files, and forensic artifacts in IPED using Apache Lucene query syntax.
            Supports full-text keywords, metadata filters, boolean operators (AND, OR, NOT), wildcards (*), and ranges.
            
            BEFORE BUILDING A QUERY, DISCOVER THE NAMES USED BY THIS CASE:
              - Categories: call list_categories and use the exact names (e.g. category:"Instant Messages").
                Category names depend on the IPED version and language of the case; do not guess them.
              - Properties: call list_properties (groups, names, types, counts) and describe_property (meaning,
                scale, min/max or frequent values). Results of AI modules (nudity, faces, age, CSAM, OCR, audio
                transcription, UFED media classes) are in the 'AI' group of list_properties.

            LUCENE QUERY EXAMPLES:
              - 'contrato AND fraude'                                 -> Full-text search for both words
              - 'category:"Instant Messages" AND (dinheiro OR pix)'   -> Messages with either keyword
              - 'name:*.pdf AND size:[1000000 TO *]'                  -> PDF files of 1 MB or more
              - 'url:*banco*'                                         -> Items whose URL contains 'banco'
              - 'hash:d41d8cd98f00b204e9800998ecf8427e'              -> Lookup by exact hash
              - 'Communication\\:From:*11988887777*'                 -> Escape colons of property names with a backslash

            NUMERIC RANGES ON PROCESSING PROPERTIES:
              - 'nudityScore:[650 TO *]'                        -> Images scored as likely nudity (scale 0-1000)
              - 'ocrCharCount:[1 TO *]'                         -> Items with OCR-recognized text
              - 'audio\\:transcriptConfidence:[0.5 TO *]'      -> Transcriptions with confidence >= 0.5
              Always write decimals with a dot (0.5); the server converts it for the case locale.
              When a range finds nothing, the response 'warnings' field tells whether the property is absent
              from the case or gives its minimum and maximum values, so you can adjust the range.

            WARNINGS: when the query names a property that does not exist in the case, 'warnings' reports it
            (type 'property_not_in_case', with similar existing names). That part of the query matches nothing,
            which is not the same as absence of evidence.

            CRITICAL WARNING:
            - To identify who owns the device, DO NOT search for 'proprietario' or 'dono';
              call 'get_device_and_owner_info' instead!
            """
    )
    public Map<String, Object> searchDocuments(
            @ToolArg(name = "query", description = "Lucene search query string (e.g. 'category:whatsapp AND pix', 'name:*.docx')")
            String query,
            @ToolArg(name = "limit", description = "Max document IDs to return (default 30, max 100)", required = false, defaultValue = "30")
            int limit
    ) {
        IpedCoreService service = IpedCoreService.getInstance();
        if (!service.isCaseOpen()) {
            return Map.of("error", "Nenhum caso está aberto no momento.");
        }
        try {
            return service.executeSearch(query, limit);
        } catch (IOException e) {
            return Map.of("error", "Erro ao executar busca Lucene: " + e.getMessage(), "query", query);
        }
    }
}
