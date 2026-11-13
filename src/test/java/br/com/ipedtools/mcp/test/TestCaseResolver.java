package br.com.ipedtools.mcp.test;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

/**
 * Resolves test IPED case directories dynamically across multiple configuration tiers:
 * 1. System property: -Diped.test.case.path=<path>
 * 2. Environment variable: IPED_TEST_CASE_PATH
 * 3. Local properties file: local-test.properties (or test-case.properties) in project root
 * 4. Fallback: null (enabling graceful test skips without failure)
 *
 * <p>Besides the primary and secondary cases, optional labeled cases can be configured as
 * {@code iped.test.case.<label>.path} (e.g. {@code ios}, {@code fat}), with environment variable
 * {@code IPED_TEST_CASE_<LABEL>_PATH}. Tests that need them are skipped when the label is not configured.
 */
public final class TestCaseResolver {

    public static final String SYS_PROP_PRIMARY = "iped.test.case.path";
    public static final String SYS_PROP_SECONDARY = "iped.test.secondary_case.path";
    public static final String ENV_VAR_PRIMARY = "IPED_TEST_CASE_PATH";
    public static final String ENV_VAR_SECONDARY = "IPED_TEST_SECONDARY_CASE_PATH";
    public static final String LOCAL_PROPS_FILE = "local-test.properties";
    public static final String FALLBACK_PROPS_FILE = "test-case.properties";

    /** Label of the optional iOS extraction test case. */
    public static final String LABEL_IOS = "ios";
    /** Label of the optional FAT file system test case. */
    public static final String LABEL_FAT = "fat";

    private static Properties fileProperties;
    private static File primaryCaseDir;
    private static File secondaryCaseDir;
    private static boolean initialized = false;

    private TestCaseResolver() {}

    private static synchronized void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;

        Properties fileProps = loadLocalProperties();
        fileProperties = fileProps;

        String primaryPath = resolvePath(SYS_PROP_PRIMARY, ENV_VAR_PRIMARY, fileProps);
        if (primaryPath != null && !primaryPath.isBlank()) {
            primaryCaseDir = new File(primaryPath.trim());
        }

        String secondaryPath = resolvePath(SYS_PROP_SECONDARY, ENV_VAR_SECONDARY, fileProps);
        if (secondaryPath != null && !secondaryPath.isBlank()) {
            secondaryCaseDir = new File(secondaryPath.trim());
        }

        if (isCaseAvailable()) {
            System.err.println("[TestCaseResolver] Caso de teste detectado: " + primaryCaseDir.getAbsolutePath());
        } else {
            System.err.println("[TestCaseResolver] Nenhum caso IPED local configurado. Testes dependentes de índice serão ignorados (skipped).");
        }
    }

    private static String resolvePath(String sysProp, String envVar, Properties fileProps) {
        String path = System.getProperty(sysProp);
        if (path != null && !path.isBlank()) {
            return path;
        }

        path = System.getenv(envVar);
        if (path != null && !path.isBlank()) {
            return path;
        }

        if (fileProps != null) {
            path = fileProps.getProperty(sysProp);
            if (path != null && !path.isBlank()) {
                return path;
            }
        }

        return null;
    }

    private static Properties loadLocalProperties() {
        Properties props = new Properties();

        File file = new File(LOCAL_PROPS_FILE);
        if (!file.exists()) {
            file = new File(FALLBACK_PROPS_FILE);
        }

        if (file.exists() && file.isFile()) {
            try (InputStream is = new FileInputStream(file)) {
                props.load(is);
            } catch (Exception e) {
                System.err.println("[TestCaseResolver] Aviso: Falha ao carregar " + file.getName() + ": " + e.getMessage());
            }
        }

        return props;
    }

    public static File getPrimaryCaseDir() {
        initialize();
        return primaryCaseDir;
    }

    public static File getSecondaryCaseDir() {
        initialize();
        return secondaryCaseDir;
    }

    public static boolean isCaseAvailable() {
        initialize();
        return primaryCaseDir != null
                && primaryCaseDir.exists()
                && new File(primaryCaseDir, "iped/index").exists();
    }

    public static boolean isSecondaryCaseAvailable() {
        initialize();
        return secondaryCaseDir != null
                && secondaryCaseDir.exists()
                && new File(secondaryCaseDir, "iped/index").exists();
    }

    static String labeledPropertyName(String label) {
        return "iped.test.case." + label + ".path";
    }

    /** Directory of a labeled test case, or null when it is not configured. */
    public static File getLabeledCaseDir(String label) {
        initialize();
        String envVar = "IPED_TEST_CASE_" + label.toUpperCase(java.util.Locale.ROOT) + "_PATH";
        String path = resolvePath(labeledPropertyName(label), envVar, fileProperties);
        return path == null || path.isBlank() ? null : new File(path.trim());
    }

    /** True when the labeled case is configured and holds an IPED index. */
    public static boolean isLabeledCaseAvailable(String label) {
        File dir = getLabeledCaseDir(label);
        return dir != null && dir.exists() && new File(dir, "iped/index").exists();
    }
}
