package br.com.ipedtools.mcp.service;

import java.util.List;
import java.util.Map;

import org.apache.lucene.document.DoublePoint;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PointRangeQuery;
import org.apache.lucene.search.Query;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import br.com.ipedtools.mcp.test.TestCaseResolver;
import iped.engine.search.IPEDSearcher;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Range queries on numeric extra properties (issue #7), checked against direct Lucene point queries.
 *
 * <p>Fields used from the local test case (typed in iped/conf/metadataTypes.txt):
 * - nudityScore (java.lang.Integer, DIETask, scale 0-1000)
 * - ocrCharCount (java.lang.Integer, OCRParser)
 * - audio:transcriptConfidence (java.lang.Double, transcription task, 0-1)
 *
 * <p>Root cause found: QueryBuilder caches the numeric PointsConfig per case the first time a query is parsed,
 * which happened inside the IPEDSource constructor before metadataTypes.txt was loaded, so the cache had no
 * extra numeric fields and ranges became textual. In addition, the pt-BR number format read "0.5" as 5.
 */
public class NumericRangeQueryTest {

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

    private static int directCount(Query q) throws Exception {
        IndexSearcher searcher = IpedCoreService.getInstance().getSource().getSearcher();
        return searcher.count(q);
    }

    private static Map<String, Object> search(String query) throws Exception {
        return IpedCoreService.getInstance().executeSearch(query, 1);
    }

    private static int mcpCount(String query) throws Exception {
        return (int) search(query).get("total_found");
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void integerRangeIsParsedAsPointRange() {
        Query q = new IPEDSearcher(IpedCoreService.getInstance().getSource(), "nudityScore:[500 TO 1000]").getQuery();
        assertInstanceOf(PointRangeQuery.class, q);
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void integerRangeMatchesDirectPointQuery() throws Exception {
        int expected = directCount(IntPoint.newRangeQuery("nudityScore", 500, 1000));
        assertTrue(expected > 0, "caso de teste deveria ter itens com nudityScore entre 500 e 1000");
        assertEquals(expected, mcpCount("nudityScore:[500 TO 1000]"));
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void openIntegerRangeMatchesDirectPointQuery() throws Exception {
        int expected = directCount(IntPoint.newRangeQuery("ocrCharCount", 1, Integer.MAX_VALUE));
        assertTrue(expected > 0, "caso de teste deveria ter itens com ocrCharCount >= 1");
        assertEquals(expected, mcpCount("ocrCharCount:[1 TO *]"));
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void doubleRangeWithDotDecimalMatchesDirectPointQuery() throws Exception {
        int expected = directCount(DoublePoint.newRangeQuery("audio:transcriptConfidence", 0.5, Double.POSITIVE_INFINITY));
        assertTrue(expected > 0, "caso de teste deveria ter transcrições com confiança >= 0.5");
        assertEquals(expected, mcpCount("audio\\:transcriptConfidence:[0.5 TO *]"));
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void rangeSurvivesCaseSwitch() throws Exception {
        IpedCoreService service = IpedCoreService.getInstance();
        int before = mcpCount("nudityScore:[500 TO 1000]");
        // Switch to the secondary case when configured, then back to the primary one; otherwise reopen the primary.
        if (TestCaseResolver.isSecondaryCaseAvailable()) {
            service.openCase(TestCaseResolver.getSecondaryCaseDir());
        }
        service.openCase(TestCaseResolver.getPrimaryCaseDir());
        assertTrue(before > 0);
        assertEquals(before, mcpCount("nudityScore:[500 TO 1000]"));
        assertEquals(directCount(DoublePoint.newRangeQuery("audio:transcriptConfidence", 0.5, Double.POSITIVE_INFINITY)),
                mcpCount("audio\\:transcriptConfidence:[0.5 TO *]"));
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void emptyNumericRangeReturnsWarningWithMinAndMax() throws Exception {
        Map<String, Object> res = search("nudityScore:[5000 TO 6000]");
        assertEquals(0, res.get("total_found"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> warnings = (List<Map<String, Object>>) res.get("warnings");
        assertNotNull(warnings, "esperava aviso de faixa vazia");
        Map<String, Object> w = warnings.getFirst();
        assertEquals("nudityScore", w.get("property"));
        assertEquals("Integer", w.get("property_type"));
        assertNotNull(w.get("min_value"));
        assertNotNull(w.get("max_value"));
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void rangeOverAbsentPropertyReturnsNotInCaseWarning() throws Exception {
        Map<String, Object> res = search("hasWeaponScore:[1 TO *]");
        assertEquals(0, res.get("total_found"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> warnings = (List<Map<String, Object>>) res.get("warnings");
        assertNotNull(warnings);
        assertEquals("property_not_in_case", warnings.getFirst().get("type"));
    }

    /** Examples documented in docs/wiki/Consultas-Lucene-e-Propriedades.md and in the search_documents description. */
    @Test
    @EnabledIf("isCaseAvailable")
    void documentedExamplesReturnResults() throws Exception {
        for (String q : List.of("nudityScore:[650 TO *]", "ocrCharCount:[1 TO *]", "audio\\:transcriptConfidence:[0.5 TO *]")) {
            assertTrue(mcpCount(q) > 0, "exemplo documentado sem resultado: " + q);
        }
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void queryWithResultsHasNoWarnings() throws Exception {
        Map<String, Object> res = search("nudityScore:[500 TO 1000]");
        assertTrue((int) res.get("total_found") > 0);
        assertFalse(res.containsKey("warnings"));
    }
}
