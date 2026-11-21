package br.com.ipedtools.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Properties;

import org.junit.jupiter.api.Test;

/**
 * The release version is declared only in pom.xml: these tests derive it from
 * the build and never compare it with a literal release number.
 */
class VersionInfoTest {

    private static final String SEMVER = "^\\d+\\.\\d+\\.\\d+(-[0-9A-Za-z.-]+)?$";

    @Test
    void testVersionPropertiesFiltered() {
        String version = VersionInfo.getVersion();
        assertNotEquals(VersionInfo.DEV_VERSION, version, "version.properties nao foi filtrado pelo Maven");
        assertFalse(version.contains("${"), "version.properties nao foi filtrado pelo Maven");
        assertTrue(version.matches(SEMVER), "Versao fora do padrao SemVer: " + version);
        assertNotEquals(VersionInfo.UNKNOWN, VersionInfo.getBuildTimestamp());
        assertNotEquals(VersionInfo.UNKNOWN, VersionInfo.getIpedCoreVersion());
        assertTrue(VersionInfo.getFullVersionString().contains("IPED Tools MCP v" + version));
    }

    @Test
    void testUnfilteredPropertiesFallBackToDev() {
        Properties unfiltered = new Properties();
        unfiltered.setProperty("application.version", "${project.version}");
        unfiltered.setProperty("application.build.timestamp", "${timestamp}");

        assertEquals(VersionInfo.DEV_VERSION,
                VersionInfo.resolve(unfiltered, "application.version", VersionInfo.DEV_VERSION));
        assertEquals(VersionInfo.UNKNOWN,
                VersionInfo.resolve(unfiltered, "application.build.timestamp", VersionInfo.UNKNOWN));
        assertEquals(VersionInfo.UNKNOWN,
                VersionInfo.resolve(unfiltered, "iped.core.version", VersionInfo.UNKNOWN));

        Properties filtered = new Properties();
        filtered.setProperty("application.version", " 2.3.4-SNAPSHOT ");
        assertEquals("2.3.4-SNAPSHOT",
                VersionInfo.resolve(filtered, "application.version", VersionInfo.DEV_VERSION));
    }

    @Test
    void testOnlyStdioIsRecognized() {
        // No information flags: the native launcher has no console, so they open the configurator instead
        for (String arg : new String[] { "--version", "-v", "--help", "-h", "--gui", "" }) {
            McpApplication.LaunchOptions options = McpApplication.parseArguments(arg);
            assertFalse(options.stdio(), arg + " deve abrir o configurador");
            assertFalse(options.legacyCaseArgument());
        }
        assertEquals(new McpApplication.LaunchOptions(false, false), McpApplication.parseArguments());
        assertEquals(new McpApplication.LaunchOptions(true, false), McpApplication.parseArguments("--stdio"));
        assertEquals(new McpApplication.LaunchOptions(true, true),
                McpApplication.parseArguments("--stdio", "--case", "C:\\casos_forenses\\caso_operacao_01"));
        assertEquals(new McpApplication.LaunchOptions(true, true),
                McpApplication.parseArguments("--case=C:\\casos_forenses\\caso_operacao_01", "--stdio"));
    }

}
