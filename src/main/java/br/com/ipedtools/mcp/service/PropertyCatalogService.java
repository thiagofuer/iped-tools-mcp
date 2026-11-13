package br.com.ipedtools.mcp.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.DocValuesType;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.PointValues;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.index.SortedNumericDocValues;
import org.apache.lucene.index.SortedSetDocValues;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.FixedBitSet;
import org.apache.lucene.util.NumericUtils;

import iped.engine.data.IPEDSource;
import iped.engine.task.index.IndexItem;
import iped.properties.BasicProps;

/**
 * Property catalog (issue #2): joins the curated semantics (layer 1, {@link PropertySemantics}) with what the
 * active case index actually holds (layer 2): fields, types, exact item counts and value statistics, all read
 * from index structures (terms, points, doc values) without loading stored documents.
 */
public final class PropertyCatalogService {

    static final int MAX_LISTED = 200;
    static final int DEFAULT_TOP_N = 20;
    static final int MAX_TOP_N = 100;
    static final int SAMPLE_LIMIT = 5000;
    static final int MAX_SIMILAR = 5;
    private static final int MAX_VALUE_CHARS = 200;

    /** Fields used by the IPED engine itself (content, text fragments, face and image feature vectors). */
    private static final Set<String> INTERNAL_FIELDS = Set.of(BasicProps.CONTENT, "fragNum", "fragParentId",
            "imageFeatures", BasicProps.PARENTIDs, "textSize", "textSplitted", "compressRatioLZ4");
    private static final List<String> INTERNAL_PREFIXES = List.of("face_encodings", "geo_ssdv_");

    private static final Set<String> BASIC_DATE_FIELDS = Set.of(BasicProps.CREATED, BasicProps.MODIFIED,
            BasicProps.ACCESSED, BasicProps.CHANGED, BasicProps.TIMESTAMP);

    private static final PropertyCatalogService INSTANCE = new PropertyCatalogService();

    /** A property present in the case. */
    record CaseField(String name, String group, String type, long itemCount, FieldInfo info) {
    }

    private IPEDSource cachedSource;
    private Map<String, CaseField> cachedFields;

    private PropertyCatalogService() {
    }

    public static PropertyCatalogService getInstance() {
        return INSTANCE;
    }

    private static IpedCoreService core() {
        return IpedCoreService.getInstance();
    }

    // =========================================================================
    // LAYER 2: CASE FIELDS
    // =========================================================================

    static boolean isInternal(String field) {
        if (IpedCoreService.isBlacklistedKey(field) || INTERNAL_FIELDS.contains(field)) {
            return true;
        }
        for (String p : INTERNAL_PREFIXES) {
            if (field.startsWith(p)) {
                return true;
            }
        }
        return false;
    }

    static String group(String field) {
        return PropertyGroups.classify(field, PropertySemantics.get()::groupOf);
    }

    /** Type of a field: metadataTypes.txt first, then the IPED basic date fields, then the index structure. */
    static String typeOf(String field, FieldInfo fi) {
        Class<?> c = IndexItem.getMetadataTypes().get(field);
        if (c != null) {
            if (Date.class.equals(c)) {
                return "Date";
            }
            if (Number.class.isAssignableFrom(c) || Boolean.class.equals(c)) {
                return c.getSimpleName();
            }
            return "String";
        }
        if (BASIC_DATE_FIELDS.contains(field)) {
            return "Date";
        }
        if (fi != null && fi.getPointDimensionCount() == 1) {
            return fi.getPointNumBytes() == 4 ? "Integer" : "Long";
        }
        return "String";
    }

    static boolean isNumeric(String type) {
        return switch (type) {
            case "Integer", "Long", "Short", "Byte", "Float", "Double" -> true;
            default -> false;
        };
    }

    private static Class<?> numericClass(String type) {
        return switch (type) {
            case "Float" -> Float.class;
            case "Double" -> Double.class;
            case "Long" -> Long.class;
            default -> Integer.class;
        };
    }

