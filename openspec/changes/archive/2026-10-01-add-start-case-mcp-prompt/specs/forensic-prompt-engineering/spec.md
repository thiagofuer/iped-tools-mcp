# Spec Delta

## ADDED Requirements

### Requirement: Standardized start_case MCP Prompt for Investigation Initialization
The MCP server SHALL expose a standardized prompt named `start_case` via the MCP Prompts protocol (`prompts/list` and `prompts/get`), providing the LLM with an authoritative forensic investigator persona, orientation on IPED tool usage, step-by-step investigation methodology, and strict negative constraints prohibiting direct shell, command-line, or out-of-band filesystem access to the case directory.

#### Scenario: MCP client retrieves available prompts
- **WHEN** a client sends a `prompts/list` request
- **THEN** the server returns `start_case` in the list of available prompts with title "Iniciar Investigação Forense" and an explanation of its purpose.

#### Scenario: MCP client requests the start_case prompt without arguments
- **WHEN** a client sends a `prompts/get` request for `start_case` without arguments
- **THEN** the server returns a prompt message instructing the model to act as a digital forensics specialist adhering to ISO/IEC 27037 standards, to begin by calling `get_server_status` and `get_case_summary`, to escape Lucene query syntax, and strictly prohibiting any host operating system shell commands or direct file inspection on the case folder.

#### Scenario: MCP client requests the start_case prompt with a case path or target subject
- **WHEN** a client sends a `prompts/get` request for `start_case` with optional arguments such as `case_path` or `investigation_target`
- **THEN** the server incorporates the provided case path or target subject into the generated prompt instructions, directing the model to focus on the specified target while maintaining all forensic boundaries and negative constraints.
