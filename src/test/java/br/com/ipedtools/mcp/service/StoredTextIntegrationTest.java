package br.com.ipedtools.mcp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import br.com.ipedtools.mcp.test.TestCaseResolver;
import br.com.ipedtools.mcp.tools.DocumentSearchTool;
import iped.data.IItem;

/**
 * get_document_text returns the OCR text and the audio transcription that IPED processing stored in the case, as the
 * IPED "Text" tab shows them, without changing the case. Items are chosen by search at run time.
 */
@EnabledIf("isCaseAvailable")
class StoredTextIntegrationTest {

    static boolean isCaseAvailable() {
        return TestCaseResolver.isCaseAvailable();
    }

    @BeforeAll
    static void open() {
        IpedCoreService.getInstance().openCase(TestCaseResolver.getPrimaryCaseDir());
    }

    @AfterAll
    static void close() {
        IpedCoreService.getInstance().closeCase();
    }

    @SuppressWarnings("unchecked")
    private static Optional<Integer> firstItem(String query) {
        List<Map<String, Object>> items = (List<Map<String, Object>>) new DocumentSearchTool()
                .searchDocuments(query, 1, null).get("items");
        return items == null || items.isEmpty() ? Optional.empty() : Optional.of((Integer) items.getFirst().get("id"));
    }

    /** Names, sizes and modification times of the case text folder, to prove nothing was written there. */
    private static Map<String, String> snapshot(File dir) {
        Map<String, String> files = new TreeMap<>();
        File[] list = dir.listFiles();
        if (list != null) {
            for (File f : list) {
                files.put(f.getName(), f.length() + "|" + f.lastModified());
            }
        }
        return files;
    }

    @Test
    void testOcrTextIsReturnedFromTheCaseWithoutWritingToIt() throws Exception {
        Optional<Integer> id = firstItem("ocrCharCount:[50 TO *]");
        assumeTrue(id.isPresent(), "o caso de teste não tem itens com OCR");
        IpedCoreService service = IpedCoreService.getInstance();
        File moduleDir = service.getSource().getModuleDir();
        File textDir = new File(moduleDir, "text");
        Map<String, String> before = snapshot(textDir);

        IItem item = service.getSource().getItemByID(id.get());
        Optional<String> stored;
        try (OcrTextStore store = new OcrTextStore(moduleDir)) {
            stored = store.find(item.getHash());
        }
        assumeTrue(stored.isPresent(), "o OCR deste item não está no cache do caso");

        String text = service.getDocumentText(id.get(), 0, 6000);
        assertTrue(text.startsWith(IpedCoreService.OCR_SECTION), "a seção de OCR deve abrir a resposta");
        String expected = stored.get().length() > 200 ? stored.get().substring(0, 200) : stored.get();
        assertTrue(text.contains(expected), "a resposta deve trazer o texto de OCR gravado no caso");

        assertEquals(before, snapshot(textDir), "a pasta text do caso não pode mudar");
    }

    @Test
    void testTranscriptionComesInTheFirstChunk() {
        Optional<Integer> id = firstItem("audio\\:transcription:*");
        assumeTrue(id.isPresent(), "o caso de teste não tem áudios transcritos");
        String text = IpedCoreService.getInstance().getDocumentText(id.get(), 0, 3500);
        assertTrue(text.contains(IpedCoreService.TRANSCRIPT_SECTION),
                "a transcrição deve estar no primeiro bloco: " + text.substring(0, Math.min(80, text.length())));
        assertTrue(text.indexOf(IpedCoreService.TRANSCRIPT_SECTION) < 200, "a transcrição deve vir antes do texto do arquivo");
    }
}
