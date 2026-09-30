# MCP Server Core Specification

## Purpose
Provides the foundational Model Context Protocol (MCP) server running over Standard Input/Output (STDIO) using JSON-RPC 2.0. Ensures reliable headless execution and total stream isolation so that underlying forensic and engine logging never corrupts the communication protocol with the LLM client.

## Requirements

### Requirement: STDIO JSON-RPC Transport
The server SHALL implement JSON-RPC 2.0 communication over standard input (`stdin`) and standard output (`stdout`) conforming to the Model Context Protocol specification.

#### Scenario: Successful protocol handshake and tool listing
- **WHEN** a client LLM initiates the process with `--stdio` and sends a `tools/list` JSON-RPC request
- **THEN** the server responds with a valid JSON-RPC result containing the registered forensic tool signatures without any non-protocol characters on `stdout`.

### Requirement: Stream Isolation
The server SHALL guarantee that `stdout` is strictly reserved for JSON-RPC frames, redirecting all logging, framework diagnostics, and third-party engine output to `stderr`.

#### Scenario: Engine warnings or log events occur during search
- **WHEN** Lucene, Tika, Log4j or Quarkus log informational or warning messages during processing
- **THEN** those messages are emitted to `stderr` and never written to `stdout`.

### Requirement: Headless Dispatch Mode
The application entrypoint (`McpApplication`) SHALL detect command-line flags and route execution directly to the headless Quarkus MCP runtime when `--stdio` is present.

#### Scenario: Application started by client LLM
- **WHEN** the process is spawned with `--stdio`
- **THEN** the graphical interface is suppressed and the server enters the blocking STDIO event loop immediately.
