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
The application launcher configuration (`app/IPED-Tools-MCP.cfg`) SHALL inject required JVM flags to ensure full compatibility with IPED engine reflection, regex analysis, and legacy security manager calls.

#### Scenario: IPED engine invokes reflective tasks
- **WHEN** the embedded JRE executes `IPED-Tools-MCP.exe`
- **THEN** JVM options `--add-opens=java.base/java.math=ALL-UNNAMED`, `--add-opens=java.base/java.lang=ALL-UNNAMED`, and `-Djava.security.manager=allow` are active, preventing `InaccessibleObjectException` during regex or big-decimal operations.

### Requirement: Dual Distribution Artifacts
The automated build scripts (`package_app.ps1` and `package_msi.ps1`) SHALL generate both a portable folder (`dist/IPED-Tools-MCP/`) and an MSI package (`dist/IPED-Tools-MCP-1.0.0.msi`).

#### Scenario: Examiner prefers portable execution
- **WHEN** an examiner copies the portable folder `dist/IPED-Tools-MCP/` to a thumb drive or isolated workstation
- **THEN** double-clicking `IPED-Tools-MCP.exe` starts the application without administrative installation rights.

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
