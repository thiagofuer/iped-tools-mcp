package br.com.ipedtools.mcp.service;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.lucene.document.Document;

import iped.data.IBookmarks;
import iped.engine.data.IPEDSource;

/**
 * Reads and writes the bookmarks of the open case (listing, item listing, creation, item removal, editing of name,
 * color and comment, deletion). Every write starts from the state saved in {@code bookmarks.iped} and is saved back
 * to it before the method returns. The server never assigns a bookmark color on its own: the color is the user's
 * choice.
 */
public final class BookmarkService {

    /**
     * Standard bookmark palette of IPED Desktop, copied from {@code iped.app.ui.bookmarks.BookmarkStandardColors}
     * (iped-app, not a dependency of this project): the 36 standard colors, without the trailing grays, in the
     * same order. Used only to suggest unused colors; any {@code #RRGGBB} value is accepted.
     */
    static final List<Color> STANDARD_COLORS = List.of(
            new Color(102, 0, 6), new Color(124, 55, 19), new Color(127, 106, 16), new Color(66, 109, 54),
            new Color(23, 82, 38), new Color(27, 71, 114), new Color(32, 47, 91), new Color(46, 19, 75),
            new Color(78, 17, 65), new Color(207, 0, 15), new Color(242, 121, 53), new Color(230, 200, 40),
            new Color(135, 211, 124), new Color(38, 166, 91), new Color(52, 152, 219), new Color(68, 108, 179),
            new Color(102, 51, 152), new Color(131, 29, 109), new Color(240, 110, 120), new Color(250, 170, 120),
            new Color(250, 230, 150), new Color(181, 235, 170), new Color(120, 200, 150), new Color(140, 190, 240),
            new Color(140, 160, 220), new Color(180, 140, 220), new Color(190, 130, 180), new Color(245, 204, 207),
            new Color(252, 228, 215), new Color(253, 246, 217), new Color(231, 246, 229), new Color(212, 237, 222),
            new Color(214, 234, 248), new Color(218, 226, 240), new Color(243, 239, 248), new Color(230, 210, 226));

    private static final Pattern HEX_COLOR = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    static final String COLOR_FORMAT_ERROR = "Cor inválida: '%s'. Use o formato #RRGGBB (ex.: #CF000F).";

    static final int DEFAULT_ITEMS_LIMIT = 50;
    static final int MAX_ITEMS_LIMIT = 500;

    private static final BookmarkService INSTANCE = new BookmarkService();

    private BookmarkService() {
    }

    public static BookmarkService getInstance() {
        return INSTANCE;
    }

    // =========================================================================
    // COLORS (pure functions, unit tested)
    // =========================================================================

    /** Parses a {@code #RRGGBB} value; throws {@link IllegalArgumentException} with the expected format. */
    static Color parseColor(String value) {
        if (value == null || !HEX_COLOR.matcher(value.trim()).matches()) {
            throw new IllegalArgumentException(String.format(COLOR_FORMAT_ERROR, value));
        }
        return new Color(Integer.parseInt(value.trim().substring(1), 16));
    }

    /** Formats a color as upper-case {@code #RRGGBB}, or null when there is no color. */
    static String toHex(Color color) {
        if (color == null) {
            return null;
        }
        return String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
    }

    /** Standard palette colors not in {@code used}, in palette order, as {@code #RRGGBB}. */
    static List<String> unusedStandardColors(Set<Color> used) {
        List<String> result = new ArrayList<>();
        for (Color c : STANDARD_COLORS) {
            if (used == null || !used.contains(c)) {
                result.add(toHex(c));
            }
        }
        return result;
    }

    // =========================================================================
    // READ
    // =========================================================================

    public Map<String, Object> listBookmarks() {
        IpedCoreService core = IpedCoreService.getInstance();
        synchronized (core) {
            try {
                core.reloadBookmarks();
            } catch (IOException e) {
                return error("Falha ao ler os marcadores do caso: " + e.getMessage());
            }
            IBookmarks bm = core.getSource().getBookmarks();
            List<Map<String, Object>> list = new ArrayList<>();
            Set<Color> used = new java.util.HashSet<>();
            for (Map.Entry<Integer, String> e : sortedBookmarks(bm).entrySet()) {
                list.add(describe(bm, e.getKey()));
                Color c = bm.getBookmarkColor(e.getKey());
                if (c != null) {
                    used.add(c);
                }
            }
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("total", list.size());
            response.put("bookmarks", list);
            response.put("unused_standard_colors", unusedStandardColors(used));
            return response;
        }
    }

