package br.com.ipedtools.mcp.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.utils.ImageUtil;
import iped.viewers.util.ImageMetadataUtil;

/**
 * Chooses the image delivered by {@code get_item_thumbnail}. The thumbnail IPED stored during processing has the
 * size of {@code imgThumbSize} (often 256 px); when a larger size is requested, the original item is decoded by
 * subsampling, as IPED does to build its thumbnails, with the EXIF orientation applied. The stored thumbnail is used
 * when it already reaches the requested size or when the original cannot be decoded. Images are never enlarged.
 */
public final class ItemImageLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(ItemImageLoader.class);

    public static final String SOURCE_ORIGINAL = "original";
    public static final String SOURCE_VIEW_FILE = "view_file";
    public static final String SOURCE_CONVERTED = "converted";
    public static final String SOURCE_THUMBNAIL = "thumbnail";
    public static final int MAX_DIMENSION = 2048;

    /** Opens a new stream over the original item content. */
    @FunctionalInterface
    public interface StreamSource {
        InputStream open() throws IOException;
    }

    /** Converts an image that Java cannot decode, already scaled to {@code target}; null when it cannot. */
    @FunctionalInterface
    public interface Converter {
        BufferedImage convert(StreamSource source, int target, Long size);
    }

    /**
     * The chosen image, its source and the original dimensions as displayed (after EXIF orientation), or -1 when
     * unknown.
     */
    public record Loaded(BufferedImage image, String source, int originalWidth, int originalHeight,
            boolean thumbnailFallback) {
    }

    private ItemImageLoader() {
    }

    /** The requested longest side, between 1 and {@link #MAX_DIMENSION}. */
    public static int target(int maxDim) {
        return Math.clamp(maxDim > 0 ? maxDim : 512, 1, MAX_DIMENSION);
    }

    /**
     * @param thumb    stored IPED thumbnail bytes, or null
     * @param original the original content, or null
     * @param viewFile the IPED view file, or null
     * @param maxDim   requested longest side
     * @return the image, or null when nothing could be decoded
     */
    public static Loaded load(byte[] thumb, StreamSource original, File viewFile, int maxDim) {
        return load(thumb, original, null, null, viewFile, maxDim);
    }

    /**
     * Same as {@link #load(byte[], StreamSource, File, int)}, with external conversion (null when disabled) tried
     * after the native decoders and before the view file.
     *
     * @param size the original size in bytes, used for the conversion time limit, or null
     */
    public static Loaded load(byte[] thumb, StreamSource original, Converter converter, Long size, File viewFile,
            int maxDim) {
        int target = target(maxDim);
        BufferedImage thumbImage = decode(thumb);
        int[] dims = original != null ? dimensions(original) : new int[] { -1, -1 };
        int orientation = original != null && dims[0] > 0 ? orientation(original) : 0;
        if (orientation > 4) {
            dims = new int[] { dims[1], dims[0] };
        }

        if (thumbImage != null && Math.max(thumbImage.getWidth(), thumbImage.getHeight()) >= target) {
            return new Loaded(fit(thumbImage, target), SOURCE_THUMBNAIL, dims[0], dims[1], false);
        }

        if (original != null && dims[0] > 0) {
            try (InputStream is = original.open()) {
                BufferedImage img = ImageUtil.getSubSampledImage(is, target);
                if (img != null) {
                    if (orientation > 1) {
                        img = ImageUtil.applyOrientation(img, orientation);
                    }
                    return new Loaded(fit(img, target), SOURCE_ORIGINAL, dims[0], dims[1], false);
                }
            } catch (Exception | LinkageError e) {
                LOGGER.debug("Falha ao decodificar a imagem original: {}", e.toString());
            }
        }

        if (converter != null && original != null) {
            try {
                BufferedImage img = converter.convert(original, target, size);
                if (img != null) {
                    return new Loaded(fit(img, target), SOURCE_CONVERTED, dims[0], dims[1], false);
                }
            } catch (Exception | LinkageError e) {
                LOGGER.debug("Falha na conversão externa: {}", e.toString());
            }
        }

        if (viewFile != null && viewFile.isFile()) {
            try {
                BufferedImage img = ImageUtil.getSubSampledImage(viewFile, target, null);
                if (img != null) {
                    return new Loaded(fit(img, target), SOURCE_VIEW_FILE, dims[0], dims[1], false);
                }
            } catch (Exception | LinkageError e) {
                LOGGER.debug("Falha ao decodificar o arquivo de visualização: {}", e.toString());
            }
        }

        if (thumbImage != null) {
            return new Loaded(fit(thumbImage, target), SOURCE_THUMBNAIL, dims[0], dims[1], true);
        }
        return null;
    }

    private static BufferedImage fit(BufferedImage img, int target) {
        if (img.getWidth() > target || img.getHeight() > target) {
            return ImageUtil.resizeImage(img, target, target, BufferedImage.TYPE_INT_RGB);
        }
        return img;
    }

    private static BufferedImage decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try {
            return ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            LOGGER.debug("Falha ao decodificar a miniatura do IPED: {}", e.toString());
            return null;
        }
    }

    /** Width and height read from the image header, without decoding the pixels; -1 when not an image. */
    static int[] dimensions(StreamSource source) {
        try (InputStream is = source.open(); ImageInputStream iis = ImageIO.createImageInputStream(is)) {
            if (iis == null) {
                return new int[] { -1, -1 };
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                return new int[] { -1, -1 };
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, true, true);
                return new int[] { reader.getWidth(0), reader.getHeight(0) };
            } finally {
                reader.dispose();
            }
        } catch (Exception | LinkageError e) {
            return new int[] { -1, -1 };
        }
    }

    private static int orientation(StreamSource source) {
        try (InputStream is = source.open()) {
            return ImageMetadataUtil.getOrientation(is);
        } catch (Exception | LinkageError e) {
            return 0;
        }
    }
}
