package br.com.ipedtools.mcp.service;

import java.io.File;
import java.util.List;
import java.util.Map;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.WildcardQuery;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import br.com.ipedtools.mcp.test.TestCaseResolver;
import br.com.ipedtools.mcp.test.TestItems;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class IpedCoreServiceTest {

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
    void testSetItemChecked() throws Exception {
        int[] pair = TestItems.childAndParent();
        assumeTrue(pair != null, "Caso sem subitens para o teste de triagem");
        int itemId = pair[0];
        IpedCoreService service = IpedCoreService.getInstance();
        boolean initial = service.getSource().getBookmarks().isChecked(itemId);
        
        // 1. Mark as checked
        Map<String, Object> resCheck = service.setItemChecked(itemId, true);
        assertNotNull(resCheck);
        assertTrue((boolean) resCheck.get("success"));
        assertTrue((boolean) resCheck.get("checked"));
        assertEquals(initial, resCheck.get("previous_state"));
        assertTrue(service.getSource().getBookmarks().isChecked(itemId));
        
        // Check document metadata reflects selection
        Map<String, Object> meta = service.getDocumentMetadata(itemId);
        assertTrue((boolean) meta.get("selected"));
        
        // 2. Unmark
        Map<String, Object> resUncheck = service.setItemChecked(itemId, false);
        assertNotNull(resUncheck);
        assertTrue((boolean) resUncheck.get("success"));
        assertFalse((boolean) resUncheck.get("checked"));
        assertFalse(service.getSource().getBookmarks().isChecked(itemId));
        
        // Restore initial if needed
        if (initial) {
            service.setItemChecked(itemId, true);
        }
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void testListFolderContentsRoot() throws Exception {
        IpedCoreService service = IpedCoreService.getInstance();
        
        // Non-recursive root
        Map<String, Object> rootRes = service.listFolderContents("/", false, 50);
        assertNotNull(rootRes);
        assertFalse(rootRes.containsKey("error"));
        assertEquals("/", rootRes.get("folder_path"));
        assertFalse((boolean) rootRes.get("recursive"));
        assertTrue((int) rootRes.get("total_subdirectories") > 0 || (int) rootRes.get("total_files") > 0);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> subdirs = (List<Map<String, Object>>) rootRes.get("subdirectories");
        assertNotNull(subdirs);
        assertFalse(subdirs.isEmpty());
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void testListFolderContentsSpecificDir() throws Exception {
        IpedCoreService service = IpedCoreService.getInstance();
        
        // Non-recursive inside EXTRACTION_FFS.zip (item id 2)
        Map<String, Object> dirRes = service.listFolderContents("EXTRACTION_FFS.zip", false, 50);
        assertNotNull(dirRes);
        assertFalse(dirRes.containsKey("error"));
        assertTrue((int) dirRes.get("total_subdirectories") > 0);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> subdirs = (List<Map<String, Object>>) dirRes.get("subdirectories");
        assertNotNull(subdirs);
        boolean foundApex = subdirs.stream().anyMatch(d -> "apex".equalsIgnoreCase((String) d.get("name")));
        assertTrue(foundApex, "Deveria conter subdiretório 'apex'");
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void testListFolderContentsRecursive() throws Exception {
        IpedCoreService service = IpedCoreService.getInstance();
        
        // Recursive inside apex (item id 3)
        Map<String, Object> recRes = service.listFolderContents("EXTRACTION_FFS.zip/apex", true, 50);
        assertNotNull(recRes);
        assertFalse(recRes.containsKey("error"));
        assertTrue((boolean) recRes.get("recursive"));
        assertTrue((int) recRes.get("total_subdirectories") > 0 || (int) recRes.get("total_files") > 0);
        assertTrue((int) recRes.get("returned_subdirectories_count") > 0 || (int) recRes.get("returned_files_count") > 0);
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void testCaseLoaded() {
        IpedCoreService service = IpedCoreService.getInstance();
        assertTrue(service.isCaseOpen());
        assertEquals(getCaseDir(), service.getCaseDirectory());
        assertTrue(service.getTotalIndexedItems() > 0, "Deveria conter itens indexados");
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void testExecuteSearch() throws Exception {
        IpedCoreService service = IpedCoreService.getInstance();
        Map<String, Object> result = service.executeSearch("*:*", 15);
        assertNotNull(result);
        assertEquals(15, result.get("returned_count"));
        assertTrue((int) result.get("total_found") > 1000);
        assertNotNull(result.get("items"));
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void testGetDocumentMetadata() throws Exception {
        IpedCoreService service = IpedCoreService.getInstance();
        Map<String, Object> meta = service.getDocumentMetadata(1);
        assertNotNull(meta);
        assertEquals(1, meta.get("id"));
        assertNotNull(meta.get("properties"));
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) meta.get("properties");
        assertTrue(props.containsKey("Basic"), "Deveria agrupar pelos grupos do catálogo: " + props.keySet());
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void testGetDocumentMetadataRawAndKeyFiltering() throws Exception {
        IpedCoreService service = IpedCoreService.getInstance();

        // 1. Raw mode without key filtering
        Map<String, Object> rawMeta = service.getDocumentMetadata(1, true, null);
        assertNotNull(rawMeta);
        assertEquals(1, rawMeta.get("id"));
        assertEquals(Boolean.TRUE, rawMeta.get("raw"));
        @SuppressWarnings("unchecked")
        Map<String, Object> rawProps = (Map<String, Object>) rawMeta.get("properties");
        assertNotNull(rawProps);
        // In raw mode, properties map is flat (not partitioned into catalog groups)
        assertFalse(rawProps.containsKey("Basic"), "Modo raw não deve agrupar propriedades");
        assertTrue(rawProps.containsKey("name") || rawProps.containsKey("path"), "Modo raw deve conter atributos diretos");

        // 2. Filtered mode with wildcard
        Map<String, Object> filteredMeta = service.getDocumentMetadata(1, true, List.of("name", "path*"));
        assertNotNull(filteredMeta);
        @SuppressWarnings("unchecked")
        Map<String, Object> filteredProps = (Map<String, Object>) filteredMeta.get("properties");
        assertNotNull(filteredProps);
        for (String k : filteredProps.keySet()) {
            assertTrue(k.equalsIgnoreCase("name") || k.toLowerCase().startsWith("path"),
                    "Chave retornada deve coincidir com o filtro: " + k);
        }

        // 3. Batch with raw and keys
        List<Map<String, Object>> batch = service.getDocumentMetadataBatch(List.of(1), true, List.of("name"));
        assertNotNull(batch);
        assertEquals(1, batch.size());
        assertEquals(Boolean.TRUE, batch.getFirst().get("raw"));
    }

    @Test
    void testMatchesAnyKeyPattern() {
        // Exact match
        assertTrue(IpedCoreService.matchesAnyKeyPattern("name", List.of("name")));
        assertTrue(IpedCoreService.matchesAnyKeyPattern("NAME", List.of("name")));

        // Wildcard match
        assertTrue(IpedCoreService.matchesAnyKeyPattern("Hardware-Wallet-VendorName", List.of("Hardware-Wallet-*")));
        assertTrue(IpedCoreService.matchesAnyKeyPattern("ai:csamDetector:csam", List.of("ai:*")));
        assertTrue(IpedCoreService.matchesAnyKeyPattern("faceAge:count:Child", List.of("*child*")));

        // Negative match
        assertFalse(IpedCoreService.matchesAnyKeyPattern("other_field", List.of("Hardware-Wallet-*", "ai:*")));
    }

    @Test
    @EnabledIf("isCaseAvailable")
    @SuppressWarnings("unchecked")
    void testMetadataKeepsAiPropertiesByDefaultAndFiltersByGroup() throws Exception {
        IpedCoreService service = IpedCoreService.getInstance();
        Map<String, Object> search = service.executeSearch("nudityScore:[1 TO *] AND ocrCharCount:[1 TO *]", 1);
        List<Map<String, Object>> items = (List<Map<String, Object>>) search.get("items");
        assertFalse(items.isEmpty(), "caso de teste deveria ter imagens com nudityScore e ocrCharCount");
        int id = (int) items.getFirst().get("id");

        Map<String, Object> props = (Map<String, Object>) service.getDocumentMetadata(id).get("properties");
        Map<String, Object> ai = (Map<String, Object>) props.get("AI");
        assertNotNull(ai, "grupo AI ausente no modo padrão: " + props.keySet());
        assertTrue(ai.containsKey("nudityScore"), "nudityScore descartado no modo padrão");
        assertTrue(ai.containsKey("ocrCharCount"), "ocrCharCount descartado no modo padrão");

        List<Map<String, Object>> onlyAi = service.getDocumentMetadataBatch(List.of(id), false, null, List.of("ai"));
        Map<String, Object> filtered = (Map<String, Object>) onlyAi.getFirst().get("properties");
        assertEquals(java.util.Set.of("AI"), filtered.keySet());

        List<Map<String, Object>> bad = service.getDocumentMetadataBatch(List.of(id), false, null, List.of("Inexistente"));
        assertTrue(bad.getFirst().containsKey("error"));
    }

    @Test
    @EnabledIf("isCaseAvailable")
    @SuppressWarnings("unchecked")
    void testSearchWarnsAboutUnknownProperty() throws Exception {
        IpedCoreService service = IpedCoreService.getInstance();
        int categoryCount = (int) service.executeSearch("category:\"Instant Messages\"", 1).get("total_found");
        Map<String, Object> res = service.executeSearch("hasWeapon:true OR category:\"Instant Messages\"", 1);
        assertEquals(categoryCount, res.get("total_found"), "a parte válida da consulta deve continuar funcionando");
        List<Map<String, Object>> warnings = (List<Map<String, Object>>) res.get("warnings");
        assertNotNull(warnings, "esperava aviso sobre hasWeapon");
        Map<String, Object> w = warnings.getFirst();
        assertEquals("property_not_in_case", w.get("type"));
        assertEquals("hasWeapon", w.get("property"));
        assertTrue(((String) w.get("message")).contains("list_properties"));

        Map<String, Object> ok = service.executeSearch("category:\"Instant Messages\" AND Communication\\:From:*", 1);
        assertFalse(ok.containsKey("warnings"), "consulta só com propriedades existentes não deve ter aviso");
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void testGetItemRelations() throws Exception {
        IpedCoreService service = IpedCoreService.getInstance();

        // 1. Root item (id=1)
        Map<String, Object> rootRel = service.getItemRelations(1);
        assertNotNull(rootRel);
        assertEquals(1, ((Map<?, ?>) rootRel.get("target_item")).get("id"));
        assertNull(rootRel.get("parent"), "Root item não deve possuir parent");
        assertTrue(rootRel.containsKey("children"));
        assertTrue(rootRel.containsKey("duplicates"));

        // 2. Subitem with parent (discovered in the open case)
        int[] pair = TestItems.childAndParent();
        assumeTrue(pair != null, "Caso sem subitens com parent");
        int childId = pair[0];
        int parentId = pair[1];
        Map<String, Object> childRel = service.getItemRelations(childId);
        assertNotNull(childRel);
        assertNotNull(childRel.get("parent"), "Subitem deve possuir parent identificado");
        @SuppressWarnings("unchecked")
        Map<String, Object> parentMap = (Map<String, Object>) childRel.get("parent");
        assertEquals(parentId, parentMap.get("id"));

        // 3. Parent container should list the child
        Map<String, Object> parentRel = service.getItemRelations(parentId);
        assertNotNull(parentRel);
        int childrenCount = (int) parentRel.get("children_count");
        assertTrue(childrenCount > 0, "Container " + parentId + " deve conter children");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> children = (List<Map<String, Object>>) parentRel.get("children");
        assertFalse(children.isEmpty());
        boolean foundChild = children.stream().anyMatch(c -> (int) c.get("id") == childId);
        assertTrue(foundChild, "Item " + childId + " deve constar na lista de filhos");
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void testGetTimeline() throws Exception {
        IpedCoreService service = IpedCoreService.getInstance();
        Map<String, Object> timeline = service.getTimeline("2024-06-01", "2024-06-05", "whatsapp", 30);
        assertNotNull(timeline);
        assertTrue((int) timeline.get("returned_count") > 0);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> events = (List<Map<String, Object>>) timeline.get("events");
        assertFalse(events.isEmpty());

        // Verify ascending chronological order
        String prevTime = "";
        for (Map<String, Object> evt : events) {
            String currTime = (String) evt.get("timestamp");
            assertNotNull(currTime);
            assertTrue(currTime.compareTo(prevTime) >= 0, "Eventos devem estar estritamente em ordem cronológica ascendente");
            assertNotNull(evt.get("event_type"));
            assertNotNull(evt.get("category"));
            prevTime = currTime;
        }
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void testGetEventsAroundTime() throws Exception {
        IpedCoreService service = IpedCoreService.getInstance();
        Map<String, Object> eventsAround = service.getEventsAroundTime("2024-06-02T17:39:14Z", 60, 20);
        assertNotNull(eventsAround);
        assertEquals("2024-06-02T17:39:14Z", eventsAround.get("target_time"));
        assertEquals(60, eventsAround.get("window_minutes"));
        assertNotNull(eventsAround.get("window_start"));
        assertNotNull(eventsAround.get("window_end"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> events = (List<Map<String, Object>>) eventsAround.get("events");
        assertNotNull(events);
        assertFalse(events.isEmpty(), "Deveria encontrar eventos na janela temporal de 60 minutos");

        // Verify ordering
        String prevTime = "";
        for (Map<String, Object> evt : events) {
            String currTime = (String) evt.get("timestamp");
            assertTrue(currTime.compareTo(prevTime) >= 0, "Eventos da janela devem estar em ordem cronológica ascendente");
            prevTime = currTime;
        }
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void testCategoriesAndBookmarks() {
        IpedCoreService service = IpedCoreService.getInstance();
        List<String> categories = service.listCategories();
        assertNotNull(categories);
        assertFalse(categories.isEmpty(), "Deveria listar categorias");
        assertTrue(categories.contains("whatsapp") || categories.contains("contacts") || categories.contains("audios"));

        List<String> bookmarks = service.listBookmarks();
        assertNotNull(bookmarks);
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void testCaseSummary() {
        IpedCoreService service = IpedCoreService.getInstance();
        Map<String, Object> summary = service.getCaseSummary();
        assertNotNull(summary);
        assertTrue((int) summary.get("total_indexed_items") > 0);
        assertTrue((int) summary.get("total_categories_count") > 0);
    }

    @Test
    @EnabledIf("isCaseAvailable")
    @SuppressWarnings("unchecked")
    void testDeviceAndAccountsFormat() throws Exception {
        Map<String, Object> res = DeviceAccountsService.getInstance().getDeviceAndAccounts();
        assertNotNull(res.get("note"), "Deveria explicar que o servidor não aponta o dono");
        assertNotNull(res.get("evidence_type"));
        List<Map<String, Object>> evidences = (List<Map<String, Object>>) res.get("evidences");
        assertFalse(evidences.isEmpty(), "Deveria retornar ao menos 1 container de evidência");
        for (Map<String, Object> ev : evidences) {
            for (String key : List.of("name", "type", "device_information", "account_identifiers", "user_profiles", "registry_hives")) {
                assertTrue(ev.containsKey(key), "Evidência sem '" + key + "': " + ev.keySet());
            }
            for (String key : ev.keySet()) {
                assertFalse(key.toLowerCase().contains("owner"), "Chave com 'owner' na evidência: " + key);
            }
        }
        for (String key : res.keySet()) {
            assertFalse(key.toLowerCase().contains("owner"), "Chave com 'owner' na resposta: " + key);
        }
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void testListSourcesMultiEvidence() {
        IpedCoreService service = IpedCoreService.getInstance();
        List<Map<String, Object>> sources = service.listSources();
        assertNotNull(sources);
        assertFalse(sources.isEmpty(), "listSources não deve retornar lista vazia quando caso aberto");
        for (Map<String, Object> src : sources) {
            assertTrue(src.containsKey("id"), "Fonte deve conter id");
            assertTrue(src.containsKey("path"), "Fonte deve conter path");
        }
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void testGetTopContacts() throws Exception {
        IpedCoreService service = IpedCoreService.getInstance();
        Map<String, Object> top = service.getTopContacts(15);
        assertNotNull(top);
        assertTrue(top.containsKey("total_contacts_discovered"));
        assertTrue(top.containsKey("returned_count"));
        assertTrue(top.containsKey("contacts"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> contacts = (List<Map<String, Object>>) top.get("contacts");
        assertNotNull(contacts);
        if (!contacts.isEmpty()) {
            Map<String, Object> first = contacts.getFirst();
            assertEquals(1, first.get("rank"));
            assertNotNull(first.get("identifier"));
            assertNotNull(first.get("name"));
            assertTrue((int) first.get("total_interactions") > 0);
            assertTrue(first.containsKey("messages_count"));
            assertTrue(first.containsKey("calls_count"));
            assertTrue(first.containsKey("channels"));
        }
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void testGetCommunicationsGraph() throws Exception {
        IpedCoreService service = IpedCoreService.getInstance();
        
        // 1. General graph without focal contact
        Map<String, Object> graph = service.getCommunicationsGraph(null, 1, 50);
        assertNotNull(graph);
        assertEquals(1, graph.get("min_interactions"));
        assertEquals(50, graph.get("limit_edges"));
        assertTrue(graph.containsKey("nodes"));
        assertTrue(graph.containsKey("edges"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) graph.get("nodes");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> edges = (List<Map<String, Object>>) graph.get("edges");
        assertNotNull(nodes);
        assertNotNull(edges);

        if (!edges.isEmpty()) {
            Map<String, Object> firstEdge = edges.getFirst();
            assertNotNull(firstEdge.get("source"));
            assertNotNull(firstEdge.get("target"));
            assertTrue((int) firstEdge.get("weight") >= 1);
            assertTrue(firstEdge.containsKey("channels"));
        }

        // 2. Focused graph with focal contact
        Map<String, Object> focused = service.getCommunicationsGraph("Silva", 1, 20);
        assertNotNull(focused);
        assertEquals("Silva", focused.get("focal_contact"));
        assertTrue(focused.containsKey("nodes"));
        assertTrue(focused.containsKey("edges"));
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void testGetThumbnail() throws Exception {
        Integer imageId = TestItems.imageWithThumbnail();
        assumeTrue(imageId != null, "Caso sem imagens com miniatura");
        IpedCoreService service = IpedCoreService.getInstance();
        Map<String, Object> thumb = service.getThumbnail(imageId, 256);
        assertNotNull(thumb);
        assertEquals(imageId, thumb.get("id"));
        assertEquals("image/jpeg", thumb.get("mime_type"));
        assertTrue((int) thumb.get("width") > 0);
        assertTrue((int) thumb.get("height") > 0);
        assertTrue((int) thumb.get("width") <= 256);
        assertTrue((int) thumb.get("height") <= 256);
        assertTrue((int) thumb.get("size_bytes") > 0);
        String base64 = (String) thumb.get("base64");
        assertNotNull(base64);
        assertFalse(base64.isBlank());
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void testSimilaritySearches() throws Exception {
        Integer imageId = TestItems.imageWithSimilarityFeatures();
        assumeTrue(imageId != null, "Caso sem imagens com vetores de similaridade");
        IpedCoreService service = IpedCoreService.getInstance();
        
        // 1. Similar images
        Map<String, Object> imgSim = service.searchSimilarImages(imageId, 1.0f, 10);
        assertNotNull(imgSim);
        assertEquals(imageId, imgSim.get("reference_id"));
        assertTrue(imgSim.containsKey("total_found"));
        assertTrue(imgSim.containsKey("items"));

        // 2. Similar faces
        Map<String, Object> faceSim = service.searchSimilarFaces(imageId, 50.0f, 10);
        assertNotNull(faceSim);
        assertEquals(imageId, faceSim.get("reference_id"));
        assertTrue(faceSim.containsKey("total_found") || faceSim.containsKey("error"));

        // 3. Similar documents
        Map<String, Object> docSim = service.searchSimilarDocuments(imageId, 50, 10);
        assertNotNull(docSim);
        assertEquals(imageId, docSim.get("reference_id"));
        assertTrue(docSim.containsKey("total_found") || docSim.containsKey("error"));
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void testListFolderContentsNonExistentNumericId() throws Exception {
        IpedCoreService service = IpedCoreService.getInstance();
        Map<String, Object> result = service.listFolderContents("999999999", false, 10);
        assertNotNull(result);
        assertTrue(result.containsKey("error"), "Deveria retornar erro para ID numérico inexistente");
        assertEquals("999999999", result.get("folder_path"));
        assertEquals(0, result.get("total_subdirectories"));
        assertEquals(0, result.get("total_files"));
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void testCommunicationEventCountMatchesIndex() throws Exception {
        IpedCoreService service = IpedCoreService.getInstance();
        IndexSearcher searcher = new IndexSearcher(service.getSource().getReader());
        BooleanQuery query = new BooleanQuery.Builder()
                .add(new WildcardQuery(new Term(CommunicationAnalytics.FROM, "*")), BooleanClause.Occur.SHOULD)
                .add(new WildcardQuery(new Term(CommunicationAnalytics.TO, "*")), BooleanClause.Occur.SHOULD)
                .build();
        int directCount = searcher.count(query);

        Map<String, Object> top = service.getTopContacts(5);
        @SuppressWarnings("unchecked")
        Map<String, Object> diagnostics = (Map<String, Object>) top.get("diagnostics");
        assertEquals((long) directCount, diagnostics.get("events_analyzed"),
                "Cada item com Communication:From ou Communication:To deve gerar exatamente um evento");
        assertEquals(directCount, service.countCommunicationEventItems());
    }
}
