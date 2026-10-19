# Native Windows Packaging Specification Delta

## MODIFIED Requirements

### Requirement: JVM Reflection and Security Configuration
The application launcher configuration (`app/IPED-Tools-MCP.cfg`) SHALL inject required JVM flags to ensure full compatibility with IPED engine reflection, regex analysis, legacy security manager calls, explicit `UTF-8` standard stream encoding (`-Dfile.encoding=UTF-8`, `-Dstdout.encoding=UTF-8`, `-Dstderr.encoding=UTF-8`, `-Dstdin.encoding=UTF-8`), and explicit application directory anchoring (`-Duser.dir=$APPDIR`) to prevent accidental probing of restricted parent folders.

#### Scenario: IPED engine invokes reflective tasks
- **WHEN** the embedded JRE executes `IPED-Tools-MCP.exe`
- **THEN** JVM options `--add-opens=java.base/java.math=ALL-UNNAMED`, `--add-opens=java.base/java.lang=ALL-UNNAMED`, and `-Djava.security.manager=allow` are active, preventing `InaccessibleObjectException` during regex or big-decimal operations.

#### Scenario: Native launcher spawned from Windows Store or MSIX package
- **WHEN** `IPED-Tools-MCP.exe` is launched by an MSIX client inheriting a system working directory
- **THEN** the launcher passes `-Duser.dir=$APPDIR` to the JVM runtime, ensuring configuration scanning remains scoped to the application folder.

#### Scenario: Native launcher initializes standard streams in UTF-8 on Windows
- **WHEN** `IPED-Tools-MCP.exe` is launched on a Windows workstation with a regional codepage such as `Cp1252`
- **THEN** the launcher passes `-Dfile.encoding=UTF-8`, `-Dstdout.encoding=UTF-8`, `-Dstderr.encoding=UTF-8`, and `-Dstdin.encoding=UTF-8` to the embedded JVM so `System.out`, `System.err`, and `System.in` operate in `UTF-8` from bootstrap.
