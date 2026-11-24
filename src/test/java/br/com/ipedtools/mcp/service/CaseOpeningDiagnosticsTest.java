package br.com.ipedtools.mcp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import br.com.ipedtools.mcp.test.TestCaseResolver;

/** Case opening logs every step with its duration and records the last failure for get_server_status. */
class CaseOpeningDiagnosticsTest {

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
    void testInvalidFolderRecordsLastError(@TempDir Path notACase) {
        IpedCoreService service = IpedCoreService.getInstance();
        assertThrows(IllegalArgumentException.class, () -> service.openCase(notACase.toFile()));
        Map<String, Object> error = service.getLastOpenError();
        assertNotNull(error, "a falha deve ficar registrada");
        assertEquals("validação da pasta", error.get("step"));
        assertTrue(((String) error.get("message")).contains(notACase.toString()));
        assertNotNull(error.get("time"));
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void testSuccessfulOpeningLogsStepsAndClearsLastError(@TempDir Path notACase) throws Exception {
        IpedCoreService service = IpedCoreService.getInstance();
        assertThrows(IllegalArgumentException.class, () -> service.openCase(notACase.toFile()));
        assertNotNull(service.getLastOpenError());

        service.openCase(TestCaseResolver.getPrimaryCaseDir());
        assertNull(service.getLastOpenError(), "uma abertura bem-sucedida limpa o erro anterior");

        for (String step : new String[] { "configurações do IPED", "tipos de metadados", "índice e marcadores" }) {
            assertTrue(messages.contains("[abertura do caso] início: " + step), "faltou o início de " + step + ": " + messages);
            assertTrue(messages.stream().anyMatch(m -> m.startsWith("[abertura do caso] fim: " + step + " (")
                    && m.endsWith(" ms)")), "faltou o fim com duração de " + step + ": " + messages);
        }
    }
}
