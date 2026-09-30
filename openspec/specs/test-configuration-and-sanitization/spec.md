# Test Configuration and Path Sanitization Specification

## Purpose

Provides a secure, portable mechanism for configuring test IPED cases across development and CI/CD environments while ensuring no real evidence paths or case names are exposed in code, tool schemas, or documentation.

## Requirements

### Requirement: Dynamic Test Case Resolution
The test harness SHALL dynamically resolve target IPED case folder paths across multiple configuration tiers with the following precedence:
1. Java Virtual Machine system property `-Diped.test.case.path=<path>`
2. Environment variable `IPED_TEST_CASE_PATH`
3. Local configuration file `local-test.properties` (or `test-case.properties`) located in the project root
4. Null / Not configured

#### Scenario: Resolve case path via system property
- **WHEN** the test suite executes with `-Diped.test.case.path="C:\cases\test_case"`
- **THEN** the test case resolver returns `"C:\cases\test_case"` regardless of environment variables or properties file

#### Scenario: Resolve case path via environment variable
- **WHEN** no JVM system property is supplied and `IPED_TEST_CASE_PATH` is set in the environment
- **THEN** the test case resolver returns the path specified in `IPED_TEST_CASE_PATH`

#### Scenario: Resolve case path via local properties file
- **WHEN** neither system property nor environment variable is provided and `local-test.properties` contains `iped.test.case.path`
- **THEN** the test case resolver parses and returns the path specified in `local-test.properties`

### Requirement: Graceful Test Degradation on Missing Evidence
Unit tests that interact directly with an indexed IPED case SHALL evaluate case existence via a standardized predicate (`isCaseAvailable()`) and SHALL be skipped cleanly if no valid IPED case index is found on the filesystem.

#### Scenario: Clean machine or CI execution without evidence
- **WHEN** `mvn test` is invoked in an environment where no IPED test case is configured or accessible on disk
- **THEN** case-dependent test methods are skipped without assertions failing and the Maven build succeeds with 0 failures

#### Scenario: Developer machine with configured evidence
- **WHEN** `mvn test` is invoked with a valid, accessible IPED test case directory containing `iped/index`
- **THEN** all case-dependent test methods execute against the configured index and validate functional correctness

### Requirement: Fictitious Path Standardization Across Public Interfaces
All MCP tool descriptions, CLI help text, user-facing documentation, and architectural designs SHALL use fictitious, sanitized paths (e.g. `C:\casos_forenses\caso_operacao_01`) and SHALL NOT contain real case folder names, personal disk drive letters, or private investigator identifiers.

#### Scenario: Inspect MCP tool schema for open_case
- **WHEN** an MCP client queries `tools/list` or inspects `OpenCaseTool` annotations
- **THEN** the tool parameter description displays only generic fictitious case path examples

#### Scenario: Read project documentation and guides
- **WHEN** a contributor or user consults `README.md`, `DESIGN.md`, or `TASKS.md`
- **THEN** all command invocations and mock UI diagrams reference neutral fictitious directory paths

### Requirement: Exclusion of Local Test Configuration from Version Control
Local properties files containing developer-specific file system paths SHALL be tracked by `.gitignore` to prevent confidential investigative paths from entering the repository history.

#### Scenario: Create local test configuration file
- **WHEN** a developer creates `local-test.properties` or `test-case.properties` in the project root
- **THEN** `git status` reports the file as untracked/ignored and it is not staged for commit
