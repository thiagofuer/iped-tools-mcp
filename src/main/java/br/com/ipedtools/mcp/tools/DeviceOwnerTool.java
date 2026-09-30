package br.com.ipedtools.mcp.tools;

import java.util.Map;

import br.com.ipedtools.mcp.service.IpedCoreService;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class DeviceOwnerTool {

    @Tool(
        name = "get_device_and_owner_info",
        description = """
            Identifies the OWNER of the investigated device and retrieves hardware/OS identifiers.
            Automatically extracts registered user accounts (Google, Apple ID, WhatsApp, Telegram, Microsoft, etc.),
            owner phone numbers, primary email addresses, user profile names, device model, serial, and IMEI.
            
            CRITICAL WORKFLOW RULES:
            - ALWAYS USE THIS TOOL FIRST whenever the user asks:
              * 'Quem é o proprietário / dono do aparelho?'
              * 'A quem pertence este telefone celular / computador?'
              * 'Quais contas de usuário ou e-mails estão cadastrados no aparelho?'
              * 'Qual é o número de telefone, IMEI ou modelo do dispositivo investigado?'
            - NEVER DO A GENERIC TEXT SEARCH (e.g., search_documents for 'proprietario' or 'dono') to identify the device owner!
              Mobile forensic extractions (UFED, Cellebrite, GrayKey, IPED) store ownership in dedicated structured
              artifacts ('device information' and 'user accounts'), NOT in plain text files containing the word 'dono'.
            """
    )
    public Map<String, Object> getDeviceAndOwnerInfo(
            @ToolArg(name = "source_id", description = "Optional ID of the evidence source (from list_sources)", required = false, defaultValue = "")
            String sourceId
    ) {
        IpedCoreService service = IpedCoreService.getInstance();
        if (!service.isCaseOpen()) {
            return Map.of("error", "Nenhum caso está aberto no momento.");
        }
        return service.getDeviceAndOwnerInfo();
    }
}
