package br.com.ipedtools.mcp.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import br.com.ipedtools.mcp.service.IpedCoreService;
import io.quarkiverse.mcp.server.Content;
import io.quarkiverse.mcp.server.ImageContent;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class MultimodalTool {

    @Tool(
        name = "get_item_thumbnail",
        description = """
            Retrieves the visual thumbnail of an evidence item as a Base64-encoded JPEG image block for multimodal visual inspection.
            
            WHAT THIS TOOL DOES:
            1. Fetches pre-computed thumbnails or decodes the image/first page on-demand.
            2. Scales down the image to fit within max_dimension (default: 512px) preserving aspect ratio.
            3. Returns an MCP image content block (image/jpeg) paired with a textual description block containing metadata (ID, filename, dimensions, byte size).
            
            FORENSIC WORKFLOW GUIDANCE:
            - Use when multimodal analysis is required: examining handwritten notes, bank checks, receipts, vehicle license plates, seized ID documents, weapons, or suspect photographs.
            - If an item is non-visual (e.g. raw binary or database file without thumbnail), returns an informative error text without interrupting execution.
            """
    )
    public List<Content> getItemThumbnail(
            @ToolArg(name = "item_id", description = "The numerical ID of the item to retrieve thumbnail for", required = true)
            int itemId,
            @ToolArg(name = "max_dimension", description = "Maximum width or height in pixels (default: 512, max: 2048). Preserves aspect ratio.", required = false)
            Integer maxDimension
    ) {
        IpedCoreService service = IpedCoreService.getInstance();
        if (!service.isCaseOpen()) {
            return List.of(new TextContent("Nenhum caso está aberto no momento."));
        }

        try {
            int maxDim = maxDimension != null && maxDimension > 0 ? maxDimension : 512;
            Map<String, Object> thumbData = service.getThumbnail(itemId, maxDim);

            String base64 = (String) thumbData.get("base64");
            String mimeType = (String) thumbData.get("mime_type");
            String name = (String) thumbData.get("name");
            int w = (int) thumbData.get("width");
            int h = (int) thumbData.get("height");
            int sizeBytes = (int) thumbData.get("size_bytes");

            String desc = String.format("Miniatura do item ID %d (%s) | Dimensões: %dx%d px | Tamanho: %d bytes | Formato: %s",
                    itemId, name, w, h, sizeBytes, mimeType);

            List<Content> contents = new ArrayList<>();
            contents.add(new TextContent(desc));
            contents.add(new ImageContent(base64, mimeType));
            return contents;

        } catch (IllegalArgumentException | IllegalStateException e) {
            return List.of(new TextContent("Aviso ao carregar miniatura do item ID " + itemId + ": " + e.getMessage()));
        } catch (Exception e) {
            return List.of(new TextContent("Erro ao processar miniatura do item ID " + itemId + ": " + e.getMessage()));
        }
    }
}
