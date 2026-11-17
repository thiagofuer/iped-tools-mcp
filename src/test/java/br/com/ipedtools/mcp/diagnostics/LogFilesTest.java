package br.com.ipedtools.mcp.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LogFilesTest {

    @AfterEach
    void removeHandler() {
        LogFiles.removeEarlyHandler();
    }

    @Test
    void testWritesUtf8LinesWithPidToModeFile(@TempDir Path dir) throws Exception {
        File logDir = dir.resolve("logs").toFile();
        File file = LogFiles.install(LogFiles.Mode.GUI, logDir, LogFiles.MAX_FILE_SIZE, LogFiles.MAX_BACKUPS);
        assertNotNull(file, "deve criar a pasta e ativar o log em arquivo");
        assertEquals("iped-tools-mcp-gui.log", file.getName());
        assertEquals(file, LogFiles.getActiveFile());

        Logger.getLogger("br.com.ipedtools.teste").info("Carregando configurações do caso");
        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        assertTrue(content.contains("Carregando configurações do caso"), "acentos devem estar em UTF-8: " + content);
        assertTrue(content.contains("[pid " + ProcessHandle.current().pid() + "]"), "linha deve trazer o PID: " + content);
    }

    @Test
    void testRotatesBySize() throws Exception {
        // Not a @TempDir: on Windows, freshly rotated files may stay locked for a moment (antivirus scanning) and
        // make the JUnit cleanup fail. The folder is removed here on a best-effort basis.
        Path dir = Files.createTempDirectory("iped-mcp-logrotate");
        try {
            File logDir = dir.toFile();
            File file = LogFiles.install(LogFiles.Mode.STDIO, logDir, 2_000, 2);
            assertNotNull(file);
            Logger logger = Logger.getLogger("br.com.ipedtools.rotacao");
            for (int i = 0; i < 200; i++) {
                logger.info("linha de teste de rotação número " + i);
            }
            LogFiles.removeEarlyHandler();
            String[] files = logDir.list((d, n) -> n.startsWith("iped-tools-mcp-stdio.log"));
            assertNotNull(files);
            assertTrue(files.length > 1, "deve rotacionar ao atingir o limite");
            assertTrue(files.length <= 3, "deve manter só o arquivo atual e 2 antigos: " + String.join(", ", files));
        } finally {
            LogFiles.removeEarlyHandler();
            try (java.util.stream.Stream<Path> paths = Files.walk(dir)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            } catch (Exception ignored) {
            }
        }
    }

    @Test
    void testUnwritableFolderDisablesFileLogWithoutFailing(@TempDir Path dir) throws Exception {
        // A regular file where the log folder should be: the folder cannot be created
        Path blocker = dir.resolve("logs");
        Files.writeString(blocker, "não é pasta");
        File file = LogFiles.install(LogFiles.Mode.STDIO, blocker.toFile(), LogFiles.MAX_FILE_SIZE, 1);
        assertNull(file, "sem pasta gravável o log em arquivo fica desativado");
        assertNull(LogFiles.getActiveFile());
        assertNotNull(LogFiles.getDisabledReason());
        Logger.getLogger("br.com.ipedtools.teste").info("o log continua funcionando sem arquivo");
    }
}
