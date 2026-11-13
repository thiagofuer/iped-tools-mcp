package br.com.ipedtools.mcp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.tika.metadata.Message;
import org.junit.jupiter.api.Test;

import iped.engine.datasource.UfedXmlReader;
import iped.engine.task.HashTask;
import iped.engine.task.LanguageDetectTask;
import iped.engine.task.PhotoDNALookup;
import iped.engine.task.die.DIETask;
import iped.engine.task.regex.RegexTask;
import iped.parsers.ocr.OCRParser;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;

/**
 * Checks the curated semantics (property-semantics.json) against the property names written by IPED, so that a
 * misspelled or renamed property (issue #2) fails the build instead of reaching the LLM.
 */
class PropertySemanticsTest {

    /** Exact property names, taken from IPED (and Tika) constants or, when IPED has no constant, from its sources. */
    static final Set<String> REFERENCE_NAMES = new HashSet<>();

    /** Name patterns (prefix + "*") for properties whose suffix is variable. */
    static final Set<String> REFERENCE_PATTERNS = new HashSet<>();

    static {
        // iped.engine.task.die.DIETask
        REFERENCE_NAMES.addAll(List.of(DIETask.DIE_SCORE, DIETask.DIE_CLASS, DIETask.DIE_RAW_SCORE));
        // iped.parsers.ocr.OCRParser
        REFERENCE_NAMES.add(OCRParser.OCR_CHAR_COUNT);
        // iped.properties.ExtraProperties
        REFERENCE_NAMES.addAll(List.of(ExtraProperties.FACE_COUNT, ExtraProperties.FACE_LOCATIONS,
                ExtraProperties.FACE_AGE_LABELS, ExtraProperties.TRANSCRIPT_ATTR, ExtraProperties.CONFIDENCE_ATTR,
                ExtraProperties.CSAM_HASH_HITS, ExtraProperties.HASHDB_STATUS, ExtraProperties.HASHDB_SET,
                ExtraProperties.COMMUNICATION_FROM, ExtraProperties.COMMUNICATION_TO,
                ExtraProperties.COMMUNICATION_DIRECTION, ExtraProperties.COMMUNICATION_DATE.getName(),
                ExtraProperties.PARTICIPANTS, ExtraProperties.CONVERSATION_ID, ExtraProperties.CONVERSATION_NAME,
                ExtraProperties.CONVERSATION_ACCOUNT, ExtraProperties.CONVERSATION_TYPE,
                ExtraProperties.CONVERSATION_PARTICIPANTS, ExtraProperties.CONVERSATION_ADMINS,
                ExtraProperties.GROUP_ID, ExtraProperties.IS_GROUP_MESSAGE, ExtraProperties.MESSAGE_BODY,
                ExtraProperties.MESSAGE_SUBJECT, ExtraProperties.MESSAGE_IS_ATTACHMENT,
                ExtraProperties.MESSAGE_ATTACHMENT_COUNT.getName(), ExtraProperties.URL, ExtraProperties.LOCAL_PATH,
                ExtraProperties.DOWNLOAD_TOTAL_BYTES, ExtraProperties.DOWNLOAD_RECEIVED_BYTES,
                ExtraProperties.USER_PHONE, ExtraProperties.USER_NAME, ExtraProperties.USER_ACCOUNT,
                ExtraProperties.USER_EMAIL, ExtraProperties.USER_ACCOUNT_TYPE, ExtraProperties.CONTACT_OF_ACCOUNT,
                ExtraProperties.LOCATIONS, ExtraProperties.UFED_COORDINATE_ID, ExtraProperties.UFED_ID,
                ExtraProperties.UFED_FILE_ID, ExtraProperties.UFED_JUMP_TARGETS));
        // org.apache.tika.metadata.Message (used by the IPED e-mail parsers)
        REFERENCE_NAMES.addAll(List.of(Message.MESSAGE_RECIPIENT_ADDRESS, Message.MESSAGE_CC, Message.MESSAGE_BCC,
                Message.MESSAGE_FROM_EMAIL.getName(), Message.MESSAGE_TO_EMAIL.getName()));
        // iped.properties.BasicProps and iped.engine.task.HashTask.HASH
        REFERENCE_NAMES.addAll(BasicProps.SET);
        for (HashTask.HASH h : HashTask.HASH.values()) {
            REFERENCE_NAMES.add(h.toString());
        }
        // iped.engine.datasource.UfedXmlReader (MEDIA_CLASSES_PROPERTY is private)
        REFERENCE_NAMES.add(ExtraProperties.UFED_META_PREFIX + "mediaClasses");
        REFERENCE_NAMES.add(UfedXmlReader.UFED_NATIVE_SCENE_CLASSIFICATION);
        // iped-parsers-impl iped/parsers/util/MetadataUtil.java (getRenameMap / getSingleValKeys, private)
        REFERENCE_NAMES.addAll(List.of(ExtraProperties.IMAGE_META_PREFIX + "Make", ExtraProperties.IMAGE_META_PREFIX + "Model",
                ExtraProperties.IMAGE_META_PREFIX + "Width", ExtraProperties.IMAGE_META_PREFIX + "Height",
                ExtraProperties.VIDEO_META_PREFIX + "Width", ExtraProperties.VIDEO_META_PREFIX + "Height"));

        // iped-app/resources/scripts/tasks/AgeEstimationTask.py
        REFERENCE_NAMES.addAll(List.of("faceAge:scores", "faceAge:estimationStatus"));
        REFERENCE_PATTERNS.addAll(List.of("faceAge:count:*", "faceAge:maxScore:*"));
        // iped-app/resources/scripts/tasks/CSAMDetectorTask.py
        REFERENCE_NAMES.addAll(List.of("ai:csamDetector:csam", "ai:csamDetector:porn", "ai:csamDetector:other",
                "ai:csamDetector:label", "ai:csamDetector:status", "ai:csamDetector:triggerFrame"));
        // iped-app/resources/scripts/tasks/NSFWNudityDetectTask.py
        REFERENCE_NAMES.addAll(List.of("nsfw_nudity_score", "nsfw_error"));
        // iped-app/resources/scripts/tasks/SearchHardwareWallets.py
        REFERENCE_NAMES.addAll(List.of("Hardware-Wallet-Found", "Hardware-Wallet-VendorID", "Hardware-Wallet-ProductID",
                "Hardware-Wallet-VendorName", "Hardware-Wallet-DeviceName"));

        // Variable-suffix properties
        REFERENCE_PATTERNS.add(ExtraProperties.UFED_META_PREFIX + "mediaClassScore:*"); // UfedXmlReader.MEDIA_CLASSES_SCORE_PREFIX
        REFERENCE_PATTERNS.add(PhotoDNALookup.PHOTO_DNA_HIT_PREFIX + "*");
        REFERENCE_PATTERNS.add(RegexTask.REGEX_PREFIX + "*");
        REFERENCE_PATTERNS.add(LanguageDetectTask.LANGUAGE_PREFIX + "*");
        // ExtraProperties.CONVERSATION_SUFFIX_* appended to Communication:From / Communication:To
        REFERENCE_PATTERNS.add(ExtraProperties.COMMUNICATION_FROM + ":*");
        REFERENCE_PATTERNS.add(ExtraProperties.COMMUNICATION_TO + ":*");
    }

