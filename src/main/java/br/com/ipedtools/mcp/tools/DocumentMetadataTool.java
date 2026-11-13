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
            and full-fidelity un-truncated extraction ('raw: true') with group ('groups') and key ('keys') filtering.

            OUTPUT MODES:
            - Standard mode ('raw: false'): properties are grouped by the same groups used by list_properties
              (e.g. 'Basic', 'Communication', 'Common', 'Image', 'UFED', 'AI', 'HashDB', 'Regex', 'Language', 'Other'),
              with token-safety limits (strings truncated at 500 chars, lists truncated at 10 items).
              Every indexed property is kept except internal engine fields and raw text buffers, including AI results
              (nudityScore, ocrCharCount, faceAge:*, ai:*), Regex:*, language:* and ufed:mediaClassScore:*.
            - Full-fidelity mode ('raw: true'): all indexed properties in a flat dictionary, without truncation or grouping.

            FILTERING:
            - 'groups' (e.g. ['AI', 'Image']) keeps only the properties of those catalog groups.
            - 'keys' (e.g. ['Hardware-Wallet-*', 'ai:*', 'Communication:From']) keeps only the matching names or wildcards.
              Combine with 'raw: true' to inspect critical fields at full resolution without wasting context tokens.
            Use list_properties and describe_property to learn what each property means and its scale.
            """
    )
    public List<Map<String, Object>> getDocumentMetadata(
            @ToolArg(name = "doc_ids", description = "List of integer document IDs to retrieve (e.g. [101, 102, 105])")
            List<Integer> docIds,
            @ToolArg(name = "raw", description = "When true, returns un-truncated properties without 500-char or 10-item limits and without grouping. Default is false.", required = false, defaultValue = "false")
            boolean raw,
            @ToolArg(name = "keys", description = "Optional list of property keys or wildcard patterns (e.g. ['Hardware-Wallet-*', 'ai:*', 'Communication:From']). If specified, only matching keys are returned.", required = false)
            List<String> keys,
            @ToolArg(name = "groups", description = "Optional list of catalog groups to return (e.g. ['AI', 'Image', 'Communication']). See list_properties.", required = false)
            List<String> groups,
            @SuppressWarnings("unused")
            @ToolArg(name = "source_id", description = "Optional ID of the evidence source", required = false)
            String sourceId
    ) {
        IpedCoreService service = IpedCoreService.getInstance();
        if (!service.isCaseOpen()) {
            return List.of(Map.of("error", "Nenhum caso está aberto no momento."));
        }
        return service.getDocumentMetadataBatch(docIds, raw, keys, groups);
    }
}
