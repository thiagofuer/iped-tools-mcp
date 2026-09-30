# Triage and Evidence Tree Specification

## Purpose
Provides hierarchical file system navigation and forensic triage item checking capabilities to assist examiners in organizing and preparing official deliverables.

## Requirements

### Requirement: Hierarchical Directory Traversal
The server SHALL expose `list_folder_contents` to browse files and subfolders within a specific directory path of the evidence tree.

#### Scenario: Examiner lists non-recursive folder contents
- **WHEN** the LLM calls `list_folder_contents` with `folder_path="C:/Users/Target/Downloads"` and `recursive=false`
- **THEN** the server returns immediate child files and direct subdirectories without recursing deeper.

#### Scenario: Examiner lists folder contents recursively
- **WHEN** the LLM calls `list_folder_contents` with `recursive=true`
- **THEN** the server returns all items located within that directory tree up to the specified limit.

### Requirement: Forensic Item Triage Flagging
The server SHALL expose `set_item_checked` to mark or unmark an item as selected/relevant in the IPED case state database.

#### Scenario: LLM flags critical evidence for the report
- **WHEN** the LLM calls `set_item_checked` with an item ID and `checked=true`
- **THEN** the server marks the item in `IPEDSource`, persists the check state to disk, and confirms the action.
