package br.com.ipedtools.mcp.tools;

import java.util.List;

import br.com.ipedtools.mcp.service.IpedCoreService;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class BookmarkTools {

    @Tool(
        name = "list_bookmarks",
        description = """
            Lists all existing bookmarks (tags, labels, and evidence folders) in the IPED case.
            
            WHEN TO USE:
            - Bookmarks in IPED are used by forensic examiners to organize evidence for the official forensic report (laudo pericial).
            - Call this tool to inspect existing tags before adding new items or creating new bookmarks.
            """
    )
    public List<String> listBookmarks() {
        IpedCoreService service = IpedCoreService.getInstance();
        if (!service.isCaseOpen()) {
            return List.of("[Erro: Nenhum caso está aberto no momento.]");
        }
        return service.listBookmarks();
    }

    @Tool(
        name = "add_to_bookmark",
        description = """
            Adds one or more evidence items to an IPED bookmark (tag) for inclusion in the forensic investigation report (laudo pericial).
            If the bookmark does not already exist, it is automatically created in IPED.
            
            WHEN TO USE:
            - Use this tool whenever the user instructs to bookmark, flag, mark, or save an evidence item.
              Examples:
                * 'Adicione esse documento aos marcadores'
                * 'Marque esse arquivo como Prova Principal'
                * 'Salve os achados na pasta Suspeita de Fraude'
            """
    )
    public String addToBookmark(
            @ToolArg(name = "bookmark_name", description = "The name of the target bookmark tag (e.g. 'Evidências Relevantes', 'Fraude Financeira')", required = true)
            String bookmarkName,
            @ToolArg(name = "doc_ids", description = "List of integer document IDs to bookmark", required = true)
            List<Integer> docIds
    ) {
        IpedCoreService service = IpedCoreService.getInstance();
        if (!service.isCaseOpen()) {
            return "Erro: Nenhum caso está aberto no momento.";
        }
        return service.addBookmark(bookmarkName, docIds);
    }
}
