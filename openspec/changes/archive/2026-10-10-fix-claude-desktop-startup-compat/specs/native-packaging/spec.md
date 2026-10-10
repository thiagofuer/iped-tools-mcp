# Spec Delta

## MODIFIED Requirements

### Requirement: JVM Reflection and Security Configuration
The application launcher configuration (`app/IPED-Tools-MCP.cfg`) SHALL inject required JVM flags to ensure full compatibility with IPED engine reflection, regex analysis, legacy security manager calls, and explicit application directory anchoring (`-Duser.dir=$APPDIR`) to prevent accidental probing of restricted parent folders.

#### Scenario: IPED engine invokes reflective tasks
- **WHEN** the embedded JRE executes `IPED-Tools-MCP.exe`
- **THEN** JVM options `--add-opens=java.base/java.math=ALL-UNNAMED`, `--add-opens=java.base/java.lang=ALL-UNNAMED`, and `-Djava.security.manager=allow` are active, preventing `InaccessibleObjectException` during regex or big-decimal operations.

#### Scenario: Native launcher spawned from Windows Store or MSIX package
- **WHEN** `IPED-Tools-MCP.exe` is launched by an MSIX client inheriting a system working directory
- **THEN** the launcher passes `-Duser.dir=$APPDIR` to the JVM runtime, ensuring configuration scanning remains scoped to the application folder.
