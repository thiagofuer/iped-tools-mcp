package br.com.ipedtools.mcp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

/** Source choice and size of the image delivered by get_item_thumbnail, with images generated in the test. */
class ItemImageLoaderTest {

    private static byte[] jpeg(int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, w / 2, h / 4);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", out);
        return out.toByteArray();
    }

    /** Inserts an EXIF APP1 segment with the given orientation right after the JPEG SOI marker. */
    private static byte[] withOrientation(byte[] jpeg, int orientation) {
        byte[] tiff = {
                'M', 'M', 0, 42, 0, 0, 0, 8, // big-endian TIFF header, IFD at offset 8
                0, 1, // one entry
                0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, (byte) orientation, 0, 0, // Orientation, SHORT, 1 value
                0, 0, 0, 0 }; // no next IFD
        byte[] exif = "Exif\0\0".getBytes(StandardCharsets.ISO_8859_1);
        int len = 2 + exif.length + tiff.length;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(jpeg, 0, 2);
        out.write(0xFF);
        out.write(0xE1);
        out.write(len >> 8);
        out.write(len & 0xFF);
        out.writeBytes(exif);
        out.writeBytes(tiff);
        out.write(jpeg, 2, jpeg.length - 2);
        return out.toByteArray();
    }

    private static ItemImageLoader.StreamSource source(byte[] bytes) {
        return () -> new ByteArrayInputStream(bytes);
    }

    private static int longest(BufferedImage img) {
        return Math.max(img.getWidth(), img.getHeight());
    }

    @Test
    void testLargeOriginalIsUsedWhenThumbnailIsSmaller() throws Exception {
        ItemImageLoader.Loaded loaded = ItemImageLoader.load(jpeg(256, 171), source(jpeg(3000, 2000)), null, 2048);
        assertNotNull(loaded);
        assertEquals(ItemImageLoader.SOURCE_ORIGINAL, loaded.source());
        assertEquals(2048, longest(loaded.image()));
        assertEquals(3000, loaded.originalWidth());
        assertEquals(2000, loaded.originalHeight());
        assertFalse(loaded.thumbnailFallback());
    }

    @Test
    void testThumbnailIsUsedWhenItReachesTheRequestedSize() throws Exception {
        ItemImageLoader.Loaded loaded = ItemImageLoader.load(jpeg(256, 171), source(jpeg(3000, 2000)), null, 128);
        assertEquals(ItemImageLoader.SOURCE_THUMBNAIL, loaded.source());
        assertEquals(128, longest(loaded.image()));
        assertFalse(loaded.thumbnailFallback());
    }

    @Test
    void testSmallOriginalIsNeverEnlarged() throws Exception {
        ItemImageLoader.Loaded loaded = ItemImageLoader.load(jpeg(256, 171), source(jpeg(600, 400)), null, 2048);
        assertEquals(ItemImageLoader.SOURCE_ORIGINAL, loaded.source());
        assertEquals(600, loaded.image().getWidth());
        assertEquals(400, loaded.image().getHeight());
    }

    @Test
    void testUndecodableOriginalFallsBackToThumbnail() throws Exception {
        byte[] notAnImage = "conteúdo que não é imagem".getBytes(StandardCharsets.UTF_8);
        ItemImageLoader.Loaded loaded = ItemImageLoader.load(jpeg(256, 171), source(notAnImage), null, 2048);
        assertEquals(ItemImageLoader.SOURCE_THUMBNAIL, loaded.source());
        assertTrue(loaded.thumbnailFallback());
        assertEquals(256, longest(loaded.image()));
        assertEquals(-1, loaded.originalWidth());
    }

    @Test
    void testNothingDecodable() {
        byte[] notAnImage = { 1, 2, 3, 4 };
        assertNull(ItemImageLoader.load(null, source(notAnImage), null, 512));
    }

    @Test
    void testExifOrientationIsApplied() throws Exception {
        byte[] rotated = withOrientation(jpeg(1200, 800), 6); // 90 degrees clockwise
        ItemImageLoader.Loaded loaded = ItemImageLoader.load(null, source(rotated), null, 2048);
        assertEquals(ItemImageLoader.SOURCE_ORIGINAL, loaded.source());
        assertEquals(800, loaded.image().getWidth());
        assertEquals(1200, loaded.image().getHeight());
        assertEquals(800, loaded.originalWidth());
        assertEquals(1200, loaded.originalHeight());
    }

    @Test
    void testTargetIsCapped() {
        assertEquals(2048, ItemImageLoader.target(5000));
        assertEquals(512, ItemImageLoader.target(0));
    }
}
