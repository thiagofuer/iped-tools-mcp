package br.com.ipedtools.mcp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import br.com.ipedtools.mcp.test.TestCaseResolver;
import br.com.ipedtools.mcp.tools.DocumentSearchTool;

/**
 * get_item_thumbnail delivers the requested size from the original image, beyond the thumbnail IPED stored during
 * processing. The image is chosen by search at run time.
 */
@EnabledIf("isCaseAvailable")
class ThumbnailResolutionIntegrationTest {

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

    @Test
    @SuppressWarnings("unchecked")
    void testLargeRequestComesFromTheOriginal() throws Exception {
        List<Map<String, Object>> items = (List<Map<String, Object>>) new DocumentSearchTool()
                .searchDocuments("type:jpg", 30, null).get("items");
        assumeTrue(items != null && !items.isEmpty(), "o caso de teste não tem imagens JPG");
        IpedCoreService service = IpedCoreService.getInstance();

        for (Map<String, Object> entry : items) {
            int id = (Integer) entry.get("id");
            Map<String, Object> small;
            Map<String, Object> large;
            try {
                small = service.getThumbnail(id, 128);
                large = service.getThumbnail(id, 2048);
            } catch (RuntimeException e) {
                continue;
            }
            Object ow = large.get("original_width");
            Object oh = large.get("original_height");
            if (ow == null || Math.max((Integer) ow, (Integer) oh) <= 512) {
                continue; // need an original clearly larger than the stored thumbnail
            }
            assertEquals(ItemImageLoader.SOURCE_THUMBNAIL, small.get("source"), "128 px cabe na miniatura do IPED");
            assertEquals(ItemImageLoader.SOURCE_ORIGINAL, large.get("source"));
            int longest = Math.max((Integer) large.get("width"), (Integer) large.get("height"));
            int expected = Math.min(2048, Math.max((Integer) ow, (Integer) oh));
            assertEquals(expected, longest, "a imagem deve ter o tamanho pedido, limitado ao original");
            assertTrue(longest > Math.max((Integer) small.get("width"), (Integer) small.get("height")));
            return;
        }
        assumeTrue(false, "nenhuma imagem com original maior que 512 px entre as primeiras do caso");
    }

    /** Names, sizes and modification times of the case module folder, to prove nothing was written there. */
    private static Map<String, String> snapshot(java.io.File dir) {
        Map<String, String> files = new java.util.TreeMap<>();
        java.io.File[] list = dir.listFiles();
        if (list != null) {
            for (java.io.File f : list) {
                files.put(f.getName(), f.length() + "|" + f.lastModified());
            }
        }
        return files;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> find(String query, int limit) {
        List<Map<String, Object>> items = (List<Map<String, Object>>) new DocumentSearchTool()
                .searchDocuments(query, limit, null).get("items");
        return items == null ? List.of() : items;
    }

    @Test
    void testPngLargerThanTheThumbnailComesFromTheOriginal() throws Exception {
        IpedCoreService service = IpedCoreService.getInstance();
        for (Map<String, Object> entry : find("contentType:\"image/png\"", 40)) {
            Map<String, Object> large;
            try {
                large = service.getThumbnail((Integer) entry.get("id"), 2048);
            } catch (RuntimeException e) {
                continue; // not decodable even by IPED (no thumbnail)
            }
            Object ow = large.get("original_width");
            Object oh = large.get("original_height");
            if (ow == null || Math.max((Integer) ow, (Integer) oh) <= 300) {
                continue;
            }
            assertEquals(ItemImageLoader.SOURCE_ORIGINAL, large.get("source"), "PNG deve vir do original");
            return;
        }
        assumeTrue(false, "nenhum PNG maior que 300 px entre os primeiros do caso");
    }

    @Test
    void testHeicIsConvertedWithoutWritingToTheCase() throws Exception {
        IpedCoreService service = IpedCoreService.getInstance();
        List<Map<String, Object>> heic = find("contentType:\"image/heic\"", 5);
        if (heic.isEmpty() && TestCaseResolver.isLabeledCaseAvailable(TestCaseResolver.LABEL_IOS)) {
            // iPhone photos are HEIC: try the labeled iOS extraction case
            service.openCase(TestCaseResolver.getLabeledCaseDir(TestCaseResolver.LABEL_IOS));
            heic = find("contentType:\"image/heic\"", 5);
        }
        assumeTrue(!heic.isEmpty(), "os casos de teste não têm fotos HEIC");
        java.io.File moduleDir = service.getSource().getModuleDir();
        Map<String, String> before = snapshot(moduleDir);

        boolean converted = false;
        for (Map<String, Object> entry : heic) {
            Map<String, Object> large;
            try {
                large = service.getThumbnail((Integer) entry.get("id"), 2048);
            } catch (RuntimeException e) {
                continue;
            }
            if (ItemImageLoader.SOURCE_CONVERTED.equals(large.get("source"))) {
                int longest = Math.max((Integer) large.get("width"), (Integer) large.get("height"));
                Object ow = large.get("original_width");
                Object oh = large.get("original_height");
                assertTrue(ow != null && oh != null, "a resposta deve trazer as dimensões originais registradas pelo IPED");
                int expected = Math.min(2048, Math.max((Integer) ow, (Integer) oh));
                assertEquals(expected, longest, "a foto convertida deve ter o tamanho pedido, limitado ao original");
                converted = true;
                break;
            }
        }
        assumeTrue(converted, "nenhuma foto HEIC convertida: conversão externa desativada ou indisponível no caso");
        assertEquals(before, snapshot(moduleDir), "a pasta iped do caso não pode mudar");
    }
}
