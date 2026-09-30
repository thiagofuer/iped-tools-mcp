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
            
            FORENSIC WORKFLOW GUIDANCE:
            - When relevant, incriminating, or evidentiary items are discovered during an investigation (e.g. fraudulent contracts, chat logs, illicit photos), call this tool with checked=true to flag them for the final forensic report.
            - If an item was previously marked in error, call this tool with checked=false to uncheck it.
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
