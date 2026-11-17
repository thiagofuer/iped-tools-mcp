package br.com.ipedtools.mcp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.HexFormat;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** OCR stored by IPED processing is read from the case database without changing it. */
class OcrTextStoreTest {

    private static final String HASH = "0123456789ABCDEF0123456789ABCDEF";

    private static File createDb(Path moduleDir, String[][] rows) throws Exception {
        File db = moduleDir.resolve(OcrTextStore.RELATIVE_PATH).toFile();
        Files.createDirectories(db.getParentFile().toPath());
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db.getAbsolutePath());
                Statement st = c.createStatement()) {
            st.executeUpdate("CREATE TABLE ocr(id TEXT PRIMARY KEY, text TEXT)");
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO ocr(id, text) VALUES(?, ?)")) {
                for (String[] r : rows) {
                    ps.setString(1, r[0]);
                    ps.setString(2, r[1]);
                    ps.executeUpdate();
                }
            }
        }
        return db;
    }

    private static String digest(File f) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(f.toPath())));
    }

    @Test
    void testFindsExactHashInAnyCase(@TempDir Path moduleDir) throws Exception {
        createDb(moduleDir, new String[][] { { HASH, "texto da imagem" }, { "FFFF", "outro" } });
        try (OcrTextStore store = new OcrTextStore(moduleDir.toFile())) {
            assertEquals(Optional.of("texto da imagem"), store.find(HASH));
            assertEquals(Optional.of("texto da imagem"), store.find(HASH.toLowerCase()));
            assertEquals(Optional.empty(), store.find("0000"));
        }
    }

    @Test
    void testJoinsEmbeddedImagesInOrder(@TempDir Path moduleDir) throws Exception {
        createDb(moduleDir, new String[][] { { HASH + "-child-10", "dez" }, { HASH + "-child-2", "dois" },
                { HASH, "principal" } });
        try (OcrTextStore store = new OcrTextStore(moduleDir.toFile())) {
            assertEquals(Optional.of("principal\n\ndois\n\ndez"), store.find(HASH));
        }
    }

    @Test
    void testOnlyEmbeddedImages(@TempDir Path moduleDir) throws Exception {
        createDb(moduleDir, new String[][] { { HASH + "-child-0", "pagina" } });
        try (OcrTextStore store = new OcrTextStore(moduleDir.toFile())) {
            assertEquals(Optional.of("pagina"), store.find(HASH));
        }
    }

    @Test
    void testWithoutDatabase(@TempDir Path moduleDir) {
        try (OcrTextStore store = new OcrTextStore(moduleDir.toFile())) {
            assertEquals(Optional.empty(), store.find(HASH));
        }
        assertTrue(!moduleDir.resolve("text").toFile().exists(), "nada deve ser criado na pasta do caso");
    }

    @Test
    void testDoesNotChangeTheDatabase(@TempDir Path moduleDir) throws Exception {
        File db = createDb(moduleDir, new String[][] { { HASH, "texto" } });
        String before = digest(db);
        long modified = db.lastModified();
        String[] filesBefore = db.getParentFile().list();
        try (OcrTextStore store = new OcrTextStore(moduleDir.toFile())) {
            store.find(HASH);
            store.find("0000");
        }
        assertEquals(before, digest(db));
        assertEquals(modified, db.lastModified());
        assertEquals(java.util.Arrays.asList(filesBefore), java.util.Arrays.asList(db.getParentFile().list()));
    }
}
