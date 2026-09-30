# Tasks: Forensic Timeline and Relational Item Analysis

## 1. Service Layer Implementation

- [x] 1.1 Implement `getItemRelations(int itemId)` in `IpedCoreService.java` resolving parent item, sub-items (`parent:<id>`), and hash duplicates (`hash:<hash>`).
- [x] 1.2 Implement `getTimeline(String start, String end, String category, int limit)` in `IpedCoreService.java` executing ascending date-sorted Lucene searches.
- [x] 1.3 Implement `getEventsAroundTime(String target, int windowMinutes, int limit)` in `IpedCoreService.java` converting time window boundaries into sorted range queries.

## 2. MCP Tools Implementation

- [x] 2.1 Create `ItemRelationsTool.java` registering `@Tool get_item_relations` with schema documentation and workflow guidance.
- [x] 2.2 Create `TimelineTool.java` registering `@Tool get_timeline` and `@Tool get_events_around_time`.

## 3. Integration Testing & Native Verification

- [x] 3.1 Verify compilation with `mvn clean test-compile` and test relation discovery on mock items.
- [x] 3.2 Execute automated test script verifying timeline chronological ordering and duplicate detection on real IPED cases.
- [x] 3.3 Rebuild native Windows application via `scripts/package_app.ps1` and verify with `scripts/test_native_exe.ps1`.
