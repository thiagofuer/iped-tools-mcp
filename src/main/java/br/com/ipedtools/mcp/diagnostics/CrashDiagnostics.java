package br.com.ipedtools.mcp.diagnostics;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Records failures that end the process: uncaught exceptions, the shutdown itself, and JVM crash reports
 * ({@code hs_err_pid*.log}). A native crash cannot be logged by the process that crashes, so the report the JVM
 * writes (in the working directory or, when it cannot write there, in the system temporary folder) is collected on
 * the next start and copied to the log folder.
 */
public final class CrashDiagnostics {

    private static final Logger LOGGER = LoggerFactory.getLogger(CrashDiagnostics.class);
    static final String COLLECTED_LIST = "collected-crash-reports.txt";
    static final int MAX_REPORTS = 5;

    private CrashDiagnostics() {
    }

    /**
     * Logs uncaught exceptions of any thread and, when {@code shutdownHook} is true (GUI mode), the shutdown of the
     * process. In STDIO mode the shutdown is logged by {@link ShutdownLog}, because a JVM hook races with the
     * Quarkus logging teardown.
     */
    public static void installHandlers(boolean shutdownHook) {
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            LOGGER.error("Exceção não capturada na thread '{}': {}", thread.getName(), error.toString(), error);
            if (previous != null) {
                previous.uncaughtException(thread, error);
            }
        });
        if (!shutdownHook) {
            return;
        }
        long pid = ProcessHandle.current().pid();
        Runtime.getRuntime().addShutdownHook(new Thread(
                () -> LOGGER.info("Encerrando IPED Tools MCP (pid {})", pid), "iped-tools-mcp-shutdown-log"));
    }

    /** Folders where the JVM may have written crash reports of earlier runs. */
    public static List<File> defaultSearchDirs() {
        Set<String> paths = new LinkedHashSet<>();
        paths.add(System.getProperty("java.io.tmpdir", ""));
        paths.add(System.getenv().getOrDefault("TEMP", ""));
        paths.add(System.getenv().getOrDefault("TMP", ""));
        paths.add(System.getProperty("user.dir", ""));
        paths.add(new File("").getAbsolutePath()); // process working directory
        List<File> dirs = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String p : paths) {
            if (p == null || p.isBlank()) {
                continue;
            }
            File dir = new File(p);
            String key;
            try {
                key = dir.getCanonicalPath().toLowerCase();
            } catch (Exception e) {
                key = dir.getAbsolutePath().toLowerCase();
            }
            if (dir.isDirectory() && seen.add(key)) {
                dirs.add(dir);
            }
        }
        return dirs;
    }

    /** Collects new crash reports into the log folder and logs them. Never throws. */
    public static List<File> collectCrashReports(File logDir) {
        try {
            List<File> copied = collectCrashReports(defaultSearchDirs(), logDir, MAX_REPORTS);
            for (File f : copied) {
                LOGGER.warn("Relatório de queda da JVM de uma execução anterior encontrado e copiado para {} "
                        + "(data: {}). Envie-o ao suporte junto com o log.", f.getAbsolutePath(),
                        java.time.Instant.ofEpochMilli(f.lastModified()));
            }
            return copied;
        } catch (Exception e) {
            LOGGER.warn("Falha ao procurar relatórios de queda da JVM: {}", e.toString());
            return List.of();
        }
    }

    /**
     * Copies to {@code logDir} the most recent {@code hs_err_pid*.log} files of {@code searchDirs} that were not
     * collected before (at most {@code max}), and remembers them in {@link #COLLECTED_LIST}.
     */
    static List<File> collectCrashReports(List<File> searchDirs, File logDir, int max) throws Exception {
        if (logDir == null) {
            return List.of();
        }
        Files.createDirectories(logDir.toPath());
        File listFile = new File(logDir, COLLECTED_LIST);
        Set<String> collected = new HashSet<>(
                listFile.isFile() ? Files.readAllLines(listFile.toPath(), StandardCharsets.UTF_8) : List.of());

        List<File> candidates = new ArrayList<>();
        for (File dir : searchDirs) {
            if (dir == null || dir.getAbsoluteFile().equals(logDir.getAbsoluteFile())) {
                continue;
            }
            File[] found = dir.listFiles((d, n) -> n.startsWith("hs_err_pid") && n.endsWith(".log"));
            if (found != null) {
                candidates.addAll(Arrays.asList(found));
            }
        }
        candidates.sort(Comparator.comparingLong(File::lastModified).reversed());

        List<File> copied = new ArrayList<>();
        for (File report : candidates) {
            if (copied.size() >= max) {
                break;
            }
            String key = report.getAbsolutePath() + "|" + report.lastModified();
            if (collected.contains(key)) {
                continue;
            }
            File target = new File(logDir, report.getName());
            Files.copy(report.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.COPY_ATTRIBUTES);
            Files.writeString(listFile.toPath(), key + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            collected.add(key);
            copied.add(target);
        }
        return copied;
    }
}
