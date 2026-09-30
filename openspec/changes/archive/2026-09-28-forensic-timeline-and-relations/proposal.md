# Proposal: Forensic Timeline and Relational Item Analysis

## Why
Digital forensic evidence rarely stands alone. Examiners frequently need to understand the provenance of a file (e.g. was this PDF attached to an email or extracted from a WhatsApp backup?), its children (sub-items unpacked from an archive), and whether identical duplicates (by MD5/SHA-256 hash) exist across other computers or phones seized in the same operation.

Additionally, establishing criminal culpability depends heavily on temporal context: what did the suspect do on the device in the 30 minutes before and after a specific incident? Without dedicated timeline and relational tools, the LLM must attempt complex ad-hoc queries, often missing vital connections.

## What Changes
- **Relational Lineage and Hash Duplication (`get_item_relations`)**: Single-call inspection of an item's parent container, child sub-items, and exact hash duplicates across all evidence sources in the case.
- **Chronological Timeline Stream (`get_timeline`)**: Query all evidentiary events (messages, calls, camera photos, web history, file modifications) strictly sorted by date within a specified ISO-8601 interval.
- **Milestone Event Reconstruction (`get_events_around_time`)**: Focused chronological inspection of activity occurring within a configurable window (e.g. +/- 30 minutes) of a pivotal event timestamp.

## Capabilities

### New Capabilities
- `forensic-timeline-and-relations`: Provides relational item lineage, hash cross-matching, and chronological event sequence analysis.

### Modified Capabilities
None.

## Impact
- **Service Layer**: Implements parent/subitem resolution, hash correlation across `IPEDSource`, and Lucene date sorting in `IpedCoreService.java`.
- **MCP Tools**: Introduces `ItemRelationsTool.java` and `TimelineTool.java`.
- **Performance**: Relies on indexed Lucene fields (`parent`, `hash`, `date`, `created`, `modified`), ensuring fast execution.
