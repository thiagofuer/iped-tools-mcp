# GUI Configurator Specification Delta

## MODIFIED Requirements

### Requirement: 1-Time LM Studio Configuration Assistance
The GUI SHALL provide dedicated copy buttons and instructions to configure local LLM clients (LM Studio, Claude Desktop) with a single-time `--stdio` setup, and SHALL include explicit `UTF-8` JVM stream encoding flags (`-Dfile.encoding=UTF-8`, `-Dstdout.encoding=UTF-8`, `-Dstderr.encoding=UTF-8`, `-Dstdin.encoding=UTF-8`) when generating fallback `java -jar` client configurations.

#### Scenario: User copies configuration parameters
- **WHEN** the user clicks "Copiar Caminho" or "Copiar Argumentos"
- **THEN** the exact executable path or `--stdio` arguments are copied to the system clipboard, accompanied by a visual confirmation notification.

#### Scenario: User toggles fixed case pinning
- **WHEN** the user selects the "Fixar caso no comando (--case)" checkbox
- **THEN** the arguments field dynamically updates to append `--case "<path>"` for examiners requiring fixed configurations.

#### Scenario: User generates configuration while running from executable JAR
- **WHEN** the application is running from a `.jar` artifact and generates the MCP JSON snippet for Claude Desktop, Cursor, or Antigravity
- **THEN** the generated `args` array includes `-Dfile.encoding=UTF-8`, `-Dstdout.encoding=UTF-8`, `-Dstderr.encoding=UTF-8`, and `-Dstdin.encoding=UTF-8` before `-jar`.
