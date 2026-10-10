# Proposal

## Why

The IPED Tools MCP system has reached full architectural maturity with complete MCP protocol compliance, standalone forensic GUI configurator, dual Windows distribution packaging (portable ZIP and WiX MSI installer with embedded Liberica JRE 21), high-DPI visual branding, and 100% passing tests. The project is now ready for its first official production general-availability release (`v1.0.0`). Transitioning from `-SNAPSHOT` to `1.0.0` establishes the stable baseline for distribution to forensic examiners, peritos judiciais, and assistant technical experts via the official portal `mcp.ipedtools.com.br`.

## What Changes

- Transition project version from `1.0.0-SNAPSHOT` to `1.0.0` in `pom.xml` (the project's single source of truth for versioning).
- Update `scripts/test_native_exe.ps1` and test assertions to validate the clean production version string `1.0.0` (without `-SNAPSHOT`).
- Create and initialize the canonical production branch `main` branched directly from `develop`.
- Generate official production distribution binaries:
  - `dist/IPED-Tools-MCP-1.0.0-windows-x64-portable.zip`
  - `dist/IPED-Tools-MCP-1.0.0.msi`
  - `dist/SHA256SUMS.txt` (SHA-256 cryptographic forensic manifest)
- Create the official release Git tag `v1.0.0` on the `main` branch.
- Prepare push procedures for `main`, `develop`, and `v1.0.0` tag to GitHub remote (`origin`), making binaries ready for upload to the official portal `mcp.ipedtools.com.br`.

## Capabilities

### New Capabilities
<!-- None -->

### Modified Capabilities
- `version-and-release-governance`: Adds formal requirements and verification scenarios for production release graduation (`1.0.0`), canonical `main` branch establishment, Git tag immutability, and distribution binary readiness.

## Impact

- **Build & Versioning**: `pom.xml` version updated to `1.0.0`. Built runner JAR will be `iped-tools-mcp-1.0.0-runner.jar`.
- **Runtime Metadata**: CLI `--version`, GUI title bar and About dialog, and MCP `serverInfo.version` will advertise `1.0.0`.
- **Git Repository**: Initial creation of branch `main`, creation of Git tag `v1.0.0`.
- **Distribution**: Output artifacts in `dist/` will carry clean `1.0.0` release filenames ready for public hosting.
