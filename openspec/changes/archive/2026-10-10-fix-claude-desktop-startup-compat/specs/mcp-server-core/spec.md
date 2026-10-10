# Spec Delta

## MODIFIED Requirements

### Requirement: Headless Dispatch Mode
The application entrypoint (`McpApplication`) SHALL detect command-line flags and route execution directly to the headless Quarkus MCP runtime when `--stdio` is present, safeguarding working directory resolution against restricted system paths (such as `System32`) and performing case index synchronization asynchronously to ensure JSON-RPC handshake readiness in under 1 second.

#### Scenario: Application started by client LLM
- **WHEN** the process is spawned with `--stdio`
- **THEN** the graphical interface is suppressed and the server enters the blocking STDIO event loop immediately.

#### Scenario: Subprocess inherits restricted working directory
- **WHEN** the process is spawned by a Windows store or MSIX client with `user.dir` pointing to `C:\Windows\System32` or a non-writable directory
- **THEN** the server overrides `user.dir` to a safe user directory before Quarkus configuration initialization, preventing `AccessDeniedException` on `System32\config`.

#### Scenario: Pre-synchronizing active case index
- **WHEN** the server starts in `--stdio` mode without explicit `--case` while an active case is registered
- **THEN** case index preloading runs in a background daemon thread so that initial protocol handshake (`initialize`, `tools/list`) completes without delay or timeout.
