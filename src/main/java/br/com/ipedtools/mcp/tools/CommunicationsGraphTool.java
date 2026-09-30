package br.com.ipedtools.mcp.tools;

import java.io.IOException;
import java.util.Map;

import br.com.ipedtools.mcp.service.IpedCoreService;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CommunicationsGraphTool {

    @Tool(
        name = "get_top_contacts",
        description = """
            Aggregates and ranks the most active interlocutors across all communication channels in the case
            (WhatsApp, Telegram, Signal, phone calls, SMS, emails).
            
            WHEN TO USE:
            - When investigating who the device owner/suspect communicated with most.
            - To identify key persons of interest, frequent calling partners, or active message threads.
            - To get interaction volume breakdown (messages count, calls count, incoming vs outgoing).
            
            PARAMETERS:
            - 'limit': Maximum number of most active contacts to return (default 20, max 200).
            
            RETURNS:
            - Ranked list of contacts with phone/account identifiers and resolved contact names.
            - Volume statistics: total interactions, messages count, calls count, incoming vs outgoing.
            - Communication channels used and first/last interaction timestamps.
            """
    )
    public Map<String, Object> getTopContacts(
            @ToolArg(name = "limit", description = "Maximum number of most active contacts to return (default 20, max 200)", required = false, defaultValue = "20")
            int limit
    ) {
        IpedCoreService service = IpedCoreService.getInstance();
        if (!service.isCaseOpen()) {
            return Map.of("error", "Nenhum caso está aberto no momento.");
        }
        try {
            return service.getTopContacts(limit);
        } catch (IOException e) {
            return Map.of("error", "Erro ao extrair ranking de contatos: " + e.getMessage());
        }
    }

    @Tool(
        name = "get_communications_graph",
        description = """
            Extracts a topological communication network graph consisting of nodes (individuals, accounts, groups)
            and weighted edges (interaction volume, channels, date range).
            
            WHEN TO USE:
            - To map the relationship network around a specific person of interest (pass 'focal_contact').
            - To discover core communication clusters and intermediaries across multiple seized devices.
            - To filter out noise/spam using the 'min_interactions' threshold.
            
            PARAMETERS:
            - 'focal_contact': Optional phone number, email, account, or contact name to center the graph around (e.g. '11988887777' or 'Carlos'). If omitted, extracts general network clusters.
            - 'min_interactions': Minimum interaction threshold to include an edge (default 5, min 1).
            - 'limit_edges': Maximum number of edges to return, prioritizing highest volume (default 50, max 200).
            
            RETURNS:
            - 'nodes': List of nodes with id, label, resolved name, and total interactions.
            - 'edges': Directed interactions with source, target, weight, channels, calls/messages count, and timestamps.
            """
    )
    public Map<String, Object> getCommunicationsGraph(
            @ToolArg(name = "focal_contact", description = "Optional identifier or name to center the network around (e.g. phone, email, or name)", required = false, defaultValue = "")
            String focalContact,
            @ToolArg(name = "min_interactions", description = "Minimum interactions threshold to filter out incidental noise (default 5, min 1)", required = false, defaultValue = "5")
            int minInteractions,
            @ToolArg(name = "limit_edges", description = "Maximum number of graph edges to return (default 50, max 200)", required = false, defaultValue = "50")
            int limitEdges
    ) {
        IpedCoreService service = IpedCoreService.getInstance();
        if (!service.isCaseOpen()) {
            return Map.of("error", "Nenhum caso está aberto no momento.");
        }
        try {
            return service.getCommunicationsGraph(focalContact, minInteractions, limitEdges);
        } catch (IOException e) {
            return Map.of("error", "Erro ao extrair grafo de comunicações: " + e.getMessage());
        }
    }
}
