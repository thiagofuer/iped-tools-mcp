package br.com.ipedtools.mcp.tools;

import java.io.File;
import java.util.List;
import java.util.Map;

import io.quarkiverse.mcp.server.Content;
import io.quarkiverse.mcp.server.ImageContent;
import io.quarkiverse.mcp.server.TextContent;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import br.com.ipedtools.mcp.service.IpedCoreService;
import br.com.ipedtools.mcp.test.TestCaseResolver;
import br.com.ipedtools.mcp.test.TestItems;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class McpToolsTest {

    private static File getCaseDir() {
        return TestCaseResolver.getPrimaryCaseDir();
    }

    static boolean isCaseAvailable() {
        return TestCaseResolver.isCaseAvailable();
    }

    @BeforeAll
    static void setUp() {
        if (isCaseAvailable()) {
            IpedCoreService.getInstance().openCase(getCaseDir());
        }
    }

    @AfterAll
    static void tearDown() {
        if (isCaseAvailable()) {
            IpedCoreService.getInstance().closeCase();
        }
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void testServerStatusTool() {
        ServerStatusTool tool = new ServerStatusTool();
        Map<String, Object> status = tool.getServerStatus();
        assertNotNull(status);
        assertTrue((boolean) status.get("connected"));
        assertTrue((boolean) status.get("case_open"));
        assertEquals(br.com.ipedtools.mcp.VersionInfo.getVersion(), status.get("server_version"));
        assertEquals(1, status.get("sources_count"));

        Map<String, Object> conn = tool.checkConnection();
        assertEquals(status, conn);
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void testCaseSummaryTool() {
        CaseSummaryTool tool = new CaseSummaryTool();
        Map<String, Object> summary = tool.getCaseSummary("");
        assertNotNull(summary);
        assertTrue((int) summary.get("total_indexed_items") > 0);
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void testSourcesTool() {
        SourcesTool tool = new SourcesTool();
        List<Map<String, Object>> sources = tool.listSources();
        assertNotNull(sources);
        assertEquals(1, sources.size());
        assertEquals(IpedCoreService.getInstance().getSourceId(), sources.getFirst().get("id"));
    }

    @Test
    @EnabledIf("isCaseAvailable")
    @SuppressWarnings("unchecked")
    void testDeviceAccountsTool() {
        DeviceAccountsTool tool = new DeviceAccountsTool();
        Map<String, Object> res = tool.getDeviceAndAccounts("");
        assertNotNull(res);
        assertFalse(res.containsKey("error"), String.valueOf(res.get("error")));
        assertTrue(res.containsKey("note"));
        assertTrue(res.containsKey("evidence_type"));
        List<Map<String, Object>> evidences = (List<Map<String, Object>>) res.get("evidences");
        assertTrue(evidences.getFirst().containsKey("account_identifiers"));
        assertFalse(res.keySet().stream().anyMatch(k -> k.toLowerCase().contains("owner")));
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void testDocumentSearchTool() {
        DocumentSearchTool tool = new DocumentSearchTool();
        Map<String, Object> result = tool.searchDocuments("*:*", 10, null);
        assertNotNull(result);
        assertEquals(10, result.get("returned_count"));
        assertNotNull(result.get("items"));
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void testDocumentMetadataTool() {
        DocumentMetadataTool tool = new DocumentMetadataTool();

        // 1. Standard mode
        List<Map<String, Object>> meta = tool.getDocumentMetadata(List.of(1, 2), false, null, null, "");
        assertNotNull(meta);
        assertEquals(2, meta.size());
        assertFalse(meta.getFirst().containsKey("raw"));

        // 2. Raw mode with key filtering
        List<Map<String, Object>> rawMeta = tool.getDocumentMetadata(List.of(1), true, List.of("name"), null, "");
        assertNotNull(rawMeta);
        assertEquals(1, rawMeta.size());
        assertEquals(Boolean.TRUE, rawMeta.getFirst().get("raw"));
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) rawMeta.getFirst().get("properties");
        assertNotNull(props);
        assertTrue(props.containsKey("name"));
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void testDocumentTextTool() {
        DocumentTextTool tool = new DocumentTextTool();
        String text = tool.getDocumentText(1, 0, 500, "");
        assertNotNull(text);
        assertFalse(text.isEmpty());
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void testCategoryAndBookmarkTools() {
        CategoryListTool catTool = new CategoryListTool();
        List<String> categories = catTool.listCategories();
        assertNotNull(categories);
        assertFalse(categories.isEmpty());

        BookmarkTools bmTools = new BookmarkTools();
        List<String> bookmarks = bmTools.listBookmarks();
        assertNotNull(bookmarks);

        String addResult = bmTools.addToBookmark("TestBookmark_MCP", List.of(1));
        assertNotNull(addResult);
        assertTrue(addResult.startsWith("Sucesso:"));
    }

    @Test
    void testDocumentSearchToolDescribesNumericRanges() throws Exception {
        io.quarkiverse.mcp.server.Tool tool = DocumentSearchTool.class
                .getMethod("searchDocuments", String.class, int.class, Integer.class)
                .getAnnotation(io.quarkiverse.mcp.server.Tool.class);
        assertNotNull(tool);
        assertTrue(tool.description().contains("nudityScore:[650 TO *]"));
        assertTrue(tool.description().contains("decimals with a dot"));
        for (String term : java.util.List.of("offset", "has_more", "next_offset", "total_found")) {
            assertTrue(tool.description().contains(term), "descrição deve explicar a paginação: " + term);
        }
    }

    @Test
    @EnabledIf("isCaseAvailable")
    @SuppressWarnings("unchecked")
    void testDocumentSearchToolPaginates() {
        DocumentSearchTool tool = new DocumentSearchTool();
        Map<String, Object> first = tool.searchDocuments("*:*", 5, null);
        assertEquals(true, first.get("has_more"));
        assertEquals(5, first.get("next_offset"));
        Map<String, Object> second = tool.searchDocuments("*:*", 5, (Integer) first.get("next_offset"));
        assertEquals(5, second.get("offset"));
        java.util.Set<Object> firstIds = new java.util.HashSet<>();
        ((List<Map<String, Object>>) first.get("items")).forEach(i -> firstIds.add(i.get("id")));
        assertTrue(((List<Map<String, Object>>) second.get("items")).stream().noneMatch(i -> firstIds.contains(i.get("id"))));
    }

    @Test
    void testWriteToolsRequireUserConfirmation() throws Exception {
        io.quarkiverse.mcp.server.Tool bookmark = BookmarkTools.class
                .getMethod("addToBookmark", String.class, List.class)
                .getAnnotation(io.quarkiverse.mcp.server.Tool.class);
        io.quarkiverse.mcp.server.Tool checked = TriageTool.class
                .getMethod("setItemChecked", int.class, Boolean.class)
                .getAnnotation(io.quarkiverse.mcp.server.Tool.class);
        for (io.quarkiverse.mcp.server.Tool t : List.of(bookmark, checked)) {
            assertTrue(t.description().contains("ALWAYS ASK FIRST"), t.name() + " deve exigir pergunta antes");
            assertTrue(t.description().contains("explicit confirmation"), t.name() + " deve exigir confirmação expressa");
            assertTrue(t.description().contains("Never call this tool on your own initiative"), t.name());
            assertFalse(t.description().contains("are discovered during an investigation"),
                    t.name() + " não pode orientar marcar por iniciativa própria");
        }
        String normalized = String.join(" ", bookmark.description().split("\\s+"));
        assertTrue(normalized.contains("never use or add words that identify AI or LLM authorship"),
                "add_to_bookmark deve proibir identificar IA no nome do marcador");
    }

    @Test
    void testPropertyCatalogToolsAreRegistered() throws Exception {
        io.quarkiverse.mcp.server.Tool list = PropertyCatalogTool.class
                .getMethod("listProperties", String.class, String.class, String.class, Boolean.class)
                .getAnnotation(io.quarkiverse.mcp.server.Tool.class);
        io.quarkiverse.mcp.server.Tool describe = PropertyCatalogTool.class
                .getMethod("describeProperty", String.class, String.class, Integer.class)
                .getAnnotation(io.quarkiverse.mcp.server.Tool.class);
        assertEquals("list_properties", list.name());
        assertEquals("describe_property", describe.name());
        assertTrue(list.description().contains("describe_property(name=\"nudityScore\")"), "deve mostrar o fluxo de descoberta");
        assertTrue(list.description().contains("search_documents"), "deve terminar o fluxo em search_documents");
        assertTrue(list.description().contains("faceAge\\:labels:child"), "deve mostrar o escape de dois-pontos");
        assertTrue(describe.description().contains("audio\\:transcriptConfidence:[0.5 TO *]"), "deve mostrar o escape");
    }

    @Test
    void testToolCatalogHas26ToolsAndNoRemovedOnes() throws Exception {
        // Tool classes are read from the main output directory (target/classes), where PropertyCatalogTool lives.
        File root = new File(PropertyCatalogTool.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        File dir = new File(root, "br/com/ipedtools/mcp/tools");
        java.util.Set<String> names = new java.util.TreeSet<>();
        File[] classes = dir.listFiles((d, n) -> n.endsWith(".class") && !n.contains("$"));
        assertNotNull(classes);
        for (File f : classes) {
            String cls = "br.com.ipedtools.mcp.tools." + f.getName().replace(".class", "");
            for (java.lang.reflect.Method m : Class.forName(cls).getDeclaredMethods()) {
                io.quarkiverse.mcp.server.Tool t = m.getAnnotation(io.quarkiverse.mcp.server.Tool.class);
                if (t != null) {
                    names.add(t.name());
                }
            }
        }
        assertEquals(26, names.size(), "ferramentas MCP: " + names);
        for (String removed : List.of("list_ai_filters", "query_ai_detections", "get_property_dictionary", "list_available_properties")) {
            assertFalse(names.contains(removed), "ferramenta removida ainda exposta: " + removed);
        }
        assertTrue(names.containsAll(List.of("list_properties", "describe_property")));
    }

    @Test
    @EnabledIf("isCaseAvailable")
    @SuppressWarnings("unchecked")
    void testListPropertiesTool() {
        PropertyCatalogTool tool = new PropertyCatalogTool();

        Map<String, Object> overview = tool.listProperties(null, null, null, null);
        assertFalse(overview.containsKey("error"), String.valueOf(overview.get("error")));
        List<Map<String, Object>> groups = (List<Map<String, Object>>) overview.get("groups");
        assertNotNull(groups);
        assertTrue((int) overview.get("total_properties") > 0);
        for (Map<String, Object> g : groups) {
            assertTrue(g.keySet().containsAll(List.of("name", "description", "property_count")), "formato do grupo: " + g);
        }

        Map<String, Object> ai = tool.listProperties("ai", null, null, null);
        assertEquals("AI", ai.get("group"));
        List<Map<String, Object>> props = (List<Map<String, Object>>) ai.get("properties");
        assertFalse(props.isEmpty());
        assertTrue(ai.containsKey("total") && ai.containsKey("truncated"));
        for (Map<String, Object> p : props) {
            assertTrue(p.keySet().containsAll(List.of("name", "group", "type", "item_count", "curated", "query_example")),
                    "formato da propriedade: " + p);
        }

        Map<String, Object> bad = tool.listProperties("Inexistente", null, null, null);
        assertTrue(bad.containsKey("error"));
        assertNotNull(bad.get("available_groups"));
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void testDescribePropertyTool() {
        PropertyCatalogTool tool = new PropertyCatalogTool();

        Map<String, Object> d = tool.describeProperty("nudityScore", null, null);
        for (String key : List.of("name", "present", "group", "type", "item_count", "stats", "query_examples")) {
            assertTrue(d.containsKey(key), "describe_property sem '" + key + "': " + d);
        }
        assertEquals(true, d.get("present"));

        Map<String, Object> absent = tool.describeProperty("hasWeapon", null, null);
        assertEquals(false, absent.get("present"));
        assertTrue(absent.containsKey("similar"));

        Map<String, Object> missingName = tool.describeProperty(" ", null, null);
        assertTrue(missingName.containsKey("error"));
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void testItemRelationsTool() {
        int[] pair = TestItems.childAndParent();
        assumeTrue(pair != null, "Caso sem subitens com parent");
        int childId = pair[0];
        ItemRelationsTool tool = new ItemRelationsTool();
        Map<String, Object> relations = tool.getItemRelations(childId);
        assertNotNull(relations);
        assertFalse(relations.containsKey("error"));
        assertEquals(childId, ((Map<?, ?>) relations.get("target_item")).get("id"));
        assertNotNull(relations.get("parent"));
        assertTrue(relations.containsKey("children"));
        assertTrue(relations.containsKey("duplicates"));
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void testTimelineTool() {
        TimelineTool tool = new TimelineTool();
        Map<String, Object> timeline = tool.getTimeline("2024-06-01", "2024-06-05", "whatsapp", 15);
        assertNotNull(timeline);
        assertFalse(timeline.containsKey("error"));
        assertTrue((int) timeline.get("returned_count") > 0);

        Map<String, Object> around = tool.getEventsAroundTime("2024-06-02T17:39:14Z", 45, 10);
        assertNotNull(around);
        assertFalse(around.containsKey("error"));
        assertEquals(45, around.get("window_minutes"));
        assertTrue(around.containsKey("events"));
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void testCommunicationsGraphTool() {
        CommunicationsGraphTool tool = new CommunicationsGraphTool();

        // 1. Top contacts: previous contract fields kept, plus diagnostics/truncation
        Map<String, Object> top = tool.getTopContacts(10, "", "", "");
        assertNotNull(top);
        assertFalse(top.containsKey("error"));
        assertTrue(top.containsKey("contacts"));
        assertTrue(top.containsKey("total_contacts_discovered"));
        assertTrue(top.containsKey("returned_count"));
        assertTrue(top.containsKey("truncated"));
        assertTrue(top.containsKey("unknown_direction_total"));
        assertTrue(top.containsKey("group_activity"));
        assertTrue(top.containsKey("diagnostics"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> contacts = (List<Map<String, Object>>) top.get("contacts");
        assertTrue(contacts.size() <= 10);
        for (Map<String, Object> c : contacts) {
            for (String key : List.of("rank", "identifier", "name", "total_interactions", "messages_count", "calls_count",
                    "incoming_count", "outgoing_count", "channels", "first_interaction", "last_interaction",
                    "unknown_direction_count", "by_channel", "by_month", "active_days", "evidence_ids")) {
                assertTrue(c.containsKey(key), "Contato deve conter " + key);
            }
            assertEquals((int) c.get("total_interactions"), (int) c.get("incoming_count") + (int) c.get("outgoing_count"),
                    "Total do ranking deve ser formado só por eventos com direção explícita");
            assertTrue(((List<?>) c.get("evidence_ids")).size() <= 5);
        }

        // 2. Communications graph
        Map<String, Object> graph = tool.getCommunicationsGraph("", 1, 30, "", "", "", false);
        assertNotNull(graph);
        assertFalse(graph.containsKey("error"));
        assertTrue(graph.containsKey("nodes"));
        assertTrue(graph.containsKey("edges"));
        assertTrue(graph.containsKey("truncated"));
        assertTrue(graph.containsKey("diagnostics"));
        assertTrue(graph.containsKey("unlinked_events"));
        assertEquals("lucene-index", graph.get("graph_source"));
        assertFalse(graph.containsKey("mermaid"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) graph.get("nodes");
        for (Map<String, Object> n : nodes) {
            assertNotEquals("Dispositivo_Local", n.get("id"));
            assertTrue(List.of("ACCOUNT", "GROUP", "LOCAL_ENDPOINT").contains((String) n.get("type")));
            if ("LOCAL_ENDPOINT".equals(n.get("type"))) {
                assertEquals(false, n.get("identity_resolved"));
            }
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> edges = (List<Map<String, Object>>) graph.get("edges");
        assertTrue(edges.size() <= 30);

        Map<String, Object> withMermaid = tool.getCommunicationsGraph("", 1, 30, "", "", "", true);
        assertTrue(((String) withMermaid.get("mermaid")).startsWith("flowchart LR"));

        // 3. Focal contact graph
        Map<String, Object> focal = tool.getCommunicationsGraph("Silva", 1, 10, "", "", "", false);
        assertNotNull(focal);
        assertFalse(focal.containsKey("error"));
        assertEquals("Silva", focal.get("focal_contact"));
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void testContactRelationshipTool() {
        CommunicationsGraphTool tool = new CommunicationsGraphTool();
        Map<String, Object> graph = tool.getCommunicationsGraph("", 1, 50, "", "", "", false);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> edges = (List<Map<String, Object>>) graph.get("edges");
        Map<String, Object> edge = edges.stream()
                .filter(e -> !((String) e.get("source")).startsWith("local:") && !((String) e.get("target")).startsWith("local:"))
                .findFirst().orElse(null);
        if (edge != null) {
            Map<String, Object> rel = tool.getContactRelationship((String) edge.get("source"), (String) edge.get("target"), "", "", "", 5);
            assertFalse(rel.containsKey("error"));
            assertEquals("found", rel.get("status"));
            assertTrue((int) rel.get("total_interactions") >= 1 || !((List<?>) rel.get("shared_groups")).isEmpty());
            assertTrue(rel.containsKey("by_channel"));
            assertTrue(rel.containsKey("by_month"));
            assertTrue(rel.containsKey("diagnostics"));
            assertTrue(((List<?>) rel.get("evidence_ids")).size() <= 5);
        }

        Map<String, Object> missing = tool.getContactRelationship("zz-inexistente-zz", "yy-inexistente-yy", "", "", "", 5);
        assertEquals("not_found", missing.get("status"));
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void testEvidenceTreeTool() {
        EvidenceTreeTool tool = new EvidenceTreeTool();

        // 1. Root listing
        Map<String, Object> root = tool.listFolderContents("/", false, 20);
        assertNotNull(root);
        assertFalse(root.containsKey("error"));
        assertEquals("/", root.get("folder_path"));
        assertFalse((boolean) root.get("recursive"));

        // 2. Specific directory listing
        Map<String, Object> dir = tool.listFolderContents("EXTRACTION_FFS.zip", false, 20);
        assertNotNull(dir);
        assertFalse(dir.containsKey("error"));
        assertTrue((int) dir.get("total_subdirectories") > 0);

        // 3. Recursive listing
        Map<String, Object> rec = tool.listFolderContents("EXTRACTION_FFS.zip/apex", true, 20);
        assertNotNull(rec);
        assertFalse(rec.containsKey("error"));
        assertTrue((boolean) rec.get("recursive"));
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void testTriageTool() {
        int[] pair = TestItems.childAndParent();
        assumeTrue(pair != null, "Caso sem subitens para o teste de triagem");
        int itemId = pair[0];
        boolean initial = IpedCoreService.getInstance().getSource().getBookmarks().isChecked(itemId);
        TriageTool tool = new TriageTool();

        // 1. Set item checked
        Map<String, Object> checked = tool.setItemChecked(itemId, true);
        assertNotNull(checked);
        assertFalse(checked.containsKey("error"));
        assertTrue((boolean) checked.get("success"));
        assertTrue((boolean) checked.get("checked"));

        // 2. Set item unchecked
        Map<String, Object> unchecked = tool.setItemChecked(itemId, false);
        assertNotNull(unchecked);
        assertFalse(unchecked.containsKey("error"));
        assertTrue((boolean) unchecked.get("success"));
        assertFalse((boolean) unchecked.get("checked"));

        // Restore the examiner's original triage state
        if (initial) {
            tool.setItemChecked(itemId, true);
        }
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void testMultimodalTool() {
        Integer imageId = TestItems.imageWithThumbnail();
        assumeTrue(imageId != null, "Caso sem imagens com miniatura");
        MultimodalTool tool = new MultimodalTool();
        List<Content> contents = tool.getItemThumbnail(imageId, 256);
        assertNotNull(contents);
        assertEquals(2, contents.size());
        assertInstanceOf(TextContent.class, contents.getFirst());
        assertInstanceOf(ImageContent.class, contents.get(1));
        TextContent text = (TextContent) contents.getFirst();
        ImageContent image = (ImageContent) contents.get(1);
        assertTrue(text.text().contains(String.valueOf(imageId)));
        assertEquals("image/jpeg", image.mimeType());
        assertNotNull(image.data());
        assertFalse(image.data().isBlank());
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void testSimilaritySearchTool() {
        Integer imageId = TestItems.imageWithSimilarityFeatures();
        assumeTrue(imageId != null, "Caso sem imagens com vetores de similaridade");
        SimilaritySearchTool tool = new SimilaritySearchTool();

        // 1. Similar images
        Map<String, Object> imgRes = tool.searchSimilarImages(imageId, 1.0f, 10);
        assertNotNull(imgRes);
        assertFalse(imgRes.containsKey("error"));
        assertEquals(imageId, imgRes.get("reference_id"));

        // 2. Similar faces
        Map<String, Object> faceRes = tool.searchSimilarFaces(imageId, 50.0f, 10);
        assertNotNull(faceRes);
        assertEquals(imageId, faceRes.get("reference_id"));

        // 3. Similar documents
        Map<String, Object> docRes = tool.searchSimilarDocuments(imageId, 50, 10);
        assertNotNull(docRes);
        assertEquals(imageId, docRes.get("reference_id"));
    }

    @Test
    void testEnforceUtf8StandardStreams() {
        java.io.PrintStream origOut = System.out;
        java.io.PrintStream origErr = System.err;
        try {
            java.nio.charset.Charset cp1252 = java.nio.charset.Charset.forName("windows-1252");
            System.setOut(new java.io.PrintStream(new java.io.ByteArrayOutputStream(), true, cp1252));
            System.setErr(new java.io.PrintStream(new java.io.ByteArrayOutputStream(), true, cp1252));
            assertEquals(cp1252, System.out.charset());

            br.com.ipedtools.mcp.McpApplication.enforceUtf8StandardStreams();
            assertEquals(java.nio.charset.StandardCharsets.UTF_8, System.out.charset());
            assertEquals(java.nio.charset.StandardCharsets.UTF_8, System.err.charset());
            assertEquals("UTF-8", System.getProperty("stdout.encoding"));
            assertEquals("UTF-8", System.getProperty("stderr.encoding"));
            assertEquals("UTF-8", System.getProperty("stdin.encoding"));
            // The property set at runtime is what is checked; Charset.defaultCharset() is fixed at JVM start.
            //noinspection SystemGetProperty
            assertEquals("UTF-8", System.getProperty("file.encoding"));

            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            try (java.io.PrintStream utf8Stream = new java.io.PrintStream(baos, true, System.out.charset())) {
                utf8Stream.print("Aparelho_Exemplo_2024-01-01_Relatório.ufdr | Perícia");
            }
            String decoded = baos.toString(java.nio.charset.StandardCharsets.UTF_8);
            assertEquals("Aparelho_Exemplo_2024-01-01_Relatório.ufdr | Perícia", decoded);
            assertFalse(decoded.contains("\uFFFD"));
        } finally {
            System.setOut(origOut);
            System.setErr(origErr);
        }
    }
}

