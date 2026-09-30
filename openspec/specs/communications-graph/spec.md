# Communications Graph Specification

## Purpose
Provides network topology analysis and communication volume ranking between contacts and suspects across messaging, calling, and email evidence.

## Requirements

### Requirement: Top Contact Ranking
The server SHALL expose `get_top_contacts` returning the most active communicators in the case ranked by total interaction count.

#### Scenario: Examiner inquires who the suspect communicated with most
- **WHEN** the LLM calls `get_top_contacts` with an optional limit
- **THEN** the server aggregates calls and messages and returns a list of contacts with names, numbers/identifiers, and total communication volume.

### Requirement: Communications Network Graph
The server SHALL expose `get_communications_graph` returning nodes and weighted edges representing interactions between individuals.

#### Scenario: LLM maps the communication network around a target
- **WHEN** the LLM calls `get_communications_graph` with a `focal_contact` and `min_interactions` threshold
- **THEN** the server returns a graph structure containing nodes (contact identifiers, names) and edges (source, target, channel, message count, date range).

#### Scenario: Examiner maps general case network
- **WHEN** the LLM calls `get_communications_graph` without a focal contact
- **THEN** the server returns the primary communication clusters across the case up to the edge limit.
