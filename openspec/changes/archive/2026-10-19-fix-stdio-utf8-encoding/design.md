# Design

## Context

See `proposal.md` for motivation. In Java 21 on Windows, JEP 400 sets `file.encoding=UTF-8`, which governs `Charset.defaultCharset()`. However, the JVM initializes `System.out`, `System.err`, and `System.in` using `stdout.encoding`, `stderr.encoding`, and `stdin.encoding`, which default to `native.encoding` (`Cp1252` on Brazilian/Western Windows).

During Quarkus startup inside `Quarkus.run(args)`:
1. `StdioMcpServerRecorder.initialize()` captures `PrintStream stdout = System.out;` before replacing `System.out` with `OutputStream.nullOutputStream()`.
2. `StdioMcpConnection.send(JsonObject)` invokes `out.println(message.encode())` on that captured `PrintStream`.
3. `StdioMcpMessageHandler.initialize(PrintStream)` reads `System.in` via `new BufferedReader(new InputStreamReader(System.in))`.

If `System.out` still uses `Cp1252` when `StdioMcpServerRecorder.initialize()` runs, every non-ASCII character in JSON-RPC responses is encoded as single-byte `Cp1252` (or `'?'` for characters outside Windows-1252), which UTF-8 MCP clients decode as `\uFFFD`.

## Goals / Non-Goals

**Goals:**
- Guarantee `UTF-8` encoding on `stdout`, `stderr`, and `stdin` across all launch modes (`IPED-Tools-MCP.exe` native binary and `java -jar` execution).
- Ensure automated PowerShell integration tests (`test_stdio_mcp.ps1` and `test_native_exe.ps1`) enforce UTF-8 pipe encoding and validate non-ASCII character fidelity.

**Non-Goals:**
- Replacing orforking `quarkus-mcp-server-stdio` internals.
- Modifying Lucene index storage encodings (already internal UTF-8).

## Decisions

### 1. Early `System.out` and `System.err` Rebinding in `McpApplication.main()`
- **Decision**: At the very beginning of `McpApplication.main(String... args)`, before parsing CLI flags or invoking `Quarkus.run(args)`, set `stdout.encoding`, `stderr.encoding`, `stdin.encoding`, and `file.encoding` system properties to `UTF-8`, and rebind `System.out` and `System.err`:
  ```java
  System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
  System.setErr(new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8));
  ```
- **Rationale**: `StdioMcpServerRecorder.initialize()` runs inside `Quarkus.run(args)` and captures the `PrintStream` reference currently held by `System.out`. Rebinding `System.out` to a `UTF-8` `PrintStream` wrapping `FileDescriptor.out` at the entry of `main()` guarantees that `StdioMcpConnection` receives a `UTF-8` `PrintStream` even if a user invokes `java -jar iped-tools-mcp-runner.jar --stdio` without passing `-Dstdout.encoding=UTF-8` on the command line.
- **Alternatives Considered**: Relying solely on `jpackage` `--java-options`. Rejected as sole mechanism because developers or examiners invoking the Uber-JAR directly with `java -jar` would still suffer `Cp1252` corruption if they omitted JVM flags.

### 2. Native Launcher JVM Options (`package_app.ps1` and `MainWindow.java`)
- **Decision**: Add `-Dfile.encoding=UTF-8`, `-Dstdout.encoding=UTF-8`, `-Dstderr.encoding=UTF-8`, and `-Dstdin.encoding=UTF-8` to `$jvmOptions` in `scripts/package_app.ps1` and to the `.jar` configuration template in `MainWindow.java`.
- **Rationale**: Passing these flags at JVM creation ensures the JVM itself initializes `System.in`, `System.out`, `System.err`, and JBoss LogManager console handlers in `UTF-8` from bootstrap, providing defense-in-depth alongside programmatic stream rebinding.

### 3. Explicit UTF-8 Pipe Encoding in Integration Test Scripts
- **Decision**: Configure `$psi.StandardOutputEncoding = [System.Text.Encoding]::UTF8` and `$psi.StandardInputEncoding = [System.Text.Encoding]::UTF8` on `System.Diagnostics.ProcessStartInfo` in both `scripts/test_stdio_mcp.ps1` and `scripts/test_native_exe.ps1`, and assert that accented strings (e.g., bookmark `"Perícia IA"` and source/document metadata) are returned intact without `\uFFFD` (`[char]0xFFFD`).
- **Rationale**: PowerShell 5.1 / .NET `ProcessStartInfo` defaults redirected `StandardOutput` to the Windows OEM/ANSI codepage, which previously masked the bug during automated tests.

## Risks / Trade-offs

- **[Risk] Windows console attachment (`--version` / `--help`) overriding `System.out`** → `attachConsoleOnWindows()` in `McpApplication` already explicitly creates its `CONOUT$` `PrintStream` with `StandardCharsets.UTF_8` and only runs when `fileType != 1 && fileType != 3` (interactive console, not redirected pipes). Early UTF-8 rebinding on `FileDescriptor.out` complements this when `--version` or `--help` output is piped.
