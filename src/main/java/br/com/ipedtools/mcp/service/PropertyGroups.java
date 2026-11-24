package br.com.ipedtools.mcp.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.apache.tika.metadata.Message;

import iped.engine.datasource.UfedXmlReader;
import iped.engine.task.HashTask;
import iped.engine.task.LanguageDetectTask;
import iped.engine.task.NamedEntityTask;
import iped.engine.task.PhotoDNALookup;
import iped.engine.task.die.DIETask;
import iped.engine.task.index.IndexItem;
import iped.engine.task.regex.RegexTask;
import iped.parsers.evtx.EvtxParser;
import iped.parsers.ocr.OCRParser;
import iped.parsers.standard.StandardParser;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;

/**
 * Classifies IPED property names into catalog groups. The prefixes follow the column groups of the IPED
 * SearchApp ({@code iped.app.ui.columns.ColumnsManager}), rebuilt here from the IPED constants because the
 * {@code iped-app} module is not a dependency of the server. AI and HashDB come first so that, for example,
 * {@code audio:transcription} and {@code ufed:mediaClassScore:*} are grouped as AI instead of Audio and UFED.
 */
public final class PropertyGroups {

    public static final String AI = "AI";
    public static final String HASHDB = "HashDB";
    public static final String BASIC = "Basic";
    public static final String OTHER = "Other";

    /** Groups in display order, with a short pt-BR description. */
    public static final Map<String, String> DESCRIPTIONS;

    private static final Set<String> AI_EXACT = Set.of(
            DIETask.DIE_SCORE, DIETask.DIE_CLASS, DIETask.DIE_RAW_SCORE,
            ExtraProperties.FACE_COUNT, ExtraProperties.FACE_LOCATIONS,
            OCRParser.OCR_CHAR_COUNT,
            ExtraProperties.CONFIDENCE_ATTR);

    private static final List<String> AI_PREFIXES = List.of(
            "faceAge:", // AgeEstimationTask.py
            "ai:", // CSAMDetectorTask.py and other AI scripts
            "nsfw", // NSFWNudityDetectTask.py
            ExtraProperties.TRANSCRIPT_ATTR,
            ExtraProperties.UFED_META_PREFIX + "mediaClass", // UfedXmlReader.MEDIA_CLASSES_PROPERTY / SCORE_PREFIX
            UfedXmlReader.UFED_NATIVE_SCENE_CLASSIFICATION);

    private static final Set<String> HASHDB_EXACT = Set.of(ExtraProperties.CSAM_HASH_HITS);

    private static final List<String> HASHDB_PREFIXES = List.of(
            ExtraProperties.HASHDB_PREFIX, PhotoDNALookup.PHOTO_DNA_HIT_PREFIX, "photoDna");

    /** Prefix groups, checked in this order (same order as the SearchApp column groups). */
    private static final Map<String, List<String>> PREFIX_GROUPS = new LinkedHashMap<>();

    private static final Set<String> COMMUNICATION_EXACT = new LinkedHashSet<>();

    private static final Set<String> BASIC_FIELDS = new LinkedHashSet<>();

