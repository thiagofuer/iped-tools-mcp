# Spec Delta: Forensic Query Tools

## ADDED Requirements

### Requirement: Evidence Sources Discovery
The server SHALL expose `list_sources` and connectivity alias `check_connection` to discover active evidence sources, physical storage paths, and connection readiness for the loaded IPED case.

#### Scenario: LLM inquires about active evidence sources
- **WHEN** the LLM calls `list_sources`
- **THEN** the server returns a list of configured sources including each source's unique identifier (e.g. `caso1`) and absolute filesystem path.

#### Scenario: Client checks connection readiness
- **WHEN** the LLM calls `check_connection`
- **THEN** the server returns connection health, active server version, whether a case is loaded, and source descriptors identical to `get_server_status`.

#### Scenario: LLM calls list_sources when no case is loaded
- **WHEN** `list_sources` is called while no IPED case is open
- **THEN** the server returns an error object indicating that no IPED case is currently loaded without crashing or terminating STDIO.
