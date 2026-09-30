# Tasks: Versioning, Distribution, and Repository Governance

## 1. Version Infrastructure & Application Visibility

- [x] 1.1 Set project version to `1.0.0` in `pom.xml`, configure resource filtering for `version.properties`, and implement `VersionInfo.java` to read version, build timestamp, and IPED Core compatibility.
- [x] 1.2 Implement CLI `--version` and `-v` flags in `McpApplication.java` displaying version metadata and exiting immediately without starting GUI or STDIO server; update `--help` output.
- [x] 1.3 Update `MainWindow.java` to display `v1.0.0` in the window title bar and add an interactive "Sobre" (About) modal dialog with links to www.ipedtools.com.br and GitHub.
- [x] 1.4 Expose `server_version` in `ServerStatusTool.java` (`get_server_status` and `check_connection`) and verify MCP `initialize` handshake advertises `version: "1.0.0"`.

## 2. Packaging Automation & WiX Auto-Bootstrap

- [x] 2.1 Enhance `scripts/package_msi.ps1` to detect WiX 3.11 Toolset, automatically download and extract official binaries into `tools/wix311` if missing in connected mode, and provide descriptive manual instructions in offline mode.
- [x] 2.2 Update `scripts/package_app.ps1` and `scripts/package_msi.ps1` to dynamically extract the SemVer version from `pom.xml` and generate a cryptographic `dist/SHA256SUMS.txt` manifest.
- [x] 2.3 Update `scripts/test_stdio_mcp.ps1` and `scripts/test_native_exe.ps1` to dynamically resolve runner JAR path, verify `--version` output, and validate `server_version` against sample IPED cases.

## 3. Repository Governance & Documentation

- [x] 3.1 Create `.gitignore` excluding `target/`, `dist/`, `tools/`, IDE metadata (`.idea/`, `*.iml`), and local forensic evidence states (`*.iped`, `active_case.txt`, `*.mv.db`).
- [x] 3.2 Create `README.md` detailing project architecture, core capabilities (27 MCP tools), build instructions, and binary distribution via www.ipedtools.com.br.
- [x] 3.3 Create `CONTRIBUTING.md` formalizing traditional GitFlow branching (`main`, `develop`, `feature/*`, `release/*`, `hotfix/*`), Semantic Versioning standards, and pull request workflows.
- [x] 3.4 Create `CHANGELOG.md` following *Keep a Changelog* conventions documenting the v1.0.0 release.
- [x] 3.5 Initialize local Git repository, verify `.gitignore` excludes heavy binaries, commit initial project codebase to `main`, tag `v1.0.0`, and create the `develop` branch.
