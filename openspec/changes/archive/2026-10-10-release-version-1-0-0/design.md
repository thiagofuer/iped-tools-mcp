# Design

## Context

The repository is currently on the `develop` branch with version `1.0.0-SNAPSHOT` in `pom.xml`. All features, forensic query tools, GUI configurator, native packaging with embedded JRE 21, and test cases are implemented and verified. The remote repository has only `origin/develop`, without a `main` branch.

Per `CONTRIBUTING.md`, `pom.xml` is the single source of truth for project versioning. Stable releases must drop the `-SNAPSHOT` suffix.

## Goals / Non-Goals

**Goals:**
- Update `pom.xml` version from `1.0.0-SNAPSHOT` to `1.0.0`.
- Rebuild production runner JAR and distribution packages (`.zip` and `.msi`) with release name `1.0.0`.
- Verify full test battery (`mvn clean test`, `scripts/test_native_exe.ps1`).
- Establish `main` branch pointing to the release commit.
- Create annotated Git tag `v1.0.0`.
- Ensure distribution binaries and `SHA256SUMS.txt` are prepared for publishing to `mcp.ipedtools.com.br` and GitHub Releases.

**Non-Goals:**
- Introducing new functional features or changing existing MCP tool schemas.
- Automated SFTP/FTP upload script to the web hosting server (binaries are uploaded to the portal via administrative channels).

## Decisions

### 1. Version Promotion in pom.xml
- Update `<version>1.0.0-SNAPSHOT</version>` to `<version>1.0.0</version>` in `pom.xml`.
- Commit on `develop` with message `chore(release): preparar versao de lancamento 1.0.0`.
- *Rationale*: Respects SemVer and the established contribution guidelines where `pom.xml` governs all runtime and packaging version strings.

### 2. Creation and Synchronization of branch `main`
- Create `main` directly from `develop` (`git checkout -b main` or `git branch main develop`).
- Push `main` to `origin` (`git push -u origin main`).
- *Rationale*: Establishes GitFlow convention with `main` representing production-ready code.

### 3. Annotated Release Tag `v1.0.0`
- Tag the release commit on `main` with `git tag -a v1.0.0 -m "Release v1.0.0 - Assistente Forense IPED Tools MCP"`.
- Push tag to `origin` (`git push origin v1.0.0`).
- *Rationale*: Immutable reference points for GitHub Releases, citation, and provenance verification.

### 4. Deterministic Binary Packaging and Cryptographic Manifest
- Execute `mvn clean package -DskipTests` to generate `iped-tools-mcp-1.0.0-runner.jar`.
- Execute `scripts/package_app.ps1` to produce `dist/IPED-Tools-MCP-1.0.0-windows-x64-portable.zip`.
- Execute `scripts/package_msi.ps1` to produce `dist/IPED-Tools-MCP-1.0.0.msi`.
- Generate updated `dist/SHA256SUMS.txt` with release checksums.

## Risks / Trade-offs

- **[Risk]** Maven cache or previous snapshot files remaining in `target/` or `dist/`.  
  → **Mitigation:** Run `mvn clean` and purge `dist/` artifacts before packaging final distribution files.
- **[Risk]** Test scripts checking for `-SNAPSHOT`.  
  → **Mitigation:** Test scripts use flexible regex matching (`v1\.0\.0`), verified during validation.
