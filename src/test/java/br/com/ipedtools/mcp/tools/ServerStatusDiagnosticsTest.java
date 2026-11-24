package br.com.ipedtools.mcp.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import br.com.ipedtools.mcp.service.IpedCoreService;
import br.com.ipedtools.mcp.test.TestCaseResolver;

/** get_server_status reports the log file and, after a failed opening, the failed step. */
class ServerStatusDiagnosticsTest {

    static boolean isCaseAvailable() {
        return TestCaseResolver.isCaseAvailable();
    }

    @BeforeEach
    @AfterEach
    void close() {
        IpedCoreService.getInstance().closeCase();
    }

    @Test
    @SuppressWarnings("unchecked")
    void testFailedOpeningIsReportedWithStepAndLogFile(@TempDir Path notACase) {
        assertThrows(IllegalArgumentException.class,
                () -> IpedCoreService.getInstance().openCase(notACase.toFile()));

        Map<String, Object> status = new ServerStatusTool().getServerStatus();
        assertEquals(false, status.get("case_open"));
        assertTrue(status.containsKey("log_file"), "a resposta sempre informa o arquivo de log");
        Map<String, Object> lastError = (Map<String, Object>) status.get("last_error");
        assertNotNull(lastError, "a falha da abertura deve aparecer em get_server_status: " + status);
        assertEquals("validação da pasta", lastError.get("step"));
        assertTrue(((String) lastError.get("message")).contains(notACase.toString()));
    }

    @Test
    void testStatusWithoutCasePointsToOpenCase() {
        Map<String, Object> status = new ServerStatusTool().getServerStatus();
        assertEquals(false, status.get("case_open"));
        String text = String.valueOf(status.get("status"));
        assertTrue(text.contains("open_case"), "sem caso, o status deve orientar o open_case: " + text);
        assertFalse(text.toLowerCase().contains("configurador"), "o status não pode citar o configurador");
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void testSuccessfulOpeningHasNoLastError() {
        IpedCoreService.getInstance().openCase(TestCaseResolver.getPrimaryCaseDir());
        Map<String, Object> status = new ServerStatusTool().getServerStatus();
        assertEquals(true, status.get("case_open"));
        assertFalse(status.containsKey("last_error"));
        assertTrue(status.containsKey("log_file"));
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void testInvalidFolderKeepsTheOpenCase(@TempDir Path notACase) {
        IpedCoreService service = IpedCoreService.getInstance();
        service.openCase(TestCaseResolver.getPrimaryCaseDir());
        assertThrows(IllegalArgumentException.class, () -> service.openCase(notACase.toFile()));
        assertTrue(service.isCaseOpen(), "uma pasta inválida não pode fechar o caso aberto");
        assertEquals(TestCaseResolver.getPrimaryCaseDir().getAbsolutePath(),
                service.getCaseDirectory().getAbsolutePath());
    }
}
