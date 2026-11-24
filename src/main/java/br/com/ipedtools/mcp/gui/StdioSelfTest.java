package br.com.ipedtools.mcp.gui;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Local test of the MCP server as an AI client runs it: starts the same command the configurator generates, in
 * {@code --stdio} mode, as a child process, and talks to it over MCP (JSON-RPC, one message per line, UTF-8). The case,
 * when given, is opened only in the child process. No Swing here, so the test can run from JUnit.
 */
public final class StdioSelfTest {

    /** JVM options of a {@code java -jar} client configuration; the same list the configurator generates. */
    public static final List<String> JAR_JVM_ARGS = List.of(
            "-Dfile.encoding=UTF-8",
            "-Dstdout.encoding=UTF-8",
            "-Dstderr.encoding=UTF-8",
            "-Dstdin.encoding=UTF-8",
            "-Djava.security.manager=allow",
            "--add-opens=java.base/java.lang=ALL-UNNAMED",
            "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
            "--add-opens=java.base/java.math=ALL-UNNAMED",
            "--add-opens=java.base/java.util=ALL-UNNAMED",
            "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED",
            "--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED",
            "--add-opens=java.base/java.net=ALL-UNNAMED",
            "--add-opens=java.base/java.text=ALL-UNNAMED",
            "--add-opens=java.base/java.nio=ALL-UNNAMED",
            "--add-opens=java.base/java.io=ALL-UNNAMED",
            "--add-exports=java.desktop/sun.awt.image=ALL-UNNAMED",
            "--add-exports=java.desktop/com.sun.imageio.plugins.common=ALL-UNNAMED");

    public static final String STEP_START = "Iniciar o servidor";
    public static final String STEP_INITIALIZE = "initialize";
    public static final String STEP_TOOLS = "tools/list";
    public static final String STEP_STATUS = "get_server_status";
    public static final String STEP_OPEN_CASE = "open_case";
    public static final String STEP_SUMMARY = "get_case_summary";

    public enum Status {
        PASSED, FAILED, NOT_RUN
    }

    /** Result of one step: name, status, duration and a short detail for the user. */
    public record StepResult(String name, Status status, long millis, String detail) {
    }

    /** Receives each step as it starts and ends; called from the thread that runs the test. */
    public interface Listener {
        void stepStarted(String name);

        void stepFinished(StepResult result);
    }

    /** Time limits of each step. */
    public record Timeouts(Duration handshake, Duration openCase, Duration summary) {
        public static final Timeouts DEFAULT = new Timeouts(Duration.ofSeconds(30), Duration.ofMinutes(10),
                Duration.ofSeconds(60));
    }

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int STDERR_TAIL = 30;
    private static final Object EOF = new Object();

    private final List<String> command;
    private final File caseDir;
    private final String expectedVersion;
    private final Timeouts timeouts;
    private final Listener listener;

    private final BlockingQueue<Object> stdoutLines = new LinkedBlockingQueue<>();
    private final Deque<String> stderrTail = new ArrayDeque<>();
    private volatile Process process;
    private volatile boolean cancelled;
    private volatile String logFile;
    private Writer stdin;
    private int nextId = 1;

    /**
     * @param command         the command line, see {@link #command(String, String)}
     * @param caseDir         case folder to open in the child process, or null to test only the handshake
     * @param expectedVersion version the server must report in {@code initialize}
     */
    public StdioSelfTest(List<String> command, File caseDir, String expectedVersion, Timeouts timeouts,
            Listener listener) {
        this.command = List.copyOf(command);
        this.caseDir = caseDir;
        this.expectedVersion = expectedVersion;
        this.timeouts = timeouts;
        this.listener = listener;
    }

