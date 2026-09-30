package br.com.ipedtools.mcp.tools;

import br.com.ipedtools.mcp.service.IpedCoreService;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class DocumentTextTool {

    @Tool(
        name = "get_document_text",
        description = """
            Retrieves the extracted plain text from a document, chat message body, email, or OCR.
            Safely truncated into manageable chunks to preserve context window limits.
            
            WHEN TO USE:
            - Call this tool when you need to read the actual textual contents of an item (e.g. reading a PDF,
              reading an email body, or examining transcription text of an audio note).
            - If the text is long, the response includes a truncation notice indicating how to fetch the next chunk.
            """
    )
    public String getDocumentText(
            @ToolArg(name = "doc_id", description = "The integer document ID to read", required = true)
            int docId,
            @ToolArg(name = "offset", description = "Starting character offset (default 0)", required = false, defaultValue = "0")
            int offset,
            @ToolArg(name = "max_chars", description = "Maximum characters per chunk (default 3500, max 6000)", required = false, defaultValue = "3500")
            int maxChars,
            @ToolArg(name = "source_id", description = "Optional source ID", required = false, defaultValue = "")
            String sourceId
    ) {
        IpedCoreService service = IpedCoreService.getInstance();
        if (!service.isCaseOpen()) {
            return "[Erro: Nenhum caso está aberto no momento.]";
        }
        return service.getDocumentText(docId, offset, maxChars);
    }
}
