package br.com.ipedtools.mcp.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import br.com.ipedtools.mcp.test.TestCaseResolver;
import iped.parsers.python.PythonParser;

/** The IPED embedded Python runtime never starts in the MCP process, whatever Python the machine has. */
class PythonRuntimeGuardTest {

    private final List<String> messages = new ArrayList<>();
    private final Handler capture = new Handler() {
        @Override
        public void publish(LogRecord r) {
            String msg = r.getMessage();
            if (r.getParameters() != null) {
                for (Object p : r.getParameters()) {
                    msg = msg.replaceFirst("\\{\\}", java.util.regex.Matcher.quoteReplacement(String.valueOf(p)));
                }
            }
            messages.add(msg);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    };
    private final Logger serviceLogger = Logger.getLogger(IpedCoreService.class.getName());

    static boolean isCaseAvailable() {
        return TestCaseResolver.isCaseAvailable();
    }

    @BeforeEach
    void attach() {
        serviceLogger.addHandler(capture);
    }

    @AfterEach
    void detach() {
        serviceLogger.removeHandler(capture);
        IpedCoreService.getInstance().closeCase();
    }

    @Test
    void testDisabledLibraryPathDoesNotExist() {
        assertFalse(new File(PythonRuntimeGuard.disabledLibraryPath()).exists());
    }

    @Test
    void testJepIsUnavailableAfterDisable() {
        PythonRuntimeGuard.disable();
        // Without the guard, a mismatched Python in the environment makes this call hang in native code.
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> assertNull(PythonParser.getJep()));
    }

    @Test
    void testDisableTwiceDoesNotThrow() {
        assertDoesNotThrow(() -> {
            PythonRuntimeGuard.disable();
            PythonRuntimeGuard.disable();
        });
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void testCaseOpensTwiceWithEveryStep() {
        IpedCoreService service = IpedCoreService.getInstance();
        service.openCase(TestCaseResolver.getPrimaryCaseDir());
        service.openCase(TestCaseResolver.getPrimaryCaseDir());

        for (String step : new String[] { "configurações do IPED", "tipos de metadados", "índice e marcadores" }) {
            long ends = messages.stream().filter(m -> m.startsWith("[abertura do caso] fim: " + step + " (")).count();
            assertEquals(2, ends, "cada abertura deve concluir a etapa " + step + ": " + messages);
        }
    }
}
