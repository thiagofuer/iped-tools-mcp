package br.com.ipedtools.mcp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.awt.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import br.com.ipedtools.mcp.test.TestCaseResolver;
import br.com.ipedtools.mcp.test.TestItems;

/**
 * Bookmark reading and writing. The case tests create their own bookmarks with the {@link #PREFIX} prefix and
 * delete them afterwards, leaving the test case as it was.
 */
class BookmarkServiceTest {

    private static final String PREFIX = "zz-teste-marcador-";
    private static final BookmarkService SERVICE = BookmarkService.getInstance();

    static boolean isCaseAvailable() {
        return TestCaseResolver.isCaseAvailable();
    }

    @BeforeAll
    static void setUp() {
        if (isCaseAvailable()) {
            IpedCoreService.getInstance().openCase(TestCaseResolver.getPrimaryCaseDir());
        }
    }

    @AfterEach
    void cleanUp() {
        if (isCaseAvailable() && IpedCoreService.getInstance().isCaseOpen()) {
            for (String name : IpedCoreService.getInstance().listBookmarkNames()) {
                if (name.toLowerCase().startsWith(PREFIX)) {
                    SERVICE.deleteBookmark(name);
                }
            }
        }
    }

    @AfterAll
    static void tearDown() {
        if (isCaseAvailable()) {
            IpedCoreService.getInstance().closeCase();
        }
    }

    // ---------------------------------------------------------------- colors

    @Test
    void testColorRoundTrip() {
        assertEquals(new Color(207, 0, 15), BookmarkService.parseColor("#CF000F"));
        assertEquals(new Color(207, 0, 15), BookmarkService.parseColor("#cf000f"));
        assertEquals("#CF000F", BookmarkService.toHex(BookmarkService.parseColor(" #cf000f ")));
        assertNull(BookmarkService.toHex(null));
        for (Color c : BookmarkService.STANDARD_COLORS) {
            assertEquals(c, BookmarkService.parseColor(BookmarkService.toHex(c)));
        }
    }

