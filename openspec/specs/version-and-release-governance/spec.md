# Version and Release Governance Specification

## Purpose
Standardizes application version visibility across all interaction channels (CLI, GUI, MCP protocol), establishes repository hygiene to prevent committing build and packaging binaries, and formalizes GitFlow governance and release documentation.

## Requirements

### Requirement: Command-Line Version Query
The application executable SHALL support `--version` and `-v` flags to display the release version, build date, runtime environment, and IPED Core compatibility without launching the GUI or MCP server.

#### Scenario: User queries version via CLI
- **WHEN** the user invokes `IPED-Tools-MCP.exe --version` or `IPED-Tools-MCP.exe -v`
- **THEN** the application writes the version information to standard output and exits cleanly with code 0 without initializing GUI components or connecting to an IPED case.

#### Scenario: User queries help documentation
- **WHEN** the user invokes `IPED-Tools-MCP.exe --help`
- **THEN** the output lists `--version, -v` alongside existing options.

### Requirement: Graphical Interface Version and About Modal
The Swing GUI (`MainWindow`) SHALL display the active version in the window title bar and provide an interactive "Sobre" (About) modal dialog.

#### Scenario: Examiner inspects window title
- **WHEN** the examiner opens the graphical interface
- **THEN** the window title bar displays `IPED Tools MCP v<version>` alongside the product description.

#### Scenario: Examiner opens About dialog
- **WHEN** the user clicks the "Sobre" button in the GUI
- **THEN** a modal dialog opens showing the application version, official website link (www.ipedtools.com.br), source repository link (GitHub), license, and IPED engine version.

### Requirement: MCP Protocol Server Version Metadata
The MCP server runtime SHALL advertise the authoritative SemVer release version in protocol handshakes and diagnostic tool calls.

#### Scenario: Client LLM performs initialization handshake
- **WHEN** a client initiates STDIO communication with an `initialize` JSON-RPC request
- **THEN** the server returns `serverInfo.name="iped-tools-mcp"` and `serverInfo.version` matching the release version defined in `pom.xml`.

#### Scenario: Client LLM queries server status
- **WHEN** a client calls the `get_server_status` or `check_connection` tool
- **THEN** the returned JSON payload includes a `server_version` property containing the SemVer release version.

### Requirement: Repository Hygiene and Governance Documentation
The project repository SHALL maintain strict `.gitignore` exclusions and comprehensive documentation for GitFlow development, semantic versioning, and binary distribution.

#### Scenario: Contributor builds project in a clean clone
- **WHEN** a developer compiles and packages the project
- **THEN** `.gitignore` ensures that `target/`, `dist/`, `tools/`, IDE metadata, and local `.iped` state files remain untracked.

#### Scenario: Contributor reviews contribution guidelines
- **WHEN** a contributor inspects `CONTRIBUTING.md`
- **THEN** the document details the GitFlow branching model (`main`, `develop`, `feature/*`, `release/*`, `hotfix/*`), Semantic Versioning standards, and pull request procedures.
