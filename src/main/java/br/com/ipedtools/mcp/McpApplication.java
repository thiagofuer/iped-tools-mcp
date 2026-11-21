package br.com.ipedtools.mcp;

import java.awt.EventQueue;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.ipedtools.mcp.diagnostics.CrashDiagnostics;
import br.com.ipedtools.mcp.diagnostics.LogFiles;
import br.com.ipedtools.mcp.diagnostics.StartupDiagnostics;
import br.com.ipedtools.mcp.gui.MainWindow;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.annotations.QuarkusMain;

/**
 * Main application entrypoint for IPED Tools MCP.
 * Implements dual-mode execution:
 * - GUI Mode: Launches the Swing visual configurator when executed without '--stdio'.
 * - STDIO Mode: Launches the headless Quarkus MCP Server when called with '--stdio'. The case is opened by the
 *   open_case tool, in each server process; it is never taken from the command line.
 */
@QuarkusMain
public class McpApplication {

    private static final Logger LOGGER = LoggerFactory.getLogger(McpApplication.class);

    public static void main(String... args) {
        enforceUtf8StandardStreams();

        // Safeguard user.dir when launched by clients like Claude Desktop (Windows Store / MSIX)
        // which default child process working directory to C:\Windows\System32.
        // Quarkus scans ${user.dir}/config for config files, throwing AccessDeniedException on C:\Windows\System32\config.
        String currentDir = System.getProperty("user.dir", "");
        if (currentDir.toLowerCase().contains("system32") || !new File(currentDir).canWrite()) {
            File fallbackDir = new File(System.getProperty("user.home", "."), ".iped-tools-mcp");
            if (!fallbackDir.exists()) {
                fallbackDir.mkdirs();
            }
            System.setProperty("user.dir", fallbackDir.getAbsolutePath());
        }

        LaunchOptions options = parseArguments(args);
        boolean stdioMode = options.stdio();

        // Persistent log file of this mode (~/.iped-tools-mcp/logs), installed before anything else is logged.
        File logFile = LogFiles.install(stdioMode ? LogFiles.Mode.STDIO : LogFiles.Mode.GUI);
        StartupDiagnostics.log(stdioMode ? "STDIO" : "GUI", args, logFile);
        // STDIO logs its shutdown through the Quarkus ShutdownEvent (ShutdownLog); GUI through a JVM hook.
        CrashDiagnostics.installHandlers(!stdioMode);
        if (logFile != null) {
            CrashDiagnostics.collectCrashReports(logFile.getParentFile());
        }

        if (stdioMode) {
            // Mode A: STDIO Headless Server for LLM clients
            LOGGER.info("Iniciando IPED Tools MCP em modo STDIO...");
            // The session starts without a case: the LLM opens one with open_case.
            if (options.legacyCaseArgument()) {
                LOGGER.warn(LEGACY_CASE_WARNING);
            }

            // The file handler installed above stays on the root logger after Quarkus configures logging. The Quarkus
            // file log is not enabled: it would also receive the records Quarkus buffers before its logging setup and
            // replays afterwards, writing the startup lines twice.
            Quarkus.run(args);

        } else {
            // Mode B: Interactive Swing GUI for examiners
            detachConsoleIfPresent();
            LOGGER.info("Iniciando IPED Tools MCP em modo Gráfico (GUI)...");
            EventQueue.invokeLater(() -> {
                try {
                    MainWindow window = new MainWindow();
                    window.setVisible(true);
                } catch (Exception e) {
                    LOGGER.error("Erro fatal ao abrir interface gráfica: {}", e.getMessage(), e);
                }
            });
        }
    }

    /**
     * How the executable was started: {@code --stdio} runs the MCP server; anything else opens the configurator.
     *
     * @param legacyCaseArgument whether an older client configuration passed {@code --case}, which is ignored
     */
    record LaunchOptions(boolean stdio, boolean legacyCaseArgument) {
    }

