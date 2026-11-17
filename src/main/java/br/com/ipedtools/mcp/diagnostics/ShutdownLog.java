package br.com.ipedtools.mcp.diagnostics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkus.runtime.ShutdownEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

/**
 * Logs the shutdown of the STDIO server. Quarkus fires {@link ShutdownEvent} before it tears down logging, so the
 * line always reaches the log file; a JVM shutdown hook would race with that teardown. The GUI mode does not run
 * Quarkus and uses the JVM hook of {@link CrashDiagnostics} instead.
 */
@ApplicationScoped
public class ShutdownLog {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShutdownLog.class);

    void onStop(@Observes ShutdownEvent event) {
        LOGGER.info("Encerrando IPED Tools MCP (pid {})", ProcessHandle.current().pid());
    }
}
