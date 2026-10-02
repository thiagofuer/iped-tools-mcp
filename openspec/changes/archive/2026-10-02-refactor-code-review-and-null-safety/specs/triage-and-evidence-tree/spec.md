# Spec Delta

## MODIFIED Requirements

### Requirement: Hierarchical Directory Traversal
The server SHALL expose `list_folder_contents` to browse files and subfolders within a specific directory path of the evidence tree.

#### Scenario: Examiner lists non-recursive folder contents
- **WHEN** the LLM calls `list_folder_contents` with `folder_path="C:/Users/Target/Downloads"` and `recursive=false`
- **THEN** the server returns immediate child files and direct subdirectories without recursing deeper.

#### Scenario: Examiner lists folder contents recursively
- **WHEN** the LLM calls `list_folder_contents` with `recursive=true`
- **THEN** the server returns all items located within that directory tree up to the specified limit.

#### Scenario: Nonexistent or invalid numeric folder path handled gracefully
- **WHEN** the LLM calls `list_folder_contents` with a non-existent folder path or unknown numeric ID
- **THEN** the server returns a structured error object indicating directory not found without throwing NullPointerException.
