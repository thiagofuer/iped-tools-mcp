# Proposal: Evidence Tree Navigation and Triage Flagging

## Why
Navigating forensic evidence often requires structured file system exploration (e.g. browsing through user profile directories, application data folders, and system artifacts). Currently, the LLM must construct fragile regex or substring queries on the `path` field to discover folder contents.

Furthermore, forensic analysis culminates in producing evidence deliverables: the examiner and AI assistant need to flag relevant items for the final forensic report (marking items as *checked* in IPED).

## What Changes
- **Hierarchical Directory Browsing (`list_folder_contents`)**: Explore files and subdirectories located within a specific directory path with optional recursive expansion.
- **Triage Flagging (`set_item_checked`)**: Mark or unmark items as verified/relevant in IPED's persistent state, mirroring the checkmark column in IPED Desktop.

## Capabilities

### New Capabilities
- `triage-and-evidence-tree`: Provides hierarchical evidence tree browsing and item triage flagging operations.

### Modified Capabilities
None.

## Impact
- **Service Layer**: Implements directory path querying and `ipedSource.setChecked()` state persistence in `IpedCoreService.java`.
- **MCP Tools**: Introduces `EvidenceTreeTool.java` and `TriageTool.java`.
- **Integrity**: Original case evidence remains strictly read-only; only the examiner checkmark triage state is saved to the case metadata.
