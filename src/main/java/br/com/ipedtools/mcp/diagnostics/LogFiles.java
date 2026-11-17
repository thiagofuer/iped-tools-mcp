package br.com.ipedtools.mcp.diagnostics;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.logging.Handler;

import org.jboss.logmanager.formatters.PatternFormatter;
import org.jboss.logmanager.handlers.SizeRotatingFileHandler;

/**
 * Persistent log file of the application, under {@code ~/.iped-tools-mcp/logs/}, one file per execution mode.
 * Every line carries the process ID, because several clients may run instances at the same time.
 *
 * <p>The file handler is added to the root logger at the very start of {@code main} and stays there after Quarkus
 * configures logging. The Quarkus file log is not used: Quarkus replays to its handlers the records logged before
 * its logging setup, which would write the startup lines twice.
 */
public final class LogFiles {

    /** Line format: date, process ID, level, logger, thread and message. */
    public static final String FORMAT = "%d{yyyy-MM-dd HH:mm:ss.SSS} [pid %i] %-5p [%c{2.}] (%t) %s%e%n";
    public static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    public static final int MAX_BACKUPS = 5;

    public enum Mode {
        STDIO("iped-tools-mcp-stdio.log"), GUI("iped-tools-mcp-gui.log");

        private final String fileName;

        Mode(String fileName) {
            this.fileName = fileName;
        }

        public String fileName() {
            return fileName;
        }
    }

    private static volatile File activeFile;
    private static volatile String disabledReason;
    private static volatile Handler earlyHandler;

    private LogFiles() {
    }

    /** {@code ~/.iped-tools-mcp/logs}. */
    public static File logDir() {
        return new File(new File(System.getProperty("user.home", "."), ".iped-tools-mcp"), "logs");
    }

    /** The log file of this process, or null when file logging is disabled. */
    public static File getActiveFile() {
        return activeFile;
    }

    /** Why file logging is disabled, or null when it is enabled. */
    public static String getDisabledReason() {
        return disabledReason;
    }

    /** Adds the file handler of the given mode to the root logger. Never throws. */
    public static File install(Mode mode) {
        return install(mode, logDir(), MAX_FILE_SIZE, MAX_BACKUPS);
    }

    static File install(Mode mode, File dir, long maxFileSize, int maxBackups) {
        try {
            Files.createDirectories(dir.toPath());
            if (!Files.isWritable(dir.toPath())) {
                throw new java.io.IOException("sem permissão de escrita em " + dir.getAbsolutePath());
            }
            File file = new File(dir, mode.fileName());
            SizeRotatingFileHandler handler = new SizeRotatingFileHandler(file, true, maxFileSize, maxBackups);
            handler.setRotateOnBoot(false);
            handler.setAutoFlush(true);
            // Before Quarkus configures logging the root level is not yet INFO: keep framework DEBUG lines out.
            handler.setLevel(java.util.logging.Level.INFO);
            handler.setEncoding(StandardCharsets.UTF_8.name());
            handler.setFormatter(new PatternFormatter(FORMAT));
            java.util.logging.Logger.getLogger("").addHandler(handler);
            earlyHandler = handler;
            activeFile = file;
            disabledReason = null;
            return file;
        } catch (Exception | LinkageError e) {
            activeFile = null;
            disabledReason = e.getMessage();
            System.err.println("AVISO: log em arquivo desativado (" + e.getMessage()
                    + "). O log continua apenas no stderr.");
            return null;
        }
    }

    /** Removes and closes the file handler, if any (used by tests). */
    static void removeEarlyHandler() {
        Handler h = earlyHandler;
        if (h != null) {
            java.util.logging.Logger.getLogger("").removeHandler(h);
            h.close();
            earlyHandler = null;
        }
    }
}