    /** Returns the keys of the entries whose name or pattern is not written by IPED. */
    static List<String> divergentEntries(List<PropertySemantics.Entry> entries) {
        List<String> bad = new ArrayList<>();
        for (PropertySemantics.Entry e : entries) {
            boolean ok = e.name() != null ? REFERENCE_NAMES.contains(e.name()) : REFERENCE_PATTERNS.contains(e.pattern());
            if (!ok) {
                bad.add(e.key());
            }
        }
        return bad;
    }

    private static PropertySemantics.Entry entry(String name) {
        return new PropertySemantics.Entry(name, null, null, "x", null, null, null, List.of());
    }

    @Test
    void curatedFileLoads() {
        PropertySemantics semantics = PropertySemantics.load();
        assertTrue(semantics.entries().size() > 50, "catálogo curado deveria ter dezenas de entradas");
        for (PropertySemantics.Entry e : semantics.entries()) {
            assertNotNull(e.queryExamples(), "entrada sem exemplos de consulta: " + e.key());
            assertFalse(e.queryExamples().isEmpty(), "entrada sem exemplos de consulta: " + e.key());
            if (e.group() != null) {
                assertNotNull(PropertyGroups.resolve(e.group()), "grupo inválido em " + e.key() + ": " + e.group());
            }
        }
    }

    @Test
    void everyCuratedNameIsWrittenByIped() {
        List<String> bad = divergentEntries(PropertySemantics.load().entries());
        assertTrue(bad.isEmpty(), "Entradas curadas que não correspondem a nomes do IPED: " + bad);
    }

    @Test
    void misspelledEntryIsDetected() {
        // Message-AttachmentCount was listed by the old dictionary; IPED writes Message:AttachmentCount.
        assertEquals(List.of("Message-AttachmentCount"),
                divergentEntries(List.of(entry("Message-AttachmentCount"), entry("Message:AttachmentCount"))));
    }

    @Test
    void curatedNamesCoverTheAiGroup() {
        PropertySemantics semantics = PropertySemantics.load();
        for (String name : List.of("nudityScore", "nudityClass", "face_count", "faceAge:labels", "faceAge:count:child",
                "ai:csamDetector:label", "ocrCharCount", "audio:transcription", "audio:transcriptConfidence",
                "ufed:mediaClasses", "ufed:mediaClassScore:Weapons", "childPornHashHits", "hashDb:status")) {
            assertNotNull(semantics.find(name), "sem semântica curada: " + name);
        }
        assertNull(semantics.find("hasWeapon"));
    }

    @Test
    void mostSpecificPatternWins() {
        PropertySemantics semantics = new PropertySemantics(new ArrayList<>(List.of(
                new PropertySemantics.Entry(null, "ufed:*", null, "geral", null, null, null, List.of("a")),
                new PropertySemantics.Entry(null, "ufed:mediaClassScore:*", null, "score", null, null, null, List.of("b")))));
        assertEquals("score", semantics.find("ufed:mediaClassScore:Weapons").description());
        assertEquals("geral", semantics.find("ufed:id").description());
    }
}
