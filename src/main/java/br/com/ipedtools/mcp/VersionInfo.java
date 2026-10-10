package br.com.ipedtools.mcp;

import java.io.InputStream;
import java.util.Properties;

/**
 * Centralized application metadata and runtime version provider.
 * Reads version properties generated during the Maven build lifecycle.
 */
public final class VersionInfo {

    public static final String APP_NAME = "IPED Tools MCP";
    public static final String APP_DESCRIPTION = "Assistente de IA Forense para casos do IPED via Model Context Protocol";
    public static final String OFFICIAL_WEBSITE = "https://www.mcp.ipedtools.com.br";
    public static final String GITHUB_REPO = "https://github.com/thiagofuer/iped-tools-mcp";
    public static final String LICENSE = "GPLv3 / Open Source";

    private static String version = "1.0.1";
    private static String buildTimestamp = "2026-09-29";
    private static String ipedVersion = "4.4.0";

    static {
        try (InputStream is = VersionInfo.class.getResourceAsStream("/version.properties")) {
            if (is != null) {
                Properties props = new Properties();
                props.load(is);
                String ver = props.getProperty("application.version");
                if (ver != null && !ver.isBlank() && !ver.startsWith("${")) {
                    version = ver.trim();
                }
                String bTime = props.getProperty("application.build.timestamp");
                if (bTime != null && !bTime.isBlank() && !bTime.startsWith("${")) {
                    buildTimestamp = bTime.trim();
                }
                String ipedVer = props.getProperty("iped.core.version");
                if (ipedVer != null && !ipedVer.isBlank() && !ipedVer.startsWith("${")) {
                    ipedVersion = ipedVer.trim();
                }
            }
        } catch (Exception ignored) {
        }
    }

    private VersionInfo() {
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
