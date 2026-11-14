package br.com.ipedtools.mcp.tools;

import java.io.IOException;
import java.util.Map;

import br.com.ipedtools.mcp.service.IpedCoreService;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class TriageTool {

    @Tool(
        name = "set_item_checked",
        description = """
            Marks or unmarks a forensic item as checked (selected) in the IPED case state database.
            
            WHAT THIS TOOL DOES:
            - Mirrors the checkbox column ('Marcado/Checked') in IPED Desktop.
            - Persists the selection state immediately to the case metadata, ensuring it is visible in IPED Desktop and included when generating official reports or exports.
            
            WRITES TO THE CASE: ALWAYS ASK FIRST.
            - Never call this tool on your own initiative. Before calling it, show the user the items (ID and name)
              and the new state (checked or unchecked), and wait for the user's explicit confirmation.
              A confirmation covers only the batch shown; a new batch needs a new confirmation.
            - If you find items that seem relevant, suggest marking them and let the user decide.
            - Use checked=false to uncheck an item marked in error, also after the user confirms.
            """
    )
    public Map<String, Object> setItemChecked(
            @ToolArg(name = "item_id", description = "The numerical ID of the item to mark or unmark", required = true)
            int itemId,
            @ToolArg(name = "checked", description = "True to mark item as checked (selected); False to unmark (default: true)", required = false)
            Boolean checked
    ) {
        IpedCoreService service = IpedCoreService.getInstance();
        if (!service.isCaseOpen()) {
            return Map.of("error", "Nenhum caso está aberto no momento.");
        }
        try {
            boolean isChecked = checked == null || checked;
            return service.setItemChecked(itemId, isChecked);
        } catch (IllegalArgumentException e) {
            return Map.of("id", itemId, "error", e.getMessage());
        } catch (IOException e) {
            return Map.of("id", itemId, "error", "Erro ao atualizar estado de seleção do item: " + e.getMessage());
        }
    }
}
