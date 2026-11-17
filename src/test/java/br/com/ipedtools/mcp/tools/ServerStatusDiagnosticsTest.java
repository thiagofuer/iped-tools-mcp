package br.com.ipedtools.mcp.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import br.com.ipedtools.mcp.service.IpedCoreService;
import br.com.ipedtools.mcp.test.TestCaseResolver;

/**
 * get_server_status reports the log file and, after a failed opening, the failed step. The user's
 * active_case.txt is saved before each test and restored afterwards.
 */
class ServerStatusDiagnosticsTest {

    private String savedActiveCase;

    static boolean isCaseAvailable() {
        return TestCaseResolver.isCaseAvailable();
    }

    @BeforeEach
    void save() throws Exception {
        Path p = IpedCoreService.ACTIVE_CASE_FILE.toPath();
        savedActiveCase = Files.exists(p) ? Files.readString(p, StandardCharsets.UTF_8) : null;
        IpedCoreService.getInstance().closeCase();
    }

    @AfterEach
    void restore() throws Exception {
        Path p = IpedCoreService.ACTIVE_CASE_FILE.toPath();
        if (savedActiveCase == null) {
            Files.deleteIfExists(p);
        } else {
            Files.writeString(p, savedActiveCase, StandardCharsets.UTF_8);
        }
        IpedCoreService.getInstance().closeCase();
    }

    @Test
    @SuppressWarnings("unchecked")
    void testFailedOpeningIsReportedWithStepAndLogFile(@TempDir Path notACase) throws Exception {
        Files.createDirectories(IpedCoreService.ACTIVE_CASE_FILE.toPath().getParent());
        Files.writeString(IpedCoreService.ACTIVE_CASE_FILE.toPath(), notACase.toString(), StandardCharsets.UTF_8);

        Map<String, Object> status = new ServerStatusTool().getServerStatus();
        assertEquals(false, status.get("case_open"));
        assertTrue(status.containsKey("log_file"), "a resposta sempre informa o arquivo de log");
        Map<String, Object> lastError = (Map<String, Object>) status.get("last_error");
        assertNotNull(lastError, "a falha da abertura deve aparecer em get_server_status: " + status);
        assertEquals("validação da pasta", lastError.get("step"));
        assertTrue(((String) lastError.get("message")).contains(notACase.toString()));
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void testSuccessfulOpeningHasNoLastError() throws Exception {
        Files.writeString(IpedCoreService.ACTIVE_CASE_FILE.toPath(),
                TestCaseResolver.getPrimaryCaseDir().getAbsolutePath(), StandardCharsets.UTF_8);
        Map<String, Object> status = new ServerStatusTool().getServerStatus();
        assertEquals(true, status.get("case_open"));
        assertFalse(status.containsKey("last_error"));
        assertTrue(status.containsKey("log_file"));
    }
}