    /** All non-internal fields of the active case that hold at least one item, cached per opened case. */
    // Leaf readers belong to the case IndexReader and must not be closed here.
    @SuppressWarnings("resource")
    synchronized Map<String, CaseField> caseFields() throws IOException {
        IPEDSource source = core().getSource();
        if (source == null) {
            throw new IllegalStateException("Nenhum caso do IPED está aberto no momento.");
        }
        if (source == cachedSource && cachedFields != null) {
            return cachedFields;
        }
        IndexReader reader = source.getReader();
        Map<String, FieldInfo> infos = new TreeMap<>();
        for (LeafReaderContext ctx : reader.leaves()) {
            for (FieldInfo fi : ctx.reader().getFieldInfos()) {
                if (!isInternal(fi.name)) {
                    infos.putIfAbsent(fi.name, fi);
                }
            }
        }
        Map<String, CaseField> fields = new LinkedHashMap<>();
        for (FieldInfo fi : infos.values()) {
            long count = countInCase(reader, fi.name);
            if (count > 0) {
                fields.put(fi.name, new CaseField(fi.name, group(fi.name), typeOf(fi.name, fi), count, fi));
            }
        }
        cachedSource = source;
        cachedFields = Collections.unmodifiableMap(fields);
        return cachedFields;
    }

