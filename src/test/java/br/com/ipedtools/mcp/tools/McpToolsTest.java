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
    void testDeviceOwnerTool() {
        DeviceOwnerTool tool = new DeviceOwnerTool();
        Map<String, Object> owner = tool.getDeviceAndOwnerInfo("");
        assertNotNull(owner);
        assertFalse(owner.containsKey("error"));
        assertTrue(owner.containsKey("likely_owner_names"));
        assertTrue(owner.containsKey("evidence_type"));
        assertTrue(owner.containsKey("system_info"));
        assertTrue(owner.containsKey("user_profile_dirs"));
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void testDocumentSearchTool() {
        DocumentSearchTool tool = new DocumentSearchTool();
        Map<String, Object> result = tool.searchDocuments("*:*", 10);
        assertNotNull(result);
        assertEquals(10, result.get("returned_count"));
        assertNotNull(result.get("items"));
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void testDocumentMetadataTool() {
        DocumentMetadataTool tool = new DocumentMetadataTool();

        // 1. Standard mode
        List<Map<String, Object>> meta = tool.getDocumentMetadata(List.of(1, 2), false, null, "");
        assertNotNull(meta);
        assertEquals(2, meta.size());
        assertFalse(meta.getFirst().containsKey("raw"));

        // 2. Raw mode with key filtering
        List<Map<String, Object>> rawMeta = tool.getDocumentMetadata(List.of(1), true, List.of("name"), "");
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
    void testPropertyDictionaryToolStatic() {
        PropertyDictionaryTool tool = new PropertyDictionaryTool();
        Map<String, Object> all = tool.getPropertyDictionary("all");
        assertNotNull(all);
        assertTrue(all.containsKey("domains"));
        assertTrue(all.containsKey("escaping_note"));

        Map<String, Object> chats = tool.getPropertyDictionary("chats");
        assertNotNull(chats);
        assertEquals("chats", chats.get("domain"));
        assertTrue(chats.containsKey("properties"));

        Map<String, Object> crypto = tool.getPropertyDictionary("crypto");
        assertNotNull(crypto);
        assertEquals("crypto", crypto.get("domain"));
        assertTrue(crypto.containsKey("properties"));
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void testListAvailableProperties() {
        PropertyDictionaryTool tool = new PropertyDictionaryTool();
        Map<String, Object> props = tool.listAvailableProperties("chat messages");
        assertNotNull(props);
        assertTrue(props.containsKey("properties"));
        assertTrue(props.containsKey("total_matching_items"));
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
    @EnabledIf("isCaseAvailable")
    void testAiDetectionTool() {
        AiDetectionTool tool = new AiDetectionTool();

        // 1. List filters
        Map<String, Object> filters = tool.listAiFilters();
        assertNotNull(filters);
        assertFalse(filters.containsKey("error"));
        assertTrue((int) filters.get("total_filters") >= 10);

        // 2. Query detections
        Map<String, Object> detections = tool.queryAiDetections("faces", null, 10, 0);
        assertNotNull(detections);
        assertFalse(detections.containsKey("error"));
        assertEquals("faces", detections.get("filter_type"));

        // 3. Query new python task filters
        Map<String, Object> crypto = tool.queryAiDetections("crypto_wallets", null, 10, 0);
        assertNotNull(crypto);
        assertFalse(crypto.containsKey("error"));
        assertEquals("crypto_wallets", crypto.get("filter_type"));

        Map<String, Object> age = tool.queryAiDetections("age_estimation", null, 10, 0);
        assertNotNull(age);
        assertFalse(age.containsKey("error"));
        assertEquals("age_estimation", age.get("filter_type"));

        Map<String, Object> nsfw = tool.queryAiDetections("nsfw", null, 10, 0);
        assertNotNull(nsfw);
        assertFalse(nsfw.containsKey("error"));
        assertEquals("nsfw", nsfw.get("filter_type"));

        Map<String, Object> csam = tool.queryAiDetections("csam", null, 10, 0);
        assertNotNull(csam);
        assertFalse(csam.containsKey("error"));
        assertEquals("csam", csam.get("filter_type"));
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

