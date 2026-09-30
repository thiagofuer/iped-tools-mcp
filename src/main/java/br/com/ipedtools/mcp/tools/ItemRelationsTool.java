package br.com.ipedtools.mcp.tools;

import java.io.IOException;
import java.util.Map;

import br.com.ipedtools.mcp.service.IpedCoreService;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ItemRelationsTool {

    @Tool(
        name = "get_item_relations",
        description = """
            Retrieves the relational lineage and exact hash duplicates for a specific item in the IPED case.
            
            WHAT THIS TOOL REVEALS:
            1. Parent Container: Identifies the parent file, archive, folder, or database that contained this item.
            2. Child Sub-items: Lists items extracted from or belonging to this container (e.g., attachments, carved files).
            3. Hash Duplicates: Finds exact duplicate files across all seized devices and evidence sources sharing the same MD5 or SHA-256 hash.
            
            FORENSIC WORKFLOW GUIDANCE:
            - When an incriminating or suspicious file is found (e.g. PDF, image, audio), call this tool to discover:
              a) Where did it come from? (Was it received via WhatsApp, downloaded from a browser, or unpacked from a ZIP?)
              b) Does the exact same file exist on other devices seized in the investigation?
              c) If it is an archive or container, what files were extracted from it?
            """
    )
    public Map<String, Object> getItemRelations(
            @ToolArg(name = "item_id", description = "The numerical ID of the item to inspect relations for", required = true)
            int itemId
    ) {
        IpedCoreService service = IpedCoreService.getInstance();
        if (!service.isCaseOpen()) {
            return Map.of("error", "Nenhum caso está aberto no momento.");
        }
        try {
            return service.getItemRelations(itemId);
        } catch (IOException e) {
            return Map.of("id", itemId, "error", "Erro ao recuperar relações do item: " + e.getMessage());
        }
    }
}
