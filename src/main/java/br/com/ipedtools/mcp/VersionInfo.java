package br.com.ipedtools.mcp;

import java.io.InputStream;
import java.util.Properties;

/**
 * Centralized application metadata and runtime version provider.
 * The version is declared only in pom.xml and reaches the runtime through
 * the Maven-filtered /version.properties resource. A build whose resources
 * were not filtered reports {@link #DEV_VERSION} instead of a release number.
 */
public final class VersionInfo {

    public static final String APP_NAME = "IPED Tools MCP";
    public static final String APP_DESCRIPTION = "Assistente de IA Forense para casos do IPED via Model Context Protocol";
    public static final String OFFICIAL_WEBSITE = "https://www.mcp.ipedtools.com.br";
    public static final String GITHUB_REPO = "https://github.com/thiagofuer/iped-tools-mcp";
    public static final String LICENSE = "GPLv3 / Open Source";

    /** Version reported when version.properties was not filtered by Maven. */
    public static final String DEV_VERSION = "dev";
    /** Build date and IPED version reported when version.properties was not filtered. */
    public static final String UNKNOWN = "desconhecida";

    private static final String version;
    private static final String buildTimestamp;
    private static final String ipedVersion;

    static {
        Properties props = new Properties();
        try (InputStream is = VersionInfo.class.getResourceAsStream("/version.properties")) {
            if (is != null) {
                props.load(is);
            }
        } catch (Exception ignored) {
        }
        version = resolve(props, "application.version", DEV_VERSION);
        buildTimestamp = resolve(props, "application.build.timestamp", UNKNOWN);
        ipedVersion = resolve(props, "iped.core.version", UNKNOWN);
    }

    private VersionInfo() {
    }

    /**
     * Returns the filtered value of a property, or the given fallback when the
     * property is missing, blank or still holds an unfiltered Maven placeholder.
     */
    static String resolve(Properties props, String key, String fallback) {
        String value = props.getProperty(key);
        if (value == null || value.isBlank() || value.contains("${")) {
            return fallback;
        }
        return value.trim();
    }

    public static String getVersion() {
        return version;
    }

    public static String getBuildTimestamp() {
        return buildTimestamp;
    }

    public static String getIpedCoreVersion() {
        return ipedVersion;
    }

    public static String getFullVersionString() {
        return String.format("%s v%s (build %s, Java %s, IPED Core %s)",
                APP_NAME,
                version,
                buildTimestamp,
                System.getProperty("java.version"),
                ipedVersion);
    }
}