    public Map<String, Object> listBookmarkItems(String name, Integer offset, Integer limit) {
        IpedCoreService core = IpedCoreService.getInstance();
        synchronized (core) {
            try {
                core.reloadBookmarks();
            } catch (IOException e) {
                return error("Falha ao ler os marcadores do caso: " + e.getMessage());
            }
            IPEDSource source = core.getSource();
            IBookmarks bm = source.getBookmarks();
            int bmId = findExact(bm, name);
            if (bmId < 0) {
                return notFound(name);
            }
            int start = offset == null ? 0 : Math.max(0, offset);
            int max = limit == null ? DEFAULT_ITEMS_LIMIT : Math.clamp(limit, 1, MAX_ITEMS_LIMIT);
            int total = bm.getBookmarkCount(bmId);

            List<Map<String, Object>> items = new ArrayList<>();
            int seen = 0;
            int lastId = bm.getLastId();
            for (int id = 0; id <= lastId && items.size() < max && seen < total; id++) {
                if (!bm.hasBookmark(id, bmId)) {
                    continue;
                }
                if (seen++ < start) {
                    continue;
                }
                items.add(itemSummary(source, id));
            }
            boolean hasMore = start + items.size() < total;

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("bookmark", bm.getBookmarkName(bmId));
            response.put("total_found", total);
            response.put("offset", start);
            response.put("returned_count", items.size());
            response.put("limit", max);
            response.put("has_more", hasMore);
            if (hasMore) {
                response.put("next_offset", start + items.size());
            }
            response.put("items", items);
            return response;
        }
    }

    // =========================================================================
    // WRITE
    // =========================================================================

    public Map<String, Object> addToBookmark(String name, List<Integer> itemIds, String color, String comment) {
        if (name == null || name.isBlank()) {
            return error("O nome do marcador não pode ser vazio.");
        }
        if (itemIds == null || itemIds.isEmpty()) {
            return error("Nenhum ID de item informado para incluir no marcador.");
        }
        Color parsedColor;
        try {
            parsedColor = color == null || color.isBlank() ? null : parseColor(color);
        } catch (IllegalArgumentException e) {
            return error(e.getMessage());
        }
        String bookmarkName = name.trim();

        IpedCoreService core = IpedCoreService.getInstance();
        synchronized (core) {
            try {
                core.reloadBookmarks();
                IPEDSource source = core.getSource();
                IBookmarks bm = source.getBookmarks();

                int bmId = findExact(bm, bookmarkName);
                String conflict = bmId < 0 ? findIgnoringCase(bm, bookmarkName, -1) : null;
                if (conflict != null) {
                    return error("Já existe o marcador '" + conflict + "', que difere só em maiúsculas/minúsculas. "
                            + "Use esse nome ou escolha outro.", "existing_bookmark", conflict);
                }

                List<Integer> valid = new ArrayList<>();
                List<Integer> notFound = new ArrayList<>();
                for (Integer id : new LinkedHashSet<>(itemIds)) {
                    if (id != null && itemExists(source, id)) {
                        valid.add(id);
                    } else {
                        notFound.add(id);
                    }
                }
                if (valid.isEmpty()) {
                    return error("Nenhum dos IDs informados existe no caso aberto.", "not_found_ids", notFound);
                }

                boolean created = bmId < 0;
                List<String> ignored = new ArrayList<>();
                if (created) {
                    bmId = bm.newBookmark(bookmarkName);
                    if (parsedColor != null) {
                        bm.setBookmarkColor(bmId, parsedColor);
                    }
                    if (comment != null && !comment.isBlank()) {
                        bm.setBookmarkComment(bmId, comment.trim());
                    }
                } else {
                    if (parsedColor != null) {
                        ignored.add("color");
                    }
                    if (comment != null && !comment.isBlank()) {
                        ignored.add("comment");
                    }
                }
                int before = bm.getBookmarkCount(bmId);
                bm.addBookmark(valid, bmId);
                int added = bm.getBookmarkCount(bmId) - before;
                core.saveBookmarks();

                Map<String, Object> response = success(bm, bmId);
                response.put("created", created);
                response.put("added", added);
                response.put("already_in_bookmark", valid.size() - added);
                if (!notFound.isEmpty()) {
                    response.put("not_found_ids", notFound);
                }
                StringBuilder msg = new StringBuilder();
                msg.append(added).append(" item(ns) incluído(s) no marcador '").append(bookmarkName).append("'")
                        .append(created ? " (marcador criado)." : ".");
                if (!ignored.isEmpty()) {
                    response.put("ignored_fields", ignored);
                    msg.append(" O marcador já existia: ").append(String.join(" e ", ignored))
                            .append(" não aplicado(s); use update_bookmark para alterá-los.");
                }
                if (!notFound.isEmpty()) {
                    msg.append(" IDs não encontrados no caso: ").append(notFound).append('.');
                }
                response.put("message", msg.toString());
                return response;
            } catch (IOException e) {
                return error(e.getMessage());
            }
        }
    }

