package br.com.ipedtools.mcp.tools;

import java.util.List;
import java.util.Map;

import br.com.ipedtools.mcp.service.BookmarkService;
import br.com.ipedtools.mcp.service.IpedCoreService;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class BookmarkTools {

    private static final Map<String, Object> NO_CASE = Map.of("success", false, "error",
            IpedCoreService.NO_CASE_OPEN_MESSAGE);

    @Tool(
        name = "list_bookmarks",
        description = """
            Lists the bookmarks of the IPED case with name, item_count, comment and color (#RRGGBB, or null when
            the bookmark has no color), plus unused_standard_colors: the colors of the IPED standard palette that
            no bookmark uses.

            WHEN TO USE:
            - Bookmarks are how the examiner organizes evidence for the forensic report (laudo pericial), which is
              generated in IPED Desktop.
            - Call it before adding items, to reuse the exact name of an existing bookmark, and before suggesting
              a color for a new bookmark.
            - To see which items a bookmark holds, use list_bookmark_items.
            """
    )
    public Map<String, Object> listBookmarks() {
        if (!IpedCoreService.getInstance().isCaseOpen()) {
            return NO_CASE;
        }
        return BookmarkService.getInstance().listBookmarks();
    }

    @Tool(
        name = "list_bookmark_items",
        description = """
            Lists the items of one bookmark (id, name, path, category) in item ID order, with pagination.
            Read-only.

            WHEN TO USE:
            - To check what was recorded in a bookmark, or to find the IDs before removing items from it.
            - Pagination: the response has total_found, offset, has_more and next_offset. While has_more is true,
              call again with offset=next_offset before stating that an item is not in the bookmark.
            """
    )
    public Map<String, Object> listBookmarkItems(
            @ToolArg(name = "bookmark_name", description = "Exact bookmark name, as returned by list_bookmarks", required = true)
            String bookmarkName,
            @ToolArg(name = "offset", description = "Index of the first item to return (default 0); use next_offset to continue", required = false)
            Integer offset,
            @ToolArg(name = "limit", description = "Maximum items per page (default 50, max 500)", required = false)
            Integer limit
    ) {
        if (!IpedCoreService.getInstance().isCaseOpen()) {
            return NO_CASE;
        }
        return BookmarkService.getInstance().listBookmarkItems(bookmarkName, offset, limit);
    }

    @Tool(
        name = "add_to_bookmark",
        description = """
            Adds one or more evidence items to an IPED bookmark (tag) for inclusion in the forensic investigation report (laudo pericial).
            If the bookmark does not already exist, it is created with the optional color and comment.
            The response states whether the bookmark was created and lists IDs not found in the case.

            WRITES TO THE CASE: ALWAYS ASK FIRST.
            - Never call this tool on your own initiative. Before calling it, show the user the items (ID and name),
              the bookmark name and, for a new bookmark, the color and comment, and wait for the user's
              explicit confirmation. A confirmation covers only the batch shown; a new batch needs a new confirmation.
            - The bookmark name and comment are the ones the user chooses. You may suggest them, but never use or
              add words that identify AI or LLM authorship (e.g. "IA", "LLM", "Claude", "GPT", "automatico").
            - The color is the user's choice. You may suggest one, preferably from unused_standard_colors of
              list_bookmarks, as part of the confirmation. If the user does not choose a color, create the bookmark
              without one (IPED Desktop shows it in its default color).
            - Color and comment apply only when the bookmark is created; to change an existing bookmark, use
              update_bookmark.

            WHEN TO USE:
            - When the user asks to bookmark, flag or save evidence items, after the confirmation above.
              Examples:
                * 'Adicione esse documento aos marcadores'
                * 'Marque esse arquivo como Prova Principal'
                * 'Salve os achados na pasta Suspeita de Fraude'
            """
    )
    public Map<String, Object> addToBookmark(
            @ToolArg(name = "bookmark_name", description = "The name of the target bookmark tag (e.g. 'Evidências Relevantes', 'Fraude Financeira')", required = true)
            String bookmarkName,
            @ToolArg(name = "doc_ids", description = "List of integer document IDs to bookmark", required = true)
            List<Integer> docIds,
            @ToolArg(name = "color", description = "Color for a new bookmark, as #RRGGBB, chosen by the user (optional)", required = false)
            String color,
            @ToolArg(name = "comment", description = "Comment for a new bookmark, written or approved by the user (optional)", required = false)
            String comment
    ) {
        if (!IpedCoreService.getInstance().isCaseOpen()) {
            return NO_CASE;
        }
        return BookmarkService.getInstance().addToBookmark(bookmarkName, docIds, color, comment);
    }

    @Tool(
        name = "remove_from_bookmark",
        description = """
            Removes items from an existing bookmark. The bookmark is kept, even if it becomes empty; the items stay
            in the case with their other bookmarks and checked state. The response lists IDs that were not in it.

            WRITES TO THE CASE: ALWAYS ASK FIRST.
            - Never call this tool on your own initiative. Before calling it, show the user the bookmark and the
              items (ID and name) that will leave it, say that this cannot be undone through the MCP (the items
              would have to be added again), and wait for the user's explicit confirmation.
              A confirmation covers only the batch shown; a new batch needs a new confirmation.
            - Use list_bookmark_items to find the IDs in the bookmark.
            """
    )
    public Map<String, Object> removeFromBookmark(
            @ToolArg(name = "bookmark_name", description = "Exact bookmark name, as returned by list_bookmarks", required = true)
            String bookmarkName,
            @ToolArg(name = "doc_ids", description = "List of integer document IDs to remove from the bookmark", required = true)
            List<Integer> docIds
    ) {
        if (!IpedCoreService.getInstance().isCaseOpen()) {
            return NO_CASE;
        }
        return BookmarkService.getInstance().removeFromBookmark(bookmarkName, docIds);
    }

    @Tool(
        name = "update_bookmark",
        description = """
            Changes the name, the color or the comment of an existing bookmark, keeping its items.
            Give at least one of new_name, color or comment; an empty comment clears the comment.
            A new name equal to another bookmark (ignoring letter case) is refused.

            WRITES TO THE CASE: ALWAYS ASK FIRST.
            - Never call this tool on your own initiative. Before calling it, show the user the bookmark and the
              changes (current and new name, color or comment), and wait for the user's explicit confirmation.
            - The name, color and comment are the user's choice. You may suggest them (for colors, preferably from
              unused_standard_colors of list_bookmarks), but never use or add words that identify AI or LLM
              authorship (e.g. "IA", "LLM", "Claude", "GPT", "automatico").
            """
    )
    public Map<String, Object> updateBookmark(
            @ToolArg(name = "bookmark_name", description = "Exact current bookmark name, as returned by list_bookmarks", required = true)
            String bookmarkName,
            @ToolArg(name = "new_name", description = "New bookmark name (optional)", required = false)
            String newName,
            @ToolArg(name = "color", description = "New color as #RRGGBB (optional)", required = false)
            String color,
            @ToolArg(name = "comment", description = "New comment (optional); an empty string clears it", required = false)
            String comment
    ) {
        if (!IpedCoreService.getInstance().isCaseOpen()) {
            return NO_CASE;
        }
        return BookmarkService.getInstance().updateBookmark(bookmarkName, newName, color, comment);
    }

    @Tool(
        name = "delete_bookmark",
        description = """
            Deletes a bookmark with its name, color and comment. The items stay in the case, with their other
            bookmarks and checked state unchanged. The response reports how many items the bookmark had.

            WRITES TO THE CASE: ALWAYS ASK FIRST.
            - Never call this tool on your own initiative. Before calling it, show the user the bookmark and how
              many items it holds (list_bookmarks), say that this cannot be undone through the MCP, and wait for the
              user's explicit confirmation.
            """
    )
    public Map<String, Object> deleteBookmark(
            @ToolArg(name = "bookmark_name", description = "Exact bookmark name, as returned by list_bookmarks", required = true)
            String bookmarkName
    ) {
        if (!IpedCoreService.getInstance().isCaseOpen()) {
            return NO_CASE;
        }
        return BookmarkService.getInstance().deleteBookmark(bookmarkName);
    }
}
