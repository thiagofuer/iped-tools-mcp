# Proposal

## Why

Currently, `get_device_and_owner_info` is strictly tailored to mobile extractions (specifically Cellebrite UFED), relying exclusively on `category:"device information"` with `ufed:EntryName` / `ufed:EntryValue` and mobile chat account heuristics (`Telegram`, `WhatsApp`). When examining forensic disk images of computers (.E01, .dd, RAW, or filesystem dumps of Windows, Linux, or macOS), these UFED properties do not exist. Consequently, the tool returns empty device properties and fails to identify the computer name, registered owner, operating system version, or local user accounts.

Furthermore, real forensic cases frequently contain multiple evidence containers (e.g. a Windows PC disk image `Mantooth.E01` alongside a mobile device image `E01Capture.E01`). Without multi-evidence discrimination and deep regex/messenger parsing (e.g., `Regex:PHONE`, `com.whatsapp_preferences.xml`), LLMs are forced to execute dozens of ad-hoc Lucene searches to uncover essential case demographics. Finally, LLMs operating under MCP prompts may suffer from language drift (responding in English instead of the examiner's language) unless explicit language mirroring directives are provided.

## What Changes

- **Evidence Type Detection**: The tool detects whether the evidence corresponds to a `mobile` extraction, a `computer` filesystem/disk image, `hybrid` (multi-evidence), or `generic` evidence.
- **Multi-Evidence Container Discrimination (`evidences`)**:
  - Automatically identifies root evidence images via `isRoot:true` and path root segments (e.g., `Mantooth.E01`, `E01Capture.E01`).
  - Groups operating system metadata, registered owners, and user/messenger accounts by their respective evidence container.
- **Deep Extraction via IPED Regex & Messenger Configs**:
  - Extracts phone numbers and identifiers from IPED regex metadata fields (`Regex:PHONE`, `Regex:EMAIL`, `phone`, `cellPhone`).
  - Supports case-insensitive matching for mobile messengers (`whatsapp`, `telegram`, `signal`) and detects configuration/preferences files (e.g. `com.whatsapp_preferences.xml`, `me.xml`).
- **Computer & OS Artifact Extraction**:
  - Extract computer hostname / machine name (`ComputerName`, `HostName`).
  - Extract registered owner and organization from OS registry / system artifacts (`RegisteredOwner`, `RegisteredOrganization`, `productName`, `CurrentVersion`).
  - Extract local operating system user accounts (`category:"user accounts"`) without requiring mobile app prefixes.
  - Extract user profile directories (e.g. paths under `\Users\*`, `\Documents and Settings\*`, or `/home/*`).
- **Language Mirroring in Forensic Prompts**:
  - Update `start_case` prompt in `ForensicPrompts.java` to instruct LLMs to detect and mirror the examiner's language actively (e.g. Portuguese pt-BR for Brazilian examiners, English for international examiners).
- **Enriched Unified Schema**: The returned JSON structure provides both generalized fields (`evidence_type`, `evidences`, `system_info`, `user_accounts`, `user_profile_dirs`) and backward-compatible fields (`likely_owner_names`, `owner_phone_numbers`, `owner_emails`, `device_properties`, `primary_accounts`).
- **Tool Description Update**: Update `@Tool` description in `DeviceOwnerTool.java` to guide LLMs on computer, mobile, and multi-evidence investigations.
- **Packaging and Automated Tests**: Unit tests covering multi-evidence segregation, regex extraction, and prompt language directives, followed by binary repackaging.

## Capabilities

### Modified Capabilities
- `forensic-query-tools`: Modify requirement `Device and Owner Identification` to mandate multi-evidence container grouping (`evidences`), regex phone extraction (`Regex:PHONE`), and identification across both mobile extractions and computer disk images.
- `forensic-prompt-engineering`: Modify requirement `Forensic Case Startup Prompt` to include an explicit language mirroring directive that guides the LLM to communicate in the examiner's language.

## Impact

- **Affected Code**:
  - `br.com.ipedtools.mcp.service.IpedCoreService`: Implementation of `getDeviceAndOwnerInfo()`.
  - `br.com.ipedtools.mcp.tools.DeviceOwnerTool`: Documentation updates in `@Tool` annotation.
  - `br.com.ipedtools.mcp.prompts.ForensicPrompts`: Prompt instructions for language mirroring.
  - Tests: `IpedCoreServiceTest.java`, `ForensicPromptsTest.java`.
- **APIs**: The MCP tool `get_device_and_owner_info` response is enriched with new non-breaking fields (`evidences`, `evidence_type`, etc.).
- **Dependencies**: None.
