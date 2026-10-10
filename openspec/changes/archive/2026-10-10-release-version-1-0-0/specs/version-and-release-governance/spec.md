# Spec Delta

## ADDED Requirements

### Requirement: Production Release 1.0.0 Tagging and Branch Governance
The project SHALL formalize the first production general-availability release (`1.0.0`) by dropping the `-SNAPSHOT` development suffix in `pom.xml`, establishing the canonical production `main` branch, generating an immutable release Git tag `v1.0.0`, and producing validated distribution binaries.

#### Scenario: User queries production release version
- **WHEN** the user executes `IPED-Tools-MCP.exe --version`
- **THEN** the application outputs `IPED Tools MCP v1.0.0` without any `-SNAPSHOT` suffix and exits with code 0.

#### Scenario: Contributor verifies production Git repository state
- **WHEN** the release transition completes
- **THEN** branch `main` exists containing the identical commits from `develop`, and tag `v1.0.0` points directly to the release commit on `main`.

#### Scenario: Examiner downloads verified production binaries
- **WHEN** the examiner obtains `IPED-Tools-MCP-1.0.0-windows-x64-portable.zip` or `IPED-Tools-MCP-1.0.0.msi`
- **THEN** both distribution files match their respective cryptographic hashes recorded in `SHA256SUMS.txt`.
