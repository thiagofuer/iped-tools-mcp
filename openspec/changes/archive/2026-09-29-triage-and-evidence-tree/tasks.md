# Tasks: Evidence Tree Navigation and Triage Flagging

## 1. Service Layer Implementation

- [x] 1.1 Implement `listFolderContents(String folderPath, boolean recursive, int limit)` in `IpedCoreService.java` querying directory paths and subfolders.
- [x] 1.2 Implement `setItemChecked(int itemId, boolean checked)` in `IpedCoreService.java` updating and persisting check state.

## 2. MCP Tools Implementation

- [x] 2.1 Create `EvidenceTreeTool.java` registering `@Tool list_folder_contents`.
- [x] 2.2 Create `TriageTool.java` registering `@Tool set_item_checked`.

## 3. Integration Testing & Native Verification

- [x] 3.1 Verify compilation with `mvn clean test-compile` and test folder listing logic.
- [x] 3.2 Execute automated test script verifying folder tree traversal and checkmark persistence against sample IPED cases.
- [x] 3.3 Rebuild native Windows application via `scripts/package_app.ps1` and verify with `scripts/test_native_exe.ps1`.

