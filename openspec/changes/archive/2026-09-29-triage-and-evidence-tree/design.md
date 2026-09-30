# Design: Evidence Tree Navigation and Triage Flagging

## Context
See `proposal.md` for motivation. IPED indexes file paths in standard field `path`. Evidence selection is tracked in `IPEDSource` via internal bitsets and saved to `checked.iped` or session databases.

## Goals / Non-Goals

**Goals:**
- Provide intuitive folder browsing matching the IPED Desktop evidence tree.
- Provide programmatic toggle of IPED checkmarks (`setChecked`) with persistent storage.

**Non-Goals:**
- In-place modification of files inside the original evidentiary image.
- Physical extraction or exporting of raw files to external host directories (scope excluded/deferred).

## Decisions

### Decision 1: Lucene Path Prefixing with Subdirectory Aggregation
- **Choice:** Query `path:"<folderPath>/*"` for immediate children and extract distinct direct subfolders on the fly.
- **Rationale:** Avoids maintaining a duplicate in-memory tree while leveraging Lucene's fast term index.

### Decision 2: Direct State Persistence in `IPEDSource`
- **Choice:** Call `ipedSource.setChecked(itemId, checked)` and invoke state persistence immediately.
- **Rationale:** Ensures that checkmarks set by the LLM are instantly visible if the examiner opens the case in IPED Desktop.

## Risks / Trade-offs

- **[Risk] Deep directory trees with thousands of entries**  
  → *Mitigation:* Cap returned child items to a configurable limit (default 100) and return explicit counts of total files and direct subdirectories.
