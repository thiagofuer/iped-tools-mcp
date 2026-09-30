# Design: Sync Legacy Documentation and Specs

## Context

The IPED Tools MCP codebase already contains 27 active forensic `@Tool` methods, robust in-process Lucene reading, GUI configurator with state synchronization, and packaged Windows binaries. However, requirements and architectural decisions were split across legacy markdown documents in `docs/` and the formal OpenSpec specifications.

See `proposal.md` for motivation.

## Goals / Non-Goals

**Goals:**
- Formally align OpenSpec capabilities with the codebase by specifying `list_sources`, `check_connection`, read-only evidence immutability, air-gapped offline execution, and forensic prompt engineering rules.
- Consolidate product vision, operational architecture, user personas, and tool catalogs into `README.md` to provide a single, rich reference for users and developers.
- Cleanly remove the legacy `docs/` folder (`PRD.md`, `DESIGN.md`, `TASKS.md`, `docs/features/`) to eliminate documentation drift and technical debt.
- Record the explicit architectural decision to discard pending features `export_evidence_items` and `set_item_comment`.

**Non-Goals:**
- No code modification of Java `@Tool` implementations: the existing Java code already complies with the requirements being codified.
- No implementation of discarded features: `export_evidence_items` and `set_item_comment` are explicitly excluded from the project scope.
- No changes to existing build, packaging, or test workflows.

## Decisions

### Decision 1: Consolidate Architecture and Product Guide into `README.md`
- **Choice**: Merge the architectural diagrams, target personas, operational workflows, and the complete 27-tool catalog into `README.md`.
- **Alternatives Considered**: Keeping a separate `docs/` folder or setting up an external GitHub Wiki.
- **Rationale**: An all-in-one root `README.md` is immediately visible on GitHub, easy to maintain in sync with releases, and prevents the divergence that previously occurred between `docs/` and `openspec/`. Git history preserves all previous legacy documents.

### Decision 2: Discard `export_evidence_items` and `set_item_comment`
- **Choice**: Drop both features without implementation.
- **Alternatives Considered**: Implementing them in `TriageTool.java`.
- **Rationale**: IPED Desktop already handles evidence file export (ZIP/folder) and comprehensive official report generation natively. The MCP server's primary value is cognitive interrogation, search, and triage tagging (`set_item_checked` and bookmarks). Keeping raw file export out of MCP minimizes disk I/O vulnerabilities and keeps the MCP server strictly read-only with respect to evidentiary files.

### Decision 3: Dedicated `forensic-prompt-engineering` Capability
- **Choice**: Introduce a centralized capability specification for prompt engineering.
- **Alternatives Considered**: Duplicating prompt rules inside each tool's individual specification.
- **Rationale**: The 5 prompt engineering principles (workflow guidance, negative constraints, value catalogs, syntax examples, trigger questions) apply universally across all forensic tools. Defining them in a dedicated capability creates a clear contract for current and future tool development.

## Risks / Trade-offs

- **[Risk] Deletion of `docs/` removes legacy links or references**:
  - *Mitigation*: All vital design sections, Mermaid architecture diagrams, persona descriptions, and tool listings are directly integrated into `README.md`. Commit history in Git ensures historical references remain retrievable.
- **[Risk] User or external tool expects `export_evidence_items`**:
  - *Mitigation*: Clearly document in `README.md` and the proposal that evidence extraction is intentionally delegated to IPED Desktop to maintain forensic custody boundaries.
