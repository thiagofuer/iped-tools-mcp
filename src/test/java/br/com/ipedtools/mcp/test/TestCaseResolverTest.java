package br.com.ipedtools.mcp.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class TestCaseResolverTest {

    @Test
    void unconfiguredLabelIsUnavailableInsteadOfFailing() {
        String label = "rotulo_inexistente_para_teste";
        assertNull(TestCaseResolver.getLabeledCaseDir(label));
        assertFalse(TestCaseResolver.isLabeledCaseAvailable(label));
    }

    @Test
    void labeledPropertyFollowsNamingConvention() {
        assertEquals("iped.test.case.ios.path", TestCaseResolver.labeledPropertyName(TestCaseResolver.LABEL_IOS));
        assertEquals("iped.test.case.fat.path", TestCaseResolver.labeledPropertyName(TestCaseResolver.LABEL_FAT));
    }
}
