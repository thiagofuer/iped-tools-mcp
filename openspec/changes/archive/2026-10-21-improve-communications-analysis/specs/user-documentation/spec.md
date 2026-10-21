# Spec Delta

## MODIFIED Requirements

### Requirement: Usage and Tool Reference Coverage
The wiki SHALL document the graphical configurator and active case synchronization, the `start_case` prompt and investigation workflow, the full catalog of MCP tools exposed by the server including the output contract of the communication tools (direction `unknown`, channel resolution, truncation and coverage diagnostics), Lucene query syntax, and the property dictionary domains.

#### Scenario: Examiner learns which tool answers a question
- **WHEN** an examiner opens the tool catalog page
- **THEN** every MCP tool exposed by the current release is listed with its purpose and at least one example question in pt-BR

#### Scenario: Examiner switches cases
- **WHEN** an examiner reads the configurator page
- **THEN** it explains that the active case is shared between the GUI and the chat via the user profile state and can be changed with `open_case` without reconfiguring the client

#### Scenario: Examiner interprets communication results
- **WHEN** an examiner reads the entries of `get_top_contacts`, `get_communications_graph` and `get_contact_relationship`
- **THEN** the catalog explains that direction is reported only from explicit properties, that device ownership is not assumed, how channels are resolved, and how to read the truncation and coverage fields
