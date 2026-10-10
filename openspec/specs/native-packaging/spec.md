# Native Windows Packaging Specification

## Purpose
Defines the packaging, runtime bundling, and distribution architecture for Windows operating systems. Utilizes JDK 21 `jpackage` to produce self-contained portable folders and an MSI installer with an embedded Liberica JRE 21, eliminating any requirement for pre-installed Java or Python environments on the forensic workstation.

## Requirements

### Requirement: Self-Contained Native Distribution
The build pipeline SHALL produce a standalone Windows application directory and an MSI installer containing an embedded Java 21 runtime and the application uber-jar.

#### Scenario: User runs installer on clean machine
- **WHEN** the user executes `IPED-Tools-MCP-1.0.0.msi` on a Windows machine without Java or Python installed
- **THEN** the application installs into `Program Files`, adds Start Menu and Desktop shortcuts, and runs successfully using its embedded JRE.

### Requirement: JVM Reflection and Security Configuration
The application launcher configuration (`app/IPED-Tools-MCP.cfg`) SHALL inject required JVM flags to ensure full compatibility with IPED engine reflection, regex analysis, legacy security manager calls, and explicit application directory anchoring (`-Duser.dir=$APPDIR`) to prevent accidental probing of restricted parent folders.

#### Scenario: IPED engine invokes reflective tasks
- **WHEN** the embedded JRE executes `IPED-Tools-MCP.exe`
- **THEN** JVM options `--add-opens=java.base/java.math=ALL-UNNAMED`, `--add-opens=java.base/java.lang=ALL-UNNAMED`, and `-Djava.security.manager=allow` are active, preventing `InaccessibleObjectException` during regex or big-decimal operations.

#### Scenario: Native launcher spawned from Windows Store or MSIX package
- **WHEN** `IPED-Tools-MCP.exe` is launched by an MSIX client inheriting a system working directory
- **THEN** the launcher passes `-Duser.dir=$APPDIR` to the JVM runtime, ensuring configuration scanning remains scoped to the application folder.

### Requirement: Dual Distribution Artifacts
The automated build scripts (`package_app.ps1` and `package_msi.ps1`) SHALL generate both a portable folder (`dist/IPED-Tools-MCP/`) and an MSI package (`dist/IPED-Tools-MCP-1.0.0.msi`) compiled targeting the Windows GUI subsystem (`IMAGE_SUBSYSTEM_WINDOWS_GUI`) without allocating a console window upon interactive launch.

#### Scenario: Examiner prefers portable execution
- **WHEN** an examiner copies the portable folder `dist/IPED-Tools-MCP/` to a thumb drive or isolated workstation
- **THEN** double-clicking `IPED-Tools-MCP.exe` starts the application without administrative installation rights and opens the graphical configurator directly without spawning or displaying a terminal/console window.

#### Scenario: LLM client launches server via STDIO
- **WHEN** an LLM client (Claude Desktop, LM Studio, Cursor) executes `IPED-Tools-MCP.exe` with argument `--stdio` and redirected standard I/O pipes
- **THEN** the application executes in headless mode exchanging JSON-RPC 2.0 frames over `stdin` and `stdout` without console window interference.

### Requirement: Automated WiX Toolset Resolution
The MSI packaging script (`package_msi.ps1`) SHALL detect WiX Toolset 3.11 presence, automatically download and extract official binaries when missing in connected environments, and provide descriptive instructions in air-gapped environments.

#### Scenario: WiX Toolset missing in connected workstation
- **WHEN** `package_msi.ps1` runs and WiX binaries (`candle.exe`) are not present in `tools/wix311` or on `PATH`
- **THEN** the script downloads `wix311-binaries.zip` from the official repository, extracts it into `tools/wix311/`, removes the zip, and proceeds with MSI building.

#### Scenario: WiX Toolset missing in offline air-gapped workstation
- **WHEN** `package_msi.ps1` runs in an air-gapped environment without WiX present and download fails
- **THEN** the script halts with an explicit error explaining how to extract `wix311-binaries.zip` into `tools/wix311/` without corrupting build state.

### Requirement: Forensic Distribution Checksums
The packaging pipeline SHALL generate a cryptographic SHA-256 manifest for all distributed binary artifacts in `dist/`.

#### Scenario: Packaging pipeline produces distribution bundles
- **WHEN** `package_app.ps1` or `package_msi.ps1` completes successfully
- **THEN** a `SHA256SUMS.txt` file is generated or updated in `dist/` containing the SHA-256 hashes and filenames of the generated `.zip` and `.msi` packages.

### Requirement: Dynamic Semantic Version Resolution
The packaging scripts SHALL dynamically resolve the project version from Maven configuration instead of hardcoded strings.

#### Scenario: Developer packages a specific release version
- **WHEN** `package_app.ps1` or `package_msi.ps1` is executed without explicit version parameter
- **THEN** the script extracts the version defined in `pom.xml` and formats output artifact filenames accordingly (`IPED-Tools-MCP-<version>-windows-x64-portable.zip` and `IPED-Tools-MCP-<version>.msi`).

### Requirement: Packaging Artifact Sanitization and Maven Clean Idempotence
The packaging script (`package_app.ps1`) SHALL sanitize generated file attributes and purge temporary staging directories upon completion.

#### Scenario: Packaging script completes native image generation
- **WHEN** `package_app.ps1` completes assembling the application bundle into `dist/`
- **THEN** temporary build staging directory `target/dist-build/` is deleted and read-only attributes on packaged binaries in `dist/` are cleared, allowing subsequent `mvn clean` invocations to succeed without file access errors.

### Requirement: Native Application Icon Branding
The packaging pipeline (`package_app.ps1` and `package_msi.ps1`) SHALL embed the dedicated Windows application icon resource (`src/main/resources/images/app.ico`) into the native executable binary and installer shortcuts using the `jpackage` `--icon` configuration option.

#### Scenario: User inspects packaged executable in Windows Explorer
- **WHEN** the user builds the portable application via `package_app.ps1`
- **THEN** `IPED-Tools-MCP.exe` in `dist/IPED-Tools-MCP/` contains the embedded icon resource and displays the forensic branding in File Explorer and the Windows taskbar.

#### Scenario: User installs application via MSI installer
- **WHEN** the user installs the application using `IPED-Tools-MCP-1.0.0.msi`
- **THEN** the Desktop and Start Menu shortcuts, as well as the Windows Add/Remove Programs entry, display the embedded application icon.
