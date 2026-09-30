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
The server SHALL expose `get_device_and_owner_info` to automatically identify the suspect or device owner from user account artifacts and hardware properties.

#### Scenario: LLM investigates device ownership
- **WHEN** the LLM calls `get_device_and_owner_info`
- **THEN** the server queries `category:"user accounts"` and `category:"device information"` and aggregates likely owner names, phone numbers, emails, and account profiles.

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
