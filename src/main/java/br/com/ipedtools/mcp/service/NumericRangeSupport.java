package br.com.ipedtools.mcp.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.lucene.document.DoublePoint;
import org.apache.lucene.document.FloatPoint;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.PointValues;

/**
 * Helpers for Lucene range clauses ({@code field:[a TO b]}) over numeric extra properties:
 * decimal separator normalization and zero-result diagnostics (issue #7).
 */
public final class NumericRangeSupport {

    /** field (with Lucene escapes) : open bracket, lower bound, TO, upper bound, close bracket. */
    private static final Pattern RANGE = Pattern.compile(
            "((?:\\\\.|[^\\s:()\\[\\]{}\"\\\\])+)\\s*:\\s*([\\[{])\\s*(\\S+?)\\s+TO\\s+(\\S+?)\\s*([]}])");

    private static final Pattern DOT_DECIMAL = Pattern.compile("[-+]?\\d*\\.\\d+(?:[eE][-+]?\\d+)?|[-+]?\\d+(?:\\.\\d+)?[eE][-+]?\\d+");

    private NumericRangeSupport() {
    }

    /** A range clause found in a query. */
    public record RangeClause(String field, String lower, String upper) {
    }

    /** Removes Lucene escape backslashes from a field name ({@code audio\:x} becomes {@code audio:x}). */
    static String unescapeField(String field) {
        return field.replaceAll("\\\\(.)", "$1");
    }

    /** Returns every range clause of the query, with unescaped field names. */
    public static List<RangeClause> extractRanges(String query) {
        List<RangeClause> result = new ArrayList<>();
        if (query == null) {
            return result;
        }
        Matcher m = RANGE.matcher(query);
        while (m.find()) {
            result.add(new RangeClause(unescapeField(m.group(1)), m.group(3), m.group(4)));
        }
        return result;
    }

    static boolean isFloatingType(Class<?> type) {
        return Float.class.equals(type) || Double.class.equals(type);
    }

    static boolean isNumericType(Class<?> type) {
        return type != null && (Integer.class.equals(type) || Long.class.equals(type) || Short.class.equals(type)
                || Byte.class.equals(type) || isFloatingType(type));
    }

    /**
     * Rewrites dot-decimal bounds ({@code 0.5}, {@code 1e-3}) of ranges over Float/Double fields into the number
     * format the IPED query parser expects for the given locale format. Other clauses are left untouched.
     */
    public static String normalizeDecimalSeparators(String query, Function<String, Class<?>> typeOf, NumberFormat localeFormat) {
        if (query == null || query.isEmpty()) {
            return query;
        }
        NumberFormat nf = (NumberFormat) localeFormat.clone();
        nf.setGroupingUsed(false);
        nf.setMaximumFractionDigits(340);

        Matcher m = RANGE.matcher(query);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String field = unescapeField(m.group(1));
            String replacement = m.group();
            if (isFloatingType(typeOf.apply(field))) {
                replacement = m.group(1) + ":" + m.group(2) + localizeBound(m.group(3), nf) + " TO "
                        + localizeBound(m.group(4), nf) + m.group(5);
            }
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static String localizeBound(String bound, NumberFormat nf) {
        if (!DOT_DECIMAL.matcher(bound).matches()) {
            return bound;
        }
        return nf.format(new BigDecimal(bound));
    }

    /**
     * Builds one warning per distinct range field of a query that returned no items: absent property,
     * non-numeric property, or numeric type with the minimum and maximum values present in the case.
     */
    public static List<Map<String, Object>> zeroResultWarnings(String query, IndexReader reader,
            Function<String, Class<?>> typeOf) throws IOException {
        List<Map<String, Object>> warnings = new ArrayList<>();
        Set<String> fields = new LinkedHashSet<>();
        for (RangeClause rc : extractRanges(query)) {
            fields.add(rc.field());
        }
        for (String field : fields) {
            Map<String, Object> w = new LinkedHashMap<>();
            w.put("type", "numeric_range_no_results");
            w.put("property", field);
            if (!fieldExists(reader, field)) {
                w.put("type", "property_not_in_case");
                w.put("message", "A propriedade '" + field + "' não existe no índice deste caso; o módulo que a gera pode não ter sido executado.");
                warnings.add(w);
                continue;
            }
            Class<?> type = typeOf.apply(field);
            if (!isNumericType(type)) {
                w.put("type", "range_on_non_numeric_property");
                w.put("property_type", type == null ? "desconhecido" : type.getSimpleName());
                w.put("message", "A propriedade '" + field + "' não é numérica neste caso; a faixa é comparada como texto.");
                warnings.add(w);
                continue;
            }
            w.put("property_type", type.getSimpleName());
            Number[] minMax = minMax(reader, field, type);
            if (minMax == null) {
                w.put("message", "Nenhum item do caso possui valor numérico em '" + field + "'.");
            } else {
                w.put("min_value", minMax[0]);
                w.put("max_value", minMax[1]);
                w.put("message", "Nenhum item na faixa pedida. Valores de '" + field + "' no caso: de " + minMax[0] + " a " + minMax[1] + ".");
            }
            warnings.add(w);
        }
        return warnings;
    }

    // Leaf readers belong to the case IndexReader and must not be closed here.
    @SuppressWarnings("resource")
    static boolean fieldExists(IndexReader reader, String field) {
        for (LeafReaderContext ctx : reader.leaves()) {
            FieldInfo fi = ctx.reader().getFieldInfos().fieldInfo(field);
            if (fi != null) {
                return true;
            }
        }
        return false;
    }

    // Leaf readers belong to the case IndexReader and must not be closed here.
    @SuppressWarnings("resource")
    static Number[] minMax(IndexReader reader, String field, Class<?> type) throws IOException {
        Number min = null;
        Number max = null;
        for (LeafReaderContext ctx : reader.leaves()) {
            PointValues pv = ctx.reader().getPointValues(field);
            if (pv == null || pv.size() == 0) {
                continue;
            }
            Number lo = decode(pv.getMinPackedValue(), type);
            Number hi = decode(pv.getMaxPackedValue(), type);
            if (min == null || compare(lo, min) < 0) {
                min = lo;
            }
            if (max == null || compare(hi, max) > 0) {
                max = hi;
            }
        }
        return min == null ? null : new Number[] { min, max };
    }

    private static Number decode(byte[] packed, Class<?> type) {
        if (Double.class.equals(type)) {
            return DoublePoint.decodeDimension(packed, 0);
        } else if (Float.class.equals(type)) {
            return FloatPoint.decodeDimension(packed, 0);
        } else if (Long.class.equals(type)) {
            return LongPoint.decodeDimension(packed, 0);
        }
        return IntPoint.decodeDimension(packed, 0);
    }

    private static int compare(Number a, Number b) {
        return Double.compare(a.doubleValue(), b.doubleValue());
    }
}
