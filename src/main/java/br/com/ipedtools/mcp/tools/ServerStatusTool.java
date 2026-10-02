package br.com.ipedtools.mcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import br.com.ipedtools.mcp.VersionInfo;
import br.com.ipedtools.mcp.service.IpedCoreService;
import io.quarkiverse.mcp.server.Tool;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ServerStatusTool {

    @Tool(
        name = "get_server_status",
        description = """
            Returns the operational status of the IPED MCP server and whether a forensic case is currently open.
            
            WORKFLOW RULE:
            - CALL THIS TOOL FIRST: It confirms that the IPED MCP server is running and tells you whether a case is loaded.
            - If case_open is false, no other tools will work until a case folder is loaded by the examiner.
            - If case_open is true, case_source_id contains the active case identifier (e.g. 'caso1') needed for other tools.
            """
    )
    public Map<String, Object> getServerStatus() {
        return checkStatus();
    }

    @Tool(
        name = "check_connection",
        description = """
            Alias for get_server_status: checks connectivity and lists active evidence sources.
            """
    )
    public Map<String, Object> checkConnection() {
        return checkStatus();
    }

    private Map<String, Object> checkStatus() {
        IpedCoreService service = IpedCoreService.getInstance();
        boolean isOpen = service.isCaseOpen();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("connected", true);
        response.put("server_version", VersionInfo.getVersion());
        response.put("case_open", isOpen);
        response.put("status", isOpen ? "Pronto para análise forense" : "Aguardando carregamento de caso");

        if (isOpen) {
            response.put("case_source_id", service.getSourceId());
            response.put("case_path", service.getCaseDirectory().getAbsolutePath());
            List<Map<String, Object>> sourcesList = service.listSources();
            response.put("sources_count", sourcesList.size());
            response.put("sources", sourcesList);
        } else {
            response.put("sources_count", 0);
            response.put("sources", List.of());
        }

        return response;
    }
}
