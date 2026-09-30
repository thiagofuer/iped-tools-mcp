# Tasks: Sanitize Test Cases and Dynamic Test Configuration

## 1. Test Configuration & Dynamic Resolution Layer

- [x] 1.1 Implement `TestCaseResolver.java` in `src/test/java/br/com/ipedtools/mcp/test/TestCaseResolver.java` resolving primary and secondary case paths from System property (`iped.test.case.path`), environment variable (`IPED_TEST_CASE_PATH`), and project root `local-test.properties`.
- [x] 1.2 Create `local-test.properties.example` documenting configuration keys and update `.gitignore` to exclude `local-test.properties` and `test-case.properties`.
- [x] 1.3 Refactor `IpedCoreServiceTest.java` and `McpToolsTest.java` to eliminate hardcoded paths and use `TestCaseResolver`, verifying `mvn test` passes with zero failures.

## 2. Script & Tool Schema Sanitization

- [x] 2.1 Update `scripts/test_stdio_mcp.ps1` and `scripts/test_native_exe.ps1` to dynamically read test case paths from `local-test.properties` or environment variables when `-CasePath` is omitted, and handle secondary case fallback cleanly.
- [x] 2.2 Sanitize `OpenCaseTool.java` parameter annotations and docstrings, replacing real investigation paths with fictitious examples (`C:\casos_forenses\operacao_alfa`).
- [x] 2.3 Sanitize all documentation and mock UI diagrams in `README.md`, `docs/DESIGN.md`, and `docs/TASKS.md` to reference neutral fictitious paths (`C:\casos_forenses\caso_operacao_01`).

## 3. Git History Cleansing & Final Verification

- [x] 3.1 Create local `local-test.properties` for developer testing, verify it is ignored by `git status`, and run `mvn clean test` to ensure full test suite passes.
- [x] 3.2 Execute `powershell scripts\test_stdio_mcp.ps1` and `powershell scripts\test_native_exe.ps1` to confirm end-to-end MCP and native .exe test suites succeed.
- [x] 3.3 Perform a recursive repository scan ensuring zero occurrences of real paths remain, and amend/rebase the local commit on `develop` to wipe all trace from Git history.