    /**
     * The command an AI client runs for the given executable or JAR, or null when the application is not packaged
     * (running from compiled classes).
     *
     * @param javaCommand the java launcher for a JAR, usually the one running this process
     */
    public static List<String> command(String execPath, String javaCommand) {
        if (execPath == null) {
            return null;
        }
        String lower = execPath.toLowerCase();
        if (lower.endsWith(".exe")) {
            return List.of(execPath, "--stdio");
        }
        if (lower.endsWith(".jar")) {
            List<String> cmd = new ArrayList<>();
            cmd.add(javaCommand != null ? javaCommand : "java");
            cmd.addAll(JAR_JVM_ARGS);
            cmd.add("-jar");
            cmd.add(execPath);
            cmd.add("--stdio");
            return cmd;
        }
        return null;
    }

    /** The java launcher of this process, or "java". */
    public static String currentJava() {
        return ProcessHandle.current().info().command().orElse("java");
    }

    /** Last lines the server wrote to stderr (its log), for the user when a step fails. */
    public synchronized List<String> stderrTail() {
        return List.copyOf(stderrTail);
    }

    /** Log file reported by the server in {@code get_server_status}, or null. */
    public String logFile() {
        return logFile;
    }

    /** Ends the child process; the running step fails and the remaining ones are reported as not run. */
    public void cancel() {
        cancelled = true;
        destroy();
    }

    /** Runs every step and returns their results; never throws. The child process is always ended. */
    public List<StepResult> run() {
        List<String> steps = new ArrayList<>(List.of(STEP_START, STEP_INITIALIZE, STEP_TOOLS, STEP_STATUS));
        if (caseDir != null) {
            steps.add(STEP_OPEN_CASE);
            steps.add(STEP_SUMMARY);
        }
        List<StepResult> results = new ArrayList<>();
        try {
            for (String step : steps) {
                if (cancelled) {
                    break;
                }
                listener.stepStarted(step);
                long start = System.nanoTime();
                StepResult result;
                try {
                    String detail = runStep(step);
                    result = new StepResult(step, Status.PASSED, elapsed(start), detail);
                } catch (StepFailure e) {
                    result = new StepResult(step, Status.FAILED, elapsed(start),
                            cancelled ? "Teste cancelado pelo usuário." : e.getMessage());
                } catch (Exception e) {
                    result = new StepResult(step, Status.FAILED, elapsed(start),
                            cancelled ? "Teste cancelado pelo usuário." : e.toString());
                }
                results.add(result);
                listener.stepFinished(result);
                if (result.status() != Status.PASSED) {
                    break;
                }
            }
        } finally {
            shutdown();
        }
        for (String step : steps.subList(results.size(), steps.size())) {
            StepResult notRun = new StepResult(step, Status.NOT_RUN, 0, "Não executada.");
            results.add(notRun);
            listener.stepFinished(notRun);
        }
        return results;
    }

    private static long elapsed(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }

    private String runStep(String step) throws Exception {
        return switch (step) {
            case STEP_START -> start();
            case STEP_INITIALIZE -> initialize();
            case STEP_TOOLS -> listTools();
            case STEP_STATUS -> status();
            case STEP_OPEN_CASE -> openCase();
            case STEP_SUMMARY -> summary();
            default -> throw new IllegalArgumentException(step);
        };
    }

