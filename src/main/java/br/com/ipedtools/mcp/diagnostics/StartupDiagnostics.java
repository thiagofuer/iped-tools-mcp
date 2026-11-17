package br.com.ipedtools.mcp.diagnostics;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.ipedtools.mcp.VersionInfo;

/**
 * Diagnostic header logged on every start. It records the runtime environment that support needs to explain a
 * failure that happens only on the examiner's machine: versions, mode, arguments, encodings, active case, the
 * IPED root and the PATH entries that point to Python, JEP or IPED installations (native libraries that IPED may
 * load while it reads the case configuration).
 */
public final class StartupDiagnostics {

    private static final Logger LOGGER = LoggerFactory.getLogger(StartupDiagnostics.class);
    private static final String[] RELEVANT_PATH_WORDS = { "python", "jep", "iped" };

    private StartupDiagnostics() {
    }

    /** Logs the header of this process. Never throws. */
    public static void log(String mode, String[] args, File activeCaseFile, File logFile) {
        try {
            for (String line : header(mode, args, System.getenv(), System.getProperties(), readActiveCase(activeCaseFile),
                    logFile, ProcessHandle.current().pid())) {
                LOGGER.info(line);
            }
        } catch (RuntimeException e) {
            LOGGER.warn("Falha ao montar o cabeçalho de diagnóstico: {}", e.toString());
        }
    }

    static List<String> header(String mode, String[] args, Map<String, String> env, Properties props,
            String activeCase, File logFile, long pid) {
        List<String> lines = new ArrayList<>();
        lines.add("===== Diagnóstico de inicialização =====");
        lines.add("Aplicação: " + VersionInfo.APP_NAME + " v" + VersionInfo.getVersion() + " (build "
                + VersionInfo.getBuildTimestamp() + ", IPED Core " + VersionInfo.getIpedCoreVersion() + ")");
        lines.add("Modo: " + mode + " | PID: " + pid + " | Argumentos: "
                + (args == null || args.length == 0 ? "(nenhum)" : String.join(" ", args)));
        lines.add("Java: " + props.getProperty("java.version") + " (" + props.getProperty("java.vendor") + ") em "
                + props.getProperty("java.home"));
        lines.add("Sistema: " + props.getProperty("os.name") + " " + props.getProperty("os.version") + " "
                + props.getProperty("os.arch"));
        lines.add("Diretório de trabalho (user.dir): " + props.getProperty("user.dir"));
        lines.add("Codificações: file=" + props.getProperty("file.encoding") + " stdout="
                + props.getProperty("stdout.encoding") + " stderr=" + props.getProperty("stderr.encoding")
                + " native=" + props.getProperty("native.encoding"));
        lines.add("Caso ativo (active_case.txt): " + (activeCase == null || activeCase.isBlank() ? "(nenhum)" : activeCase));
        String ipedRoot = props.getProperty("iped.root");
        lines.add("iped.root: " + (ipedRoot == null ? "(não definido)" : ipedRoot));
        lines.add("Arquivo de log: " + (logFile == null ? "(desativado)" : logFile.getAbsolutePath()));
        lines.add("java.library.path: " + props.getProperty("java.library.path", ""));
        List<String> relevant = relevantPathEntries(pathOf(env));
        if (relevant.isEmpty()) {
            lines.add("PATH: nenhuma entrada de Python, JEP ou IPED");
        } else {
            lines.add("PATH: entradas de Python, JEP ou IPED (" + relevant.size() + "):");
            for (String entry : relevant) {
                lines.add("  " + entry);
            }
        }
        lines.add("===== Fim do diagnóstico =====");
        return lines;
    }

    /** PATH entries whose folder name mentions Python, JEP or IPED, with the native libraries found in them. */
    static List<String> relevantPathEntries(String path) {
        List<String> result = new ArrayList<>();
        if (path == null || path.isBlank()) {
            return result;
        }
        for (String entry : path.split(File.pathSeparator)) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String lower = trimmed.toLowerCase(Locale.ROOT);
            boolean relevant = false;
            for (String word : RELEVANT_PATH_WORDS) {
                if (lower.contains(word)) {
                    relevant = true;
                    break;
                }
            }
            if (relevant) {
                result.add(trimmed + nativeLibrariesIn(new File(trimmed)));
            }
        }
        return result;
    }

    private static String nativeLibrariesIn(File dir) {
        String[] names = dir.isDirectory() ? dir.list((d, n) -> {
            String l = n.toLowerCase(Locale.ROOT);
            return l.equals("jep.dll") || (l.startsWith("python") && l.endsWith(".dll"));
        }) : null;
        if (!dir.isDirectory()) {
            return " [pasta não existe]";
        }
        return names == null || names.length == 0 ? "" : " [" + String.join(", ", names) + "]";
    }

    private static String pathOf(Map<String, String> env) {
        for (Map.Entry<String, String> e : env.entrySet()) {
            if (e.getKey().equalsIgnoreCase("PATH")) {
                return e.getValue();
            }
        }
        return null;
    }

    private static String readActiveCase(File activeCaseFile) {
        try {
            if (activeCaseFile != null && activeCaseFile.isFile()) {
                return Files.readString(activeCaseFile.toPath(), StandardCharsets.UTF_8).trim();
            }
        } catch (Exception e) {
            return "(erro ao ler: " + e.getMessage() + ")";
        }
        return null;
    }
}
