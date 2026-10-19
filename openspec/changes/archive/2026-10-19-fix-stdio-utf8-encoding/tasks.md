# Tasks

## 1. MCP Server Core Stream Encoding

- [x] 1.1 Implement `enforceUtf8StandardStreams()` in `src/main/java/br/com/ipedtools/mcp/McpApplication.java` and invoke it at the start of `main(String... args)` before CLI flag handling and `Quarkus.run(args)`, setting `file.encoding`, `stdout.encoding`, `stderr.encoding`, and `stdin.encoding` to `UTF-8` and rebinding `System.out` and `System.err` to `StandardCharsets.UTF_8` via `FileDescriptor.out` and `FileDescriptor.err`.
- [x] 1.2 Add a JUnit test in `src/test/java/br/com/ipedtools/mcp/tools/McpToolsTest.java` verifying that `McpApplication.enforceUtf8StandardStreams()` sets `System.out.charset()` and `System.err.charset()` to `StandardCharsets.UTF_8` and preserves Portuguese diacritics (`Relatório.ufdr`, `Perícia`), and verify `mvn test` passes.

## 2. GUI Configuration Snippet Update

- [x] 2.1 Update `updateGeneratedConfig()` in `src/main/java/br/com/ipedtools/mcp/gui/MainWindow.java` so the fallback `.jar` configuration snippet includes `"-Dfile.encoding=UTF-8"`, `"-Dstdout.encoding=UTF-8"`, `"-Dstderr.encoding=UTF-8"`, and `"-Dstdin.encoding=UTF-8"` in the `args` array, and verify `mvn test-compile` succeeds.

## 3. Packaging & Automated STDIO Validation

- [x] 3.1 Update `$jvmOptions` in `scripts/package_app.ps1` to inject `-Dfile.encoding=UTF-8`, `-Dstdout.encoding=UTF-8`, `-Dstderr.encoding=UTF-8`, and `-Dstdin.encoding=UTF-8` into the `jpackage` launcher configuration (`app/IPED-Tools-MCP.cfg`), and also add these flags to `$jvmArgs` in `scripts/test_stdio_mcp.ps1`.
- [x] 3.2 Update `scripts/test_stdio_mcp.ps1` and `scripts/test_native_exe.ps1` to set `$psi.StandardOutputEncoding = [System.Text.Encoding]::UTF8` and `$psi.StandardInputEncoding = [System.Text.Encoding]::UTF8` on `ProcessStartInfo`, and add assertions that tool outputs (`list_sources`, `add_to_bookmark` with `"Perícia IA"`, `get_document_metadata`) contain no Unicode replacement characters (`[char]0xFFFD`).
- [x] 3.3 Build the Uber-JAR (`mvn package -DskipTests`), rebuild the native Windows package (`powershell -ExecutionPolicy Bypass -File scripts/package_app.ps1`), and run both `scripts/test_stdio_mcp.ps1` and `scripts/test_native_exe.ps1` against the sample IPED case to verify 100% pass rate and UTF-8 character integrity.
