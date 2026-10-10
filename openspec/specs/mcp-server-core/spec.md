# MCP Server Core Specification

## Purpose
Provides the foundational Model Context Protocol (MCP) server running over Standard Input/Output (STDIO) using JSON-RPC 2.0. Ensures reliable headless execution and total stream isolation so that underlying forensic and engine logging never corrupts the communication protocol with the LLM client.

## Requirements

### Requirement: STDIO JSON-RPC Transport
The server SHALL implement JSON-RPC 2.0 communication over standard input (`stdin`) and standard output (`stdout`) conforming to the Model Context Protocol specification.

#### Scenario: Successful protocol handshake and tool listing
- **WHEN** a client LLM initiates the process with `--stdio` and sends a `tools/list` JSON-RPC request
- **THEN** the server responds with a valid JSON-RPC result containing the registered forensic tool signatures without any non-protocol characters on `stdout`.

### Requirement: Stream Isolation
The server SHALL guarantee that `stdout` is strictly reserved for JSON-RPC frames, redirecting all logging, framework diagnostics, and third-party engine output to `stderr`.

#### Scenario: Engine warnings or log events occur during search
- **WHEN** Lucene, Tika, Log4j or Quarkus log informational or warning messages during processing
- **THEN** those messages are emitted to `stderr` and never written to `stdout`.

### Requirement: Headless Dispatch Mode
The application entrypoint (`McpApplication`) SHALL detect command-line flags and route execution directly to the headless Quarkus MCP runtime when `--stdio` is present, safeguarding working directory resolution against restricted system paths (such as `System32`) and performing case index synchronization asynchronously to ensure JSON-RPC handshake readiness in under 1 second.

#### Scenario: Application started by client LLM
- **WHEN** the process is spawned with `--stdio`
- **THEN** the graphical interface is suppressed and the server enters the blocking STDIO event loop immediately.

#### Scenario: Subprocess inherits restricted working directory
- **WHEN** the process is spawned by a Windows store or MSIX client with `user.dir` pointing to `C:\Windows\System32` or a non-writable directory
- **THEN** the server overrides `user.dir` to a safe user directory before Quarkus configuration initialization, preventing `AccessDeniedException` on `System32\config`.

#### Scenario: Pre-synchronizing active case index
- **WHEN** the server starts in `--stdio` mode without explicit `--case` while an active case is registered
- **THEN** case index preloading runs in a background daemon thread so that initial protocol handshake (`initialize`, `tools/list`) completes without delay or timeout.

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
