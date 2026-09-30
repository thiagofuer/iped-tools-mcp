# Case State Synchronization Specification

## Purpose
Provides persistent state sharing and live bidirectional synchronization between the graphical configurator (Swing GUI) and the headless MCP server instance running within the LLM client (e.g. LM Studio, Claude Desktop). Ensures that case switches made in either interface are immediately recognized across the system without manual reconfiguration.

## Requirements

### Requirement: Active Case State Persistence
The system SHALL persist the absolute directory path of the active IPED case in a dedicated user-level state file (`~/.iped-tools-mcp/active_case.txt`).

#### Scenario: User selects a case in the GUI or via open_case
- **WHEN** an IPED case is loaded in the GUI or via the `open_case` MCP tool
- **THEN** the system creates or updates `~/.iped-tools-mcp/active_case.txt` with the absolute path using UTF-8 encoding.

### Requirement: Automatic Session Restoration
The application SHALL read `~/.iped-tools-mcp/active_case.txt` upon startup in both GUI and headless STDIO modes, restoring the previous investigation session automatically.

#### Scenario: User launches the application
- **WHEN** the application starts without explicit command-line arguments
- **THEN** it checks for `active_case.txt`, pre-populates the case directory field, and loads index statistics asynchronously.

#### Scenario: Client LLM launches server with generic arguments
- **WHEN** the server is spawned with `--stdio` but without `--case`
- **THEN** it reads `active_case.txt` and initializes `IpedCoreService` with the persisted case path.

### Requirement: GUI-to-STDIO Live Synchronization
The headless MCP server SHALL check for changes in `active_case.txt` before executing queries, reloading the case automatically if a new case was selected in the GUI.

#### Scenario: User switches cases in the GUI while LM Studio is connected
- **WHEN** the user selects a new case in the GUI and submits a prompt in the LLM chat
- **THEN** the MCP server detects the updated timestamp of `active_case.txt`, closes the previous case, loads the new case, and executes the query against the new index.

### Requirement: Chat-to-GUI Live Synchronization
The GUI window SHALL run a periodic background timer (every 1.5 seconds) to detect if the active case path changed on disk due to an LLM `open_case` call.

#### Scenario: LLM switches cases via chat tool call
- **WHEN** the LLM executes `open_case` and writes a new path to `active_case.txt`
- **THEN** the GUI detects the path change, updates the text input, and refreshes the summary metrics on screen without requiring an application restart.
