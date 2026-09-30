# Tasks: Communications Network Graph and Contact Analytics

## 1. Service Layer Implementation

- [x] 1.1 Implement `getTopContacts(int limit)` in `IpedCoreService.java` aggregating message and call volume by contact identity.
- [x] 1.2 Implement `getCommunicationsGraph(String focalContact, int minInteractions, int limitEdges)` in `IpedCoreService.java` assembling nodes and weighted interaction edges.

## 2. MCP Tools Implementation

- [x] 2.1 Create `CommunicationsGraphTool.java` registering `@Tool get_top_contacts` and `@Tool get_communications_graph` with schema definitions and prompt engineering.

## 3. Integration Testing & Native Verification

- [x] 3.1 Verify compilation with `mvn clean test-compile` and validate graph data structures.
- [x] 3.2 Execute automated test script verifying contact ranking and edge extraction on real IPED case data.
- [x] 3.3 Rebuild native Windows application via `scripts/package_app.ps1` and verify with `scripts/test_native_exe.ps1`.
