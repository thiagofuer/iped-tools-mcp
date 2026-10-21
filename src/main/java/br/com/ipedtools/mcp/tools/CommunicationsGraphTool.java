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
            Ranks the most active interlocutors in the case from the indexed communication properties
            (Communication:From/To) of messages, calls and emails, per real application channel (Viber, WhatsApp,
            Telegram, Signal, Phone, Email...).

            WHEN TO USE:
            - To identify key persons of interest, frequent calling partners or active message threads.
            - To get interaction volume breakdown by direction, channel and month, with evidence item ids.

            HOW TO READ THE RESULT:
            - The interlocutor of an incoming event is its sender; of an outgoing event, its recipients.
            - Direction comes ONLY from explicit properties (Communication:Direction, ufed:Direction). Events without
              them are 'unknown': they are reported in 'unknown_direction_total' and 'group_activity' and are NEVER
              counted as incoming or outgoing. Do not conclude who started a conversation from unknown events.
            - Device ownership is NOT assumed: no contact is labeled as the owner.
            - 'truncated', 'total_contacts_discovered' and 'returned_count' tell whether more contacts exist;
              'diagnostics' shows the coverage (explicit direction, resolved channel, timestamp) of the analyzed events.
            - 'evidence_ids' can be passed to get_document_metadata. 'contact_key' can be passed to get_contact_relationship.

            PARAMETERS:
            - 'limit': Maximum number of contacts (default 20, max 200).
            - 'channel': Optional application filter (e.g. 'Viber', 'WhatsApp', 'Phone', 'Email').
            - 'start_date' / 'end_date': Optional inclusive ISO-8601 period (e.g. '2024-01-01').
            """
    )
    public Map<String, Object> getTopContacts(
            @ToolArg(name = "limit", description = "Maximum number of most active contacts to return (default 20, max 200)", required = false, defaultValue = "20")
            int limit,
            @ToolArg(name = "channel", description = "Optional channel/application filter (e.g. Viber, WhatsApp, Phone, Email)", required = false)
            String channel,
            @ToolArg(name = "start_date", description = "Optional inclusive start date in ISO-8601 (e.g. 2024-01-01)", required = false)
            String startDate,
            @ToolArg(name = "end_date", description = "Optional inclusive end date in ISO-8601 (e.g. 2024-12-31)", required = false)
            String endDate
    ) {
        IpedCoreService service = IpedCoreService.getInstance();
        if (!service.isCaseOpen()) {
            return Map.of("error", "Nenhum caso está aberto no momento.");
        }
        try {
            return service.getTopContacts(limit, channel, startDate, endDate);
        } catch (IOException e) {
            return Map.of("error", "Erro ao extrair ranking de contatos: " + e.getMessage());
        }
    }

    @Tool(
        name = "get_communications_graph",
        description = """
            Builds a communication network from the indexed properties: typed nodes and weighted directed edges with
            per-channel and per-month aggregates and evidence item ids.

            WHEN TO USE:
            - To map the relationship network around a specific person of interest (pass 'focal_contact').
            - To discover communication clusters and intermediaries, or to draw a link chart / report.

            HOW TO READ THE RESULT:
            - Node 'type': ACCOUNT (person/account), GROUP (group chat, with 'participants_observed') or
              LOCAL_ENDPOINT ('local:<channel>', the local side of events with explicit direction on that channel).
              LOCAL_ENDPOINT has 'identity_resolved: false' and only lists 'observed_local_identifiers': device ownership
              is NOT assumed and no node is labeled as the owner.
            - Events without explicit direction link sender -> recipient only when both are identified; otherwise they
              are counted in 'unlinked_events'.
            - 'truncated' and 'total_eligible_edges' tell whether the edge limit cut the result; 'diagnostics' shows
              the coverage of explicit direction, resolved channel and timestamps.

            PARAMETERS:
            - 'focal_contact': Optional phone, email, account, node id or name to center the graph on.
            - 'min_interactions': Minimum interactions to include an edge (default 5, min 1).
            - 'limit_edges': Maximum edges, highest weight first (default 50, max 200).
            - 'channel', 'start_date', 'end_date': Optional filters.
            - 'include_mermaid': When true, adds a Mermaid 'flowchart LR' with exactly the returned nodes and edges.
            """
    )
    public Map<String, Object> getCommunicationsGraph(
            @ToolArg(name = "focal_contact", description = "Optional identifier or name to center the network around (e.g. phone, email, or name)", required = false)
            String focalContact,
            @ToolArg(name = "min_interactions", description = "Minimum interactions threshold to filter out incidental noise (default 5, min 1)", required = false, defaultValue = "5")
            int minInteractions,
            @ToolArg(name = "limit_edges", description = "Maximum number of graph edges to return (default 50, max 200)", required = false, defaultValue = "50")
            int limitEdges,
            @ToolArg(name = "channel", description = "Optional channel/application filter (e.g. Viber, WhatsApp, Phone, Email)", required = false)
            String channel,
            @ToolArg(name = "start_date", description = "Optional inclusive start date in ISO-8601 (e.g. 2024-01-01)", required = false)
            String startDate,
            @ToolArg(name = "end_date", description = "Optional inclusive end date in ISO-8601 (e.g. 2024-12-31)", required = false)
            String endDate,
            @ToolArg(name = "include_mermaid", description = "When true, include a Mermaid flowchart of the returned nodes and edges (default false)", required = false, defaultValue = "false")
            boolean includeMermaid
    ) {
        IpedCoreService service = IpedCoreService.getInstance();
        if (!service.isCaseOpen()) {
            return Map.of("error", "Nenhum caso está aberto no momento.");
        }
        try {
            return service.getCommunicationsGraph(focalContact, minInteractions, limitEdges, channel, startDate, endDate, includeMermaid);
        } catch (IOException e) {
            return Map.of("error", "Erro ao extrair grafo de comunicações: " + e.getMessage());
        }
    }

    @Tool(
        name = "get_contact_relationship",
        description = """
            Details how two contacts relate in the indexed communication properties: total interactions, messages,
            calls and emails, per-channel and per-direction breakdowns, monthly histogram, first/last interaction,
            groups in common and evidence item ids.

            WHEN TO USE:
            - When the examiner asks how, how often, when and through which channel two people communicated.
            - After get_top_contacts or get_communications_graph, passing their 'contact_key' / node id.

            HOW TO READ THE RESULT:
            - 'status': found | no_relationship | ambiguous | not_found | same_identity.
            - 'ambiguous': a reference matched several identities; 'candidates_a'/'candidates_b' are listed and NONE is
              chosen. Repeat the call with a candidate's 'contact_key'.
            - 'by_direction' (a_to_b / b_to_a) follows sender/recipient; 'by_event_direction' shows the explicit
              direction property ('unknown' when absent).
            - 'endpoint_inferred': true when a reference is an identifier observed on the local side of a channel and
              events whose local side has no identity were matched to it (inference, flagged as such).
            - 'diagnostics' reports the coverage of the analyzed events.

            PARAMETERS:
            - 'contact_a', 'contact_b': Phone, email, account, contact_key or name (required).
            - 'channel', 'start_date', 'end_date': Optional filters.
            - 'evidence_limit': Maximum evidence ids (default 5, max 20).
            """
    )
    public Map<String, Object> getContactRelationship(
            @ToolArg(name = "contact_a", description = "First contact: phone, email, account, contact_key or name")
            String contactA,
            @ToolArg(name = "contact_b", description = "Second contact: phone, email, account, contact_key or name")
            String contactB,
            @ToolArg(name = "channel", description = "Optional channel/application filter (e.g. Viber, WhatsApp, Phone, Email)", required = false)
            String channel,
            @ToolArg(name = "start_date", description = "Optional inclusive start date in ISO-8601 (e.g. 2024-01-01)", required = false)
            String startDate,
            @ToolArg(name = "end_date", description = "Optional inclusive end date in ISO-8601 (e.g. 2024-12-31)", required = false)
            String endDate,
            @ToolArg(name = "evidence_limit", description = "Maximum number of evidence item ids to return (default 5, max 20)", required = false, defaultValue = "5")
            int evidenceLimit
    ) {
        IpedCoreService service = IpedCoreService.getInstance();
        if (!service.isCaseOpen()) {
            return Map.of("error", "Nenhum caso está aberto no momento.");
        }
        try {
            return service.getContactRelationship(contactA, contactB, channel, startDate, endDate, evidenceLimit);
        } catch (IOException e) {
            return Map.of("error", "Erro ao analisar a relação entre contatos: " + e.getMessage());
        }
    }
}
