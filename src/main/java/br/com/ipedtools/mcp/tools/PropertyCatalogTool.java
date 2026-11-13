package br.com.ipedtools.mcp.tools;

import java.util.Map;

import br.com.ipedtools.mcp.service.IpedCoreService;
import br.com.ipedtools.mcp.service.PropertyCatalogService;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PropertyCatalogTool {

    @Tool(
        name = "list_properties",
        description = """
            Lists the metadata properties that actually exist in the active case index, grouped like the IPED
            SearchApp columns, with exact item counts (not samples).

            DISCOVERY FLOW (use before building a metadata query):
              1. list_properties()                      -> groups present in the case and how many properties each has
              2. list_properties(group="AI")            -> properties of a group: name, type, item count, description, query example
                 list_properties(pattern="faceAge*")    -> properties whose name matches a wildcard (case-insensitive)
                 list_properties(group="Communication", category="Instant Messages") -> counts restricted to a category
                 list_properties(identity=true)         -> identifier properties (phones, usernames, e-mails, account/device IDs)
              3. describe_property(name="nudityScore")  -> meaning, scale, min/max or most frequent values, query examples
              4. search_documents(query="nudityScore:[650 TO *]")

            GROUPS: Basic, AI, HashDB, Communication, Common, Regex, Language, Audio, Image, Video, PDF, Office, HTML,
            NER, P2P, UFED, WindowsEvt, Other. The AI group holds the results of IPED AI modules and imported
            classifiers: nudity (nudityScore, nudityClass, nsfw_*), faces and estimated age (face_count, faceAge:*),
            CSAM detector (ai:csamDetector:*), OCR (ocrCharCount), audio transcription (audio:transcription*) and
            Cellebrite media classes (ufed:mediaClass*). A property missing from the list was not produced for this
            case (the module may not have run): that is not the same as zero items.

            LUCENE ESCAPING: in queries, escape colons and spaces of property names with a backslash, e.g.
            faceAge\\:labels:child, ufed\\:mediaClassScore\\:Weapons:[0.5 TO *]. Each listed property carries a
            ready-to-use 'query_example'. Category names must be the exact ones returned by list_categories.
            """
    )
    public Map<String, Object> listProperties(
            @ToolArg(name = "group", description = "Optional group name (e.g. 'AI', 'Communication', 'UFED', 'Basic'). Omit with pattern to get the group overview.", required = false)
            String group,
            @ToolArg(name = "pattern", description = "Optional property name wildcard, case-insensitive (e.g. 'ufed:mediaClass*', '*Date*'). Without '*' it matches names containing the text.", required = false)
            String pattern,
            @ToolArg(name = "category", description = "Optional exact category name from list_categories; counts then refer only to items of that category.", required = false)
            String category,
            @ToolArg(name = "identity", description = "When true, lists only identity properties (phones, usernames, e-mails, account and device IDs) present in the case.", required = false)
            Boolean identity
    ) {
        IpedCoreService service = IpedCoreService.getInstance();
        if (!service.isCaseOpen()) {
            return Map.of("error", "Nenhum caso do IPED está aberto no momento.");
        }
        try {
            return PropertyCatalogService.getInstance().listProperties(group, pattern, category, Boolean.TRUE.equals(identity));
        } catch (Exception e) {
            return Map.of("error", "Falha ao listar propriedades: " + e.getMessage());
        }
    }

    @Tool(
        name = "describe_property",
        description = """
            Describes one property of the active case: group, type, exact item count, curated meaning (scale, unit,
            known values) and Lucene query examples suited to its type.
              - Numeric properties: minimum and maximum values present in the case.
              - Dates: earliest and latest values (ISO-8601).
              - Text: most frequent values with their item counts.
            With 'query', the counts and the distribution are computed only over the items matching that Lucene
            query (facets), e.g. describe_property(name="faceAge:labels", query="category:\\"Other Images\\"").

            When the property does not exist in the case the response says so ('present': false) and suggests
            existing properties with similar names. Absent is not the same as zero items: the producing IPED
            module may not have run on this case.

            Write the property name as stored (e.g. 'audio:transcriptConfidence', without escaping). Escape colons
            only inside Lucene queries: audio\\:transcriptConfidence:[0.5 TO *]. Use dots for decimals.
            """
    )
    public Map<String, Object> describeProperty(
            @ToolArg(name = "name", description = "Property name as listed by list_properties (e.g. 'nudityScore', 'ufed:mediaClasses', 'Communication:Date')")
            String name,
            @ToolArg(name = "query", description = "Optional Lucene query restricting the items considered (e.g. 'category:\"Other Images\"')", required = false)
            String query,
            @ToolArg(name = "top_n", description = "Number of most frequent values for text properties (default 20, max 100)", required = false)
            Integer topN
    ) {
        IpedCoreService service = IpedCoreService.getInstance();
        if (!service.isCaseOpen()) {
            return Map.of("error", "Nenhum caso do IPED está aberto no momento.");
        }
        if (name == null || name.isBlank()) {
            return Map.of("error", "Informe o nome da propriedade (use list_properties para descobrir os nomes).");
        }
        try {
            return PropertyCatalogService.getInstance().describeProperty(name, query, topN);
        } catch (Exception e) {
            return Map.of("name", name, "error", "Falha ao descrever a propriedade: " + e.getMessage());
        }
    }
}
