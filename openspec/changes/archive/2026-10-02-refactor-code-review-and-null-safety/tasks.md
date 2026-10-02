# Tasks

## 1. Service Layer - Null Safety & Bug Fixes

- [x] 1.1 Fix unboxing `NullPointerException` in `listFolderContents` for non-existent or invalid numeric folder IDs and verify with a unit test querying an invalid ID
- [x] 1.2 Implement null-safe focal contact matching in `getCommunicationsGraph` (`ci.name != null`) and verify with a unit test using contact identities without display names

## 2. Service Layer - Java 21 Modernization & Cleanups

- [x] 2.1 Refactor range bounds to use Java 21 `Math.clamp()` across `executeSearch`, `getDocumentText`, `listFolderContents`, `getTimeline`, `getTopContacts`, and `getCommunicationsGraph` and verify with existing pagination tests
- [x] 2.2 Clean redundant `arr.length > 0` checks preceding enhanced `for` loops across `IpedCoreService.java`
- [x] 2.3 Modernize stream collection with `.toList()`, replace `File.mkdirs()` with `Files.createDirectories()`, and clean redundant regex escapes in `IpedCoreService.java`
- [x] 2.4 Mark inner class fields as `final` (`ContactStats`, `EdgeAggregator`) and remove unused `category` parameter in `extractEventSummary`

## 3. Test Suite & MCP Tools Polish

- [x] 3.1 Remove unused imports and adopt Java 21 `List.getFirst()` in `IpedCoreServiceTest.java`
- [x] 3.2 Update `McpToolsTest.java` and `ForensicPromptsTest.java` with `List.getFirst()` and JUnit 5 `assertInstanceOf`
- [x] 3.3 Ensure tool parameter contracts (`sourceId` in `DeviceOwnerTool` and `DocumentMetadataTool`) remain intact and add `@SuppressWarnings("unused")` to prevent lint warnings

## 4. Automated Validation

- [x] 4.1 Run full Maven Surefire test suite (`mvn clean test`) against sample IPED cases to verify 100% pass rate and zero regressions
