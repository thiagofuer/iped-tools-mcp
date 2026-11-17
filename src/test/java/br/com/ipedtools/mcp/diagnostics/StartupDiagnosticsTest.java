package br.com.ipedtools.mcp.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StartupDiagnosticsTest {

    @Test
    void testHeaderListsVersionsModeCaseAndRelevantPathEntries(@TempDir Path dir) throws Exception {
        Path python = Files.createDirectories(dir.resolve("Python39"));
        Files.createFile(python.resolve("python39.dll"));
        Path jep = Files.createDirectories(dir.resolve("site-packages").resolve("jep"));
        Files.createFile(jep.resolve("jep.dll"));
        Path other = Files.createDirectories(dir.resolve("ferramentas"));
        String path = String.join(File.pathSeparator, other.toString(), python.toString(), jep.toString(),
                dir.resolve("IPED-4.3.1-inexistente").toString());

        Properties props = new Properties();
        props.setProperty("java.version", "21.0.1");
        props.setProperty("os.name", "Windows 11");
        props.setProperty("user.dir", "C:\\Windows\\System32");
        props.setProperty("java.library.path", path);

        List<String> header = StartupDiagnostics.header("STDIO", new String[] { "--stdio" }, Map.of("Path", path), props,
                "C:\\casos_forenses\\caso_operacao_01", new File("teste.log"), 1234L);
        String text = String.join("\n", header);

        assertTrue(text.contains("Modo: STDIO | PID: 1234 | Argumentos: --stdio"));
        assertTrue(text.contains("Java: 21.0.1"));
        assertTrue(text.contains("user.dir): C:\\Windows\\System32"));
        assertTrue(text.contains("Caso ativo (active_case.txt): C:\\casos_forenses\\caso_operacao_01"));
        assertTrue(text.contains("iped.root: (não definido)"));
        assertTrue(text.contains(python + " [python39.dll]"), "deve apontar a DLL do Python: " + text);
        assertTrue(text.contains(jep + " [jep.dll]"), "deve apontar o jep.dll: " + text);
        assertTrue(text.contains("IPED-4.3.1-inexistente [pasta não existe]"));
        assertFalse(header.stream().anyMatch(l -> l.trim().startsWith(other.toString())),
                "pastas sem relação não entram na lista do PATH");
        assertEquals(3 + 1, header.stream().filter(l -> l.startsWith("  ")).count() + 1,
                "três entradas relevantes no PATH");
    }

    @Test
    void testHeaderWithoutRelevantPathOrActiveCase() {
        List<String> header = StartupDiagnostics.header("GUI", new String[0], Map.of("PATH", "C:\\Windows"),
                new Properties(), null, null, 1L);
        String text = String.join("\n", header);
        assertTrue(text.contains("Argumentos: (nenhum)"));
        assertTrue(text.contains("Caso ativo (active_case.txt): (nenhum)"));
        assertTrue(text.contains("Arquivo de log: (desativado)"));
        assertTrue(text.contains("PATH: nenhuma entrada de Python, JEP ou IPED"));
    }
}
