# Proposal

## Why

When forensic examiners connect an LLM to IPED Tools MCP, the model initially lacks guidance on the forensic persona, available tool semantics, search syntax, and evidentiary boundaries. Crucially, without explicit systemic guidelines, LLMs may hallucinate investigation steps or even attempt out-of-band operating system commands (such as executing `dir`, `ls`, or PowerShell scripts against the case folder) to inspect raw files directly. Such out-of-band filesystem access risks index corruption, breaks forensic chain of custody, and produces unreliable results.

An official MCP Prompt (`start_case`) provides client LLMs with an authoritative initialization prompt that establishes forensic methodology, instructs the model on tool usage, and strictly prohibits out-of-band filesystem inspection.

## What Changes

- Expose a standardized MCP Prompt named `start_case` registered via Quarkus MCP Server annotations (`@Prompt`), accessible to MCP clients via standard `prompts/list` and `prompts/get` protocol methods.
- Embed comprehensive forensic directives within `start_case`:
  - **Forensic Persona & Standards**: Instruct the model to operate as a digital forensics specialist adhering to forensic integrity and chain of custody principles (ISO/IEC 27037).
  - **Strict Negative Constraints (Zero Out-of-Band Filesystem Access)**: Strictly prohibit executing operating system shell commands, directory listings (`dir`, `ls`), or direct file operations on the case directory, mandating that all evidence access be performed exclusively through IPED MCP tools.
  - **Case Initialization Workflow**: Instruct the model to verify server status and active case availability (`get_server_status`, `get_case_summary`) before running item-level searches.
  - **Search & Inspection Methodology**: Provide guidance on Lucene syntax escaping, category filtering, AI detection queries (`query_ai_detections`), and progressive inspection (`search_documents` -> `get_document_metadata` / `get_document_text`).
- Update GUI configurator instructions (`MainWindow.java`) to inform examiners how to trigger the `/start_case` prompt in prompt-capable MCP clients.
- Add automated test coverage to verify that `start_case` is properly discovered and returns the expected prompt content and role messages.

## Capabilities

### New Capabilities
<!-- None -->

### Modified Capabilities
- `forensic-prompt-engineering`: Add requirement for standardized investigation initialization MCP prompt (`start_case`) with strict negative constraints prohibiting direct shell/filesystem operations on the case folder and establishing structured forensic inquiry workflows.

## Impact

- **Affected Code**:
  - `br.com.ipedtools.mcp.prompts.ForensicPrompts`: New prompt provider declaring `@Prompt(name = "start_case", ...)`.
  - `br.com.ipedtools.mcp.gui.MainWindow`: Configuration snippets updated to reference `/start_case`.
  - `br.com.ipedtools.mcp.prompts.ForensicPromptsTest`: Unit tests for prompt generation.
- **APIs**: Exposes `prompts/list` and `prompts/get` in the MCP STDIO protocol.
- **Dependencies**: None (already provided by `quarkus-mcp-server-core`).
