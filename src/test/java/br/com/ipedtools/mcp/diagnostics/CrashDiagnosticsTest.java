package br.com.ipedtools.mcp.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CrashDiagnosticsTest {

    @Test
    void testCopiesNewReportsOnceAndRespectsTheLimit(@TempDir Path tmp) throws Exception {
        Path temp = Files.createDirectories(tmp.resolve("temp"));
        Path app = Files.createDirectories(tmp.resolve("app"));
        File logDir = tmp.resolve("logs").toFile();
        long now = System.currentTimeMillis();
        for (int i = 1; i <= 6; i++) {
            Path report = (i % 2 == 0 ? temp : app).resolve("hs_err_pid" + (100 + i) + ".log");
            Files.writeString(report, "# A fatal error has been detected by the Java Runtime Environment " + i);
            report.toFile().setLastModified(now - (10 - i) * 60_000L);
        }
        Files.writeString(temp.resolve("outro.log"), "não é relatório de queda");

        List<File> first = CrashDiagnostics.collectCrashReports(List.of(temp.toFile(), app.toFile()), logDir, 5);
        assertEquals(5, first.size(), "respeita o limite de relatórios por início");
        assertEquals("hs_err_pid106.log", first.get(0).getName(), "copia os mais recentes primeiro");
        assertTrue(new File(logDir, "hs_err_pid106.log").isFile());
        assertTrue(!new File(logDir, "outro.log").exists());

        List<File> second = CrashDiagnostics.collectCrashReports(List.of(temp.toFile(), app.toFile()), logDir, 5);
        assertEquals(1, second.size(), "só o relatório que ficou de fora no início anterior");
        assertEquals("hs_err_pid101.log", second.get(0).getName());

        List<File> third = CrashDiagnostics.collectCrashReports(List.of(temp.toFile(), app.toFile()), logDir, 5);
        assertTrue(third.isEmpty(), "relatórios já coletados não são repetidos");
    }

    @Test
    void testNoReportsFound(@TempDir Path tmp) throws Exception {
        List<File> copied = CrashDiagnostics.collectCrashReports(List.of(tmp.toFile()), tmp.resolve("logs").toFile(), 5);
        assertTrue(copied.isEmpty());
    }
}
