package br.com.ipedtools.mcp.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Curated semantics of IPED properties (layer 1 of the property catalog), loaded once from
 * {@code property-semantics.json}. Entry names are checked against IPED constants by PropertySemanticsTest.
 */
public final class PropertySemantics {

    static final String RESOURCE = "/property-semantics.json";

    /** One curated entry; exactly one of {@code name} and {@code pattern} is set. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Entry(
            String name,
            String pattern,
            String group,
            String description,
            String scale,
            String unit,
            @JsonProperty("known_values") List<String> knownValues,
            @JsonProperty("query_examples") List<String> queryExamples,
            Boolean identity) {

        /** True when the property identifies an account or a device (phone, username, e-mail, IDs). */
        public boolean isIdentity() {
            return Boolean.TRUE.equals(identity);
        }

        /** Name or pattern, for messages. */
        public String key() {
            return name != null ? name : pattern;
        }

        boolean matches(String property) {
            if (name != null) {
                return name.equals(property);
            }
            return pattern != null && pattern.endsWith("*")
                    && property.startsWith(pattern.substring(0, pattern.length() - 1));
        }

        /** Semantics as a response map, without empty fields. */
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("description", description);
            if (scale != null) {
                m.put("scale", scale);
            }
            if (unit != null) {
                m.put("unit", unit);
            }
            if (knownValues != null && !knownValues.isEmpty()) {
                m.put("known_values", knownValues);
            }
            if (pattern != null) {
                m.put("curated_pattern", pattern);
            }
            if (isIdentity()) {
                m.put("identity", true);
            }
            return m;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Catalog(int version, List<Entry> properties) {
    }

    private static volatile PropertySemantics instance;

    private final List<Entry> entries;
    private final Map<String, Entry> byName = new LinkedHashMap<>();
    private final List<Entry> patterns = new ArrayList<>();

    PropertySemantics(List<Entry> entries) {
        this.entries = Collections.unmodifiableList(entries);
        for (Entry e : entries) {
            if ((e.name() == null) == (e.pattern() == null)) {
                throw new IllegalStateException("Entrada curada deve ter 'name' ou 'pattern' (apenas um): " + e);
            }
            if (e.description() == null || e.description().isBlank()) {
                throw new IllegalStateException("Entrada curada sem descrição: " + e.key());
            }
            if (e.name() != null) {
                if (byName.put(e.name(), e) != null) {
                    throw new IllegalStateException("Entrada curada duplicada: " + e.name());
                }
            } else {
                patterns.add(e);
            }
        }
        // Longer patterns first, so the most specific one wins.
        patterns.sort((a, b) -> Integer.compare(b.pattern().length(), a.pattern().length()));
    }

    public static PropertySemantics get() {
        PropertySemantics local = instance;
        if (local == null) {
            synchronized (PropertySemantics.class) {
                local = instance;
                if (local == null) {
                    local = load();
                    instance = local;
                }
            }
        }
        return local;
    }

    static PropertySemantics load() {
        try (InputStream in = PropertySemantics.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Recurso não encontrado: " + RESOURCE);
            }
            Catalog catalog = new ObjectMapper().readValue(in, Catalog.class);
            return new PropertySemantics(new ArrayList<>(catalog.properties()));
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao carregar " + RESOURCE, e);
        }
    }

    public List<Entry> entries() {
        return entries;
    }

    /** Curated entry for a property: exact name first, then the most specific pattern. */
    public Entry find(String property) {
        if (property == null) {
            return null;
        }
        Entry e = byName.get(property);
        if (e != null) {
            return e;
        }
        for (Entry p : patterns) {
            if (p.matches(property)) {
                return p;
            }
        }
        return null;
    }

    /** True when the curated entry of a property marks it as an identity property. */
    public boolean isIdentity(String property) {
        Entry e = find(property);
        return e != null && e.isIdentity();
    }

    /** Curated group override for a property, or null. */
    public String groupOf(String property) {
        Entry e = find(property);
        return e != null ? e.group() : null;
    }
}
