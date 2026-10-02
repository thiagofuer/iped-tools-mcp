# Tasks

## 1. Service Layer Enhancement

- [x] 1.1 Expand `IpedCoreService.getDeviceAndOwnerInfo()` to detect evidence type (`mobile`, `computer`, `generic`) and query computer OS metadata (`RegisteredOwner`, `ComputerName`, `productName`, `installDate`) and user profile directories. Verify compilation with `mvn test-compile`.
- [x] 1.2 Generalize user account parsing from `category:"user accounts"` in `IpedCoreService.java` to capture local OS user accounts without requiring mobile prefixes while filtering default system accounts from `likely_owner_names`. Verify compilation with `mvn test-compile`.
- [x] 1.3 Enhance `IpedCoreService.getDeviceAndOwnerInfo()` with case-insensitive app matching, IPED `Regex:PHONE` / `Regex:EMAIL` / `phone` extraction, and messenger configuration detection (e.g. `com.whatsapp_preferences.xml`).
- [x] 1.4 Implement multi-evidence container resolution (`isRoot:true` / root paths) in `IpedCoreService.java` and return structured `evidences` array with container-specific OS, owner, and account findings.

## 2. Prompt & Tool Documentation Updates

- [x] 2.1 Update `@Tool` documentation on `DeviceOwnerTool.java` to advise LLMs that the tool identifies ownership, operating system, and hardware characteristics for both mobile extractions and computer disk images. Verify compilation with `mvn test-compile`.
- [x] 2.2 Update `ForensicPrompts.java` with active language mirroring directive (instructing LLMs to detect and mirror the examiner's language) and update prompt guidance.

## 3. Testing, Validation & Packaging

- [x] 3.1 Add unit tests in `IpedCoreServiceTest.java` and `McpToolsTest.java` verifying that `getDeviceAndOwnerInfo()` returns the enriched schema with `evidence_type` and handles computer/OS metadata gracefully.
- [x] 3.2 Add unit tests for `Regex:PHONE` extraction, case-insensitive messenger accounts, and multi-evidence `evidences` grouping in `IpedCoreServiceTest.java`.
- [x] 3.3 Add unit tests for language mirroring directive in `ForensicPromptsTest.java`.
- [x] 3.4 Run full project test suite (`mvn test`) to ensure zero regressions across existing tools.
- [x] 3.5 Rebuild and package application (`mvn package` / `scripts/package_app.ps1`) to refresh binaries in `dist/` and runner jar for client environments.
- [x] 3.6 Validate OpenSpec change artifacts using `openspec validate generalize-device-and-owner-info`.
