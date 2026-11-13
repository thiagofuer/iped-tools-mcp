package br.com.ipedtools.mcp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.apache.lucene.document.IntPoint;
import org.apache.lucene.search.FieldExistsQuery;
import org.apache.lucene.search.IndexSearcher;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import br.com.ipedtools.mcp.test.TestCaseResolver;

/**
 * Property catalog against the local test case. Fields used (present in the IPED 4.3 test case):
 * nudityScore (Integer, DIE), Communication:From and Communication:Direction (String), Communication:Date (Date),
 * faceAge:labels (String, AgeEstimationTask.py), categories "Instant Messages" and "Other Images".
 */
public class PropertyCatalogServiceTest {

    static boolean isCaseAvailable() {
        return TestCaseResolver.isCaseAvailable();
    }

    private static PropertyCatalogService catalog() {
        return PropertyCatalogService.getInstance();
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

    private static IndexSearcher searcher() {
        return IpedCoreService.getInstance().getSource().getSearcher();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Map<String, Object> m, String key) {
        return (List<Map<String, Object>>) m.get(key);
    }

    // ---- 2.1 -----------------------------------------------------------------------------------------------

    @Test
    @EnabledIf("isCaseAvailable")
    void caseCountsMatchFieldExistsQuery() throws Exception {
        Map<String, PropertyCatalogService.CaseField> fields = catalog().caseFields();
        for (String field : List.of("nudityScore", "Communication:From", "category")) {
            PropertyCatalogService.CaseField f = fields.get(field);
            assertNotNull(f, "campo ausente do catálogo: " + field);
            assertEquals(searcher().count(new FieldExistsQuery(field)), f.itemCount(), "contagem de " + field);
        }
        assertEquals("Integer", fields.get("nudityScore").type());
        assertEquals("Date", fields.get("Communication:Date").type());
        assertFalse(fields.containsKey("content"), "campo interno não deve ser listado");
        assertFalse(fields.containsKey("face_encodings"), "campo interno não deve ser listado");
    }

    // ---- 2.2 -----------------------------------------------------------------------------------------------

    @Test
    @EnabledIf("isCaseAvailable")
    void categoryCountMatchesSearchAndIsExhaustive() throws Exception {
        Map<String, Object> res = catalog().listProperties("Communication", null, "Instant Messages");
        Map<String, Object> from = list(res, "properties").stream()
                .filter(p -> "Communication:From".equals(p.get("name"))).findFirst().orElseThrow();
        long count = (Long) from.get("item_count");
        int expected = (int) IpedCoreService.getInstance()
                .executeSearch("category:\"Instant Messages\" AND Communication\\:From:*", 1).get("total_found");
        assertEquals(expected, count);
        assertTrue(count > 100, "a contagem deve ir além dos 100 primeiros itens da categoria");
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void categoryOmitsAbsentProperties() throws Exception {
        Map<String, Object> res = catalog().listProperties("AI", null, "Instant Messages");
        assertTrue(list(res, "properties").stream().noneMatch(p -> "nudityScore".equals(p.get("name"))),
                "mensagens não têm nudityScore; a propriedade deve ser omitida");
    }

    // ---- 2.3 -----------------------------------------------------------------------------------------------

    @Test
    @EnabledIf("isCaseAvailable")
    void integerPropertyHasMinAndMax() throws Exception {
        Map<String, Object> d = catalog().describeProperty("nudityScore", null, null);
        @SuppressWarnings("unchecked")
        Map<String, Object> stats = (Map<String, Object>) d.get("stats");
        assertNotNull(stats);
        int min = ((Number) stats.get("min")).intValue();
        int max = ((Number) stats.get("max")).intValue();
        assertTrue(min >= 1 && max <= 1000 && min <= max, "nudityScore fora da escala 1-1000: " + stats);
        assertEquals(0, searcher().count(IntPoint.newRangeQuery("nudityScore", max + 1, Integer.MAX_VALUE)));
        assertTrue(searcher().count(IntPoint.newExactQuery("nudityScore", max)) > 0);
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void integerStatsWithinQuery() throws Exception {
        Map<String, Object> d = catalog().describeProperty("nudityScore", "nudityScore:[900 TO *]", null);
        @SuppressWarnings("unchecked")
        Map<String, Object> stats = (Map<String, Object>) d.get("stats");
        assertTrue(((Number) stats.get("min")).intValue() >= 900, "mínimo dentro da consulta: " + stats);
        assertEquals(d.get("query_matched_items"), ((Long) d.get("item_count")).intValue());
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void datePropertyHasIsoMinAndMax() throws Exception {
        Map<String, Object> d = catalog().describeProperty("Communication:Date", null, null);
        @SuppressWarnings("unchecked")
        Map<String, Object> stats = (Map<String, Object>) d.get("stats");
        Instant min = Instant.parse((String) stats.get("min"));
        Instant max = Instant.parse((String) stats.get("max"));
        assertTrue(min.isBefore(max));
        int afterMax = (int) IpedCoreService.getInstance()
                .executeSearch("Communication\\:Date:{" + stats.get("max") + " TO *]", 1).get("total_found");
        assertEquals(0, afterMax, "nenhuma comunicação deve ser posterior ao máximo");
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void textualPropertyHasTopValuesSummingToCount() throws Exception {
        Map<String, Object> d = catalog().describeProperty("Communication:Direction", null, 10);
        List<Map<String, Object>> top = list(d, "top_values");
        assertFalse(top.isEmpty());
        long sum = top.stream().mapToLong(v -> (Long) v.get("count")).sum();
        assertEquals((Long) d.get("item_count"), sum, "valor único por item: soma deve bater com a contagem");
        assertTrue(top.stream().anyMatch(v -> "outgoing".equalsIgnoreCase((String) v.get("value"))));
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void distributionWithinCategoryQuery() throws Exception {
        Map<String, Object> d = catalog().describeProperty("faceAge:labels", "category:\"Other Images\"", 5);
        assertTrue((Integer) d.get("query_matched_items") > 0);
        assertFalse(list(d, "top_values").isEmpty());
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void invalidQueryReturnsStructuredError() throws Exception {
        Map<String, Object> d = catalog().describeProperty("nudityScore", "category:(\"sem fechamento", null);
        assertNotNull(d.get("error"));
    }

    // ---- 2.4 -----------------------------------------------------------------------------------------------

    @Test
    void similarNamesSuggestTheCorrectProperty() {
        List<String> names = List.of("nudityScore", "nudityClass", "ocrCharCount", "face_count", "faceAge:labels");
        assertEquals("nudityScore", PropertyCatalogService.similarNames("nudityScor", names).getFirst());
        assertEquals("face_count", PropertyCatalogService.similarNames("face_cout", names).getFirst());
        assertTrue(PropertyCatalogService.similarNames("hasWeapon", names).isEmpty());
        assertTrue(PropertyCatalogService.similarNames("x", List.of("a", "b", "c", "d", "e", "f", "g")).size() <= 5);
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void absentPropertyIsNotZeroItems() throws Exception {
        Map<String, Object> d = catalog().describeProperty("hasWeapon", null, null);
        assertEquals(false, d.get("present"));
        assertNull(d.get("item_count"), "propriedade ausente não deve informar contagem zero");
        assertNotNull(d.get("similar"));
        assertTrue(((String) d.get("message")).contains("não existe"));
    }

    // ---- 2.5 -----------------------------------------------------------------------------------------------

    @Test
    @EnabledIf("isCaseAvailable")
    void curatedAndUncuratedPropertiesAreListed() throws Exception {
        Map<String, Object> res = catalog().listProperties(null, "nudityScore", null);
        Map<String, Object> curated = list(res, "properties").getFirst();
        assertEquals("nudityScore", curated.get("name"));
        assertEquals("AI", curated.get("group"));
        assertEquals(true, curated.get("curated"));
        assertNotNull(curated.get("description"));
        assertEquals("nudityScore:[650 TO *]", curated.get("query_example"));

        Map<String, Object> res2 = catalog().listProperties(null, "machine:endian", null);
        Map<String, Object> plain = list(res2, "properties").getFirst();
        assertEquals(false, plain.get("curated"));
        assertNull(plain.get("description"));
        assertEquals("machine\\:endian:*", plain.get("query_example"));

        Map<String, Object> d = catalog().describeProperty("nudityScore", null, null);
        assertEquals(true, d.get("curated"));
        assertNotNull(d.get("semantics"));
        assertTrue(((List<?>) d.get("query_examples")).contains("nudityScore:[650 TO *]"));
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void groupOverviewHasAiGroup() throws Exception {
        Map<String, Object> res = catalog().listProperties(null, null, null);
        List<Map<String, Object>> groups = list(res, "groups");
        assertTrue(groups.stream().anyMatch(g -> "AI".equals(g.get("name")) && (Integer) g.get("property_count") > 0));
        assertNull(res.get("properties"), "a visão geral não lista propriedades individuais");
    }
}
