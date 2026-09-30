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
            
            AVAILABLE IPED CATEGORIES (use 'category:<name>' in query):
              - category:whatsapp               -> WhatsApp chat messages, audios, and media
              - category:"whatsapp calls"       -> WhatsApp call logs
              - category:telegram               -> Telegram chat messages, channels, and media
              - category:signal                 -> Signal private messages
              - category:skype                  -> Skype messages and calls
              - category:"instant messages"     -> SMS, MMS, and generic instant messaging
              - category:contacts               -> Extracted contact book entries
              - category:"phone calls"          -> Cellular call logs (incoming, outgoing, missed)
              - category:emails                 -> Emails (Outlook, Thunderbird, EML, MSG)
              - category:"email attachments"    -> Files attached to emails
              - category:"device information"   -> Device identifiers (IMEI, serial, model, phone number)
              - category:"user accounts"        -> User accounts configured on device (Google, Apple, etc.)
              - category:"financial accounts"   -> Financial data, bank accounts, credit cards, PIX keysag
              - category:"credit cards"         -> Credit card numbers and details
              - category:"internet history"     -> Web history and visited URLs
              - category:"web bookmarks"        -> Browser bookmarks
              - category:cookies                -> Web cookies
              - category:searches               -> Web search terms entered by the user
              - category:locations              -> GPS coordinates, waypoints, geolocations
              - category:journeys               -> Traveled routes and movement journeys
              - category:"social media activities" -> Facebook, Instagram, TikTok, Snapchat actions
              - category:passwords              -> Stored passwords and credentials
              - category:autofill               -> Browser autofill entries
              - category:"pdf documents"        -> PDF files
              - category:"text documents"       -> Word, TXT, RTF, markdown documents
              - category:spreadsheets           -> Excel, CSV, Calc spreadsheets
              - category:"other images"         -> Photos, pictures, screenshots
              - category:videos                 -> Video recordings
              - category:audios                 -> Audio files and voice recordings
              - category:"compressed archives"  -> ZIP, RAR, 7Z, TAR archives
              - category:databases              -> SQLite databases, DB files
            
            LUCENE QUERY EXAMPLES:
              - 'contrato AND fraude'                           -> Full-text search for both words
              - 'category:emails AND "transferência bancária"'   -> Emails containing exact phrase
              - 'category:whatsapp AND (dinheiro OR pagamento)' -> WhatsApp messages with either keyword
              - 'name:*.pdf AND size:>1000000'                  -> PDF files larger than 1MB
              - 'category:"internet history" AND url:*banco*'   -> Browser history matching URLs with 'banco'
              - 'hash:d41d8cd98f00b204e9800998ecf8427e'        -> Lookup by exact MD5 hash
            
            CRITICAL WARNING:
            - To identify who owns the device, DO NOT search for 'proprietario' or 'dono';
              call 'get_device_and_owner_info' instead!
            """
    )
    public Map<String, Object> searchDocuments(
            @ToolArg(name = "query", description = "Lucene search query string (e.g. 'category:whatsapp AND pix', 'name:*.docx')", required = true)
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
