package br.com.ipedtools.mcp.service;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jep.MainInterpreter;

/**
 * Keeps the IPED embedded Python runtime (JEP) from starting in this process.
 * <p>
 * Loading the IPED configuration instantiates the Python processing tasks, which start JEP. Without {@code iped.root}
 * JEP looks for its native library in the machine environment ({@code PATH}, {@code PYTHONHOME}, {@code PYTHONPATH},
 * {@code %APPDATA%\Python}) and may pair the library of one Python version with another one, which hangs the native
 * interpreter start while the case-opening lock is held. The MCP only reads cases and needs no Python, so the library
 * path is pinned to a file that does not exist: JEP then fails at once with {@link UnsatisfiedLinkError}, which IPED
 * handles by disabling its Python tasks and parsers.
 */
public final class PythonRuntimeGuard {

    private static final Logger LOGGER = LoggerFactory.getLogger(PythonRuntimeGuard.class);
    private static final AtomicBoolean DONE = new AtomicBoolean();

    private PythonRuntimeGuard() {
    }

    /** Path given to JEP as its native library; it never exists. */
    static String disabledLibraryPath() {
        return new File(new File(System.getProperty("java.io.tmpdir", "."), "iped-tools-mcp-python-disabled"),
                "jep.dll").getAbsolutePath();
    }

    /** Disables the embedded Python runtime. Only the first call has an effect; never throws. */
    public static void disable() {
        if (!DONE.compareAndSet(false, true)) {
            return;
        }
        try {
            MainInterpreter.setJepLibraryPath(disabledLibraryPath());
            LOGGER.info("Python embutido do IPED desativado: o MCP não executa tarefas de processamento. "
                    + "Mensagens 'JEP not found' do IPED a seguir são esperadas.");
        } catch (IllegalStateException e) {
            LOGGER.warn("Não foi possível desativar o Python embutido do IPED: o interpretador já foi iniciado ({})",
                    e.getMessage());
        } catch (Throwable t) {
            LOGGER.warn("Não foi possível desativar o Python embutido do IPED: {}", t.toString());
        }
    }
}
