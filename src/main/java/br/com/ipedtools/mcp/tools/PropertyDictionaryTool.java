package br.com.ipedtools.mcp.tools;

import java.util.Map;

import br.com.ipedtools.mcp.service.IpedCoreService;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PropertyDictionaryTool {

    @Tool(
        name = "get_property_dictionary",
        description = """
            Returns searchable forensic property definitions, data types, descriptions, and Lucene query syntax examples
            grouped by forensic domain.
            
            AVAILABLE DOMAINS:
            - 'chats': WhatsApp, Telegram, SMS communication fields (Direction, From, To, Date, Participants, GroupID, Message-Body).
            - 'browsers': Web history and download properties (url, visitDate, downloadDate, totalBytes, localPath).
            - 'emails': Email metadata (from, to, cc, bcc, subject, Message-Subject, Message-IsEmailAttachment).
            - 'media': Image/video/audio EXIF, transcription, and face recognition (image:make, image:model, audio:transcription, face_count).
            - 'system': File system attributes (name, path, category, type, size, created, modified, deleted, carved).
            - 'gps': Coordinates and geographic location data (common:geo:locations, latitude, longitude, ufed:coordinate_id).
            - 'ufed': Cellebrite UFED device extraction properties (ufed:EntryName, ufed:EntryValue, ufed:id, ufed:file_id).
            - 'ai': Computer vision, transcription, neural CSAM (ai:csamDetector:csam), age estimation (faceAge:count:Child), NSFW scores (nsfw_nudity_score), and hash matches.
            - 'crypto': Cryptocurrency hardware wallet artifacts (Hardware-Wallet-Found, Hardware-Wallet-VendorName, Hardware-Wallet-DeviceName).
            - 'all': Complete catalog encompassing all forensic domains.
            
            IMPORTANT LUCENE QUERY ESCAPING:
            Fields containing colons (e.g., 'Communication:From' or 'common:geo:locations') MUST have the colon escaped
            with a backslash in queries:
            Example: Communication\\:Direction:INCOMING
            Example: Communication\\:From:*11988887777*
            Example: common\\:geo\\:locations:*
            Example: ai\\:csamDetector\\:csam:>0.6
            Example: Hardware-Wallet-Found:true
            """
    )
    public Map<String, Object> getPropertyDictionary(
            @ToolArg(name = "domain", description = "Forensic domain to inspect: 'chats', 'browsers', 'emails', 'media', 'system', 'gps', 'ufed', 'ai', 'crypto', or 'all' (default)", required = false, defaultValue = "all")
            String domain
    ) {
        return IpedCoreService.getInstance().getPropertyDictionary(domain);
    }

    @Tool(
        name = "list_available_properties",
        description = """
            Dynamically inspects the Lucene index of the active case and returns the distinct metadata property keys
            actually populated for items in a specified category (e.g. 'chat messages', 'browsers/history', 'contacts', 'audios').
            
            WHEN TO USE:
            - When querying a specific evidence category and wanting to know the exact field names and sample values
              present in this specific case before running precise Lucene queries.
            - Provides sample values, document occurrence counts, and ready-to-use escaped Lucene query examples.
            """
    )
    public Map<String, Object> listAvailableProperties(
            @ToolArg(name = "category", description = "Category name (e.g. 'chat messages', 'browsers/history', 'audios', or '' for all items)", required = false, defaultValue = "")
            String category
    ) {
        IpedCoreService service = IpedCoreService.getInstance();
        if (!service.isCaseOpen()) {
            return Map.of("error", "Nenhum caso do IPED está aberto no momento.");
        }
        try {
            return service.listAvailableProperties(category);
        } catch (Exception e) {
            return Map.of("error", "Falha ao listar propriedades disponíveis: " + e.getMessage());
        }
    }
}
