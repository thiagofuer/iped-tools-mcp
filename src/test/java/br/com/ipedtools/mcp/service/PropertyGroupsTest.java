package br.com.ipedtools.mcp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class PropertyGroupsTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', value = {
            "nudityScore|AI",
            "nudityClass|AI",
            "face_count|AI",
            "faceAge:labels|AI",
            "faceAge:maxScore:child|AI",
            "ai:csamDetector:label|AI",
            "ocrCharCount|AI",
            "audio:transcription|AI",
            "audio:transcriptConfidence|AI",
            "ufed:mediaClassScore:Weapons|AI",
            "ufed:mediaClasses|AI",
            "nsfw_nudity_score|AI",
            "hashDb:status|HashDB",
            "childPornHashHits|HashDB",
            "photoDnaDb:md5|HashDB",
            "Communication:From|Communication",
            "Message-Body|Communication",
            "Message:AttachmentCount|Communication",
            "Conversation:Name|Communication",
            "isGroupMessage|Communication",
            "image:width|Image",
            "audio:channels|Audio",
            "Regex:PHONE|Regex",
            "language:detected_1|Language",
            "ufed:EntryName|UFED",
            "common:geo:locations|Common",
            "WinEvt:EventID|WindowsEvt",
            "NER_PERSON|NER",
            "name|Basic",
            "size|Basic",
            "md5|Basic",
            "sha-256|Basic",
            "created|Basic",
            "hasWeapon|Other",
            "QRCodeText|Other"
    })
    void classifiesByIpedPrefixes(String name, String group) {
        assertEquals(group, PropertyGroups.classify(name));
    }

    @Test
    void curatedGroupWins() {
        assertEquals("Communication", PropertyGroups.classify("phoneNumber", n -> "Communication"));
    }

    @Test
    void resolvesGroupNamesIgnoringCase() {
        assertEquals("AI", PropertyGroups.resolve("ai"));
        assertEquals("HashDB", PropertyGroups.resolve(" hashdb "));
        assertNull(PropertyGroups.resolve("inexistente"));
    }
}