    private String start() throws IOException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectInput(ProcessBuilder.Redirect.PIPE);
        Process p = pb.start();
        process = p;
        stdin = new OutputStreamWriter(p.getOutputStream(), StandardCharsets.UTF_8);
        Thread out = new Thread(() -> readStdout(p), "mcp-selftest-stdout");
        out.setDaemon(true);
        out.start();
        Thread err = new Thread(() -> readStderr(p), "mcp-selftest-stderr");
        err.setDaemon(true);
        err.start();
        return String.join(" ", command);
    }

    private void readStdout(Process p) {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                stdoutLines.add(line);
            }
        } catch (IOException ignored) {
            // the process ended
        }
        stdoutLines.add(EOF);
    }

    private void readStderr(Process p) {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getErrorStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                synchronized (this) {
                    stderrTail.addLast(line);
                    if (stderrTail.size() > STDERR_TAIL) {
                        stderrTail.removeFirst();
                    }
                }
            }
        } catch (IOException ignored) {
            // the process ended
        }
    }

    private String initialize() throws Exception {
        Map<String, Object> clientInfo = Map.of("name", "iped-tools-mcp-selftest", "version", expectedVersion);
        JsonNode result = request("initialize", Map.of("protocolVersion", "2024-11-05", "capabilities", Map.of(),
                "clientInfo", clientInfo), timeouts.handshake());
        String name = result.path("serverInfo").path("name").asText("");
        String version = result.path("serverInfo").path("version").asText("");
        if (!version.equals(expectedVersion)) {
            throw new StepFailure("O servidor informou a versão " + version + ", mas esta janela é da versão "
                    + expectedVersion + ": o cliente de IA executaria outra instalação.");
        }
        notification("notifications/initialized");
        return "Servidor " + name + " versão " + version;
    }

    private String listTools() throws Exception {
        JsonNode tools = request("tools/list", Map.of(), timeouts.handshake()).path("tools");
        boolean hasOpenCase = false;
        for (JsonNode tool : tools) {
            hasOpenCase |= STEP_OPEN_CASE.equals(tool.path("name").asText());
        }
        if (tools.isEmpty() || !hasOpenCase) {
            throw new StepFailure("A lista de ferramentas veio sem a ferramenta open_case (" + tools.size()
                    + " ferramentas).");
        }
        return tools.size() + " ferramentas disponíveis";
    }

    private String status() throws Exception {
        JsonNode status = callTool(STEP_STATUS, Map.of(), timeouts.handshake());
        if (status.hasNonNull("log_file")) {
            logFile = status.get("log_file").asText();
        }
        if (status.path("case_open").asBoolean(false)) {
            throw new StepFailure("O servidor iniciou com um caso aberto; uma sessão nova deveria começar sem caso.");
        }
        return "Sessão iniciada sem caso aberto";
    }

    private String openCase() throws Exception {
        JsonNode result = callTool(STEP_OPEN_CASE, Map.of("case_path", caseDir.getAbsolutePath()), timeouts.openCase());
        if (!"success".equals(result.path("status").asText())) {
            throw new StepFailure(result.path("message").asText("O caso não abriu."));
        }
        return "Caso aberto: " + result.path("total_items").asText("?") + " itens";
    }

    private String summary() throws Exception {
        JsonNode s = callTool(STEP_SUMMARY, Map.of(), timeouts.summary());
        if (s.has("error")) {
            throw new StepFailure(s.get("error").asText());
        }
        return String.format("%s itens indexados, %s categorias, %s marcadores",
                s.path("total_indexed_items").asText("?"), s.path("total_categories_count").asText("?"),
                s.path("total_bookmarks_count").asText("?"));
    }

    /** Calls a tool and returns its JSON result, read from the first text content of the answer. */
    private JsonNode callTool(String tool, Map<String, Object> args, Duration timeout) throws Exception {
        JsonNode result = request("tools/call", Map.of("name", tool, "arguments", args), timeout);
        JsonNode content = result.path("content");
        String text = content.isArray() && !content.isEmpty() ? content.get(0).path("text").asText("") : "";
        if (result.path("isError").asBoolean(false)) {
            throw new StepFailure("A ferramenta " + tool + " devolveu erro: " + text);
        }
        try {
            return JSON.readTree(text);
        } catch (IOException e) {
            throw new StepFailure("A resposta da ferramenta " + tool + " não é JSON: " + abbreviate(text));
        }
    }

    private JsonNode request(String method, Map<String, Object> params, Duration timeout) throws Exception {
        int id = nextId++;
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("jsonrpc", "2.0");
        message.put("id", id);
        message.put("method", method);
        message.put("params", params);
        try {
            send(message);
        } catch (IOException e) {
            // The process may have ended right after writing something: what it wrote explains the failure better
            // than the closed pipe, so the output is read before reporting.
            failFromOutput();
            throw new StepFailure("Não foi possível enviar a mensagem ao servidor: " + e.getMessage());
        }

        long deadline = System.nanoTime() + timeout.toNanos();
        while (true) {
            long left = deadline - System.nanoTime();
            Object next = left > 0 ? stdoutLines.poll(left, TimeUnit.NANOSECONDS) : null;
            if (next == null) {
                destroy();
                throw new StepFailure("Sem resposta em " + describe(timeout) + ".");
            }
            if (next == EOF) {
                throw new StepFailure("O servidor terminou antes de responder" + exitCode() + ".");
            }
            String line = (String) next;
            JsonNode node;
            try {
                node = JSON.readTree(line);
            } catch (IOException e) {
                node = null;
            }
            if (node == null || !node.isObject() || !"2.0".equals(node.path("jsonrpc").asText())) {
                throw new StepFailure("O servidor escreveu na saída padrão algo que não é uma mensagem JSON-RPC, o "
                        + "que impede os clientes de IA de funcionar: " + abbreviate(line));
            }
            if (node.path("id").asInt(-1) != id) {
                continue; // notification or another message
            }
            if (node.has("error")) {
                throw new StepFailure("Erro do protocolo: " + node.get("error").path("message").asText());
            }
            return node.path("result");
        }
    }

    /**
     * After a failed send: throws for a non JSON-RPC line already written by the process, or for the process end.
     * Waits briefly for the output reader, since the process may still be closing.
     */
    private void failFromOutput() throws StepFailure, InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (true) {
            long left = deadline - System.nanoTime();
            Object next = left > 0 ? stdoutLines.poll(left, TimeUnit.NANOSECONDS) : null;
            if (next == null) {
                return;
            }
            if (next == EOF) {
                throw new StepFailure("O servidor terminou antes de responder" + exitCode() + ".");
            }
            String line = (String) next;
            if (!isJsonRpc(line)) {
                throw new StepFailure("O servidor escreveu na saída padrão algo que não é uma mensagem JSON-RPC, o "
                        + "que impede os clientes de IA de funcionar: " + abbreviate(line));
            }
        }
    }

    private static boolean isJsonRpc(String line) {
        try {
            JsonNode node = JSON.readTree(line);
            return node != null && node.isObject() && "2.0".equals(node.path("jsonrpc").asText());
        } catch (IOException e) {
            return false;
        }
    }

    private void notification(String method) throws IOException {
        send(Map.of("jsonrpc", "2.0", "method", method));
    }

    private void send(Map<String, Object> message) throws IOException {
        stdin.write(JSON.writeValueAsString(message));
        stdin.write('\n');
        stdin.flush();
    }

    private String exitCode() {
        Process p = process;
        try {
            if (p != null && p.waitFor(2, TimeUnit.SECONDS)) {
                return " (código de saída " + p.exitValue() + ")";
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return "";
    }

    private static String describe(Duration d) {
        return d.toMinutes() >= 1 ? d.toMinutes() + " min" : d.toSeconds() + " s";
    }

    private static String abbreviate(String s) {
        return s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }

    /** Closes stdin so the server ends by itself, then ends the process tree if it is still running. */
    private void shutdown() {
        Process p = process;
        if (p == null) {
            return;
        }
        try {
            if (stdin != null) {
                stdin.close();
            }
        } catch (IOException ignored) {
            // the process already ended
        }
        try {
            if (!p.waitFor(5, TimeUnit.SECONDS)) {
                destroy();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            destroy();
        }
    }

    private void destroy() {
        Process p = process;
        if (p != null) {
            p.descendants().forEach(ProcessHandle::destroyForcibly);
            p.destroyForcibly();
        }
    }

    /** Whether the child process, or any of its descendants, is still running. */
    public boolean isAlive() {
        Process p = process;
        return p != null && (p.isAlive() || p.descendants().anyMatch(ProcessHandle::isAlive));
    }

    private static final class StepFailure extends Exception {
        private static final long serialVersionUID = 1L;

        StepFailure(String message) {
            super(message);
        }
    }
}
