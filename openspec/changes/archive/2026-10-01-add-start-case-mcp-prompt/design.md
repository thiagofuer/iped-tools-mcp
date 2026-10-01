# Design

## Context

IPED Tools MCP exposes digital forensic capabilities over the standard Model Context Protocol (MCP) using the Quarkiverse MCP SDK (`quarkus-mcp-server-stdio`). While forensic tools (`@Tool`) contain descriptive annotations, LLMs operating without an overarching system directive frequently struggle with:
1. Understanding the specialized forensic persona and the principles of ISO/IEC 27037 chain of custody.
2. Knowing the ideal phased investigation lifecycle (Server Status → Sources → Discovery → Targeted Search → Progressive Metadata/Text Inspection → Bookmarking).
3. The risk of out-of-band execution: in multi-modal or code-capable agent environments (like Gemini or Claude Code), models may mistakenly attempt to run operating system commands (`dir`, `ls`, `cat`, PowerShell/bash scripts) to list or read files directly from the case folder, which risks index segment corruption and breaks forensic auditability.

Quarkus MCP Server 2.0.1 natively supports MCP Prompts via `@Prompt`, `@PromptArg`, `PromptResponse`, and `PromptMessage`, enabling client LLMs to discover and invoke structured prompts (`prompts/list` and `prompts/get`).

## Goals / Non-Goals

**Goals:**
- Implement a dedicated MCP prompt named `start_case` registered via `@Prompt` in `br.com.ipedtools.mcp.prompts.ForensicPrompts`.
- Encode explicit forensic persona instructions, tool usage recommendations, and query escaping rules.
- Encode strict negative constraints explicitly forbidding direct OS shell commands or direct filesystem manipulation against the case folder.
- Provide optional prompt parameters (`investigation_target` and `case_path`) allowing examiners to tailor prompt guidance.
- Update `MainWindow.java` to guide examiners on how to run `/start_case` in supported MCP clients.
- Verify prompt discovery and message generation via automated unit tests.

**Non-Goals:**
- Implementing OS-level sandboxing or process permission restrictions (the MCP server cannot prevent the client agent environment from running external tools if the client platform allows it; the prompt acts as the system-level behavioral directive to the LLM).
- Replacing individual `@Tool` annotations (the prompt complements existing tool annotations by providing end-to-end procedural workflow).

## Decisions

### Decision 1: Quarkus MCP Prompt Implementation
- **Choice**: Create `br.com.ipedtools.mcp.prompts.ForensicPrompts` annotated with `@ApplicationScoped`, defining the method `@Prompt(name = "start_case", ...)`.
- **Return Type**: `PromptResponse` containing a `PromptMessage.withUserRole(...)` (or structured prompt messages).
- **Rationale**: Quarkiverse MCP automatically scans `@ApplicationScoped` beans for `@Prompt` methods and registers them in the `prompts/list` and `prompts/get` JSON-RPC dispatchers without requiring manual protocol plumbing.
- **Alternatives Considered**:
  - Exposing an ad-hoc `@Tool` named `get_forensic_instructions`: Rejected because MCP has a first-class `prompts` protocol primitive specifically designed for seeding LLM conversations with instructions.

### Decision 2: Prompt Arguments Schema
- **Choice**: Declare two optional parameters via `@PromptArg`:
  - `investigation_target`: The specific inquiry focus (e.g., "fraude em licitação", "mensagens de contato X", "imagens de interesse").
  - `case_path`: An optional file path to the IPED case directory.
- **Rationale**: Examiners often start an investigation with a known hypothesis or case folder. Injecting these into the prompt provides tailored starting suggestions while maintaining default usability when invoked without arguments.

### Decision 3: Prompt Text Structure and Negative Constraints
- **Choice**: The generated prompt text is structured into five distinct, high-impact sections:
  1. **Forensic Persona & ISO/IEC 27037 Integrity**: Instructions to act as a senior digital forensics specialist, prioritizing verifiable evidence and chain of custody.
  2. **STRICT NEGATIVE CONSTRAINTS (Prohibition of Direct Filesystem/OS Commands)**: Explicit warning:
     > "NEVER attempt to run host operating system commands (e.g. `dir`, `ls`, `cat`, PowerShell, bash, cmd) or write external scripts to inspect the case directory or raw files.
     > NEVER attempt to directly open or parse `iped.db`, Lucene index segments, or raw evidence files from the OS.
     > ALL evidence inspection MUST be performed exclusively through the official IPED MCP tools (`search_documents`, `get_document_metadata`, `get_document_text`, etc.). Direct filesystem inspection violates forensic integrity, risks index corruption, and bypasses IPED's forensic normalization."
  3. **Phased Investigation Workflow**: Step-by-step guidance:
     - Phase 1: Verification (`get_server_status`, `get_case_summary`)
     - Phase 2: Evidence Landscape (`list_sources`, `list_categories`)
     - Phase 3: Targeted Discovery & Syntax (`search_documents`, Lucene escaping, `query_ai_detections`, `get_timeline`, `get_communications_graph`)
     - Phase 4: Progressive Inspection (`get_document_metadata` -> `get_document_text` chunks -> `get_item_thumbnail`)
     - Phase 5: Evidentiary Record (`add_to_bookmark`, `set_item_checked`)
  4. **Investigation Target Customization**: If `investigation_target` is supplied, append guidance for hypothesis formulation and targeted keyword/category search.
  5. **Case Path Customization**: If `case_path` is supplied, instruct the model to verify if that case is currently loaded, or call `open_case` if needed.

### Decision 4: GUI Configurator Hints
- **Choice**: Update `MainWindow.java`'s instruction templates to include a prominent tip recommending that examiners type `/start_case` (or select the prompt from the client UI) upon connecting.
- **Rationale**: Examiners configuring LM Studio or Claude Desktop through the GUI will immediately see how to trigger the forensic guidelines in their chat session.

### Decision 5: Test Strategy
- **Choice**: Add `br.com.ipedtools.mcp.prompts.ForensicPromptsTest` using `@QuarkusTest` or standard JUnit 5 to test:
  - Default prompt generation without parameters contains all mandatory sections and negative constraints.
  - Parameterized prompt generation includes the target and case path.
  - Verification that prompt metadata (name, description, arguments) matches the specification.

## Risks / Trade-offs

- **[Risk] Prompt Length vs Context Window**: A detailed forensic prompt consumes context tokens at the start of a conversation.
  - *Mitigation*: Structure the prompt concisely using markdown bullet points and clear headings, keeping the total token count under ~1,000 tokens while retaining all critical negative constraints and tool guidance.
- **[Risk] MCP Clients lacking prompt support**: Some basic MCP clients only implement `tools/*` and ignore `prompts/*`.
  - *Mitigation*: Tool descriptions already retain concise negative constraints and syntax rules as specified in `forensic-prompt-engineering`; `start_case` provides the macro-level system instructions for clients that do support MCP prompts (e.g., Claude Desktop, LM Studio 0.3+, cursor, etc.).

## Migration Plan

No database schema migrations or backward compatibility breaks. The change is strictly additive to the MCP protocol surface and GUI documentation.
