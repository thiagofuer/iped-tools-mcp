# MCP Server Core Specification Delta

## MODIFIED Requirements

### Requirement: STDIO JSON-RPC Transport
The server SHALL implement JSON-RPC 2.0 communication over standard input (`stdin`) and standard output (`stdout`) conforming to the Model Context Protocol specification, explicitly enforcing `UTF-8` character encoding on `stdin`, `stdout`, and `stderr` prior to transport initialization regardless of the operating system native codepage (such as Windows `Cp1252`).

#### Scenario: Successful protocol handshake and tool listing
- **WHEN** a client LLM initiates the process with `--stdio` and sends a `tools/list` JSON-RPC request
- **THEN** the server responds with a valid JSON-RPC result containing the registered forensic tool signatures without any non-protocol characters on `stdout`.

#### Scenario: Preservation of UTF-8 diacritics and Unicode in tool responses and arguments
- **WHEN** an MCP client exchanges JSON-RPC 2.0 messages containing Portuguese diacritics (e.g., `Relatório.ufdr`, `Perícia`) or Unicode characters over `stdin` and `stdout` on Windows
- **THEN** the server encodes all `stdout` responses and `stderr` diagnostics in `UTF-8` without emitting `Cp1252` byte sequences or Unicode replacement characters (`\uFFFD`).