    public Map<String, Object> removeFromBookmark(String name, List<Integer> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return error("Nenhum ID de item informado para remover do marcador.");
        }
        IpedCoreService core = IpedCoreService.getInstance();
        synchronized (core) {
            try {
                core.reloadBookmarks();
                IBookmarks bm = core.getSource().getBookmarks();
                int bmId = findExact(bm, name);
                if (bmId < 0) {
                    return notFound(name);
                }
                List<Integer> present = new ArrayList<>();
                List<Integer> notIn = new ArrayList<>();
                int lastId = bm.getLastId();
                boolean empty = bm.getBookmarkCount(bmId) == 0;
                for (Integer id : new LinkedHashSet<>(itemIds)) {
                    if (!empty && id != null && id >= 0 && id <= lastId && bm.hasBookmark(id, bmId)) {
                        present.add(id);
                    } else {
                        notIn.add(id);
                    }
                }
                if (present.isEmpty()) {
                    return error("Nenhum dos IDs informados está no marcador '" + bm.getBookmarkName(bmId) + "'.",
                            "not_in_bookmark_ids", notIn);
                }
                bm.removeBookmark(present, bmId);
                core.saveBookmarks();

                Map<String, Object> response = success(bm, bmId);
                response.put("removed", present.size());
                if (!notIn.isEmpty()) {
                    response.put("not_in_bookmark_ids", notIn);
                }
                response.put("message", present.size() + " item(ns) removido(s) do marcador '"
                        + bm.getBookmarkName(bmId) + "'." + (notIn.isEmpty() ? "" : " IDs que não estavam no marcador: " + notIn + "."));
                return response;
            } catch (IOException e) {
                return error(e.getMessage());
            }
        }
    }

    public Map<String, Object> updateBookmark(String name, String newName, String color, String comment) {
        boolean renaming = newName != null && !newName.isBlank();
        boolean recoloring = color != null && !color.isBlank();
        boolean commenting = comment != null;
        if (!renaming && !recoloring && !commenting) {
            return error("Nenhuma alteração informada. Campos que podem ser alterados: new_name, color, comment "
                    + "(comment vazio limpa o comentário).");
        }
        Color parsedColor;
        try {
            parsedColor = recoloring ? parseColor(color) : null;
        } catch (IllegalArgumentException e) {
            return error(e.getMessage());
        }

        IpedCoreService core = IpedCoreService.getInstance();
        synchronized (core) {
            try {
                core.reloadBookmarks();
                IBookmarks bm = core.getSource().getBookmarks();
                int bmId = findExact(bm, name);
                if (bmId < 0) {
                    return notFound(name);
                }
                List<String> changed = new ArrayList<>();
                if (renaming) {
                    String target = newName.trim();
                    if (!target.equals(bm.getBookmarkName(bmId))) {
                        String conflict = findIgnoringCase(bm, target, bmId);
                        if (conflict != null) {
                            return error("Já existe o marcador '" + conflict + "'. Escolha outro nome.",
                                    "existing_bookmark", conflict);
                        }
                        bm.renameBookmark(bmId, target);
                        changed.add("name");
                    }
                }
                if (parsedColor != null) {
                    bm.setBookmarkColor(bmId, parsedColor);
                    changed.add("color");
                }
                if (commenting) {
                    bm.setBookmarkComment(bmId, comment.isBlank() ? null : comment.trim());
                    changed.add("comment");
                }
                if (!changed.isEmpty()) {
                    core.saveBookmarks();
                }
                Map<String, Object> response = success(bm, bmId);
                response.put("changed_fields", changed);
                response.put("message", changed.isEmpty() ? "Nada a alterar: o marcador já tem esse nome."
                        : "Marcador '" + bm.getBookmarkName(bmId) + "' atualizado: " + String.join(", ", changed) + ".");
                return response;
            } catch (IOException e) {
                return error(e.getMessage());
            }
        }
    }

    public Map<String, Object> deleteBookmark(String name) {
        IpedCoreService core = IpedCoreService.getInstance();
        synchronized (core) {
            try {
                core.reloadBookmarks();
                IBookmarks bm = core.getSource().getBookmarks();
                int bmId = findExact(bm, name);
                if (bmId < 0) {
                    return notFound(name);
                }
                String deletedName = bm.getBookmarkName(bmId);
                int count = bm.getBookmarkCount(bmId);
                bm.delBookmark(bmId);
                core.saveBookmarks();

                Map<String, Object> response = new LinkedHashMap<>();
                response.put("success", true);
                response.put("deleted", deletedName);
                response.put("item_count", count);
                response.put("message", "Marcador '" + deletedName + "' excluído. Os " + count
                        + " item(ns) que ele tinha continuam no caso, com os demais marcadores e a seleção inalterados.");
                return response;
            } catch (IOException e) {
                return error(e.getMessage());
            }
        }
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private static Map<Integer, String> sortedBookmarks(IBookmarks bm) {
        Map<Integer, String> map = bm.getBookmarkMap();
        Map<Integer, String> sorted = new LinkedHashMap<>();
        if (map == null) {
            return sorted;
        }
        map.entrySet().stream()
                .filter(e -> e.getValue() != null)
                .sorted(Map.Entry.comparingByValue(String.CASE_INSENSITIVE_ORDER))
                .forEach(e -> sorted.put(e.getKey(), e.getValue()));
        return sorted;
    }

    private static int findExact(IBookmarks bm, String name) {
        if (name == null || name.isBlank()) {
            return -1;
        }
        return bm.getBookmarkId(name.trim());
    }

    /** Name of another bookmark equal to {@code name} ignoring case, or null. */
    private static String findIgnoringCase(IBookmarks bm, String name, int exceptId) {
        Map<Integer, String> map = bm.getBookmarkMap();
        if (map == null) {
            return null;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        for (Map.Entry<Integer, String> e : map.entrySet()) {
            if (e.getKey() != exceptId && e.getValue() != null && e.getValue().toLowerCase(Locale.ROOT).equals(lower)) {
                return e.getValue();
            }
        }
        return null;
    }

    private static boolean itemExists(IPEDSource source, int id) {
        if (id < 0 || id > source.getBookmarks().getLastId()) {
            return false;
        }
        try {
            return source.getLuceneId(id) >= 0;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static Map<String, Object> describe(IBookmarks bm, int bmId) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("name", bm.getBookmarkName(bmId));
        entry.put("item_count", bm.getBookmarkCount(bmId));
        String comment = bm.getBookmarkComment(bmId);
        entry.put("comment", comment == null || comment.isBlank() ? null : comment);
        entry.put("color", toHex(bm.getBookmarkColor(bmId)));
        return entry;
    }

    private static Map<String, Object> success(IBookmarks bm, int bmId) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("bookmark", describe(bm, bmId));
        return response;
    }

    private static Map<String, Object> itemSummary(IPEDSource source, int id) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", id);
        try {
            Document doc = source.getReader().document(source.getLuceneId(id));
            item.put("name", nullToEmpty(doc.get("name")));
            item.put("path", nullToEmpty(doc.get("path")));
            item.put("category", nullToEmpty(doc.get("category")));
        } catch (IOException | RuntimeException e) {
            item.put("error", "Falha ao ler o item: " + e.getMessage());
        }
        return item;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static Map<String, Object> notFound(String name) {
        return error("Marcador não encontrado: '" + name + "'. Use list_bookmarks para ver os nomes exatos.");
    }

    private static Map<String, Object> error(String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", false);
        response.put("error", message);
        return response;
    }

    private static Map<String, Object> error(String message, String key, Object value) {
        Map<String, Object> response = error(message);
        response.put(key, value);
        return response;
    }
}
