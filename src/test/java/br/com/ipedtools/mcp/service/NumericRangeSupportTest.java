package br.com.ipedtools.mcp.service;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class NumericRangeSupportTest {

    private static final Map<String, Class<?>> TYPES = Map.of(
            "audio:transcriptConfidence", Double.class,
            "ufed:mediaClassScore:Weapons", Double.class,
            "faceAge:maxScore:child", Double.class,
            "nudityScore", Integer.class);

    private static final Function<String, Class<?>> TYPE_OF = TYPES::get;

    private static final NumberFormat PT_BR = NumberFormat.getNumberInstance(Locale.of("pt", "BR"));
    private static final NumberFormat EN = NumberFormat.getNumberInstance(Locale.ENGLISH);

    @Test
    void extractsEscapedFieldNames() {
        List<NumericRangeSupport.RangeClause> r = NumericRangeSupport.extractRanges("ufed\\:mediaClassScore\\:Weapons:[0.5 TO *]");
        assertEquals(1, r.size());
        assertEquals("ufed:mediaClassScore:Weapons", r.getFirst().field());
        assertEquals("0.5", r.getFirst().lower());
        assertEquals("*", r.getFirst().upper());
    }

    @Test
    void extractsMultipleRangesAndExclusiveBrackets() {
        List<NumericRangeSupport.RangeClause> r = NumericRangeSupport.extractRanges(
                "category:Arma AND (nudityScore:[500 TO 1000] OR ocrCharCount:{0 TO *})");
        assertEquals(2, r.size());
        assertEquals("nudityScore", r.getFirst().field());
        assertEquals("ocrCharCount", r.get(1).field());
        assertEquals("0", r.get(1).lower());
    }

    @Test
    void queryWithoutRangeHasNoClauses() {
        assertTrue(NumericRangeSupport.extractRanges("category:\"chat messages\" AND content:propina").isEmpty());
        assertTrue(NumericRangeSupport.extractRanges(null).isEmpty());
    }

    @Test
    void dotDecimalBecomesCommaForPtBr() {
        assertEquals("audio\\:transcriptConfidence:[0,5 TO *]",
                NumericRangeSupport.normalizeDecimalSeparators("audio\\:transcriptConfidence:[0.5 TO *]", TYPE_OF, PT_BR));
    }

    @Test
    void integerAndWildcardBoundsAreKept() {
        assertEquals("faceAge\\:maxScore\\:child:[1 TO *]",
                NumericRangeSupport.normalizeDecimalSeparators("faceAge\\:maxScore\\:child:[1 TO *]", TYPE_OF, PT_BR));
    }

    @Test
    void scientificNotationIsExpanded() {
        assertEquals("ufed\\:mediaClassScore\\:Weapons:[0,001 TO 1]",
                NumericRangeSupport.normalizeDecimalSeparators("ufed\\:mediaClassScore\\:Weapons:[1e-3 TO 1]", TYPE_OF, PT_BR));
    }

    @Test
    void integerAndUnknownFieldsAreUntouched() {
        String q = "nudityScore:[500 TO 1000] AND size:[0.5 TO 2.5] AND name:[a TO b]";
        assertEquals(q, NumericRangeSupport.normalizeDecimalSeparators(q, TYPE_OF, PT_BR));
    }

    @Test
    void englishLocaleKeepsDot() {
        assertEquals("audio\\:transcriptConfidence:[0.5 TO 0.75]",
                NumericRangeSupport.normalizeDecimalSeparators("audio\\:transcriptConfidence:[0.5 TO 0.75]", TYPE_OF, EN));
    }

    @Test
    void alreadyLocalizedBoundIsKept() {
        assertEquals("audio\\:transcriptConfidence:[0,5 TO *]",
                NumericRangeSupport.normalizeDecimalSeparators("audio\\:transcriptConfidence:[0,5 TO *]", TYPE_OF, PT_BR));
    }
}
