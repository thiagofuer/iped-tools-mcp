# Design

## Context

In IPED cases, evidence originates either from mobile extractions (UFED, GrayKey, Cellebrite) or from computer disk images / volume clones (.E01, .dd, RAW, directory dumps of Windows, Linux, or macOS). Real cases frequently bundle multiple evidence containers together (e.g. `Mantooth.E01` and `E01Capture.E01` in the same case).

Currently, `IpedCoreService.getDeviceAndOwnerInfo()` only looked for mobile-specific patterns (`category:"device information"` with `ufed:*` fields, strict case-sensitive `WhatsApp` strings, and `doc.get("phoneNumber")`). This resulted in three major shortcomings:
1. Computer disk images returned empty system metadata and missing local users.
2. In mobile/Android extractions inside E01 images (such as `E01Capture.E01`), phones stored in IPED regex fields (`Regex:PHONE`) and configuration files (`com.whatsapp_preferences.xml`) were missed.
3. Multi-evidence cases were blended into a single flat bucket without attributing findings to specific evidence containers, forcing LLMs to execute repetitive Lucene searches.
4. Without an explicit language mirroring directive, LLMs like Qwen or Llama defaulted to English when interacting with English MCP tool definitions.

## Goals / Non-Goals

**Goals:**
- Detect evidence classification (`mobile`, `computer`, `hybrid`, or `generic`) automatically.
- Multi-evidence container discrimination: Identify root evidence containers (`isRoot:true` or path root segments) and group properties, OS metadata, and accounts into an `evidences` array.
- Deep regex and messenger parsing:
  - Read `Regex:PHONE`, `Regex:EMAIL`, `phone`, `cellPhone`, and related IPED regex fields.
  - Case-insensitive messenger recognition (`whatsapp`, `telegram`, `signal`) and preferences files (`com.whatsapp_preferences.xml`, `me.xml`, `wa.db`).
- Extract computer identity artifacts:
  - Computer Name / Hostname.
  - Operating System name, version, and installation timestamp.
  - Registered Owner and Registered Organization.
  - Local user accounts from SAM / passwd (`category:"user accounts"`).
  - User profile folders (`\Users\*`, `/home/*`).
- Maintain 100% backward compatibility for UFED extractions and callers (`getTopContacts`, `getCommunicationsGraph`).
- Add active language mirroring directive to `start_case` prompt in `ForensicPrompts.java`.
- Rebuild and package binary artifacts to refresh `dist/`.

**Non-Goals:**
- Creating multiple competing MCP tools: `get_device_and_owner_info` remains the single, authoritative entry point for target ownership.
- Deep binary registry parsing outside of what IPED already indexed into Lucene.

## Decisions

### Decision 1: Evidence Classification and Fallback Flow
- **Choice**: Check for both mobile and computer artifacts across the index.
  - Mobile: `category:"device information"` with `ufed:*` entries, mobile messenger accounts, or `Regex:PHONE` in messenger configs.
  - Computer: OS metadata documents (`registeredOwner:*`, `computerName:*`, `hostName:*`, `productName:*`), SAM accounts, or profile directories.
  - Classification: `"mobile"`, `"computer"`, `"hybrid"` (when both are detected), or `"generic"`.
- **Rationale**: Fast, automated classification with zero manual configuration.

### Decision 2: Enriched Backward-Compatible Schema with `evidences` Grouping
- **Choice**: Keep all existing top-level keys (`source`, `likely_owner_names`, `owner_phone_numbers`, `owner_emails`, `device_properties`, `total_accounts_found`, `primary_accounts`), and augment with:
  - `evidence_type`: `"mobile"`, `"computer"`, `"hybrid"`, or `"generic"`.
  - `system_info`: Map containing consolidated OS attributes.
  - `user_profile_dirs`: List of user profile directory names.
  - `user_accounts`: List of structured accounts found.
  - `evidences`: List of evidence container objects, each detailing:
    - `id`: Container identifier / index.
    - `name`: File or container name (e.g. `Mantooth.E01`, `E01Capture.E01`).
    - `type`: `"computer"`, `"mobile"`, or `"generic"`.
    - `system_info` / `device_properties`: Container-specific system/hardware attributes.
    - `likely_owners`: Container-specific owner or user names.
    - `accounts`: Container-specific accounts and phone numbers.
- **Rationale**: Allows advanced LLMs (and examiners) to receive an immediate per-evidence breakdown in 1 call without losing backward compatibility.

### Decision 3: Local Account and Profile Parsing
- **Choice**: In `category:"user accounts"`, extract all user documents regardless of app prefix. Capture `userName`, `name`, `accountType`, and add non-system usernames to `likely_owner_names`.
- Filter out well-known default system accounts from `likely_owner_names` (e.g. `WDAGUtilityAccount`, `DefaultAccount`, `Guest`, `nobody`) while preserving them in `user_accounts`.

### Decision 4: Deep Regex and Messenger Configuration Extraction
- **Choice**:
  - In addition to `doc.get("phoneNumber")`, read `doc.get("Regex:PHONE")`, `doc.get("phone")`, `doc.get("cellPhone")`, and `doc.get("contact:phone")`.
  - Apply case-insensitive pattern matching: `String lower = name.toLowerCase(); if (lower.contains("whatsapp") || lower.contains("telegram") || lower.contains("signal"))`.
  - Detect configuration files (e.g. `com.whatsapp_preferences.xml`) under `category:"user accounts"` or messaging categories and link extracted regex phone numbers as primary accounts.
- **Rationale**: Solves real-world cases where mobile filesystems are analyzed inside raw or E01 disk images rather than proprietary UFED containers.

### Decision 5: Active Language Mirroring Directive in `start_case`
- **Choice**: Add an explicit language directive in `ForensicPrompts.java`:
  - Instruct the model to detect and mirror the examiner's conversational language.
  - When invoked without conversational user text, anchor the greeting in the language of the prompt instructions (Portuguese pt-BR) while remaining agile to transition if the examiner writes in another language.
- **Rationale**: Prevents models like Qwen 2.5 from defaulting to English due to technical tool schemas, while respecting international examiners.

## Risks / Trade-offs

- **[Risk] Performance on large multi-evidence cases**: Checking container root paths across thousands of hits.
  - *Mitigation*: Restrict root searches to `isRoot:true` (typically < 10 containers per case) and cap account / OS query results to top matches.
- **[Risk] Stale binaries in client environments (LM Studio)**:
  - *Mitigation*: Re-run packaging scripts (`package_app.ps1`) after code changes to refresh `dist/` and notify the examiner to restart the MCP server in LM Studio.

## Migration Plan

Additive changes to in-process query logic, tool outputs, and prompt templates. No database migrations needed.
