# Spec Delta

## MODIFIED Requirements

### Requirement: Communications Network Graph
The server SHALL expose `get_communications_graph` returning nodes and weighted edges representing interactions between individuals.

#### Scenario: LLM maps the communication network around a target
- **WHEN** the LLM calls `get_communications_graph` with a `focal_contact` and `min_interactions` threshold
- **THEN** the server returns a graph structure containing nodes (contact identifiers, names) and edges (source, target, channel, message count, date range).

#### Scenario: Examiner maps general case network
- **WHEN** the LLM calls `get_communications_graph` without a focal contact
- **THEN** the server returns the primary communication clusters across the case up to the edge limit.

#### Scenario: Graph generation with anonymous or unnamed contacts
- **WHEN** the LLM calls `get_communications_graph` with a `focal_contact` filter and contacts lack explicit names
- **THEN** the server safely processes contact nodes using their identifier without throwing NullPointerException.
