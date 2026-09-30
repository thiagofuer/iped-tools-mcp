# Design: Forensic Timeline and Relational Item Analysis

## Context
See `proposal.md` for motivation. IPED records structural relationships between items (`item.getParentId()`, `parent:<id>` index field) and standard cryptographic hashes (MD5 and SHA-256). Furthermore, IPED indexes timestamps across multiple fields (`date`, `created`, `modified`, `accessed`).

## Goals / Non-Goals

**Goals:**
- Provide a consolidated relation discovery endpoint (`get_item_relations`).
- Provide chronological timeline streams sorted ascending by timestamp.
- Provide targeted windowed reconstruction around incident milestones.

**Non-Goals:**
- Graph topology traversal (handled in a dedicated graph change).
- Modifying parent/child relationships in the index.

## Decisions

### Decision 1: Hash Correlation via Lucene Query
- **Choice:** Find duplicate items across multi-source cases using `hash:"<itemHash>" AND NOT id:<itemId>` rather than in-memory iterating.
- **Rationale:** Lucene's term index resolves hash collisions and duplicates across millions of items in single-digit milliseconds.

### Decision 2: Normalized Date Range Filtering and Lucene Sorting
- **Choice:** Parse input ISO-8601 strings into epoch milliseconds or ISO date bounds and sort using `new Sort(new SortField("date", SortField.Type.LONG, false))`.
- **Rationale:** Ensures strict chronological sorting regardless of item category or source.

### Decision 3: Focused Milestone Window Computation
- **Choice:** `get_events_around_time` converts the target timestamp to `Instant`, computes `[target - windowMinutes, target + windowMinutes]`, and delegates to the timeline query engine.
- **Rationale:** Reuses the timeline query implementation while providing a natural investigative interface.

## Risks / Trade-offs

- **[Risk] Large numbers of events in busy timeframes flooding the LLM context**  
  → *Mitigation:* Cap timeline output to 50 events by default, reporting total matches and encouraging the LLM to use category filters when needed.
