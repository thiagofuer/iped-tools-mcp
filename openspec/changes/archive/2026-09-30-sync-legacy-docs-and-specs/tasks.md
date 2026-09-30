# Tasks: Sync Legacy Documentation and Specs

## 1. Documentation Consolidation and Architecture Enrichment

- [x] 1.1 Update `README.md` to incorporate the system architecture Mermaid diagrams, dual-mode execution flow, target personas, and the full catalog of 27 forensic MCP tools. Verify by viewing `README.md`.
- [x] 1.2 Document non-functional constraints in `README.md` including air-gapped offline operation, read-only evidence immutability (chain of custody), and the architectural decision to discard `export_evidence_items` and `set_item_comment`. Verify by viewing the updated sections in `README.md`.
- [x] 1.3 Verify that all file links, commands, and markdown formatting in `README.md` render cleanly and accurately reflect the project state.

## 2. Legacy Documentation Deprecation

- [x] 2.1 Remove the legacy `docs/` directory (`docs/PRD.md`, `docs/DESIGN.md`, `docs/TASKS.md`, and `docs/features/`). Verify directory non-existence with `Test-Path docs`.
- [x] 2.2 Verify that `git status` confirms removal of the legacy documentation tree without leaving orphan or untracked files in `docs/`.

## 3. Verification and OpenSpec Validation

- [x] 3.1 Run `mvn test` to verify all unit and integration tests continue to pass with 0 errors.
- [x] 3.2 Run `openspec validate --change sync-legacy-docs-and-specs` to verify complete spec and change integrity.