    /** True if the field exists in the index of the active case (internal fields included). */
    // Leaf readers belong to the case IndexReader and must not be closed here.
    @SuppressWarnings("resource")
    public boolean fieldExists(String field) {
        IPEDSource source = core().getSource();
        if (source == null || field == null) {
            return false;
        }
        for (LeafReaderContext ctx : source.getReader().leaves()) {
            if (ctx.reader().getFieldInfos().fieldInfo(field) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Exact number of documents holding the field. Doc values are the reference (they hold every value as written);
     * term and point statistics are used for fields without doc values.
     */
    static long countInCase(IndexReader reader, String field) throws IOException {
        long count = 0;
        for (LeafReaderContext ctx : reader.leaves()) {
            LeafReader leaf = ctx.reader();
            FieldInfo fi = leaf.getFieldInfos().fieldInfo(field);
            if (fi == null) {
                continue;
            }
            DocIdSetIterator it = docValuesIterator(leaf, fi);
            if (it != null) {
                Bits live = leaf.getLiveDocs();
                for (int doc = it.nextDoc(); doc != DocIdSetIterator.NO_MORE_DOCS; doc = it.nextDoc()) {
                    if (live == null || live.get(doc)) {
                        count++;
                    }
                }
                continue;
            }
            Terms terms = leaf.terms(field);
            if (terms != null) {
                count += terms.getDocCount();
                continue;
            }
            PointValues points = leaf.getPointValues(field);
            if (points != null) {
                count += points.getDocCount();
            }
        }
        return count;
    }

    private static DocIdSetIterator docValuesIterator(LeafReader leaf, FieldInfo fi) throws IOException {
        DocValuesType t = fi.getDocValuesType();
        return switch (t) {
            case NUMERIC -> leaf.getNumericDocValues(fi.name);
            case SORTED_NUMERIC -> leaf.getSortedNumericDocValues(fi.name);
            case SORTED -> leaf.getSortedDocValues(fi.name);
            case SORTED_SET -> leaf.getSortedSetDocValues(fi.name);
            case BINARY -> leaf.getBinaryDocValues(fi.name);
            default -> null;
        };
    }

    /** Lucene documents matched by a query, as a bit set over the whole index. */
    FixedBitSet docsOf(String query) throws IOException {
        IPEDSource source = core().getSource();
        FixedBitSet bits = new FixedBitSet(Math.max(1, source.getReader().maxDoc()));
        for (int luceneId : core().searchLuceneIds(query)) {
            bits.set(luceneId);
        }
        return bits;
    }

    /** Counts, for each field, the documents of {@code docs} holding it. Fields without doc values are skipped. */
    static Map<String, Long> countWithin(IndexReader reader, Set<String> fields, FixedBitSet docs) throws IOException {
        Map<String, Long> counts = new HashMap<>();
        for (LeafReaderContext ctx : reader.leaves()) {
            LeafReader leaf = ctx.reader();
            for (String field : fields) {
                FieldInfo fi = leaf.getFieldInfos().fieldInfo(field);
                if (fi == null) {
                    continue;
                }
                DocIdSetIterator it = docValuesIterator(leaf, fi);
                if (it == null) {
                    continue;
                }
                long c = 0;
                for (int doc = it.nextDoc(); doc != DocIdSetIterator.NO_MORE_DOCS; doc = it.nextDoc()) {
                    if (docs.get(ctx.docBase + doc)) {
                        c++;
                    }
                }
                if (c > 0) {
                    counts.merge(field, c, Long::sum);
                }
            }
        }
        return counts;
    }

    // =========================================================================
    // LAYER 1 + 2: RESPONSES
    // =========================================================================

    /** Escapes a property name for use as a Lucene field in a query ({@code audio:x} becomes {@code audio\:x}). */
    public static String escapeField(String name) {
        StringBuilder sb = new StringBuilder();
        for (char ch : name.toCharArray()) {
            if ("\\+!():^[]\"{}~*?|&/".indexOf(ch) >= 0 || Character.isWhitespace(ch)) {
                sb.append('\\');
            }
            sb.append(ch);
        }
        return sb.toString();
    }

    static String defaultQueryExample(String name, String type) {
        String f = escapeField(name);
        if (isNumeric(type)) {
            return f + ":[1 TO *]";
        }
        if ("Date".equals(type)) {
            return f + ":[2024-01-01 TO 2024-12-31]";
        }
        return f + ":*";
    }

    private static List<String> queryExamples(String name, String type, PropertySemantics.Entry semantics) {
        if (semantics != null && semantics.queryExamples() != null && !semantics.queryExamples().isEmpty()) {
            if (semantics.name() != null) {
                return semantics.queryExamples();
            }
            // Pattern entries: add an example built for the concrete name.
            List<String> examples = new ArrayList<>();
            examples.add(defaultQueryExample(name, type));
            examples.addAll(semantics.queryExamples());
            return examples;
        }
        return List.of(defaultQueryExample(name, type));
    }

    private static Map<String, Object> propertySummary(CaseField f, long count) {
        PropertySemantics.Entry sem = PropertySemantics.get().find(f.name());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", f.name());
        m.put("group", f.group());
        m.put("type", f.type());
        m.put("item_count", count);
        m.put("curated", sem != null);
        if (sem != null) {
            m.put("description", sem.description());
        }
        m.put("query_example", queryExamples(f.name(), f.type(), sem).getFirst());
        return m;
    }

    static boolean matchesPattern(String name, String pattern) {
        String p = pattern.trim();
        if (!p.contains("*") && !p.contains("?")) {
            p = "*" + p + "*";
        }
        String regex = "(?i)" + java.util.regex.Pattern.quote(p).replace("*", "\\E.*\\Q").replace("?", "\\E.\\Q");
        return name.matches(regex);
    }

    /**
     * {@code list_properties}: without filters, the groups of the case with their property counts; with
     * {@code group} or {@code pattern}, the matching properties. {@code category} restricts counts to that category.
     */
    public Map<String, Object> listProperties(String group, String pattern, String category) throws IOException {
        Map<String, CaseField> fields = caseFields();
        String groupName = null;
        if (group != null && !group.isBlank()) {
            groupName = PropertyGroups.resolve(group);
            if (groupName == null) {
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("error", "Grupo desconhecido: '" + group + "'.");
                err.put("available_groups", PropertyGroups.names());
                return err;
            }
        }
        boolean hasPattern = pattern != null && !pattern.isBlank();
        boolean hasCategory = category != null && !category.isBlank();

        Map<String, Long> counts = new HashMap<>();
        Map<String, Object> response = new LinkedHashMap<>();
        if (hasCategory) {
            String catQuery = "category:\"" + category.trim().replace("\"", "\\\"") + "\"";
            FixedBitSet docs = docsOf(catQuery);
            response.put("category", category.trim());
            response.put("category_item_count", docs.cardinality());
            counts.putAll(countWithin(core().getSource().getReader(), fields.keySet(), docs));
        } else {
            fields.values().forEach(f -> counts.put(f.name(), f.itemCount()));
        }

        if (groupName == null && !hasPattern) {
            Map<String, Integer> perGroup = new LinkedHashMap<>();
            for (String g : PropertyGroups.names()) {
                perGroup.put(g, 0);
            }
            for (CaseField f : fields.values()) {
                if (counts.getOrDefault(f.name(), 0L) > 0) {
                    perGroup.merge(f.group(), 1, Integer::sum);
                }
            }
            List<Map<String, Object>> groups = new ArrayList<>();
            int total = 0;
            for (Map.Entry<String, Integer> e : perGroup.entrySet()) {
                if (e.getValue() > 0) {
                    Map<String, Object> g = new LinkedHashMap<>();
                    g.put("name", e.getKey());
                    g.put("description", PropertyGroups.DESCRIPTIONS.getOrDefault(e.getKey(), ""));
                    g.put("property_count", e.getValue());
                    groups.add(g);
                    total += e.getValue();
                }
            }
            response.put("groups", groups);
            response.put("total_properties", total);
            response.put("next_step", "Chame list_properties com group (ex.: 'AI') ou pattern para ver as propriedades, "
                    + "e describe_property para o significado, a escala e os valores de uma propriedade.");
            return response;
        }

        List<Map<String, Object>> props = new ArrayList<>();
        for (CaseField f : fields.values()) {
            long count = counts.getOrDefault(f.name(), 0L);
            if (count == 0) {
                continue;
            }
            if (groupName != null && !groupName.equals(f.group())) {
                continue;
            }
            if (hasPattern && !matchesPattern(f.name(), pattern)) {
                continue;
            }
            props.add(propertySummary(f, count));
        }
        props.sort(Comparator.comparingLong((Map<String, Object> m) -> (Long) m.get("item_count")).reversed()
                .thenComparing(m -> (String) m.get("name")));
        int total = props.size();
        if (groupName != null) {
            response.put("group", groupName);
        }
        if (hasPattern) {
            response.put("pattern", pattern);
        }
        response.put("total", total);
        response.put("truncated", total > MAX_LISTED);
        response.put("properties", total > MAX_LISTED ? props.subList(0, MAX_LISTED) : props);
        if (total > MAX_LISTED) {
            response.put("note", "Exibindo as " + MAX_LISTED + " propriedades com mais itens de " + total
                    + ". Use pattern para refinar.");
        }
        return response;
    }

    /** {@code describe_property}: detail of one property, optionally restricted to the items of a query. */
    public Map<String, Object> describeProperty(String name, String query, Integer topN) throws IOException {
        Map<String, Object> response = new LinkedHashMap<>();
        String prop = name == null ? "" : name.trim().replace("\\:", ":");
        response.put("name", prop);
        Map<String, CaseField> fields = caseFields();
        CaseField f = fields.get(prop);
        PropertySemantics.Entry sem = PropertySemantics.get().find(prop);

        if (f == null) {
            response.put("present", false);
            response.put("group", group(prop));
            response.put("message", "A propriedade '" + prop + "' não existe neste caso. Isso não é o mesmo que zero itens: "
                    + "o módulo do IPED que a gera pode não ter sido executado, ou o nome está diferente.");
            if (sem != null) {
                response.put("semantics", sem.toMap());
            }
            response.put("similar", similarNames(prop, fields.keySet()));
            return response;
        }

        int n = topN == null || topN <= 0 ? DEFAULT_TOP_N : Math.min(topN, MAX_TOP_N);
        IndexReader reader = core().getSource().getReader();
        FixedBitSet docs = null;
        if (query != null && !query.isBlank()) {
            try {
                docs = docsOf(query);
            } catch (RuntimeException | IOException e) {
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("name", prop);
                err.put("query", query);
                err.put("error", "Consulta inválida: " + e.getMessage());
                return err;
            }
        }

        response.put("present", true);
        response.put("group", f.group());
        response.put("type", f.type());
        if (docs != null) {
            response.put("query", query);
            response.put("query_matched_items", docs.cardinality());
            response.put("item_count", countWithin(reader, Set.of(prop), docs).getOrDefault(prop, 0L));
            response.put("item_count_in_case", f.itemCount());
        } else {
            response.put("item_count", f.itemCount());
        }
        response.put("curated", sem != null);
        if (sem != null) {
            response.put("semantics", sem.toMap());
        }

        if (isNumeric(f.type()) && f.info().getPointDimensionCount() == 1) {
            Number[] mm = docs == null ? NumericRangeSupport.minMax(reader, prop, numericClass(f.type()))
                    : numericMinMaxWithin(reader, prop, f.type(), docs);
            if (mm != null) {
                response.put("stats", Map.of("min", mm[0], "max", mm[1]));
            }
        } else if ("Date".equals(f.type())) {
            String[] mm = stringMinMax(reader, prop, docs);
            if (mm != null) {
                response.put("stats", Map.of("min", mm[0], "max", mm[1]));
            }
        } else if (!isNumeric(f.type())) {
            topValues(reader, f, docs, n, response);
        }
        response.put("query_examples", queryExamples(prop, f.type(), sem));
        return response;
    }

    // =========================================================================
    // STATISTICS
    // =========================================================================

    private static Number decodeDocValue(long v, String type, boolean sortedNumeric) {
        return switch (type) {
            case "Double" -> sortedNumeric ? NumericUtils.sortableLongToDouble(v) : Double.longBitsToDouble(v);
            case "Float" -> sortedNumeric ? NumericUtils.sortableIntToFloat((int) v) : Float.intBitsToFloat((int) v);
            case "Long" -> v;
            default -> (int) v;
        };
    }

    // Leaf readers belong to the case IndexReader and must not be closed here.
    @SuppressWarnings("resource")
    static Number[] numericMinMaxWithin(IndexReader reader, String field, String type, FixedBitSet docs) throws IOException {
        Number min = null;
        Number max = null;
        for (LeafReaderContext ctx : reader.leaves()) {
            LeafReader leaf = ctx.reader();
            FieldInfo fi = leaf.getFieldInfos().fieldInfo(field);
            if (fi == null) {
                continue;
            }
            List<Number> values = new ArrayList<>();
            if (fi.getDocValuesType() == DocValuesType.NUMERIC) {
                NumericDocValues dv = leaf.getNumericDocValues(field);
                for (int doc = dv.nextDoc(); doc != DocIdSetIterator.NO_MORE_DOCS; doc = dv.nextDoc()) {
                    if (docs.get(ctx.docBase + doc)) {
                        values.add(decodeDocValue(dv.longValue(), type, false));
                    }
                }
            } else if (fi.getDocValuesType() == DocValuesType.SORTED_NUMERIC) {
                SortedNumericDocValues dv = leaf.getSortedNumericDocValues(field);
                for (int doc = dv.nextDoc(); doc != DocIdSetIterator.NO_MORE_DOCS; doc = dv.nextDoc()) {
                    if (docs.get(ctx.docBase + doc)) {
                        for (int i = 0; i < dv.docValueCount(); i++) {
                            values.add(decodeDocValue(dv.nextValue(), type, true));
                        }
                    }
                }
            }
            for (Number v : values) {
                if (min == null || Double.compare(v.doubleValue(), min.doubleValue()) < 0) {
                    min = v;
                }
                if (max == null || Double.compare(v.doubleValue(), max.doubleValue()) > 0) {
                    max = v;
                }
            }
        }
        return min == null ? null : new Number[] { min, max };
    }

    /** Lexicographic min and max of SORTED/SORTED_SET doc values (ISO-8601 dates), ignoring empty values. */
    // Leaf readers belong to the case IndexReader and must not be closed here.
    @SuppressWarnings("resource")
    static String[] stringMinMax(IndexReader reader, String field, FixedBitSet docs) throws IOException {
        String min = null;
        String max = null;
        for (LeafReaderContext ctx : reader.leaves()) {
            LeafReader leaf = ctx.reader();
            FieldInfo fi = leaf.getFieldInfos().fieldInfo(field);
            if (fi == null) {
                continue;
            }
            Bits live = leaf.getLiveDocs();
            long minOrd = Long.MAX_VALUE;
            long maxOrd = -1;
            if (fi.getDocValuesType() == DocValuesType.SORTED) {
                SortedDocValues dv = leaf.getSortedDocValues(field);
                long firstNonEmpty = firstNonEmptyOrd(dv.getValueCount(), o -> dv.lookupOrd((int) o));
                for (int doc = dv.nextDoc(); doc != DocIdSetIterator.NO_MORE_DOCS; doc = dv.nextDoc()) {
                    if ((live != null && !live.get(doc)) || (docs != null && !docs.get(ctx.docBase + doc))) {
                        continue;
                    }
                    long ord = dv.ordValue();
                    if (ord < firstNonEmpty) {
                        continue;
                    }
                    minOrd = Math.min(minOrd, ord);
                    maxOrd = Math.max(maxOrd, ord);
                }
                if (maxOrd >= 0) {
                    min = minString(min, dv.lookupOrd((int) minOrd).utf8ToString());
                    max = maxString(max, dv.lookupOrd((int) maxOrd).utf8ToString());
                }
            } else if (fi.getDocValuesType() == DocValuesType.SORTED_SET) {
                SortedSetDocValues dv = leaf.getSortedSetDocValues(field);
                long firstNonEmpty = firstNonEmptyOrd(dv.getValueCount(), dv::lookupOrd);
                for (int doc = dv.nextDoc(); doc != DocIdSetIterator.NO_MORE_DOCS; doc = dv.nextDoc()) {
                    if ((live != null && !live.get(doc)) || (docs != null && !docs.get(ctx.docBase + doc))) {
                        continue;
                    }
                    for (long ord = dv.nextOrd(); ord != SortedSetDocValues.NO_MORE_ORDS; ord = dv.nextOrd()) {
                        if (ord < firstNonEmpty) {
                            continue;
                        }
                        minOrd = Math.min(minOrd, ord);
                        maxOrd = Math.max(maxOrd, ord);
                    }
                }
                if (maxOrd >= 0) {
                    min = minString(min, dv.lookupOrd(minOrd).utf8ToString());
                    max = maxString(max, dv.lookupOrd(maxOrd).utf8ToString());
                }
            }
        }
        return min == null ? null : new String[] { min, max };
    }

    private interface OrdLookup {
        BytesRef lookup(long ord) throws IOException;
    }

    /** Ord of the first non-empty value (values are sorted, so the empty string, if present, is ord 0). */
    private static long firstNonEmptyOrd(long valueCount, OrdLookup lookup) throws IOException {
        return valueCount > 0 && lookup.lookup(0).length == 0 ? 1 : 0;
    }

    private static String minString(String a, String b) {
        return a == null || b.compareTo(a) < 0 ? b : a;
    }

    private static String maxString(String a, String b) {
        return a == null || b.compareTo(a) > 0 ? b : a;
    }

    /** Most frequent values of a textual property, from doc values, or terms/stored values when there are none. */
    // Leaf readers belong to the case IndexReader and must not be closed here.
    @SuppressWarnings("resource")
    private void topValues(IndexReader reader, CaseField f, FixedBitSet docs, int n, Map<String, Object> response)
            throws IOException {
        Map<String, Long> counts = new HashMap<>();
        boolean usedDocValues = false;
        for (LeafReaderContext ctx : reader.leaves()) {
            LeafReader leaf = ctx.reader();
            FieldInfo fi = leaf.getFieldInfos().fieldInfo(f.name());
            if (fi == null) {
                continue;
            }
            Bits live = leaf.getLiveDocs();
            if (fi.getDocValuesType() == DocValuesType.SORTED) {
                usedDocValues = true;
                SortedDocValues dv = leaf.getSortedDocValues(f.name());
                int[] ordCounts = new int[dv.getValueCount()];
                for (int doc = dv.nextDoc(); doc != DocIdSetIterator.NO_MORE_DOCS; doc = dv.nextDoc()) {
                    if ((live == null || live.get(doc)) && (docs == null || docs.get(ctx.docBase + doc))) {
                        ordCounts[dv.ordValue()]++;
                    }
                }
                for (int ord = 0; ord < ordCounts.length; ord++) {
                    if (ordCounts[ord] > 0) {
                        counts.merge(dv.lookupOrd(ord).utf8ToString(), (long) ordCounts[ord], Long::sum);
                    }
                }
            } else if (fi.getDocValuesType() == DocValuesType.SORTED_SET) {
                usedDocValues = true;
                SortedSetDocValues dv = leaf.getSortedSetDocValues(f.name());
                int[] ordCounts = new int[(int) Math.min(Integer.MAX_VALUE - 8, dv.getValueCount())];
                for (int doc = dv.nextDoc(); doc != DocIdSetIterator.NO_MORE_DOCS; doc = dv.nextDoc()) {
                    if ((live == null || live.get(doc)) && (docs == null || docs.get(ctx.docBase + doc))) {
                        for (long ord = dv.nextOrd(); ord != SortedSetDocValues.NO_MORE_ORDS; ord = dv.nextOrd()) {
                            ordCounts[(int) ord]++;
                        }
                    }
                }
                for (int ord = 0; ord < ordCounts.length; ord++) {
                    if (ordCounts[ord] > 0) {
                        counts.merge(dv.lookupOrd(ord).utf8ToString(), (long) ordCounts[ord], Long::sum);
                    }
                }
            } else if (fi.getDocValuesType() == DocValuesType.BINARY || fi.getDocValuesType() == DocValuesType.NUMERIC
                    || fi.getDocValuesType() == DocValuesType.SORTED_NUMERIC) {
                // Binary or point-encoded values (e.g. geographic coordinates) have no readable value distribution.
                return;
            }
        }
        if (!usedDocValues) {
            if (docs == null) {
                counts = termFrequencies(reader, f.name());
                response.put("values_source", "terms");
                response.put("values_note", "Termos indexados (palavras), não os valores completos.");
            } else {
                counts = storedValueSample(f.name(), docs);
                response.put("sampled", true);
                response.put("sample_size", Math.min(docs.cardinality(), SAMPLE_LIMIT));
            }
        }
        List<Map.Entry<String, Long>> sorted = new ArrayList<>(counts.entrySet());
        sorted.removeIf(e -> e.getKey().isEmpty());
        sorted.sort(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()));
        List<Map<String, Object>> top = new ArrayList<>();
        for (Map.Entry<String, Long> e : sorted.subList(0, Math.min(n, sorted.size()))) {
            String v = e.getKey();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("value", v.length() > MAX_VALUE_CHARS ? v.substring(0, MAX_VALUE_CHARS) + "... [truncated]" : v);
            m.put("count", e.getValue());
            top.add(m);
        }
        response.put("distinct_values", sorted.size());
        response.put("top_values", top);
    }

    // Leaf readers belong to the case IndexReader and must not be closed here.
    @SuppressWarnings("resource")
    private static Map<String, Long> termFrequencies(IndexReader reader, String field) throws IOException {
        Map<String, Long> counts = new HashMap<>();
        for (LeafReaderContext ctx : reader.leaves()) {
            Terms terms = ctx.reader().terms(field);
            if (terms == null) {
                continue;
            }
            TermsEnum te = terms.iterator();
            for (BytesRef t = te.next(); t != null; t = te.next()) {
                counts.merge(t.utf8ToString(), (long) te.docFreq(), Long::sum);
            }
        }
        return counts;
    }

    private Map<String, Long> storedValueSample(String field, FixedBitSet docs) throws IOException {
        Map<String, Long> counts = new HashMap<>();
        IndexReader reader = core().getSource().getReader();
        int read = 0;
        for (int doc = docs.nextSetBit(0); doc != DocIdSetIterator.NO_MORE_DOCS && read < SAMPLE_LIMIT;
                doc = doc + 1 < docs.length() ? docs.nextSetBit(doc + 1) : DocIdSetIterator.NO_MORE_DOCS) {
            Document d = reader.document(doc, Set.of(field));
            for (String v : d.getValues(field)) {
                counts.merge(v, 1L, Long::sum);
            }
            read++;
        }
        return counts;
    }

    // =========================================================================
    // ABSENT PROPERTIES
    // =========================================================================

    /** Up to five case properties with a similar name: shared prefix first, then edit distance. */
    static List<String> similarNames(String name, Iterable<String> candidates) {
        String target = name.toLowerCase(Locale.ROOT);
        List<Map.Entry<String, Integer>> scored = new ArrayList<>();
        for (String c : candidates) {
            String lc = c.toLowerCase(Locale.ROOT);
            int distance = levenshtein(target, lc);
            int maxDistance = Math.max(2, target.length() / 4);
            boolean related = distance <= maxDistance || lc.startsWith(target) || target.startsWith(lc)
                    || (target.length() >= 4 && lc.contains(target));
            if (related) {
                int score = lc.startsWith(target) || target.startsWith(lc) ? distance - 100 : distance;
                scored.add(Map.entry(c, score));
            }
        }
        scored.sort(Map.Entry.<String, Integer>comparingByValue().thenComparing(Map.Entry.comparingByKey()));
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, Integer> e : scored) {
            if (result.size() >= MAX_SIMILAR) {
                break;
            }
            result.add(e.getKey());
        }
        return result;
    }

    static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = cur;
            cur = tmp;
        }
        return prev[b.length()];
    }
}
