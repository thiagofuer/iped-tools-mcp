# Spec Delta: Native Windows Packaging

## MODIFIED Requirements

### Requirement: Dual Distribution Artifacts
The automated build scripts (`package_app.ps1` and `package_msi.ps1`) SHALL generate both a portable folder (`dist/IPED-Tools-MCP/`) and an MSI package (`dist/IPED-Tools-MCP-1.0.0.msi`) compiled targeting the Windows GUI subsystem (`IMAGE_SUBSYSTEM_WINDOWS_GUI`) without allocating a console window upon interactive launch.

#### Scenario: Examiner prefers portable execution
- **WHEN** an examiner copies the portable folder `dist/IPED-Tools-MCP/` to a thumb drive or isolated workstation
- **THEN** double-clicking `IPED-Tools-MCP.exe` starts the application without administrative installation rights and opens the graphical configurator directly without spawning or displaying a terminal/console window.

#### Scenario: LLM client launches server via STDIO
- **WHEN** an LLM client (Claude Desktop, LM Studio, Cursor) executes `IPED-Tools-MCP.exe` with argument `--stdio` and redirected standard I/O pipes
- **THEN** the application executes in headless mode exchanging JSON-RPC 2.0 frames over `stdin` and `stdout` without console window interference.

## ADDED Requirements

### Requirement: Packaging Artifact Sanitization and Maven Clean Idempotence
The packaging script (`package_app.ps1`) SHALL sanitize generated file attributes and purge temporary staging directories upon completion.

#### Scenario: Packaging script completes native image generation
- **WHEN** `package_app.ps1` completes assembling the application bundle into `dist/`
- **THEN** temporary build staging directory `target/dist-build/` is deleted and read-only attributes on packaged binaries in `dist/` are cleared, allowing subsequent `mvn clean` invocations to succeed without file access errors.