    /**
     * Reads the command line. {@code --stdio} is the only recognized argument; {@code --case} (with its value) is
     * consumed and reported as legacy. There are no information flags: the native launcher runs the JVM as a child
     * process without a console, so text printed to the terminal would be lost. {@code --version}, {@code --help}
     * and any other argument open the configurator, which shows the version in its title.
     */
    static LaunchOptions parseArguments(String... args) {
        boolean stdio = false;
        boolean legacyCase = false;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("--stdio".equalsIgnoreCase(arg)) {
                stdio = true;
            } else if (arg.startsWith("--case=")) {
                legacyCase = true;
            } else if ("--case".equalsIgnoreCase(arg)) {
                // Older client configurations still pass the case; it is consumed and ignored.
                legacyCase = true;
                if (i + 1 < args.length) {
                    i++;
                }
            }
        }
        return new LaunchOptions(stdio, legacyCase);
    }

    /** Logged when an older client configuration passes {@code --case}, which is no longer supported. */
    static final String LEGACY_CASE_WARNING = "O argumento '--case' não é mais suportado e foi ignorado: "
            + "o servidor inicia sem caso aberto, e o caso é aberto na conversa com a ferramenta open_case. "
            + "Remova '--case' da configuração do cliente de IA.";

    /**
     * Enforces UTF-8 character encoding on standard I/O streams (stdout, stderr, stdin)
     * so JSON-RPC 2.0 messages and forensic metadata containing Portuguese diacritics or Unicode
     * characters are never corrupted by the Windows native codepage (e.g. Cp1252).
     */
    public static void enforceUtf8StandardStreams() {
        System.setProperty("file.encoding", "UTF-8");
        System.setProperty("stdout.encoding", "UTF-8");
        System.setProperty("stderr.encoding", "UTF-8");
        System.setProperty("stdin.encoding", "UTF-8");
        System.setProperty("sun.stdout.encoding", "UTF-8");
        System.setProperty("sun.stderr.encoding", "UTF-8");
        if (System.out != null && !StandardCharsets.UTF_8.equals(System.out.charset())) {
            System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
        }
        if (System.err != null && !StandardCharsets.UTF_8.equals(System.err.charset())) {
            System.setErr(new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8));
        }
    }

    /**
     * Resolves the current executing native executable path (or fallback JAR) for configuration generation.
     */
    public static String getAppExecutableOrJarPath() {
        try {
            File codeSource = new File(McpApplication.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            // 1. Running inside jpackage app-image: <install-dir>/app/<runner>.jar -> <install-dir>/IPED-Tools-MCP.exe
            if (codeSource.getParentFile() != null && "app".equalsIgnoreCase(codeSource.getParentFile().getName())) {
                File exeInRoot = new File(codeSource.getParentFile().getParentFile(), "IPED-Tools-MCP.exe");
                if (exeInRoot.exists()) {
                    return exeInRoot.getAbsolutePath();
                }
            }
            // 2. Running in same directory as IPED-Tools-MCP.exe
            if (codeSource.getParentFile() != null) {
                File exeSameDir = new File(codeSource.getParentFile(), "IPED-Tools-MCP.exe");
                if (exeSameDir.exists()) {
                    return exeSameDir.getAbsolutePath();
                }
            }
            // 3. Check dist/ folder if developing locally
            File distExe = new File("dist/IPED-Tools-MCP/IPED-Tools-MCP.exe");
            if (distExe.exists()) {
                return distExe.getAbsolutePath();
            }
            return codeSource.getAbsolutePath();
        } catch (Exception e) {
            return "IPED-Tools-MCP.exe";
        }
    }

    private static void detachConsoleIfPresent() {
        if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
            try {
                com.sun.jna.platform.win32.Kernel32.INSTANCE.FreeConsole();
            } catch (Throwable ignored) {
            }
        }
    }
}
