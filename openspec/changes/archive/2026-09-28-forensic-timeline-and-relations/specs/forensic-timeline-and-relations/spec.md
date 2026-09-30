# Spec Delta: forensic-timeline-and-relations

## Purpose
Provides forensic relational lineage analysis (parent/child hierarchy and cross-case hash duplicate matching) and chronological event reconstruction to establish evidence context and event sequences.

## ADDED Requirements

### Requirement: Item Relational Lineage and Hash Duplicates
The server SHALL expose `get_item_relations` to retrieve the parent container, child sub-items, and exact hash duplicates for a given item ID across all evidence sources.

#### Scenario: Examiner inspects an extracted file
- **WHEN** the LLM calls `get_item_relations` for an item ID
- **THEN** the server returns the parent item summary, a list of child sub-items, and any other items in the case sharing the exact same MD5 or SHA-256 hash.

#### Scenario: File is a standalone root file with no duplicates
- **WHEN** the item has no parent, sub-items, or duplicates
- **THEN** the server returns empty relation lists with null parent and zero duplicate count without error.

### Requirement: Chronological Timeline Query
The server SHALL expose `get_timeline` returning case events in strict chronological order within a designated ISO-8601 date range.

#### Scenario: Examiner inspects daily activity
- **WHEN** the LLM calls `get_timeline` with `start_date` and `end_date`
- **THEN** the server returns items with timestamps falling in that range, sorted ascending by date, including item category and event type.

### Requirement: Focused Event Reconstruction Around Timestamp
The server SHALL expose `get_events_around_time` returning events immediately preceding and succeeding a target datetime milestone within +/- N minutes.

#### Scenario: Examiner analyzes activity during an incident
- **WHEN** the LLM calls `get_events_around_time` with a target datetime (e.g. `2026-03-15T21:45:00`) and a 30-minute window
- **THEN** the server returns all calls, messages, photos, and file activity recorded between 21:15:00 and 22:15:00 in chronological order.
