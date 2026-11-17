package br.com.ipedtools.mcp.service;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sqlite.SQLiteConfig;

/**
 * Reads the OCR text that IPED processing stored in the case, without running OCR and without writing to the case.
 * <p>
 * IPED's {@code OCRParser} stores each result in {@code <case>/iped/text/ocr-results.db}, table
 * {@code ocr(id TEXT PRIMARY KEY, text TEXT)}, keyed by the item hash. An image embedded in another item and
 * recognized while that item was parsed is keyed {@code <parent hash>-child-<n>}. The IPED "Text" tab shows this
 * text by re-parsing the item; here the database is opened read-only and queried directly.
 */
public class OcrTextStore implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(OcrTextStore.class);
    static final String RELATIVE_PATH = "text" + File.separator + "ocr-results.db";
    private static final String CHILD_PREFIX = "-child-";

    private final File dbFile;
    private Connection connection;
    private boolean unavailable;

    /** @param moduleDir the case module folder ({@code <case>/iped}) */
    public OcrTextStore(File moduleDir) {
        this.dbFile = new File(moduleDir, RELATIVE_PATH);
    }

    /**
     * The OCR text of the item with this hash: its own entry followed by the entries of images embedded in it, in
     * order. Empty when the case has no OCR database or no entry for the hash. Never throws.
     */
    public synchronized Optional<String> find(String hash) {
        if (hash == null || hash.isBlank()) {
            return Optional.empty();
        }
        Connection conn = connection();
        if (conn == null) {
            return Optional.empty();
        }
        for (String key : new String[] { hash.toUpperCase(), hash.toLowerCase() }) {
            try {
                List<String> parts = new ArrayList<>();
                try (PreparedStatement ps = conn.prepareStatement("SELECT text FROM ocr WHERE id = ?")) {
                    ps.setString(1, key);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next() && rs.getString(1) != null && !rs.getString(1).isBlank()) {
                            parts.add(rs.getString(1).trim());
                        }
                    }
                }
                List<long[]> order = new ArrayList<>();
                List<String> children = new ArrayList<>();
                try (PreparedStatement ps = conn.prepareStatement("SELECT id, text FROM ocr WHERE id LIKE ? ESCAPE '\\'")) {
                    ps.setString(1, key.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + CHILD_PREFIX + "%");
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            String text = rs.getString(2);
                            if (text == null || text.isBlank()) {
                                continue;
                            }
                            order.add(new long[] { childNumber(rs.getString(1)), children.size() });
                            children.add(text.trim());
                        }
                    }
                }
                order.sort((a, b) -> Long.compare(a[0], b[0]));
                for (long[] o : order) {
                    parts.add(children.get((int) o[1]));
                }
                if (!parts.isEmpty()) {
                    return Optional.of(String.join("\n\n", parts));
                }
            } catch (Exception e) {
                LOGGER.debug("Falha ao consultar o OCR do caso em {}: {}", dbFile, e.toString());
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static long childNumber(String id) {
        int i = id.lastIndexOf(CHILD_PREFIX);
        try {
            return Long.parseLong(id.substring(i + CHILD_PREFIX.length()));
        } catch (RuntimeException e) {
            return Long.MAX_VALUE;
        }
    }

    private Connection connection() {
        if (connection != null || unavailable) {
            return connection;
        }
        if (!dbFile.isFile()) {
            unavailable = true;
            return null;
        }
        try {
            SQLiteConfig config = new SQLiteConfig();
            config.setReadOnly(true);
            String url = "jdbc:sqlite:file:" + dbFile.toURI().getRawPath() + "?mode=ro";
            connection = DriverManager.getConnection(url, config.toProperties());
        } catch (Exception e) {
            unavailable = true;
            LOGGER.debug("Banco de OCR do caso indisponível ({}): {}", dbFile, e.toString());
        }
        return connection;
    }

    @Override
    public synchronized void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (Exception ignored) {
            } finally {
                connection = null;
            }
        }
    }
}
