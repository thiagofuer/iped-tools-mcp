package br.com.ipedtools.mcp.prompts;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import br.com.ipedtools.mcp.VersionInfo;

/**
 * Text of the {@code start_case} prompt, in three parts: the methodology, which the user may customize; the sections
 * generated from the prompt arguments (investigation focus and case folder); and the conduct rules shipped with the
 * application, which come last and cannot be replaced.
 * <p>
 * The default methodology is the resource {@code prompts/start_case.md}. A user copy, created from it on request of the
 * configurator, lives in {@code <profile>/prompts/start_case.md} and is read on every request, so an edit applies
 * without restarting the server. Its first line records the hash of the default it was created from, so a newer
 * default is reported; that line is never sent to the model. The server only reads the user copy.
 */
public final class StartPromptStore {

    /** Which methodology is in use. */
    public enum State {
        /** No user copy: the default of this version. */
        DEFAULT,
        /** The user copy. */
        CUSTOM,
        /** The user copy, created from a default that differs from the one of this version. */
        CUSTOM_OUTDATED,
        /** The user copy exists but cannot be used: the default is in use. */
        CUSTOM_INVALID
    }

    /**
     * The methodology in use, its state, a detail for the user (reason of an invalid copy, version of an outdated
     * one, or whether the origin could not be checked) and the user file.
     */
    public record Methodology(String text, State state, String detail, File userFile) {
    }

    public static final String DEFAULT_RESOURCE = "/prompts/start_case.md";
    public static final String CONDUCT_RESOURCE = "/prompts/start_case-conduct.md";
    /** Largest user copy accepted. */
    public static final long MAX_BYTES = 200 * 1024;

    private static final String ORIGIN_PREFIX = "<!-- iped-tools-mcp start_case base: ";
    private static final Pattern ORIGIN = Pattern.compile(
            "^<!-- iped-tools-mcp start_case base: ([0-9a-f]{64})(?: vers\u00e3o (\\S+))? -->$");

    private final File profileDir;

    /** @param profileDir the application folder of the user profile ({@code ~/.iped-tools-mcp}) */
    public StartPromptStore(File profileDir) {
        this.profileDir = profileDir;
    }

    /** The store of the current user. */
    public static StartPromptStore forUser() {
        return new StartPromptStore(new File(System.getProperty("user.home"), ".iped-tools-mcp"));
    }

    /** The user copy of the methodology, whether it exists or not. */
    public File userFile() {
        return new File(new File(profileDir, "prompts"), "start_case.md");
    }

    /** The default methodology shipped with this version. */
    public static String defaultMethodology() {
        return resource(DEFAULT_RESOURCE);
    }

    /** The conduct rules and the closing sentence shipped with this version. */
    public static String conduct() {
        return resource(CONDUCT_RESOURCE);
    }

