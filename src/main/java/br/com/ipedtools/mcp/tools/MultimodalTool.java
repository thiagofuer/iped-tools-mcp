package br.com.ipedtools.mcp.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import br.com.ipedtools.mcp.service.IpedCoreService;
import br.com.ipedtools.mcp.service.ItemImageLoader;
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
            1. Uses the thumbnail IPED stored during processing (often only 256px) when it already reaches max_dimension.
            2. Otherwise decodes the ORIGINAL image at the requested size, with its EXIF orientation applied.
               Formats Java cannot decode (HEIC, SVG...) are converted with the image tool IPED uses for the case.
            3. Scales down to fit within max_dimension (default: 512px, max: 2048px), preserving aspect ratio; never enlarges.
            4. Returns an MCP image content block (image/jpeg) and a text block with the image source
               (original, view file or IPED thumbnail), its size and the original size.

            FORENSIC WORKFLOW GUIDANCE:
            - Use when multimodal analysis is required: examining handwritten notes, bank checks, receipts, vehicle license plates, seized ID documents, weapons, or suspect photographs.
            - To READ small text (photographed documents, receipts, screenshots), ask max_dimension 1536 to 2048.
              The default 512 gives an overview.
            - OCR text that IPED already extracted is read with get_document_text (section "Texto de OCR (IPED)").
            - If the text block says the source is the IPED thumbnail, a sharper image is not available.
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

            String desc = describe(itemId, name, w, h, sizeBytes, mimeType, thumbData);

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

    /** Text block that goes with the image: its source, its size and, when known, the original size. */
    static String describe(int itemId, String name, int w, int h, int sizeBytes, String mimeType,
            Map<String, Object> data) {
        String source = String.valueOf(data.getOrDefault("source", ItemImageLoader.SOURCE_THUMBNAIL));
        String origin = switch (source) {
            case ItemImageLoader.SOURCE_ORIGINAL -> "a partir do original";
            case ItemImageLoader.SOURCE_VIEW_FILE -> "a partir do arquivo de visualização do IPED";
            case ItemImageLoader.SOURCE_CONVERTED -> "convertida do original pela ferramenta de imagens do caso (formato não nativo, como HEIC ou SVG)";
            default -> "miniatura gravada pelo IPED";
        };
        StringBuilder sb = new StringBuilder(String.format("Imagem do item ID %d (%s) | %dx%d px, %s", itemId, name, w,
                h, origin));
        Object ow = data.get("original_width");
        Object oh = data.get("original_height");
        if (ow != null && oh != null) {
            sb.append(" | Original: ").append(ow).append('x').append(oh).append(" px");
        }
        sb.append(String.format(" | Tamanho: %d bytes | Formato: %s", sizeBytes, mimeType));
        if (Boolean.TRUE.equals(data.get("thumbnail_fallback"))) {
            sb.append(" | A imagem original não pôde ser decodificada: esta é a miniatura do IPED, sem versão mais nítida disponível.");
        }
        return sb.toString();
    }
}