    @Test
    void testInvalidColorsRejected() {
        for (String bad : new String[] { "red", "#FFF", "#GGGGGG", "", "CF000F", "#CF000F0", null }) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> BookmarkService.parseColor(bad), "deveria recusar " + bad);
            assertTrue(e.getMessage().contains("#RRGGBB"));
        }
    }

    @Test
    void testUnusedStandardColors() {
        assertEquals(36, BookmarkService.STANDARD_COLORS.size());
        assertEquals(36, BookmarkService.unusedStandardColors(Set.of()).size());
        List<String> unused = BookmarkService.unusedStandardColors(Set.of(new Color(102, 0, 6), Color.PINK));
        assertEquals(35, unused.size());
        assertFalse(unused.contains("#660006"));
        assertEquals("#7C3713", unused.get(0), "a ordem da paleta deve ser mantida");
    }

    // ------------------------------------------------------------ case tests

    @Test
    @EnabledIf("isCaseAvailable")
    void testCreateWithColorAndCommentAndList() {
        List<Integer> ids = sampleIds(3);
        String name = PREFIX + "Criação";
        Map<String, Object> res = SERVICE.addToBookmark(name, ids, "#cf000f", "Comentário do perito");
        assertEquals(true, res.get("success"), String.valueOf(res));
        assertEquals(true, res.get("created"));
        assertEquals(ids.size(), res.get("added"));

        Map<String, Object> entry = findListed(name);
        assertNotNull(entry, "marcador deve aparecer em list_bookmarks");
        assertEquals(ids.size(), entry.get("item_count"));
        assertEquals("#CF000F", entry.get("color"));
        assertEquals("Comentário do perito", entry.get("comment"));
        @SuppressWarnings("unchecked")
        List<String> unused = (List<String>) SERVICE.listBookmarks().get("unused_standard_colors");
        assertFalse(unused.contains("#CF000F"), "cor usada não deve aparecer como livre");
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void testCreateWithoutColorKeepsNoColor() {
        String name = PREFIX + "Sem cor";
        assertEquals(true, SERVICE.addToBookmark(name, sampleIds(1), null, null).get("success"));
        Map<String, Object> entry = findListed(name);
        assertNull(entry.get("color"), "o servidor não atribui cor");
        assertNull(entry.get("comment"));
    }

    @Test
    @EnabledIf("isCaseAvailable")
    @SuppressWarnings("unchecked")
    void testAddToExistingIgnoresColorAndComment() {
        List<Integer> ids = sampleIds(2);
        String name = PREFIX + "Existente";
        SERVICE.addToBookmark(name, ids.subList(0, 1), "#26A65B", "original");
        Map<String, Object> res = SERVICE.addToBookmark(name, ids, "#CF000F", "novo");
        assertEquals(true, res.get("success"));
        assertEquals(false, res.get("created"));
        assertEquals(1, res.get("added"));
        assertEquals(1, res.get("already_in_bookmark"));
        assertEquals(List.of("color", "comment"), res.get("ignored_fields"));
        assertTrue(((String) res.get("message")).contains("update_bookmark"));
        Map<String, Object> bookmark = (Map<String, Object>) res.get("bookmark");
        assertEquals("#26A65B", bookmark.get("color"));
        assertEquals("original", bookmark.get("comment"));
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void testCaseConflictAndNotFoundIds() {
        String name = PREFIX + "Contratos";
        SERVICE.addToBookmark(name, sampleIds(1), null, null);
        Map<String, Object> conflict = SERVICE.addToBookmark(name.toUpperCase(), sampleIds(1), null, null);
        assertEquals(false, conflict.get("success"));
        assertEquals(name, conflict.get("existing_bookmark"));
        assertFalse(IpedCoreService.getInstance().listBookmarkNames().contains(name.toUpperCase()));

        Map<String, Object> partial = SERVICE.addToBookmark(name, List.of(sampleIds(1).get(0), -5, Integer.MAX_VALUE),
                null, null);
        assertEquals(true, partial.get("success"));
        assertEquals(List.of(-5, Integer.MAX_VALUE), partial.get("not_found_ids"));

        Map<String, Object> invalidColor = SERVICE.addToBookmark(PREFIX + "Cor ruim", sampleIds(1), "vermelho", null);
        assertEquals(false, invalidColor.get("success"));
        assertTrue(((String) invalidColor.get("error")).contains("#RRGGBB"));
        assertNull(findListed(PREFIX + "Cor ruim"), "cor inválida não pode criar o marcador");
    }

    @Test
    @EnabledIf("isCaseAvailable")
    @SuppressWarnings("unchecked")
    void testListItemsPaginates() {
        List<Integer> ids = sampleIds(5);
        String name = PREFIX + "Itens";
        SERVICE.addToBookmark(name, ids, null, null);
        List<Integer> sorted = new ArrayList<>(ids);
        sorted.sort(null);

        List<Object> collected = new ArrayList<>();
        Integer offset = 0;
        int pages = 0;
        while (offset != null) {
            Map<String, Object> page = SERVICE.listBookmarkItems(name, offset, 2);
            assertEquals(ids.size(), page.get("total_found"));
            ((List<Map<String, Object>>) page.get("items")).forEach(i -> {
                collected.add(i.get("id"));
                assertTrue(i.containsKey("name") && i.containsKey("path") && i.containsKey("category"));
            });
            offset = (Integer) page.get("next_offset");
            assertEquals(offset != null, page.get("has_more"));
            pages++;
        }
        assertEquals(3, pages);
        assertEquals(sorted, collected, "itens em ordem de ID");

        Map<String, Object> missing = SERVICE.listBookmarkItems(PREFIX + "inexistente", 0, 10);
        assertEquals(false, missing.get("success"));
        assertTrue(((String) missing.get("error")).contains("list_bookmarks"));
    }

    @Test
    @EnabledIf("isCaseAvailable")
    @SuppressWarnings("unchecked")
    void testRemoveItemsKeepsBookmarkAndCheckedState() throws Exception {
        List<Integer> ids = sampleIds(2);
        String name = PREFIX + "Remoção";
        IpedCoreService core = IpedCoreService.getInstance();
        boolean checkedBefore = core.getSource().getBookmarks().isChecked(ids.get(0));
        SERVICE.addToBookmark(name, ids, null, null);

        Map<String, Object> res = SERVICE.removeFromBookmark(name, List.of(ids.get(0), ids.get(0) + 1_000_000));
        assertEquals(true, res.get("success"));
        assertEquals(1, res.get("removed"));
        assertEquals(List.of(ids.get(0) + 1_000_000), res.get("not_in_bookmark_ids"));
        assertEquals(checkedBefore, core.getSource().getBookmarks().isChecked(ids.get(0)));

        SERVICE.removeFromBookmark(name, List.of(ids.get(1)));
        Map<String, Object> entry = findListed(name);
        assertNotNull(entry, "o marcador vazio continua existindo");
        assertEquals(0, entry.get("item_count"));
        assertEquals(false, SERVICE.removeFromBookmark(name, List.of(ids.get(1))).get("success"));
        assertEquals(false, SERVICE.removeFromBookmark(PREFIX + "inexistente", ids).get("success"));
    }

    @Test
    @EnabledIf("isCaseAvailable")
    @SuppressWarnings("unchecked")
    void testUpdateBookmark() {
        String name = PREFIX + "Edição";
        String other = PREFIX + "Outro";
        List<Integer> ids = sampleIds(2);
        SERVICE.addToBookmark(name, ids, "#26A65B", "comentário");
        SERVICE.addToBookmark(other, sampleIds(1), null, null);

        assertEquals(false, SERVICE.updateBookmark(name, null, null, null).get("success"), "sem alteração é erro");
        Map<String, Object> conflict = SERVICE.updateBookmark(name, other.toUpperCase(), null, null);
        assertEquals(false, conflict.get("success"));
        assertEquals(other, conflict.get("existing_bookmark"));
        assertEquals(false, SERVICE.updateBookmark(name, null, "#12", null).get("success"));

        String renamed = PREFIX + "Renomeado";
        Map<String, Object> res = SERVICE.updateBookmark(name, renamed, "#3498DB", "");
        assertEquals(true, res.get("success"), String.valueOf(res));
        Map<String, Object> bookmark = (Map<String, Object>) res.get("bookmark");
        assertEquals(renamed, bookmark.get("name"));
        assertEquals(ids.size(), bookmark.get("item_count"));
        assertEquals("#3498DB", bookmark.get("color"));
        assertNull(bookmark.get("comment"), "comentário vazio limpa o comentário");
        assertNull(findListed(name));

        Map<String, Object> commentOnly = SERVICE.updateBookmark(renamed, null, null, "novo comentário");
        assertEquals("novo comentário", ((Map<String, Object>) commentOnly.get("bookmark")).get("comment"));
        assertEquals("#3498DB", ((Map<String, Object>) commentOnly.get("bookmark")).get("color"));
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void testDeleteKeepsItemsOtherBookmarksAndCheckedState() {
        List<Integer> ids = sampleIds(2);
        String keep = PREFIX + "Mantido";
        String gone = PREFIX + "Excluído";
        IpedCoreService core = IpedCoreService.getInstance();
        boolean checkedBefore = core.getSource().getBookmarks().isChecked(ids.get(0));
        SERVICE.addToBookmark(keep, ids.subList(0, 1), null, null);
        SERVICE.addToBookmark(gone, ids, "#CF000F", "x");

        Map<String, Object> res = SERVICE.deleteBookmark(gone);
        assertEquals(true, res.get("success"));
        assertEquals(2, res.get("item_count"));
        assertNull(findListed(gone));
        assertTrue(core.getSource().getBookmarks().getBookmarkList(ids.get(0)).contains(keep));
        assertEquals(checkedBefore, core.getSource().getBookmarks().isChecked(ids.get(0)));
        assertEquals(false, SERVICE.deleteBookmark(gone).get("success"));
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void testWritesReachStateFileAndSurviveReload() throws Exception {
        IpedCoreService core = IpedCoreService.getInstance();
        String name = PREFIX + "Persistência";
        SERVICE.addToBookmark(name, sampleIds(1), "#E6C828", "persistido");
        File stateFile = core.getBookmarksStateFile();
        assertTrue(stateFile.isFile());

        // Discard the in-memory change and read the file again: the bookmark must come from disk.
        core.getSource().getBookmarks().delBookmark(core.getSource().getBookmarks().getBookmarkId(name));
        Map<String, Object> entry = findListed(name);
        assertNotNull(entry, "list_bookmarks deve reler o estado gravado");
        assertEquals("#E6C828", entry.get("color"));
        assertEquals("persistido", entry.get("comment"));
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void testReadOnlyStateFileReportsError() throws Exception {
        IpedCoreService core = IpedCoreService.getInstance();
        SERVICE.addToBookmark(PREFIX + "Base", sampleIds(1), null, null);
        File stateFile = core.getBookmarksStateFile();
        long modified = stateFile.lastModified();
        assumeTrue(stateFile.setReadOnly(), "não foi possível tornar o arquivo somente leitura");
        try {
            Map<String, Object> res = SERVICE.addToBookmark(PREFIX + "Bloqueado", sampleIds(1), null, null);
            assertEquals(false, res.get("success"), String.valueOf(res));
            assertTrue(((String) res.get("error")).contains("bookmarks"));
            assertEquals(modified, stateFile.lastModified(), "o arquivo não pode mudar");
            assertFalse(core.listBookmarkNames().contains(PREFIX + "Bloqueado"),
                    "o estado em memória volta ao do arquivo");

            int itemId = sampleIds(1).get(0);
            assertThrows(java.io.IOException.class,
                    () -> core.setItemChecked(itemId, !core.getSource().getBookmarks().isChecked(itemId)));
        } finally {
            assertTrue(stateFile.setWritable(true));
        }
    }

    // --------------------------------------------------------------- helpers

    private static List<Integer> sampleIds(int n) {
        List<Integer> ids = TestItems.anyIds(n);
        assumeTrue(ids.size() == n, "caso de teste sem itens suficientes");
        return ids;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> findListed(String name) {
        List<Map<String, Object>> list = (List<Map<String, Object>>) SERVICE.listBookmarks().get("bookmarks");
        return list.stream().filter(b -> name.equals(b.get("name"))).findFirst().orElse(null);
    }
}
