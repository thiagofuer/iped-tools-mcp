# Spec Delta

## MODIFIED Requirements

### Requirement: Device and Owner Identification
The server SHALL expose `get_device_and_owner_info` to automatically identify the target system, operating system metadata, local user accounts, and suspect or device ownership across both mobile extractions (UFED/GrayKey) and computer disk images (.E01/Windows/Linux/macOS), supporting multi-evidence containers and regex metadata extraction.

#### Scenario: LLM investigates device ownership
- **WHEN** the LLM calls `get_device_and_owner_info`
- **THEN** the server queries `category:"user accounts"` and `category:"device information"` and aggregates likely owner names, phone numbers, emails, and account profiles.

#### Scenario: LLM investigates computer forensic disk image
- **WHEN** the LLM calls `get_device_and_owner_info` on a computer disk image or filesystem evidence
- **THEN** the server queries operating system attributes, computer name, registered owner, local user accounts from SAM/system records, and user profile directory paths, returning an enriched structure identifying the machine and primary users.

#### Scenario: Case contains multiple forensic evidence containers
- **WHEN** `get_device_and_owner_info` is executed on a case indexing multiple evidence images (e.g. `Mantooth.E01` and `E01Capture.E01`)
- **THEN** the server groups identified operating systems, registered owners, and primary user accounts per evidence container under an `evidences` array, classifying the overarching case evidence type as `hybrid` when both computer and mobile containers exist.

#### Scenario: Mobile accounts identified via IPED regex metadata and preferences
- **WHEN** `get_device_and_owner_info` inspects user account items or messenger configurations (e.g., `com.whatsapp_preferences.xml`)
- **THEN** the server extracts phone numbers from `Regex:PHONE`, `phone`, or related metadata fields, linking the phone number to the corresponding primary account profile.

#### Scenario: LLM queries target ownership on empty or unindexed case
- **WHEN** `get_device_and_owner_info` is called on a case lacking structured device or account artifacts
- **THEN** the server returns a graceful response indicating evidence type and empty account lists with diagnostic guidance without throwing an uncaught exception.
