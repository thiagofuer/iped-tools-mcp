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
            Identifies the OWNER and SYSTEM/HARDWARE identifiers of the investigated evidence (mobile phone, workstation, server, or disk image).
            Automatically determines evidence type ('mobile', 'computer', 'hybrid', or 'generic') and extracts:
            - For Computers/Workstations (.E01, .dd, Windows/Linux/macOS): computer name/hostname, operating system version, registered owner/organization, install date, local OS user accounts (SAM/passwd), and user profile directories (e.g. Users/...).
            - For Mobile Devices (UFED, GrayKey, Cellebrite): device model, serial, IMEI, phone numbers, and registered app accounts (Google, Apple ID, WhatsApp, Telegram, etc.).
            
            CRITICAL WORKFLOW RULES:
            - ALWAYS USE THIS TOOL FIRST whenever the user asks:
              * 'Quem é o proprietário / dono do aparelho ou computador?'
              * 'A quem pertence este telefone celular / computador / HD?'
              * 'Qual é o nome da máquina (hostname), sistema operacional ou contas cadastradas?'
              * 'Quais contas de usuário ou perfis existem na máquina?'
              * 'Qual é o número de telefone, IMEI ou modelo do dispositivo investigado?'
            - NEVER DO A GENERIC TEXT SEARCH (e.g., search_documents for 'proprietario' or 'dono') to identify the device or system owner!
              Forensic extractions (Cellebrite UFED, GrayKey, Windows SAM, Registry) store ownership in dedicated structured
              artifacts ('device information', 'operating system', and 'user accounts'), NOT in plain text files containing the word 'dono'.
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
