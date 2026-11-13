package br.com.ipedtools.mcp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import br.com.ipedtools.mcp.test.TestCaseResolver;
import iped.engine.search.IPEDSearcher;

/**
 * Pagination of search_documents. Queries and categories are chosen from the open case at run time.
 */
public class SearchPaginationTest {

    static boolean isCaseAvailable() {
        return TestCaseResolver.isCaseAvailable();
    }

    @BeforeAll
    static void setUp() {
        if (isCaseAvailable()) {
            IpedCoreService.getInstance().openCase(TestCaseResolver.getPrimaryCaseDir());
        }
    }

    @AfterAll
    static void tearDown() {
        if (isCaseAvailable()) {
            IpedCoreService.getInstance().closeCase();
        }
    }

    private static IpedCoreService service() {
        return IpedCoreService.getInstance();
    }

    @SuppressWarnings("unchecked")
    private static List<Integer> ids(Map<String, Object> res) {
        return ((List<Map<String, Object>>) res.get("items")).stream().map(i -> (Integer) i.get("id")).toList();
    }

    // ---- 1.1 ---------------------------------------------------------------------------------------------------

    @Test
    @EnabledIf("isCaseAvailable")
    void consecutivePagesDoNotOverlap() throws Exception {
        Map<String, Object> p1 = service().executeSearch("*:*", 100, 0);
        assertEquals(0, p1.get("offset"));
        assertEquals(true, p1.get("has_more"));
        assertEquals(100, p1.get("next_offset"));

        Map<String, Object> p2 = service().executeSearch("*:*", 100, (Integer) p1.get("next_offset"));
        assertEquals(p1.get("total_found"), p2.get("total_found"));
        assertEquals(100, p2.get("offset"));
        Set<Integer> first = new HashSet<>(ids(p1));
        assertTrue(ids(p2).stream().noneMatch(first::contains), "páginas consecutivas não podem repetir itens");
        assertTrue(((String) p2.get("note")).contains("offset=200"), "a nota deve indicar o próximo offset");
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void callWithoutOffsetKeepsFirstPage() throws Exception {
        Map<String, Object> legacy = service().executeSearch("*:*", 10);
        Map<String, Object> explicit = service().executeSearch("*:*", 10, 0);
        assertEquals(ids(explicit), ids(legacy));
        assertEquals(0, legacy.get("offset"));
        assertNotNull(legacy.get("next_offset"));
    }

    // ---- 1.2 ---------------------------------------------------------------------------------------------------

    @Test
    @EnabledIf("isCaseAvailable")
    void negativeOffsetBecomesZero() throws Exception {
        Map<String, Object> res = service().executeSearch("*:*", 5, -10);
        assertEquals(0, res.get("offset"));
        assertEquals(ids(service().executeSearch("*:*", 5, 0)), ids(res));
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void offsetBeyondTotalReturnsEmptyPage() throws Exception {
        int total = (int) service().executeSearch("*:*", 1, 0).get("total_found");
        Map<String, Object> res = service().executeSearch("*:*", 10, total);
        assertTrue(ids(res).isEmpty());
        assertEquals(false, res.get("has_more"));
        assertNull(res.get("next_offset"));
        assertEquals(total, res.get("total_found"));
        assertTrue(((String) res.get("note")).contains(String.valueOf(total)), "a nota deve informar o total");
        assertFalse(res.containsKey("error"));
    }

    // ---- 1.3 ---------------------------------------------------------------------------------------------------

    /** A category of the case with a few hundred items (several pages of 100), chosen at run time. */
    private static String mediumCategory() throws Exception {
        for (String cat : service().listCategories()) {
            int total = (int) service().executeSearch("category:\"" + cat.replace("\"", "\\\"") + "\"", 1, 0).get("total_found");
            if (total >= 250 && total <= 2000) {
                return cat;
            }
        }
        return null;
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void pagingReachesEveryItemExactlyOnce() throws Exception {
        String cat = mediumCategory();
        assertNotNull(cat, "o caso de teste deveria ter uma categoria com algumas centenas de itens");
        String query = "category:\"" + cat.replace("\"", "\\\"") + "\"";

        Set<Integer> seen = new HashSet<>();
        int pages = 0;
        Integer offset = 0;
        int total = -1;
        while (offset != null) {
            Map<String, Object> page = service().executeSearch(query, 100, offset);
            total = (int) page.get("total_found");
            for (int id : ids(page)) {
                assertTrue(seen.add(id), "item repetido entre páginas: " + id);
            }
            pages++;
            offset = (Integer) page.get("next_offset");
            assertEquals(offset != null, page.get("has_more"));
        }
        assertTrue(pages >= 3, "a consulta deveria ocupar várias páginas");
        assertEquals(total, seen.size());

        int[] expected = new IPEDSearcher(service().getSource(), IpedCoreService.prepareQuery(query)).search().getIds();
        Set<Integer> expectedSet = new HashSet<>();
        for (int id : expected) {
            expectedSet.add(id);
        }
        assertEquals(expectedSet, seen, "a união das páginas deve ser igual ao resultado completo do IPEDSearcher");
    }

    @Test
    void pageNoteDescribesPosition() {
        assertEquals("Nenhum item encontrado.", IpedCoreService.pageNote(0, 0, 0, false));
        assertTrue(IpedCoreService.pageNote(250, 100, 100, true).startsWith("Itens 101 a 200 de 250."));
        assertTrue(IpedCoreService.pageNote(250, 100, 100, true).contains("offset=200"));
        assertTrue(IpedCoreService.pageNote(250, 200, 50, false).contains("última página"));
        assertTrue(IpedCoreService.pageNote(30, 0, 30, false).contains("Todos os 30 itens"));
    }
}
