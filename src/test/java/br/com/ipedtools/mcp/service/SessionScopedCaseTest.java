package br.com.ipedtools.mcp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import br.com.ipedtools.mcp.test.TestCaseResolver;
import br.com.ipedtools.mcp.tools.DocumentSearchTool;
import br.com.ipedtools.mcp.tools.ServerStatusTool;

/**
 * The case lives only in the server process: nothing is written to or read from the user profile, and a case tool
 * called without a case points to open_case. A legacy active_case.txt of the user is saved and restored.
 */
class SessionScopedCaseTest {

    private static final File PROFILE_DIR = new File(System.getProperty("user.home"), ".iped-tools-mcp");
    private static final File LEGACY_STATE = new File(PROFILE_DIR, "active_case.txt");

    private byte[] savedLegacyState;

    static boolean isCaseAvailable() {
        return TestCaseResolver.isCaseAvailable();
    }

    @BeforeEach
    void save() throws Exception {
        savedLegacyState = LEGACY_STATE.isFile() ? Files.readAllBytes(LEGACY_STATE.toPath()) : null;
        IpedCoreService.getInstance().closeCase();
    }

    @AfterEach
    void restore() throws Exception {
        if (savedLegacyState == null) {
            Files.deleteIfExists(LEGACY_STATE.toPath());
        } else {
            Files.write(LEGACY_STATE.toPath(), savedLegacyState);
        }
        IpedCoreService.getInstance().closeCase();
    }

    /** Names, sizes and modification times of the profile folder entries, except the logs. */
    private static Map<String, String> profileSnapshot() {
        Map<String, String> entries = new TreeMap<>();
        File[] list = PROFILE_DIR.listFiles();
        if (list != null) {
            for (File f : list) {
                if (!f.getName().equals("logs")) {
                    entries.put(f.getName(), f.length() + "|" + f.lastModified());
                }
            }
        }
        return entries;
    }

    @Test
    void testCaseToolWithoutCasePointsToOpenCase() {
        Map<String, Object> result = new DocumentSearchTool().searchDocuments("*:*", 1, null);
        String error = String.valueOf(result.get("error"));
        assertTrue(error.contains("open_case"), "sem caso, a resposta deve indicar open_case: " + result);
        assertFalse(IpedCoreService.getInstance().isCaseOpen(), "a chamada não pode abrir um caso");
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void testLegacyStateFileIsNotRead() throws Exception {
        Files.createDirectories(PROFILE_DIR.toPath());
        String content = TestCaseResolver.getPrimaryCaseDir().getAbsolutePath();
        Files.writeString(LEGACY_STATE.toPath(), content, StandardCharsets.UTF_8);
        long modified = LEGACY_STATE.lastModified();

        IpedCoreService service = IpedCoreService.getInstance();
        assertFalse(service.isCaseOpen(), "o caso do arquivo antigo não pode ser aberto");
        assertEquals(false, new ServerStatusTool().getServerStatus().get("case_open"));
        assertEquals(content, Files.readString(LEGACY_STATE.toPath(), StandardCharsets.UTF_8));
        assertEquals(modified, LEGACY_STATE.lastModified(), "o arquivo antigo não pode ser alterado");
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void testOpeningACaseWritesNothingToTheProfile() throws Exception {
        Map<String, String> before = profileSnapshot();
        IpedCoreService service = IpedCoreService.getInstance();
        service.openCase(TestCaseResolver.getPrimaryCaseDir());
        assertTrue(service.isCaseOpen());
        assertEquals(before, profileSnapshot(), "abrir um caso não pode gravar nada no perfil do usuário");
        Path legacy = LEGACY_STATE.toPath();
        if (savedLegacyState == null) {
            assertFalse(Files.exists(legacy), "abrir um caso não pode criar active_case.txt");
        }
    }
}
