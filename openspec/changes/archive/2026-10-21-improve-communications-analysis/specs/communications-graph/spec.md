# Spec Delta

## MODIFIED Requirements

### Requirement: Top Contact Ranking
The server SHALL expose `get_top_contacts` returning the most active interlocutors in the case ranked by total interaction count, where the interlocutor of an event with explicit direction is its sender when incoming and its recipients when outgoing, with default limit 20 and maximum 200.

#### Scenario: Examiner inquires who the suspect communicated with most
- **WHEN** the LLM calls `get_top_contacts` with an optional limit
- **THEN** the server returns up to the limit contacts with identity, aliases, display name, total interactions, messages count, calls count, incoming, outgoing and unknown-direction counts, interactions by channel and by month, first and last interaction, active days and sample evidence ids

#### Scenario: Events without explicit direction are not attributed
- **WHEN** events have `UNKNOWN` direction
- **THEN** they are reported in an unknown-direction total and group activity section and are not counted as incoming or outgoing for any contact

#### Scenario: Device account is not ranked as interlocutor
- **WHEN** the local side of an event with explicit direction is the sender of an outgoing message or the recipient of an incoming one
- **THEN** that local side is never ranked as a contact of its own conversations

#### Scenario: Channel and period filters
- **WHEN** the LLM provides `channel`, `start_date` or `end_date`
- **THEN** only events matching the filters are ranked and the applied filters are echoed in the response

### Requirement: Communications Network Graph
The server SHALL expose `get_communications_graph` returning typed nodes and weighted directed edges between sender and recipient identities, with local endpoints represented per channel as `local:<channel>` nodes flagged as not identity-resolved, with default 50 edges and maximum 200.

#### Scenario: LLM maps the communication network around a target
- **WHEN** the LLM calls `get_communications_graph` with a `focal_contact` and `min_interactions` threshold
- **THEN** the server returns nodes (identifier, aliases, type, display name, total interactions, focal flag) and edges (source, target, weight, channels, messages count, calls count, first and last interaction, monthly counts) involving the focal contact

#### Scenario: Examiner maps general case network
- **WHEN** the LLM calls `get_communications_graph` without a focal contact
- **THEN** the server returns the highest-weight edges across the case up to the edge limit

#### Scenario: Graph generation with anonymous or unnamed contacts
- **WHEN** the LLM calls `get_communications_graph` with a `focal_contact` filter and contacts lack explicit names
- **THEN** the server safely processes contact nodes using their identifier without throwing NullPointerException

#### Scenario: Local side is not attributed to a person
- **WHEN** an event has explicit direction
- **THEN** its local side is the `local:<channel>` node, which lists the local identifiers observed on that channel and has `identity_resolved` equal to `false`, and no node is labeled as device owner

#### Scenario: Group activity appears as group nodes
- **WHEN** events are group messages
- **THEN** senders are linked to a group-type node carrying the group name and its participant count

#### Scenario: Events lacking both endpoints are accounted for
- **WHEN** events have unknown direction and only one party identified
- **THEN** they do not create edges and are counted in an unlinked-events total

## ADDED Requirements

### Requirement: Contact Relationship Detail
The server SHALL expose `get_contact_relationship` taking two contact references and returning how the two relate: totals, interactions by channel, by direction and by month, first and last interaction, groups in common and sample evidence ids.

#### Scenario: Examiner asks how two people relate
- **WHEN** the LLM calls `get_contact_relationship` with two identifiers or names
- **THEN** the server returns the combined interaction totals, per-channel and per-direction breakdowns, monthly histogram, first and last interaction, shared groups and up to 5 evidence ids

#### Scenario: Reference matches several identities
- **WHEN** a contact reference matches more than one identity
- **THEN** the server returns the candidate identities with their identifiers and names and does not choose one

#### Scenario: No interaction found
- **WHEN** both references resolve to identities that never appear in the same event or group
- **THEN** the server returns zero interactions and an explicit statement that no relationship was found in the indexed properties

#### Scenario: Reference equals a local identifier observed on a channel
- **WHEN** a reference equals an identifier observed on the local side of a channel and the other reference is a counterpart on that channel
- **THEN** events with explicit direction on that channel whose local identity is missing are matched and each is flagged as `endpoint_inferred`

### Requirement: Evidence Traceability
The server SHALL include up to 5 `evidence_ids` per contact and per edge, selected deterministically to cover the earliest and latest interactions, so that each can be retrieved with `get_document_metadata`.

#### Scenario: Edge lists evidence items
- **WHEN** an edge aggregates more than 5 events
- **THEN** it lists exactly 5 item ids including the earliest and the latest event, and reports the total event count

### Requirement: Result Truncation Transparency
Every result of the communication tools SHALL state the total and returned counts, a `truncated` flag and the coverage diagnostics of the event model, so that absent items are not interpreted as absent relationships.

#### Scenario: Edge limit cuts results
- **WHEN** more edges satisfy the filters than the edge limit
- **THEN** the response reports `truncated` equal to `true` with the total number of eligible edges and the returned number

### Requirement: Optional Mermaid Rendering
The server SHALL return a Mermaid flowchart of the returned nodes and edges from `get_communications_graph` only when `include_mermaid` is true.

#### Scenario: Diagram requested
- **WHEN** the LLM calls `get_communications_graph` with `include_mermaid` equal to `true`
- **THEN** the response includes a `mermaid` string whose nodes and edges correspond exactly to the returned nodes and edges

#### Scenario: Diagram not requested
- **WHEN** `include_mermaid` is omitted
- **THEN** the response contains no `mermaid` field
