# Spec Delta: native-packaging

## ADDED Requirements

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
