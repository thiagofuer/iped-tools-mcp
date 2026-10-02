# Forensic Query Tools Specification

## Purpose
Exposes forensic interrogation tools directly to Large Language Models (LLMs) via the Model Context Protocol, enabling in-process querying of IPED Lucene indexes, metadata extraction, text extraction with token pagination, category discovery, bookmark management, and dynamic case opening.

## Requirements

### Requirement: Server Status and Case Summary
The server SHALL expose `get_server_status` and `get_case_summary` to report the server health, active case directory, total item counts, categories, and bookmarks.

#### Scenario: LLM checks system status before querying
- **WHEN** the LLM calls `get_server_status`
- **THEN** the server returns a JSON response indicating whether a case is loaded and the active case path.

#### Scenario: LLM requests executive case summary
- **WHEN** the LLM calls `get_case_summary`
- **THEN** the server returns total indexed items, count of categories, top categories, and existing bookmarks.

### Requirement: Lucene Document Search
The server SHALL expose `search_documents` executing native Lucene queries against the in-process IPED index with sanitized inputs and configurable result limits.

#### Scenario: LLM searches for specific communication or keyword
- **WHEN** the LLM provides a Lucene query string (e.g. `category:"chat messages" AND content:propina`)
- **THEN** the server returns the count of matching items and a list of item IDs with basic snippet metadata.

### Requirement: Metadata Extraction
The server SHALL expose `get_document_metadata` to retrieve forensic metadata for one or more item IDs, reporting structured, categorized properties (`basic`, `communication`, `geo`, `forensic`, `extra`) and preserving parser-extracted properties without truncation from restrictive fixed whitelists.

#### Scenario: LLM retrieves properties for candidate item IDs
- **WHEN** the LLM requests metadata for a list of document IDs
- **THEN** the server returns categorized properties for each item, including message directions, participants, timestamps, coordinates, and hashes, while suppressing internal Lucene engine buffers.

#### Scenario: LLM inspects chat message or photo with GPS
- **WHEN** the requested document is a WhatsApp message or a JPEG with EXIF coordinates
- **THEN** the metadata response includes `Communication:*` properties or `common:geo:locations` without being stripped by filtering.

### Requirement: Text Extraction with Pagination
The server SHALL expose `get_document_text` to extract decoded text content using IPED's StandardParser and Tika with configurable offset and length chunking.

#### Scenario: LLM reads a large document in chunks
- **WHEN** the LLM requests text for an item with an offset and max_chars limit
- **THEN** the server returns the requested character slice and includes truncation notices indicating the total length and next offset.

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

### Requirement: Category and Bookmark Operations
The server SHALL expose `list_categories`, `list_bookmarks`, and `add_to_bookmark` to inspect evidentiary taxonomies and persist forensic bookmarks to `bookmarks.iped`.

#### Scenario: LLM tags critical evidence for the forensic report
- **WHEN** the LLM calls `add_to_bookmark` with a bookmark name and a list of document IDs
- **THEN** the server creates or updates the bookmark in `BitmapBookmarks` and commits changes synchronously to disk.

### Requirement: Dynamic Case Opening
The server SHALL expose `open_case` allowing the LLM to open or switch active IPED cases at runtime via folder path.

#### Scenario: User asks LLM to switch investigation case
- **WHEN** the LLM calls `open_case` with a valid IPED case directory path
- **THEN** the server releases previous readers, opens the new case, updates the persistent active case state, and returns the new case summary.

### Requirement: Evidence Sources Discovery
The server SHALL expose `list_sources` and connectivity alias `check_connection` to discover active evidence sources, physical storage paths, and connection readiness for the loaded IPED case.

#### Scenario: LLM inquires about active evidence sources
- **WHEN** the LLM calls `list_sources`
- **THEN** the server returns a list of configured sources including each source's unique identifier (e.g. `caso1`) and absolute filesystem path.

#### Scenario: Client checks connection readiness
- **WHEN** the LLM calls `check_connection`
- **THEN** the server returns connection health, active server version, whether a case is loaded, and source descriptors identical to `get_server_status`.

#### Scenario: LLM calls list_sources when no case is loaded
- **WHEN** `list_sources` is called while no IPED case is open
- **THEN** the server returns an error object indicating that no IPED case is currently loaded without crashing or terminating STDIO.
