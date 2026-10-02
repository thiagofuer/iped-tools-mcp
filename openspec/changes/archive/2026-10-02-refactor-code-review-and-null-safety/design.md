# Design

## Context
See `proposal.md` for motivation. Static code inspections identified edge-case null dereferences in core services and areas where Java 21 language features can streamline code and improve readability.

## Goals / Non-Goals

**Goals:**
- Eliminate `NullPointerException` risks when navigating non-existent evidence folders or traversing communications involving contacts without display names.
- Modernize mathematical bounds-checking using Java 21 `Math.clamp()`.
- Modernize test assertions and collections access using JUnit 5 `assertInstanceOf()` and `List.getFirst()`.
- Clean up unused imports, redundant array checks, and regex escapes.

**Non-Goals:**
- Changing MCP tool signatures, JSON-RPC schemas, or parameter names (e.g. keeping `source_id` intact for client compatibility).
- Altering core forensic search logic or Lucene query evaluation.

## Decisions

### 1. Unified Folder Resolution Error Handling
- **Approach**: In `listFolderContents`, if an examiner passes a numeric ID that does not map to a valid Lucene document (`candLuceneId < 0`), or if `targetFolderId` remains `null`, short-circuit immediately and return the standard structured error map (`"error": "Diretório não encontrado..."`).
- **Rationale**: Currently, numeric path lookup only sets `targetFolderId` if found, but if not found, execution falls through to the unboxing `int tFolderId = targetFolderId;`. A centralized check for `targetFolderId == null` prevents unboxing errors.
- *Alternatives considered*: Throwing `IllegalArgumentException` was rejected because the MCP tool standard across this service is to return structured diagnostic JSON to the LLM.

### 2. Null-Safe Focal Matching in Communications Graph
- **Approach**: Introduce a null-safe helper or inline condition:
  ```java
  private static boolean matchesFocal(ContactIdentity ci, String focalClean) {
      if (ci == null || focalClean == null) return false;
      boolean idMatches = ci.id != null && ci.id.toLowerCase().contains(focalClean);
      boolean nameMatches = ci.name != null && ci.name.toLowerCase().contains(focalClean);
      return idMatches || nameMatches;
  }
  ```
- **Rationale**: In UFED/forensic extraction data, SMS, call logs, or unindexed messengers often record only a phone number or raw handle with no resolved address book name (`ci.name == null`). Directly calling `ci.name.toLowerCase()` causes `NullPointerException`.

### 3. Java 21 `Math.clamp` and Simplification of Bounds
- **Approach**:
  - Replace `Math.max(1, Math.min(limit, 100))` with `Math.clamp(limit, 1, 100)`.
  - For methods with defaults (e.g., `limit <= 0 ? 50 : limit`), use `Math.clamp(limit <= 0 ? 50 : limit, 1, 200)` and remove redundant redundant outer wrappers.
- **Rationale**: Eliminates dead branches identified by static analysis while leveraging native Java 21 language enhancements.

### 4. Stream and Test Suite Modernization
- **Approach**:
  - Replace `Collectors.toList()` with `.toList()` where unmodifiable lists are acceptable.
  - In tests, replace `list.get(0)` with `list.getFirst()`.
  - Replace `assertTrue(obj instanceof Type)` with `assertInstanceOf(Type.class, obj)`.
  - Remove unused imports (`Arrays`, `Document`, `IPEDSearcher`, `SearchResult`) in `IpedCoreServiceTest.java`.
  - Make inner class fields (`ContactStats.id`, `ContactStats.isGroup`, `EdgeAggregator.source`, `EdgeAggregator.target`) `final`.

### 5. MCP Tool Contract Preservation
- **Approach**: Keep `@ToolArg` annotations and parameters like `sourceId` intact on tool classes (`DeviceOwnerTool`, `DocumentMetadataTool`, `AiDetectionTool`, `PropertyDictionaryTool`).
- **Rationale**: Quarkus MCP uses these annotations to generate the JSON-RPC tool schemas. Removing parameters would alter the tool's external API. Suppress unused parameter warnings on `sourceId` if needed.

## Risks / Trade-offs

- **[Risk] Unmodifiable List from `Stream.toList()`** → *Mitigation*: Ensure the stream results in `getTopContacts` and `getCommunicationsGraph` are only used for iteration and JSON serialization, which is already the case.
- **[Risk] Test suite regressions during refactoring** → *Mitigation*: Run all 52 unit and integration tests via `mvn test` after changes to verify zero regressions.
