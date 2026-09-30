# Design: Test Case Sanitization and Dynamic Configuration

## Context

See `proposal.md` for background and motivation. The project currently has hardcoded file paths pointing to specific disk drives and investigation case names. Tests and scripts fail on any environment lacking those exact directories.

## Goals / Non-Goals

**Goals:**
- Provide a unified, hierarchical test case resolution strategy (`TestCaseResolver`) supporting CLI properties, environment variables, and local properties files.
- Enable graceful test degradation via JUnit 5 `@EnabledIf` so `mvn test` completes with 0 failures on any clean machine or CI worker.
- Provide a versioned template `local-test.properties.example` for contributor onboarding.
- Update PowerShell test scripts (`test_stdio_mcp.ps1`, `test_native_exe.ps1`) to utilize the local properties file when `-CasePath` is not supplied.
- Sanitize public tool descriptions (`OpenCaseTool.java`), README files, design documents, and tasks with fictitious case paths.
- Cleanse the local Git history so no real investigative paths are pushed to the public GitHub repository.

**Non-Goals:**
- Creating automated in-memory Lucene test fixtures for every IPED data type (out of scope; existing real-case tests will run when available, and skip when absent).
- Modifying production case-loading logic in `IpedCoreService.java` or `MainWindow.java`.

## Decisions

### 1. Centralized `TestCaseResolver` in `src/test/java`
A dedicated helper class will encapsulate all test case location logic:
```
Priority 1: System.getProperty("iped.test.case.path")
Priority 2: System.getenv("IPED_TEST_CASE_PATH")
Priority 3: Properties loaded from project root "local-test.properties"
Priority 4: Return null (isCaseAvailable() returns false)
```
Secondary case resolution follows the same pattern using key `iped.test.secondary_case.path`.

*Rationale:* Consolidating path discovery in one test utility eliminates duplication across `IpedCoreServiceTest.java` and `McpToolsTest.java` and allows adding new test classes without hardcoding paths.

*Alternatives Considered:*
- *Quarkus `@TestProfile` or `application-test.properties`:* While Quarkus supports test profiles, resource filtering and packaging can leak file paths if not carefully isolated. A root-level ignored properties file is cleaner and natively accessible to both Java and PowerShell.

### 2. Versioned Template `local-test.properties.example` and Git Exclusion
A template file `local-test.properties.example` will be tracked in git, containing:
```properties
# Primary IPED test case folder (must contain iped/index)
iped.test.case.path=C:/casos_forenses/caso_operacao_01

# Optional secondary case for testing dynamic switching (open_case)
iped.test.secondary_case.path=C:/casos_forenses/caso_operacao_02
```
`.gitignore` will explicitly exclude `local-test.properties` and `test-case.properties`.

*Rationale:* Zero risk of accidental credential or case path leaks while maintaining clear documentation for developers.

### 3. PowerShell Scripts Configuration Loading
`test_stdio_mcp.ps1` and `test_native_exe.ps1` will be updated:
- If `-CasePath` is passed explicitly, use it.
- If `-CasePath` is not passed, search for `local-test.properties` and parse `iped.test.case.path`.
- If neither exists, output a clear, helpful warning guiding the developer to supply `-CasePath` or configure `local-test.properties`.
- For `test_native_exe.ps1` step 6 (dynamic switch), read `iped.test.secondary_case.path`. If unavailable, re-test `open_case` using the primary case directory rather than failing.

### 4. Git History Sanitization Before First Push
Because the repository has not yet been pushed to `https://github.com/thiagofuer/iped-tools-mcp.git`:
- All sanitization changes can be incorporated into the initial commit using `git commit --amend` or a clean rebase.
- The remote repository will be initialized with a completely clean history containing zero references to real case paths.

## Risks / Trade-offs

- **[Risk] Test skipping could mask regressions on environments without test cases.**
  → *Mitigation:* `TestCaseResolver` prints a clear message (`[INFO] Test IPED case not configured or not found. Skipping live case tests.`) so developers and CI logs explicitly show when tests were executed vs skipped.
- **[Risk] Secondary case might not be available on all developer machines.**
  → *Mitigation:* `test_native_exe.ps1` falls back to testing `open_case` with the primary case if the secondary case is omitted, ensuring tests always pass.
