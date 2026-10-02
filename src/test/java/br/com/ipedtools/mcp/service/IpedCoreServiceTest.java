package br.com.ipedtools.mcp.service;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.apache.lucene.document.Document;
import iped.engine.search.IPEDSearcher;
import iped.search.SearchResult;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import br.com.ipedtools.mcp.test.TestCaseResolver;

import static org.junit.jupiter.api.Assertions.*;

public class IpedCoreServiceTest {

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
    void testSetItemChecked() throws Exception {
        IpedCoreService service = IpedCoreService.getInstance();
        boolean initial = service.getSource().getBookmarks().isChecked(25830);
        
        // 1. Mark as checked
        Map<String, Object> resCheck = service.setItemChecked(25830, true);
        assertNotNull(resCheck);
        assertTrue((boolean) resCheck.get("success"));
        assertTrue((boolean) resCheck.get("checked"));
        assertEquals(initial, resCheck.get("previous_state"));
        assertTrue(service.getSource().getBookmarks().isChecked(25830));
        
        // Check document metadata reflects selection
        Map<String, Object> meta = service.getDocumentMetadata(25830);
        assertTrue((boolean) meta.get("selected"));
        
        // 2. Unmark
        Map<String, Object> resUncheck = service.setItemChecked(25830, false);
        assertNotNull(resUncheck);
        assertTrue((boolean) resUncheck.get("success"));
        assertFalse((boolean) resUncheck.get("checked"));
        assertFalse(service.getSource().getBookmarks().isChecked(25830));
        
        // Restore initial if needed
        if (initial) {
            service.setItemChecked(25830, true);
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
        assertTrue(props.containsKey("basic") || props.containsKey("forensic"), "Deveria conter bloco semântico");
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
        // In raw mode, properties map is flat (not partitioned into basic/forensic/etc.)
        assertFalse(rawProps.containsKey("basic"), "Modo raw não deve conter blocos semânticos arbitrários");
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
        assertEquals(Boolean.TRUE, batch.get(0).get("raw"));
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
    void testForensicDomainsCryptoAndAi() {
        IpedCoreService service = IpedCoreService.getInstance();

        // Crypto domain
        Map<String, Object> cryptoDict = service.getPropertyDictionary("crypto");
        assertNotNull(cryptoDict);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cryptoProps = (List<Map<String, Object>>) cryptoDict.get("properties");
        assertNotNull(cryptoProps);
        boolean hasHwFound = cryptoProps.stream().anyMatch(p -> "Hardware-Wallet-Found".equals(p.get("field")));
        assertTrue(hasHwFound, "Deveria conter Hardware-Wallet-Found no domínio crypto");

        // AI domain
        Map<String, Object> aiDict = service.getPropertyDictionary("ai");
        assertNotNull(aiDict);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> aiProps = (List<Map<String, Object>>) aiDict.get("properties");
        assertNotNull(aiProps);
        boolean hasCsam = aiProps.stream().anyMatch(p -> "ai:csamDetector:csam".equals(p.get("field")));
        boolean hasAge = aiProps.stream().anyMatch(p -> "faceAge:count:Child".equals(p.get("field")));
        boolean hasNsfw = aiProps.stream().anyMatch(p -> "nsfw_nudity_score".equals(p.get("field")));
        assertTrue(hasCsam, "Deveria conter ai:csamDetector:csam");
        assertTrue(hasAge, "Deveria conter faceAge:count:Child");
        assertTrue(hasNsfw, "Deveria conter nsfw_nudity_score");
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void testAiFiltersExpanded() {
        IpedCoreService service = IpedCoreService.getInstance();
        Map<String, Object> filters = service.listAiFilters();
        assertNotNull(filters);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = (List<Map<String, Object>>) filters.get("filters");
        assertNotNull(list);

        boolean hasCrypto = list.stream().anyMatch(f -> "crypto_wallets".equals(f.get("filter_type")));
        boolean hasAge = list.stream().anyMatch(f -> "age_estimation".equals(f.get("filter_type")));
        boolean hasNsfw = list.stream().anyMatch(f -> "nsfw".equals(f.get("filter_type")));
        boolean hasCsam = list.stream().anyMatch(f -> "csam".equals(f.get("filter_type")));

        assertTrue(hasCrypto, "Deveria conter filtro crypto_wallets");
        assertTrue(hasAge, "Deveria conter filtro age_estimation");
        assertTrue(hasNsfw, "Deveria conter filtro nsfw");
        assertTrue(hasCsam, "Deveria conter filtro csam");
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

        // 2. Subitem with parent (id=27328, parentId=20422)
        Map<String, Object> childRel = service.getItemRelations(27328);
        assertNotNull(childRel);
        assertNotNull(childRel.get("parent"), "Subitem deve possuir parent identificado");
        @SuppressWarnings("unchecked")
        Map<String, Object> parentMap = (Map<String, Object>) childRel.get("parent");
        assertEquals(20422, parentMap.get("id"));

        // 3. Parent container (id=20422) should have children
        Map<String, Object> parentRel = service.getItemRelations(20422);
        assertNotNull(parentRel);
        int childrenCount = (int) parentRel.get("children_count");
        assertTrue(childrenCount > 0, "Container 20422 deve conter children");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> children = (List<Map<String, Object>>) parentRel.get("children");
        assertFalse(children.isEmpty());
        boolean foundChild = children.stream().anyMatch(c -> (int) c.get("id") == 27328);
        assertTrue(foundChild, "Item 27328 deve constar na lista de filhos");
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
    void testGetPropertyDictionary() {
        IpedCoreService service = IpedCoreService.getInstance();
        Map<String, Object> all = service.getPropertyDictionary("all");
        assertNotNull(all);
        assertTrue(all.containsKey("domains"));
        assertTrue(all.containsKey("escaping_note"));

        Map<String, Object> chats = service.getPropertyDictionary("chats");
        assertNotNull(chats);
        assertEquals("chats", chats.get("domain"));
        assertTrue(chats.containsKey("properties"));
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void testListAvailableProperties() throws Exception {
        IpedCoreService service = IpedCoreService.getInstance();
        Map<String, Object> props = service.listAvailableProperties("chat messages");
        assertNotNull(props);
        assertTrue(props.containsKey("properties"));
        assertTrue(props.containsKey("total_matching_items"));
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
    void testDeviceAndOwnerInfo() {
        IpedCoreService service = IpedCoreService.getInstance();
        Map<String, Object> ownerInfo = service.getDeviceAndOwnerInfo();
        assertNotNull(ownerInfo);
        assertFalse(ownerInfo.containsKey("error"), "Não deveria retornar erro");
        assertTrue(ownerInfo.containsKey("device_properties"));
        assertTrue(ownerInfo.containsKey("likely_owner_names"));
        assertTrue(ownerInfo.containsKey("evidence_type"), "Deveria conter evidence_type");
        assertTrue(ownerInfo.containsKey("system_info"), "Deveria conter system_info");
        assertTrue(ownerInfo.containsKey("user_profile_dirs"), "Deveria conter user_profile_dirs");
        assertTrue(ownerInfo.containsKey("user_accounts"), "Deveria conter user_accounts");
        assertTrue(ownerInfo.containsKey("evidences"), "Deveria conter array de evidences");
        assertTrue(ownerInfo.containsKey("total_evidences"), "Deveria conter contagem total_evidences");
        assertNotNull(ownerInfo.get("evidence_type"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> evidences = (List<Map<String, Object>>) ownerInfo.get("evidences");
        assertNotNull(evidences);
        assertFalse(evidences.isEmpty(), "Deveria retornar ao menos 1 container de evidência");
        for (Map<String, Object> ev : evidences) {
            assertTrue(ev.containsKey("name"), "Evidência deve ter name");
            assertTrue(ev.containsKey("type"), "Evidência deve ter type");
            assertTrue(ev.containsKey("likely_owners"), "Evidência deve ter likely_owners");
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
            Map<String, Object> first = contacts.get(0);
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
            Map<String, Object> firstEdge = edges.get(0);
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
        IpedCoreService service = IpedCoreService.getInstance();
        Map<String, Object> thumb = service.getThumbnail(25830, 256);
        assertNotNull(thumb);
        assertEquals(25830, thumb.get("id"));
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
        IpedCoreService service = IpedCoreService.getInstance();
        
        // 1. Similar images
        Map<String, Object> imgSim = service.searchSimilarImages(25830, 1.0f, 10);
        assertNotNull(imgSim);
        assertEquals(25830, imgSim.get("reference_id"));
        assertTrue(imgSim.containsKey("total_found"));
        assertTrue(imgSim.containsKey("items"));

        // 2. Similar faces
        Map<String, Object> faceSim = service.searchSimilarFaces(25830, 50.0f, 10);
        assertNotNull(faceSim);
        assertEquals(25830, faceSim.get("reference_id"));
        assertTrue(faceSim.containsKey("total_found") || faceSim.containsKey("error"));

        // 3. Similar documents
        Map<String, Object> docSim = service.searchSimilarDocuments(25830, 50, 10);
        assertNotNull(docSim);
        assertEquals(25830, docSim.get("reference_id"));
        assertTrue(docSim.containsKey("total_found") || docSim.containsKey("error"));
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void testAiDetections() throws Exception {
        IpedCoreService service = IpedCoreService.getInstance();

        // 1. List AI filters
        Map<String, Object> filtersRes = service.listAiFilters();
        assertNotNull(filtersRes);
        assertTrue((int) filtersRes.get("total_filters") >= 7);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> filters = (List<Map<String, Object>>) filtersRes.get("filters");
        assertNotNull(filters);
        assertFalse(filters.isEmpty());

        boolean hasWeapons = false;
        boolean hasNudity = false;
        for (Map<String, Object> f : filters) {
            assertNotNull(f.get("filter_type"));
            assertNotNull(f.get("name"));
            assertNotNull(f.get("lucene_query"));
            assertTrue((int) f.get("count") >= 0);
            if ("weapons".equals(f.get("filter_type"))) hasWeapons = true;
            if ("nudity".equals(f.get("filter_type"))) hasNudity = true;
        }
        assertTrue(hasWeapons);
        assertTrue(hasNudity);

        // 2. Query AI detections for 'faces'
        Map<String, Object> queryRes = service.queryAiDetections("faces", null, 10, 0);
        assertNotNull(queryRes);
        assertEquals("faces", queryRes.get("filter_type"));
        assertTrue(queryRes.containsKey("total_found"));
        assertTrue(queryRes.containsKey("items"));
    }
}

