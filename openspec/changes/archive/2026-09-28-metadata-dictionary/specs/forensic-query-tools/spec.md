# Spec Delta: forensic-query-tools

## MODIFIED Requirements

### Requirement: Metadata Extraction
The server SHALL expose `get_document_metadata` to retrieve forensic metadata for one or more item IDs, reporting structured, categorized properties (`basic`, `communication`, `geo`, `forensic`, `extra`) and preserving parser-extracted properties without truncation from restrictive fixed whitelists.

#### Scenario: LLM retrieves properties for candidate item IDs
- **WHEN** the LLM requests metadata for a list of document IDs
- **THEN** the server returns categorized properties for each item, including message directions, participants, timestamps, coordinates, and hashes, while suppressing internal Lucene engine buffers.

#### Scenario: LLM inspects chat message or photo with GPS
- **WHEN** the requested document is a WhatsApp message or a JPEG with EXIF coordinates
- **THEN** the metadata response includes `Communication:*` properties or `common:geo:locations` without being stripped by filtering.
