package br.com.ipedtools.mcp.tools;

import java.io.IOException;
import java.util.Map;

import br.com.ipedtools.mcp.service.IpedCoreService;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class EvidenceTreeTool {

    @Tool(
        name = "list_folder_contents",
        description = """
            Lists folder contents (subdirectories and files) within the forensic evidence tree.
            
            WHAT THIS TOOL DOES:
            1. Non-recursive navigation (recursive=false): Explores immediate child files and direct subdirectories without expanding deeper. Perfect for interactive file-system browsing matching IPED Desktop's Evidence Tree tab.
            2. Recursive expansion (recursive=true): Lists all nested descendant files and folders within the target directory path up to the specified limit.
            3. Root inspection: Pass folder_path="/" or null/empty to explore root evidence images, volumes, and top-level partitions.
            
            FORENSIC WORKFLOW GUIDANCE:
            - Use this tool when exploring suspect directory trees, such as:
              * User profiles (e.g., 'Users/Target/Desktop', 'Users/Target/Downloads')
              * Application data (e.g., 'data/data/com.whatsapp', 'AppData/Roaming')
              * System directories or external USB storage volumes.
            - Returned items contain forensic metadata: item ID, name, path, file size, IPED category, hash, and triage selection status.
            """
    )
    public Map<String, Object> listFolderContents(
            @ToolArg(name = "folder_path", description = "Directory path or container in the evidence tree to browse (e.g. 'Users/Target/Downloads', 'EXTRACTION_FFS.zip/apex', or '/' for root). Defaults to root.", required = false)
            String folderPath,
            @ToolArg(name = "recursive", description = "If true, traverses all subfolders recursively. If false, returns only immediate children (default: false).", required = false)
            Boolean recursive,
            @ToolArg(name = "limit", description = "Maximum number of files/subdirectories to return (default: 100, max: 1000).", required = false)
            Integer limit
    ) {
        IpedCoreService service = IpedCoreService.getInstance();
        if (!service.isCaseOpen()) {
            return Map.of("error", "Nenhum caso está aberto no momento.");
        }
        try {
            boolean isRecursive = recursive != null && recursive;
            int maxLimit = limit != null && limit > 0 ? limit : 100;
            return service.listFolderContents(folderPath, isRecursive, maxLimit);
        } catch (IOException e) {
            return Map.of(
                    "folder_path", folderPath != null ? folderPath : "/",
                    "error", "Erro ao listar conteúdo do diretório: " + e.getMessage()
            );
        }
    }
}