    private static String resource(String name) {
        try (InputStream in = StartPromptStore.class.getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalStateException("Recurso do prompt ausente: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The methodology in use: the user copy when it exists and is valid, otherwise the default. Never throws. */
    public Methodology load() {
        File file = userFile();
        String defaultText = defaultMethodology();
        if (!file.isFile()) {
            return new Methodology(defaultText, State.DEFAULT, null, file);
        }
        String text;
        try {
            if (file.length() > MAX_BYTES) {
                return invalid(defaultText, file, "o arquivo tem mais de " + (MAX_BYTES / 1024) + " KB");
            }
            byte[] bytes = Files.readAllBytes(file.toPath());
            text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return invalid(defaultText, file, "o arquivo não está em UTF-8");
        } catch (IOException e) {
            return invalid(defaultText, file, "o arquivo não pôde ser lido (" + e.getMessage() + ")");
        }
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }
        text = text.replace("\r\n", "\n");

        String originHash = null;
        String originVersion = null;
        int lineEnd = text.indexOf('\n');
        String firstLine = (lineEnd < 0 ? text : text.substring(0, lineEnd)).trim();
        if (firstLine.startsWith(ORIGIN_PREFIX)) {
            Matcher m = ORIGIN.matcher(firstLine);
            if (m.matches()) {
                originHash = m.group(1);
                originVersion = m.group(2);
            }
            text = lineEnd < 0 ? "" : text.substring(lineEnd + 1);
        }
        if (text.isBlank()) {
            return invalid(defaultText, file, "o arquivo está vazio");
        }
        if (originHash == null) {
            return new Methodology(text, State.CUSTOM, "não foi possível verificar de qual versão do padrão a cópia "
                    + "foi criada (a primeira linha de origem foi removida)", file);
        }
        if (!originHash.equals(sha256(defaultText))) {
            return new Methodology(text, State.CUSTOM_OUTDATED, "criada a partir do padrão da versão "
                    + (originVersion != null ? originVersion : "anterior") + "; o padrão desta versão mudou", file);
        }
        return new Methodology(text, State.CUSTOM, null, file);
    }

    private static Methodology invalid(String defaultText, File file, String reason) {
        return new Methodology(defaultText, State.CUSTOM_INVALID, reason, file);
    }

    /**
     * Creates the user copy from the default methodology, with the record of its origin.
     *
     * @return false when the copy already exists (it is left untouched)
     */
    public boolean createUserCopy() throws IOException {
        File file = userFile();
        if (file.exists()) {
            return false;
        }
        Files.createDirectories(file.getParentFile().toPath());
        String defaultText = defaultMethodology();
        String content = ORIGIN_PREFIX + sha256(defaultText) + " versão " + VersionInfo.getVersion() + " -->\n"
                + defaultText;
        Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
        return true;
    }

    /**
     * Restores the default: renames the user copy to a backup next to it.
     *
     * @return the backup, or null when there was no user copy
     */
    public File restoreDefault() throws IOException {
        File file = userFile();
        if (!file.exists()) {
            return null;
        }
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        File backup = new File(file.getParentFile(), file.getName() + ".bak-" + stamp);
        Files.move(file.toPath(), backup.toPath(), StandardCopyOption.ATOMIC_MOVE);
        return backup;
    }

    /** The full prompt for the given arguments, with the methodology in use. */
    public String build(String investigationTarget, String casePath) {
        return build(load().text(), investigationTarget, casePath);
    }

    /** The full prompt: methodology, generated sections and, last, the conduct rules. */
    public static String build(String methodology, String investigationTarget, String casePath) {
        StringBuilder sb = new StringBuilder(methodology.stripTrailing()).append('\n');

        if (investigationTarget != null && !investigationTarget.isBlank()) {
            sb.append("\n---\n\n## 🎯 FOCO DA INVESTIGAÇÃO ESPECIFICADO\n");
            sb.append("O examinador definiu o seguinte alvo ou foco temático para esta sessão:\n");
            sb.append("> **").append(investigationTarget.trim()).append("**\n\n");
            sb.append("- Formule hipóteses de busca e selecione categorias pertinentes ao redor deste objetivo.\n");
            sb.append("- Priorize palavras-chave, contatos, intervalos de datas ou propriedades do grupo AI diretamente alinhados a esse tema.\n");
        }

        if (casePath != null && !casePath.isBlank()) {
            sb.append("\n---\n\n## 📁 DIRETÓRIO DO CASO FORNECIDO\n");
            sb.append("Caminho indicado para o caso: `").append(casePath.trim()).append("`\n\n");
            sb.append("- Ao verificar o status com `get_server_status`, se o caso aberto for diferente ou se `case_open: false`, utilize a ferramenta `open_case(case_path=\"")
                    .append(casePath.trim().replace("\\", "\\\\"))
                    .append("\")` para carregar este caso.\n");
        }

        sb.append('\n').append(conduct());
        return sb.toString();
    }
}
