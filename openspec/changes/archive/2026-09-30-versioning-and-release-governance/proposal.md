# Proposal: Versioning, Distribution, and Repository Governance

## Why
As IPED Tools MCP prepares for open-source distribution on GitHub and binary release downloads on www.ipedtools.com.br, the project requires an authoritative versioning scheme, clean repository hygiene, and automated release packaging. Currently, version numbers are hardcoded across multiple scripts, runtime components (CLI, GUI, MCP status) cannot report their active version, the repository lacks Git tracking and a `.gitignore` (leaving 104 MB of WiX binaries in the workspace), and contributors lack standardized GitFlow workflow documentation. Establishing an automated Semantic Versioning (SemVer) pipeline and Git governance ensures build reproducibility, repository cleanliness, and forensic software chain-of-custody.

## What Changes
- **Repository Hygiene & Git Initialization**:
  - Introduce `.gitignore` excluding build outputs (`target/`, `dist/`), external tools (`tools/`), IDE configurations (`.idea/`, `*.iml`), and local forensic case states (`*.iped`, `active_case.txt`, `*.mv.db`).
  - Initialize local Git repository with traditional GitFlow structure (`main`, `develop`, `feature/*`, `release/*`, `hotfix/*`) and initial release tag `v1.0.0`.
- **Automated Packaging & WiX Toolset Resolution**:
  - Enhance `scripts/package_msi.ps1` to detect the WiX Toolset 3.11 (`candle.exe`). If absent, automatically fetch the official WiX binaries zip from GitHub, extract into `tools/wix311`, and clean up temporary archives, while preserving offline air-gapped fallback instructions.
  - Dynamically extract the project SemVer version from `pom.xml` in packaging scripts (`package_app.ps1`, `package_msi.ps1`, `test_stdio_mcp.ps1`) rather than relying on hardcoded strings.
  - Generate cryptographic SHA-256 integrity checksums (`SHA256SUMS.txt`) alongside portable ZIP and MSI artifacts for forensic verification on www.ipedtools.com.br.
- **Runtime Version Exposure**:
  - Implement CLI `--version` / `-v` flag in `McpApplication.java` displaying application version, build timestamp, Java version, and IPED Core compatibility.
  - Expose application version in the Swing GUI (`MainWindow.java`) title bar and introduce an interactive "Sobre" (About) modal dialog with links to www.ipedtools.com.br, GitHub repository, and license information.
  - Expose version string in MCP `initialize` handshake (`serverInfo.version`) and `get_server_status` / `check_connection` tool responses (`server_version`).
- **Open Source Documentation & Contribution Standards**:
  - Create `README.md` highlighting core features, forensic architecture, compilation steps, and official binary download channels.
  - Create `CONTRIBUTING.md` formalizing the GitFlow branching strategy, commit conventions, and Semantic Versioning rules.
  - Create `CHANGELOG.md` following *Keep a Changelog* standard documenting the v1.0.0 release.

## Capabilities

### New Capabilities
- `version-and-release-governance`: Standardizes runtime version exposure (CLI `--version`, GUI title and About dialog, MCP server metadata), repository hygiene (`.gitignore`, GitFlow structure), and project documentation (`README.md`, `CONTRIBUTING.md`, `CHANGELOG.md`).

### Modified Capabilities
- `native-packaging`: Adds automated on-demand retrieval of WiX Toolset 3.11 binaries, dynamic SemVer resolution from Maven build definitions, and automatic generation of SHA-256 forensic checksum manifests for all generated distribution packages.

## Impact
- **Packaging Scripts**: `scripts/package_app.ps1`, `scripts/package_msi.ps1`, and `scripts/test_stdio_mcp.ps1` updated for dynamic versioning and WiX bootstrapping.
- **Application Core**: `McpApplication.java`, `MainWindow.java`, and `ServerStatusTool.java` updated to read and display version metadata.
- **Build & Dependencies**: `pom.xml` version set to release `1.0.0` with Maven resource filtering or properties generation for runtime version lookup.
- **Repository Root**: Addition of `.gitignore`, `README.md`, `CONTRIBUTING.md`, and `CHANGELOG.md`.
