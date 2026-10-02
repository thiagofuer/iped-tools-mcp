package br.com.ipedtools.mcp.tools;

import java.util.List;
import java.util.Map;

import br.com.ipedtools.mcp.service.IpedCoreService;
import io.quarkiverse.mcp.server.Tool;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SourcesTool {

    @Tool(
        name = "list_sources",
        description = """
            Lists all forensic evidence sources (cases, disk images, mobile extractions, target devices) loaded in IPED.
            
            WHEN TO USE:
            - Call this at the start of an investigation or when the user asks:
              * 'Quais casos ou evidências estão carregados?'
              * 'Qual é o nome do caso aberto?'
              * 'Quais fontes de dados estão disponíveis?'
            - Returns a list of sources with their 'id' (e.g. 'caso1') and absolute file path.
              The 'id' field is used as the 'source_id' argument in other tools.
            """
    )
    public List<Map<String, Object>> listSources() {
        IpedCoreService service = IpedCoreService.getInstance();
        if (!service.isCaseOpen()) {
            return List.of(Map.of("error", "Nenhum caso do IPED aberto no momento."));
        }
        return service.listSources();
    }
}
