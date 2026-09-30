package br.com.ipedtools.mcp.tools;

import java.util.Map;

import br.com.ipedtools.mcp.service.IpedCoreService;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CaseSummaryTool {

    @Tool(
        name = "get_case_summary",
        description = """
            Returns an executive summary of the currently loaded IPED forensic case.
            Includes case name, data sources, total indexed items, evidence categories, and bookmarks.
            
            WORKFLOW RULE:
            - Call this tool first for any general question about the case or evidence overview
              (e.g., 'Resuma este caso', 'Quantos arquivos existem?', 'O que foi encontrado neste dispositivo?').
            """
    )
    public Map<String, Object> getCaseSummary(
            @ToolArg(name = "source_id", description = "Optional ID of the evidence source (e.g. 'caso1')", required = false, defaultValue = "")
            String sourceId
    ) {
        IpedCoreService service = IpedCoreService.getInstance();
        if (!service.isCaseOpen()) {
            return Map.of(
                    "error", "Nenhum caso está aberto no momento. Abra um caso no IPED Tools MCP antes de solicitar o resumo."
            );
        }
        return service.getCaseSummary();
    }
}
