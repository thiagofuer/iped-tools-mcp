# Proposal: Sanitize Test Cases and Dynamic Test Configuration

## Why

The codebase and project documentation previously contained hardcoded local file paths and names of actual forensic test cases. Exposing real investigation paths, personal disk letters, and subject identifiers in a public open-source project presents significant privacy, security, and operational risks. 

Furthermore, hardcoded local paths in Java unit tests (`IpedCoreServiceTest.java`, `McpToolsTest.java`) and PowerShell verification scripts (`test_stdio_mcp.ps1`, `test_native_exe.ps1`) cause test suites to fail on other developers' machines and prevent continuous integration (CI/CD) pipelines from running cleanly.

## What Changes

- **Centralized Test Case Resolver (`TestCaseResolver`):** Implement a dedicated test utility in `src/test` that dynamically resolves target IPED test cases from:
  1. System Property `-Diped.test.case.path`
  2. Environment Variable `IPED_TEST_CASE_PATH`
  3. Local file `local-test.properties` (ignored by Git)
- **Graceful Test Degradation:** Ensure `@EnabledIf("isCaseAvailable")` gracefully skips tests when no local IPED case is present, enabling `mvn test` to pass with zero failures in any clean environment or CI pipeline.
- **Local Test Configuration Template (`local-test.properties.example`):** Provide a versioned template file documenting how contributors configure primary and secondary test cases.
- **Git Protection:** Add `local-test.properties` and `test-case.properties` to `.gitignore`.
- **PowerShell Script Deserialization:** Refactor `scripts/test_stdio_mcp.ps1` and `scripts/test_native_exe.ps1` to read `-CasePath` and secondary cases dynamically from `local-test.properties` or environment variables with clear instructions when missing.
- **Documentation & Tool Schema Sanitization:** Replace all hardcoded real case paths in `OpenCaseTool.java`, `README.md`, `docs/DESIGN.md`, and `docs/TASKS.md` with neutral, fictitious paths (e.g. `C:\casos_forenses\caso_operacao_01`).
- **Git History Sanitization:** Amend the initial local commit on `develop` to eliminate any record of real case paths before pushing the repository to GitHub.

## Capabilities

### New Capabilities
- `test-configuration-and-sanitization`: Dynamic resolution of forensic test cases, graceful test skipping on missing evidence, sanitized tool schema descriptions, and local configuration templates.

### Modified Capabilities
<!-- None: No existing functional requirements are modified. -->

## Impact

- **Test Code (`src/test`):** `IpedCoreServiceTest.java` and `McpToolsTest.java` decouple from hardcoded disk paths and use `TestCaseResolver`.
- **Production Code (`src/main`):** `OpenCaseTool.java` replaces real case mentions with fictitious examples in its `@Tool` description.
- **Scripts (`scripts/`):** `test_stdio_mcp.ps1` and `test_native_exe.ps1` support config files and environment fallback.
- **Documentation (`docs/`, `README.md`):** All path examples become fictitious.
- **Git Repository:** `.gitignore` protects local property files; initial commit sanitized prior to remote push.
