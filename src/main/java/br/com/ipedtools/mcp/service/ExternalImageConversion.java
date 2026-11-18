package br.com.ipedtools.mcp.service;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.engine.config.Configuration;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.ImageThumbTaskConfig;
import iped.utils.ExternalImageConverter;

/**
 * Converts images that Java cannot decode (HEIC, SVG, JPEG 2000, EMF...) with the tool IPED uses for the case:
 * ImageMagick or GraphicsMagick from {@code <case>/iped/tools}, as configured by {@code enableExternalConv} in the
 * case {@code ImageThumbsConfig.txt}. The image goes in through stdin and comes out through stdout; the tool temporary
 * files go to the system temporary folder, never to the case. Each conversion has the IPED time limit.
 */
public final class ExternalImageConversion {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExternalImageConversion.class);
    private static volatile boolean prepared;
    private static volatile boolean enabled;

    private ExternalImageConversion() {
    }

    /**
     * Sets the IPED conversion properties from the case configuration, once per process (the IPED configuration is
     * loaded once per process too), as {@code ImageThumbTask.init} does. Never throws.
     */
    public static synchronized void prepare() {
        if (prepared) {
            return;
        }
        prepared = true;
        try {
            ImageThumbTaskConfig config = ConfigurationManager.get().findObject(ImageThumbTaskConfig.class);
            if (config == null || !config.isEnableExternalConv()) {
                LOGGER.info("Conversão externa de imagens desativada na configuração do caso.");
                return;
            }
            System.setProperty(ExternalImageConverter.enabledProp, "true");
            System.setProperty(ExternalImageConverter.useGMProp, String.valueOf(config.isUseGraphicsMagick()));
            System.setProperty(ExternalImageConverter.lowDensityProp, String.valueOf(config.getLowResDensity()));
            System.setProperty(ExternalImageConverter.highDensityProp, String.valueOf(config.getHighResDensity()));
            System.setProperty(ExternalImageConverter.magickAreaLimitProp, String.valueOf(config.getMaxMPixelsInMemory()));
            System.setProperty(ExternalImageConverter.minTimeoutProp, String.valueOf(config.getMinTimeout()));
            System.setProperty(ExternalImageConverter.timeoutPerMBProp, String.valueOf(config.getTimeoutPerMB()));
            if (System.getProperty("os.name", "").toLowerCase().startsWith("windows")) {
                System.setProperty(ExternalImageConverter.winToolPathPrefixProp, Configuration.getInstance().appRoot);
            }
            File tmpDir = new File(System.getProperty("java.io.tmpdir"), "iped-tools-mcp-ext-conv");
            tmpDir.mkdirs();
            System.setProperty(ExternalImageConverter.tmpDirProp, tmpDir.getAbsolutePath());
            enabled = true;
            LOGGER.info("Conversão externa de imagens ativa ({}), com a ferramenta do caso.",
                    config.isUseGraphicsMagick() ? "GraphicsMagick" : "ImageMagick");
        } catch (Throwable t) {
            LOGGER.warn("Falha ao preparar a conversão externa de imagens: {}", t.toString());
        }
    }

    /** The converter for {@link ItemImageLoader}, or null when conversion is disabled. */
    public static ItemImageLoader.Converter converter() {
        if (!enabled) {
            return null;
        }
        return (source, target, size) -> {
            try (ExternalImageConverter converter = new ExternalImageConverter(); InputStream is = source.open()) {
                return converter.getImage(is, target, target > 512, size);
            } catch (Throwable t) {
                LOGGER.debug("Falha na conversão externa da imagem: {}", t.toString());
                return (BufferedImage) null;
            }
        };
    }
}
