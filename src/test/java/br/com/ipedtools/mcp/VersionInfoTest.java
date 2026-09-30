package br.com.ipedtools.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.jupiter.api.Test;

class VersionInfoTest {

    @Test
    void testVersionPropertiesLoaded() {
        assertNotNull(VersionInfo.getVersion());
        assertTrue(VersionInfo.getVersion().startsWith("1.0.0"));
        assertNotNull(VersionInfo.getBuildTimestamp());
        assertNotNull(VersionInfo.getIpedCoreVersion());
        assertTrue(VersionInfo.getFullVersionString().contains("IPED Tools MCP v" + VersionInfo.getVersion()));
    }

    @Test
    void testMcpApplicationVersionFlag() {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(baos));
            McpApplication.main("--version");
            String output = baos.toString();
            assertTrue(output.contains("IPED Tools MCP v" + VersionInfo.getVersion()), "Deveria conter a versão na saída");
            assertTrue(output.contains("Website: " + VersionInfo.OFFICIAL_WEBSITE), "Deveria conter o website oficial");
        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    void testMcpApplicationHelpFlag() {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(baos));
            McpApplication.main("--help");
            String output = baos.toString();
            assertTrue(output.contains("--version, -v"), "Help deveria listar a flag --version");
            assertTrue(output.contains("Versao: " + VersionInfo.getVersion()) || output.contains("Versão: " + VersionInfo.getVersion()), "Help deveria listar a versão atual");
        } finally {
            System.setOut(originalOut);
        }
    }
}
