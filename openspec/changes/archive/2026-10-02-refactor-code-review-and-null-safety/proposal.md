# Proposal

## Why
Static analysis of the codebase revealed two critical null-safety risks in `IpedCoreService` (`targetFolderId` unboxing in folder navigation and unchecked `toLowerCase()` on contact identities in communication graph analysis) that can crash tool execution during forensic analysis of non-standard evidence. Additionally, several code quality improvements, modern Java 21 idioms (`Math.clamp`, `.getFirst()`, `.toList()`), and test suite cleanups identified during code review need to be addressed to improve maintainability and runtime stability.

## What Changes
- **Fix Null-Safety in Evidence Tree Navigation**: Prevent unboxing `NullPointerException` in `listFolderContents` when an invalid or nonexistent numeric folder ID is queried, returning a structured error response instead.
- **Fix Null-Safety in Communications Graph Analysis**: Add null-checks prior to `.toLowerCase()` calls on contact names in `getCommunicationsGraph` when nodes contain phone numbers or raw identifiers without associated contact names.
- **Modernize Java 21 Math & Range Clamping**: Replace verbose and redundant `Math.max`/`Math.min` patterns with native Java 21 `Math.clamp()` across query limits and pagination routines.
- **Simplify Redundant Array Validations**: Clean up redundant `arr.length > 0` checks preceding enhanced `for` loops across search result handlers.
- **Clean Test Suite & Unused Imports**: Remove obsolete Lucene/IPED imports in `IpedCoreServiceTest`, adopt Java 21 `List.getFirst()`, and upgrade assertions to JUnit 5 `assertInstanceOf`.
- **Clean Redundant Regex Escapes**: Eliminate redundant escapes in regex character classes (`[\\w.-]` and `[0-9+\\-()\\s]`).
- **Retain Public Tool Signatures**: Preserve MCP tool argument definitions (such as `sourceId` and `@ToolArg` defaults) to ensure complete backwards compatibility with MCP clients.

## Capabilities

### New Capabilities
*(None)*

### Modified Capabilities
- `triage-and-evidence-tree`: Add resilience requirement for nonexistent folder IDs or paths in `list_folder_contents` to return structured errors without throwing `NullPointerException`.
- `communications-graph`: Add requirement ensuring anonymous or unnamed contact identities are safely filtered and rendered without throwing `NullPointerException`.

## Impact
- **Affected Code**: `IpedCoreService.java`, `IpedCoreServiceTest.java`, `McpToolsTest.java`, `ForensicPromptsTest.java`.
- **APIs & MCP Tools**: No breaking changes to tool names, arguments, or JSON schemas. Output payloads remain 100% compatible.
- **Dependencies**: No dependency changes. Fully conforms to Java 21 LTS and Quarkus 3.x runtime.
