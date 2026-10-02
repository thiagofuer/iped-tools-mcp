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

import static org.junit.jupiter.api.Assertions.*;

public class McpToolsTest {

    private static File getCaseDir() {
        return TestCaseResolver.getPrimaryCaseDir();
    }

    static boolean isCaseAvailable() {
        return TestCaseResolver.isCaseAvailable();
    }

    @BeforeAll
    static void setUp() throws Exception {
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
        assertEquals(IpedCoreService.getInstance().getSourceId(), sources.get(0).get("id"));
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
        List<Map<String, Object>> meta = tool.getDocumentMetadata(List.of(1, 2), "");
        assertNotNull(meta);
        assertEquals(2, meta.size());
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
        ItemRelationsTool tool = new ItemRelationsTool();
        Map<String, Object> relations = tool.getItemRelations(27328);
        assertNotNull(relations);
        assertFalse(relations.containsKey("error"));
        assertEquals(27328, ((Map<?, ?>) relations.get("target_item")).get("id"));
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
        
        // 1. Top contacts
        Map<String, Object> top = tool.getTopContacts(10);
        assertNotNull(top);
        assertFalse(top.containsKey("error"));
        assertTrue(top.containsKey("contacts"));
        assertTrue(top.containsKey("total_contacts_discovered"));

        // 2. Communications graph
        Map<String, Object> graph = tool.getCommunicationsGraph("", 1, 30);
        assertNotNull(graph);
        assertFalse(graph.containsKey("error"));
        assertTrue(graph.containsKey("nodes"));
        assertTrue(graph.containsKey("edges"));

        // 3. Focal contact graph
        Map<String, Object> focal = tool.getCommunicationsGraph("Silva", 1, 10);
        assertNotNull(focal);
        assertFalse(focal.containsKey("error"));
        assertEquals("Silva", focal.get("focal_contact"));
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
        TriageTool tool = new TriageTool();

        // 1. Set item checked
        Map<String, Object> checked = tool.setItemChecked(25830, true);
        assertNotNull(checked);
        assertFalse(checked.containsKey("error"));
        assertTrue((boolean) checked.get("success"));
        assertTrue((boolean) checked.get("checked"));

        // 2. Set item unchecked
        Map<String, Object> unchecked = tool.setItemChecked(25830, false);
        assertNotNull(unchecked);
        assertFalse(unchecked.containsKey("error"));
        assertTrue((boolean) unchecked.get("success"));
        assertFalse((boolean) unchecked.get("checked"));
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void testMultimodalTool() {
        MultimodalTool tool = new MultimodalTool();
        List<Content> contents = tool.getItemThumbnail(25830, 256);
        assertNotNull(contents);
        assertEquals(2, contents.size());
        assertTrue(contents.get(0) instanceof TextContent);
        assertTrue(contents.get(1) instanceof ImageContent);
        TextContent text = (TextContent) contents.get(0);
        ImageContent image = (ImageContent) contents.get(1);
        assertTrue(text.text().contains("25830"));
        assertEquals("image/jpeg", image.mimeType());
        assertNotNull(image.data());
        assertFalse(image.data().isBlank());
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void testSimilaritySearchTool() {
        SimilaritySearchTool tool = new SimilaritySearchTool();

        // 1. Similar images
        Map<String, Object> imgRes = tool.searchSimilarImages(25830, 1.0f, 10);
        assertNotNull(imgRes);
        assertFalse(imgRes.containsKey("error"));
        assertEquals(25830, imgRes.get("reference_id"));

        // 2. Similar faces
        Map<String, Object> faceRes = tool.searchSimilarFaces(25830, 50.0f, 10);
        assertNotNull(faceRes);
        assertEquals(25830, faceRes.get("reference_id"));

        // 3. Similar documents
        Map<String, Object> docRes = tool.searchSimilarDocuments(25830, 50, 10);
        assertNotNull(docRes);
        assertEquals(25830, docRes.get("reference_id"));
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void testAiDetectionTool() {
        AiDetectionTool tool = new AiDetectionTool();

        // 1. List filters
        Map<String, Object> filters = tool.listAiFilters();
        assertNotNull(filters);
        assertFalse(filters.containsKey("error"));
        assertTrue((int) filters.get("total_filters") >= 7);

        // 2. Query detections
        Map<String, Object> detections = tool.queryAiDetections("faces", null, 10, 0);
        assertNotNull(detections);
        assertFalse(detections.containsKey("error"));
        assertEquals("faces", detections.get("filter_type"));
    }
}

