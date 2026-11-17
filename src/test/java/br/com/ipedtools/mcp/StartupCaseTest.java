package br.com.ipedtools.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import br.com.ipedtools.mcp.service.IpedCoreService;
import br.com.ipedtools.mcp.test.TestCaseResolver;

/**
 * In STDIO mode the case given with --case is only registered at startup; its index is opened by the first tool
 * call. The user's active_case.txt is saved before each test and restored afterwards.
 */
class StartupCaseTest {

    private String savedActiveCase;

    static boolean isCaseAvailable() {
        return TestCaseResolver.isCaseAvailable();
    }

    @BeforeEach
    void saveActiveCase() throws Exception {
        File f = IpedCoreService.ACTIVE_CASE_FILE;
        savedActiveCase = f.exists() ? Files.readString(f.toPath(), StandardCharsets.UTF_8) : null;
    }

    @AfterEach
    void restoreActiveCase() throws Exception {
        Path p = IpedCoreService.ACTIVE_CASE_FILE.toPath();
        if (savedActiveCase == null) {
            Files.deleteIfExists(p);
        } else {
            Files.writeString(p, savedActiveCase, StandardCharsets.UTF_8);
        }
        IpedCoreService.getInstance().closeCase();
    }

    @Test
    void testInvalidCaseIsNotRegistered(@TempDir Path notACase) throws Exception {
        String before = IpedCoreService.ACTIVE_CASE_FILE.exists()
                ? Files.readString(IpedCoreService.ACTIVE_CASE_FILE.toPath(), StandardCharsets.UTF_8) : null;
        assertFalse(McpApplication.registerStartupCase(notACase.toString()));
        assertFalse(McpApplication.registerStartupCase(notACase.resolve("inexistente").toString()));
        String after = IpedCoreService.ACTIVE_CASE_FILE.exists()
                ? Files.readString(IpedCoreService.ACTIVE_CASE_FILE.toPath(), StandardCharsets.UTF_8) : null;
        assertEquals(before, after, "pasta inválida não pode alterar active_case.txt");
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void testValidCaseIsRegisteredAndOpenedOnFirstCall() throws Exception {
        File caseDir = TestCaseResolver.getPrimaryCaseDir();
        IpedCoreService service = IpedCoreService.getInstance();
        service.closeCase();

        assertTrue(McpApplication.registerStartupCase(caseDir.getAbsolutePath()));
        assertEquals(caseDir.getCanonicalPath(),
                Files.readString(IpedCoreService.ACTIVE_CASE_FILE.toPath(), StandardCharsets.UTF_8).trim());
        assertFalse(service.isCaseLoaded(), "o registro não pode abrir o índice");

        // First tool call: isCaseOpen opens the active case on demand
        assertTrue(service.isCaseOpen(), "a primeira chamada deve abrir o caso ativo");
        assertTrue(service.isCaseLoaded());
        assertEquals(caseDir.getCanonicalPath(), service.getCaseDirectory().getCanonicalPath());
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void testClosedCaseReopensOnNextCallWithoutThrottle() throws Exception {
        File caseDir = TestCaseResolver.getPrimaryCaseDir();
        IpedCoreService service = IpedCoreService.getInstance();
        assertTrue(McpApplication.registerStartupCase(caseDir.getAbsolutePath()));
        assertTrue(service.isCaseOpen());

        // Closing and calling again right away (inside the 500 ms check interval, same file timestamp)
        service.closeCase();
        assertFalse(service.isCaseLoaded());
        assertTrue(service.isCaseOpen(), "sem caso aberto, a próxima chamada reabre o caso ativo");
    }
}
