package br.com.ipedtools.mcp.tools;

import java.io.IOException;
import java.util.Map;

import br.com.ipedtools.mcp.service.IpedCoreService;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class TimelineTool {

    @Tool(
        name = "get_timeline",
        description = """
            Returns evidentiary events from the IPED case in strict chronological order (ascending by date)
            within a designated ISO-8601 interval.
            
            EVENT COVERAGE:
            - Instant messaging (WhatsApp, Telegram, Signal)
            - Phone calls & cellular logs
            - Emails sent & received
            - Web browser history & visited URLs
            - File creation, modification, and access timestamps
            - Media capture & EXIF photo timestamps
            - GPS waypoints & location logs
            
            PARAMETERS:
            - 'start_date': Start of the time window (e.g. '2024-06-01' or '2024-06-01T12:00:00Z').
            - 'end_date': End of the time window (e.g. '2024-06-05' or '2024-06-05T23:59:59Z').
            - 'category': Optional category filter (e.g. 'whatsapp', 'phone calls', 'internet history', 'emails', or '' for all).
            - 'limit': Maximum events to return (default 50, max 200).
            """
    )
    public Map<String, Object> getTimeline(
            @ToolArg(name = "start_date", description = "Start timestamp in ISO-8601 format (e.g. '2024-06-01' or '2024-06-01T00:00:00Z')", required = true)
            String startDate,
            @ToolArg(name = "end_date", description = "End timestamp in ISO-8601 format (e.g. '2024-06-05' or '2024-06-05T23:59:59Z')", required = true)
            String endDate,
            @ToolArg(name = "category", description = "Optional category filter (e.g. 'whatsapp', 'phone calls', 'emails', or empty for all)", required = false, defaultValue = "")
            String category,
            @ToolArg(name = "limit", description = "Max events to return (default 50, max 200)", required = false, defaultValue = "50")
            int limit
    ) {
        IpedCoreService service = IpedCoreService.getInstance();
        if (!service.isCaseOpen()) {
            return Map.of("error", "Nenhum caso está aberto no momento.");
        }
        try {
            return service.getTimeline(startDate, endDate, category, limit);
        } catch (IOException e) {
            return Map.of("error", "Erro ao executar consulta de linha do tempo: " + e.getMessage());
        }
    }

    @Tool(
        name = "get_events_around_time",
        description = """
            Reconstructs user and device activity in a temporal window immediately preceding and succeeding
            a pivotal milestone timestamp (e.g. +/- 30 minutes).
            
            FORENSIC USE CASES:
            - Suspect activity reconstruction: What occurred on the device right before and after a crime?
            - Communication context: What messages, web searches, or calls occurred around a critical wire transfer or phone call?
            - Physical movement context: GPS locations and photo captures surrounding an incident.
            
            PARAMETERS:
            - 'target_time': The central milestone timestamp in ISO-8601 format (e.g. '2024-06-02T17:39:14Z' or '2024-06-02 17:39:14').
            - 'window_minutes': Number of minutes before and after target_time to inspect (default 30, max 1440).
            - 'limit': Max events to return (default 50, max 200).
            """
    )
    public Map<String, Object> getEventsAroundTime(
            @ToolArg(name = "target_time", description = "Target milestone timestamp in ISO-8601 format (e.g. '2024-06-02T17:39:14Z')", required = true)
            String targetTime,
            @ToolArg(name = "window_minutes", description = "Minutes before and after target timestamp to inspect (default 30, max 1440)", required = false, defaultValue = "30")
            int windowMinutes,
            @ToolArg(name = "limit", description = "Max events to return (default 50, max 200)", required = false, defaultValue = "50")
            int limit
    ) {
        IpedCoreService service = IpedCoreService.getInstance();
        if (!service.isCaseOpen()) {
            return Map.of("error", "Nenhum caso está aberto no momento.");
        }
        try {
            return service.getEventsAroundTime(targetTime, windowMinutes, limit);
        } catch (IOException e) {
            return Map.of("error", "Erro ao reconstruir eventos ao redor do horário: " + e.getMessage());
        }
    }
}
