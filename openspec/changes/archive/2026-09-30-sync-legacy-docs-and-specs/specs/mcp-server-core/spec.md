# Spec Delta: MCP Server Core

## ADDED Requirements

### Requirement: Read-Only Evidence Chain of Custody
The server SHALL access all raw evidence, Lucene indexes, and parsed SQLite databases strictly in read-only mode to preserve evidentiary integrity and forensic chain of custody. The only write operations permitted on the filesystem SHALL be user-directed bookmarks (`bookmarks.iped`), triage selection state, and the user-level configuration file (`~/.iped-tools-mcp/active_case.txt`).

#### Scenario: Server opens an IPED case index
- **WHEN** an IPED case directory is opened for interrogation
- **THEN** the underlying Lucene `IndexReader` is opened in read-only mode and no modification or temporary write occurs in the raw case evidence directories.

#### Scenario: Examiner commits bookmarks or triage marks
- **WHEN** the LLM invokes `add_to_bookmark` or `set_item_checked`
- **THEN** only the specific case bookmark and triage metadata files are updated, leaving evidence documents and index segments completely unaltered.

### Requirement: Air-Gapped Offline Operation
The server SHALL function entirely offline without opening TCP/UDP network listening sockets or initiating external outbound internet connections, ensuring complete operational privacy in air-gapped forensic laboratories.

#### Scenario: Server runs in an air-gapped environment
- **WHEN** the application is executed in a network-isolated environment without internet or local network access
- **THEN** all MCP tools and GUI functions operate normally using local process pipes and local filesystem storage with zero failed network calls.
