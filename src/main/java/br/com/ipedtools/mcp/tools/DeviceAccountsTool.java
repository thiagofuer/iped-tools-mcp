package br.com.ipedtools.mcp.tools;

import java.util.Map;

import br.com.ipedtools.mcp.service.DeviceAccountsService;
import br.com.ipedtools.mcp.service.IpedCoreService;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class DeviceAccountsTool {

    @Tool(
        name = "get_device_and_accounts",
        description = """
            Returns the device and account identifiers RECORDED in the evidence, each with its origin, grouped by evidence
            container. This tool does NOT say who owns the device: it gives you the facts to compare.

            WHAT IT RETURNS:
            - device_information: entries recorded by the extraction tool (IMEI, IMSI, ICCID, MSISDN, model, device name...),
              each with its item_id.
            - account_identifiers: phone numbers, e-mails, messenger IDs and usernames taken from structured account items
              (Cellebrite/UFED user accounts and accounts decoded by IPED parsers such as WhatsApp and Telegram).
              Each identifier lists its sources (item_id, app, property) and how many apps and items support it,
              ordered by the number of apps. Regex hits and file names are never used as sources.
            - user_profiles and registry_hives for computers (read the hives with get_document_text).
            - no_structured_identifiers + suggestions when nothing structured exists (e.g. memory cards).

            HOW TO IDENTIFY WHO USES THE DEVICE:
            - Call this tool FIRST when the examiner asks who uses or owns a phone, computer or disk.
            - Treat every value as a candidate. An identifier supported by several independent apps (e.g. the same phone
              in WhatsApp, Signal and Viber accounts) weighs more than one seen once. Accounts of other people can be
              logged in on the same device.
            - Confirm with list_properties(identity=true) and describe_property(name=..., query=...) and cite the item_id
              of the sources in your answer. Never state the owner from a single value.
            - NEVER run free-text searches for words like 'proprietario' or 'dono': extractions store identities in
              structured account and device artifacts, not in text containing those words.
            """
    )
    public Map<String, Object> getDeviceAndAccounts(
            @SuppressWarnings("unused")
            @ToolArg(name = "source_id", description = "Optional ID of the evidence source (from list_sources)", required = false)
            String sourceId
    ) {
        IpedCoreService service = IpedCoreService.getInstance();
        if (!service.isCaseOpen()) {
            return Map.of("error", "Nenhum caso está aberto no momento.");
        }
        try {
            return DeviceAccountsService.getInstance().getDeviceAndAccounts();
        } catch (Exception e) {
            return Map.of("error", "Falha ao obter identificadores do aparelho e das contas: " + e.getMessage());
        }
    }
}
