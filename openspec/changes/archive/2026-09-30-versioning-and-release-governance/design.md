# Design: Versioning, Packaging Automation, and Repository Governance

## Context
See `proposal.md` for problem background. IPED Tools MCP is packaged as both a portable folder with embedded Liberica JRE 21 and a Windows MSI installer. Currently, version numbers are hardcoded as `"1.0.0"` in scripts, the workspace has no `.gitignore` (and holds 104 MB of unmanaged WiX 3.11 binaries under `tools/wix311`), and the Java application lacks runtime self-awareness of its version. When publishing to GitHub and distributing via www.ipedtools.com.br, the project needs automated dependency bootstrapping, a single source of truth for versioning, and formal GitFlow documentation.

## Goals / Non-Goals

**Goals:**
- Centralize version definition in `pom.xml` (SemVer `1.0.0`) and propagate dynamically to runtime and packaging scripts.
- Expose the version through CLI flags (`--version`, `-v`), Swing GUI title and About modal, and MCP server protocol handshakes.
- Implement automated on-demand retrieval of WiX 3.11 binaries in `package_msi.ps1` with graceful offline fallback.
- Generate standard `SHA256SUMS.txt` checksum manifests for all generated release artifacts (`.zip` and `.msi`) in `dist/`.
- Establish repository hygiene with `.gitignore` and initialize Git with traditional GitFlow branches (`main`, `develop`) and release tag `v1.0.0`.
- Document project structure and workflows in `README.md`, `CONTRIBUTING.md`, and `CHANGELOG.md`.

**Non-Goals:**
- In-place auto-update mechanics (self-patching running `.exe` files over the network).
- Linux / macOS native packaging (the application is specifically tailored for Windows forensic workstations).
- Setting up remote GitHub Actions CI/CD runners (focus is local reproducible build and repository governance).

## Decisions

### 1. Single Source of Truth & Runtime Version Propagation
- **Decision**: Define the version in `pom.xml` as `<version>1.0.0</version>` and configure Maven resource filtering or build properties to generate `version.properties` containing `version=${project.version}`, `build.date=${build.date}`, and `iped.version=${iped.version}`. Also configure `quarkus.application.version=${project.version}` in `application.properties`.
- **Implementation**:
  - Create a lightweight `VersionInfo` utility class in Java that loads `version.properties` from classpath with fallback to `"1.0.0"`.
  - In `McpApplication.java`, intercept `--version` and `-v` at the very beginning of `main()`, print formatted version info to `System.out`, and immediately return/exit before Quarkus or GUI initialization.
  - In `MainWindow.java`, use `VersionInfo.getVersion()` in the window title and add a "Sobre" (About) dialog accessible via button or menu showing project info, GitHub repository, and www.ipedtools.com.br.
  - In `ServerStatusTool.java`, include `"server_version": VersionInfo.getVersion()`.
- **Alternatives Considered**:
  - Hardcoding a Java constant: Rejected because it inevitably desynchronizes from `pom.xml` and release tags.
  - Reading git tags at runtime via JGit: Rejected because compiled binaries distributed to end-users on www.ipedtools.com.br will not have `.git` directories.

### 2. WiX Toolset 3.11 Auto-Bootstrap in `package_msi.ps1`
- **Decision**: Remove local tracking of `tools/wix311/` from Git. In `scripts/package_msi.ps1`, verify whether `candle.exe` exists in `tools/wix311` or on system `PATH`. If missing:
  1. Attempt to download `wix311-binaries.zip` from official GitHub release URL (`https://github.com/wixtoolset/wix3/releases/download/wix3112rtm/wix311-binaries.zip`).
  2. Unpack into `tools/wix311/` using PowerShell `Expand-Archive`.
  3. Delete the temporary `.zip` archive.
  4. If download fails (e.g. air-gapped machine), output a clear error message instructing the examiner to manually extract `wix311-binaries.zip` into `tools/wix311/`.
- **Alternatives Considered**:
  - Keeping 104 MB of WiX binaries in Git: Rejected because it severely bloats clone times and repository size.
  - Requiring manual MSI installation of WiX Toolset: Rejected because standalone zip extraction provides zero-install portability.

### 3. Packaging Script Version Synchronization & SHA-256 Checksums
- **Decision**: In `package_app.ps1` and `package_msi.ps1`, resolve the version dynamically from `pom.xml` using Maven command `mvn help:evaluate -Dexpression=project.version -q -DforceStdout` (or parsing `<version>` with regex as an ultra-fast fallback). After creating portable ZIP and MSI artifacts, calculate SHA-256 hashes with `Get-FileHash` and write/update `dist/SHA256SUMS.txt`.
- **Artifact Naming Convention**:
  - Portable zip: `IPED-Tools-MCP-<version>-windows-x64-portable.zip`
  - MSI installer: `IPED-Tools-MCP-<version>.msi`
  - Hash file: `dist/SHA256SUMS.txt` formatted as `<hash>  <filename>`.

### 4. GitFlow Governance and Repository Initialization
- **Decision**: Adopt traditional GitFlow:
  - `main`: Production release branch. Only receives merges from `release/*` and `hotfix/*`.
  - `develop`: Ongoing development branch.
  - `feature/*`: Branched from `develop` for specific features, merged back to `develop`.
  - `release/*`: Branched from `develop` for release stabilization and version bumping, merged into both `main` and `develop`.
  - `hotfix/*`: Emergency fixes branched from `main`, merged into `main` and `develop`.
- **Implementation**:
  - Create `.gitignore` before `git init`.
  - Initialize local repo, commit all source files to `main`.
  - Create tag `v1.0.0` on `main`.
  - Create and switch to branch `develop`.

## Risks / Trade-offs

- **[Risk: Air-gapped workstation packaging failure]** → *Mitigation*: The WiX auto-bootstrap explicitly checks for existing `tools/wix311/candle.exe` first. If offline and missing, it provides clear manual extraction instructions without crashing unexpectedly.
- **[Risk: Maven evaluate slowdown during script runs]** → *Mitigation*: The PowerShell scripts implement a fast regex fallback on `pom.xml` that extracts `<version>` in < 10ms without needing to spin up a full Maven JVM.
- **[Risk: Stream pollution on `--version` in STDIO mode]** → *Mitigation*: `--version` terminates immediately after printing to standard output and does not start the Quarkus MCP server STDIO stream handler.
