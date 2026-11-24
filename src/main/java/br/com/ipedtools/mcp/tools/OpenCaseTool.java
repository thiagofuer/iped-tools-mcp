package br.com.ipedtools.mcp.tools;

import java.io.File;
import java.util.Map;

import br.com.ipedtools.mcp.service.IpedCoreService;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class OpenCaseTool {

    @Tool(
        name = "open_case",
        description = """
            Opens or switches the IPED case of THIS conversation, by the case folder on the local machine.
            Closes the case previously open in this conversation and initializes Lucene index readers for the new case.
            This is the only way to open a case: a new session starts with no case open, and the case opened here
            does not change the case of other conversations or of the configurator window.
            If the folder is not an IPED case, the response is an error and the case already open stays open.

            WHEN TO USE:
            - When get_server_status reports case_open false: ask the user for the absolute path of the case folder
              and call this tool with it.
            - Call this tool whenever the user instructs to switch cases, open a different investigation,
              or analyze another case folder (e.g. 'Abra o caso C:\\casos_forenses\\operacao_alfa', 'Troque para o caso 2').
            - After opening the case, call 'get_case_summary' to present the new investigation overview to the user.
            """
    )
    public Map<String, Object> openCase(
            @ToolArg(name = "case_path", description = "The absolute folder path to the IPED case (containing 'iped' subfolder)", required = true)
            String casePath
    ) {
        if (casePath == null || casePath.isBlank()) {
            return Map.of("status", "error", "message", "Caminho do caso não informado.");
        }
        try {
            File targetDir = new File(casePath.trim());
            IpedCoreService.getInstance().openCase(targetDir);
            Map<String, Object> summary = IpedCoreService.getInstance().getCaseSummary();
            return Map.of(
                    "status", "success",
                    "case_name", targetDir.getName(),
                    "case_path", targetDir.getAbsolutePath(),
                    "total_items", summary.get("total_indexed_items"),
                    "message", "Caso '" + targetDir.getName() + "' aberto com sucesso!"
            );
        } catch (Exception e) {
            return Map.of(
                    "status", "error",
                    "message", "Falha ao abrir caso: " + e.getMessage()
            );
        }
    }
}
