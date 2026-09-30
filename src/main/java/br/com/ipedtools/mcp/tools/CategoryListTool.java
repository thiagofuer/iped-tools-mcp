package br.com.ipedtools.mcp.tools;

import java.util.List;

import br.com.ipedtools.mcp.service.IpedCoreService;
import io.quarkiverse.mcp.server.Tool;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CategoryListTool {

    @Tool(
        name = "list_categories",
        description = """
            Lists all document and artifact categories present and indexed in the active IPED case.
            
            WHEN TO USE:
            - Call this tool to discover what types of evidence (chats, emails, media, financial accounts, etc.)
              exist in the current case before formulating category filters in 'search_documents'.
            """
    )
    public List<String> listCategories() {
        IpedCoreService service = IpedCoreService.getInstance();
        if (!service.isCaseOpen()) {
            return List.of("[Erro: Nenhum caso está aberto no momento.]");
        }
        return service.listCategories();
    }
}
