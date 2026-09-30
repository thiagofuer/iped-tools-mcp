# Proposal: Sync Legacy Documentation and Specs

## Why

During initial project inception, architecture and requirements were documented across multiple markdown files in `docs/` (`PRD.md`, `DESIGN.md`, `TASKS.md`, and 9 feature specs in `docs/features/`). Following the migration to OpenSpec, an explore audit identified discrepancies:
1. Real implemented capabilities in the codebase (`list_sources` in `SourcesTool.java` and `check_connection` in `ServerStatusTool.java`) were omitted from `openspec/specs/forensic-query-tools/spec.md`.
2. Critical non-functional requirements governing digital forensics—specifically strict read-only chain of custody and 100% air-gapped/offline execution—are enforced in code and described in legacy docs but lack normative Gherkin requirements in `openspec/specs/mcp-server-core/spec.md`.
3. The 5-pillar prompt engineering standard defined in `PRD.md` (workflow guidance, negative constraints, value catalogs, syntax examples, trigger questions) that prevents LLM hallucinations in forensic investigations is not formalized in OpenSpec.
4. Legacy documentation in `docs/` contains duplicated and divergent content, including discarded features (`export_evidence_items` and `set_item_comment`), creating maintenance debt.

This proposal synchronizes OpenSpec with the real codebase, formalizes the missing forensic constraints and prompt engineering standards, consolidates product architecture and vision into `README.md`, and eliminates the legacy `docs/` folder.

## What Changes

- **Add Evidence Sources Discovery to Forensic Query Tools**: Formalize `list_sources` and `check_connection` in `openspec/specs/forensic-query-tools/spec.md`.
- **Add Chain of Custody and Air-Gapped Operation to MCP Server Core**: Formalize strict read-only evidence access and offline execution without network listeners in `openspec/specs/mcp-server-core/spec.md`.
- **Introduce Forensic Prompt Engineering Standard**: Create a new capability `forensic-prompt-engineering` defining mandatory tool description structures (workflow guidance, negative constraints, query syntax hints) to guide LLMs deterministically.
- **Consolidate High-Level Product Architecture into `README.md`**: Enrich `README.md` with system overview, architecture diagrams, target personas, supported tools inventory, and packaging guidelines previously scattered in `docs/PRD.md` and `docs/DESIGN.md`.
- **Discard Unimplemented Features**: Formally record the decision to discard `export_evidence_items` and `set_item_comment` from `docs/features/` without implementing them.
- **Retire Legacy `docs/` Directory**: Remove the `docs/` directory (`PRD.md`, `DESIGN.md`, `TASKS.md`, `docs/features/`) to establish OpenSpec and `README.md` as the authoritative sources of truth.

## Capabilities

### New Capabilities
- `forensic-prompt-engineering`: Formalizes the mandatory prompt engineering structure for all forensic MCP tools to ensure LLMs avoid hallucinations and understand investigation workflows.

### Modified Capabilities
- `forensic-query-tools`: Adds requirements for evidence sources discovery (`list_sources`) and connectivity health verification (`check_connection`).
- `mcp-server-core`: Adds requirements for strict read-only evidence immutability (chain of custody) and air-gapped offline execution.

## Impact

- **Specs**: Updates `forensic-query-tools` and `mcp-server-core`, adds `forensic-prompt-engineering`.
- **Code & Tools**: Zero breaking changes to existing Java `@Tool` implementations since `list_sources`, `check_connection`, prompt engineering, and read-only Lucene readers are already implemented and tested in code.
- **Repository Documentation**: `README.md` becomes the unified user- and developer-facing guide; `docs/` folder is cleanly removed.