    static {
        PREFIX_GROUPS.put("Regex", List.of(RegexTask.REGEX_PREFIX));
        PREFIX_GROUPS.put("Language", List.of(LanguageDetectTask.LANGUAGE_PREFIX));
        PREFIX_GROUPS.put("Audio", List.of(ExtraProperties.AUDIO_META_PREFIX));
        PREFIX_GROUPS.put("Image", List.of(ExtraProperties.IMAGE_META_PREFIX));
        PREFIX_GROUPS.put("Video", List.of(ExtraProperties.VIDEO_META_PREFIX));
        PREFIX_GROUPS.put("PDF", List.of(ExtraProperties.PDF_META_PREFIX));
        PREFIX_GROUPS.put("Office", List.of(ExtraProperties.OFFICE_META_PREFIX));
        PREFIX_GROUPS.put("HTML", List.of(ExtraProperties.HTML_META_PREFIX));
        PREFIX_GROUPS.put("NER", List.of(NamedEntityTask.NER_PREFIX));
        PREFIX_GROUPS.put("P2P", List.of(ExtraProperties.P2P_META_PREFIX));
        PREFIX_GROUPS.put("UFED", List.of(ExtraProperties.UFED_META_PREFIX));
        PREFIX_GROUPS.put("Communication", List.of(ExtraProperties.MESSAGE_PREFIX, Message.MESSAGE_PREFIX,
                ExtraProperties.CONVERSATION_PREFIX, ExtraProperties.COMMUNICATION_PREFIX));
        PREFIX_GROUPS.put("Common", List.of(ExtraProperties.COMMON_META_PREFIX));
        PREFIX_GROUPS.put("WindowsEvt", List.of(EvtxParser.EVTX_METADATA_PREFIX));

        COMMUNICATION_EXACT.addAll(ExtraProperties.COMMUNICATION_BASIC_PROPS);
        COMMUNICATION_EXACT.add(ExtraProperties.GROUP_ID);
        COMMUNICATION_EXACT.add(ExtraProperties.IS_GROUP_MESSAGE);

        BASIC_FIELDS.addAll(BasicProps.SET);
        for (HashTask.HASH h : HashTask.HASH.values()) {
            BASIC_FIELDS.add(h.toString());
        }
        BASIC_FIELDS.addAll(List.of(IndexItem.ID_IN_SOURCE, IndexItem.SOURCE_PATH, IndexItem.SOURCE_DECODER,
                StandardParser.PARSER_EXCEPTION, BasicProps.META_ADDRESS, BasicProps.MFT_SEQUENCE,
                BasicProps.FILESYSTEM_ID));

        Map<String, String> d = new LinkedHashMap<>();
        d.put(BASIC, "Propriedades básicas do item: nome, caminho, categoria, tipo, tamanho, datas, hashes, apagado/recuperado.");
        d.put(AI, "Resultados de módulos de IA e visão computacional: nudez (DIE/NSFW), faces e idade estimada, detector de CSAM, OCR, transcrição de áudio e classificadores de mídia do UFED.");
        d.put(HASHDB, "Correspondências com bases de hashes conhecidos (hashDb, PhotoDNA, CSAM).");
        d.put("Communication", "Mensagens, chats, e-mails e chamadas: remetente, destinatário, direção, data, participantes e conversa.");
        d.put("Common", "Metadados comuns de documentos e mídias (autor, título, geolocalização).");
        d.put("Regex", "Ocorrências encontradas por expressões regulares (telefones, e-mails, CPF, placas, URLs, carteiras de criptomoedas).");
        d.put("Language", "Idiomas detectados no texto e respectivas pontuações.");
        d.put("Audio", "Metadados técnicos de arquivos de áudio.");
        d.put("Image", "Metadados de imagens (EXIF: dimensões, câmera, datas).");
        d.put("Video", "Metadados de arquivos de vídeo.");
        d.put("PDF", "Metadados de documentos PDF.");
        d.put("Office", "Metadados de documentos do Office.");
        d.put("HTML", "Metadados de páginas HTML.");
        d.put("NER", "Entidades nomeadas extraídas do texto (pessoas, locais, organizações).");
        d.put("P2P", "Artefatos de compartilhamento ponto a ponto.");
        d.put("UFED", "Propriedades de extrações Cellebrite UFED.");
        d.put("WindowsEvt", "Campos de eventos do Windows (EVTX).");
        d.put(OTHER, "Demais propriedades gravadas pelos parsers e módulos do IPED.");
        DESCRIPTIONS = Collections.unmodifiableMap(d);
    }

    private PropertyGroups() {
    }

    /** Classifies a property name without curated overrides. */
    public static String classify(String name) {
        return classify(name, n -> null);
    }

    /**
     * Classifies a property name. A non-null group returned by {@code curatedGroup} wins over every rule.
     */
    public static String classify(String name, Function<String, String> curatedGroup) {
        if (name == null || name.isEmpty()) {
            return OTHER;
        }
        String curated = curatedGroup.apply(name);
        if (curated != null) {
            return curated;
        }
        if (AI_EXACT.contains(name) || startsWithAny(name, AI_PREFIXES)) {
            return AI;
        }
        if (HASHDB_EXACT.contains(name) || startsWithAny(name, HASHDB_PREFIXES)) {
            return HASHDB;
        }
        for (Map.Entry<String, List<String>> e : PREFIX_GROUPS.entrySet()) {
            if (startsWithAny(name, e.getValue())) {
                return e.getKey();
            }
        }
        if (COMMUNICATION_EXACT.contains(name)) {
            return "Communication";
        }
        if (BASIC_FIELDS.contains(name)) {
            return BASIC;
        }
        return OTHER;
    }

    /** Group names in display order. */
    public static List<String> names() {
        return new ArrayList<>(DESCRIPTIONS.keySet());
    }

    /** Returns the canonical group name for a user-provided name (case-insensitive), or null. */
    public static String resolve(String group) {
        if (group == null) {
            return null;
        }
        for (String g : DESCRIPTIONS.keySet()) {
            if (g.equalsIgnoreCase(group.trim())) {
                return g;
            }
        }
        return null;
    }

    private static boolean startsWithAny(String name, List<String> prefixes) {
        for (String p : prefixes) {
            if (name.startsWith(p)) {
                return true;
            }
        }
        return false;
    }
}
