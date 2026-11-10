package br.com.ipedtools.mcp.service;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import org.apache.lucene.document.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.ipedtools.mcp.service.CommunicationAnalytics.CommunicationEvent;
import br.com.ipedtools.mcp.service.CommunicationAnalytics.Diagnostics;
import br.com.ipedtools.mcp.service.CommunicationAnalytics.EventFilter;
import br.com.ipedtools.mcp.service.CommunicationAnalytics.EventSource;
import br.com.ipedtools.mcp.service.CommunicationAnalytics.ExtractionContext;
import iped.engine.data.IPEDSource;
import iped.engine.search.IPEDSearcher;
import iped.search.SearchResult;

/**
 * Streams communication events from the Lucene index of the active case (read-only).
 * Only items with {@code Communication:From} or {@code Communication:To} are read, loading just the stored
 * fields required by {@link CommunicationAnalytics#EVENT_FIELDS}.
 */
final class CommunicationEventExtractor implements EventSource {

    private static final Logger LOGGER = LoggerFactory.getLogger(CommunicationEventExtractor.class);

    static final String EVENT_QUERY = "Communication\\:From:* OR Communication\\:To:*";
    static final String CONTACTS_QUERY = "category:(\"contacts\" OR \"contatos\" OR \"phonebook\") OR type:\"text/vcard\" OR type:\"application/x-ufed-contact\"";
    static final int MAX_CONTACT_BOOK_ENTRIES = 5000;

    private static final Set<String> CONTACT_FIELDS = Set.of(
            "name", "userName", "ufed:EntryName", "phoneNumber", "userPhone", "ufed:EntryValue", "emailAddress", "userEmail");

    private final IPEDSource source;
    private final EventFilter filter;
    private final String phoneRegion;
    private final UnaryOperator<String> dateNormalizer;
    private ExtractionContext context;

    CommunicationEventExtractor(IPEDSource source, EventFilter filter, String phoneRegion,
                                UnaryOperator<String> dateNormalizer) {
        this.source = source;
        this.filter = filter != null ? filter : EventFilter.none();
        this.phoneRegion = phoneRegion;
        this.dateNormalizer = dateNormalizer;
    }

    ExtractionContext context() {
        if (context == null) {
            context = new ExtractionContext(phoneRegion, dateNormalizer, loadNameBook());
        }
        return context;
    }

    @Override
    public void forEach(Consumer<CommunicationEvent> consumer, Diagnostics diagnostics) throws IOException {
        ExtractionContext ctx = context();
        SearchResult res = new IPEDSearcher(source, EVENT_QUERY).search();
        int[] ids = res.getIds();
        if (ids == null) return;
        for (int id : ids) {
            int luceneId = source.getLuceneId(id);
            if (luceneId < 0) continue;
            Document doc = source.getReader().document(luceneId, CommunicationAnalytics.EVENT_FIELDS);
            CommunicationEvent ev = CommunicationAnalytics.buildEvent(id, doc::getValues, ctx);
            CommunicationAnalytics.dispatch(ev, filter, diagnostics, consumer);
        }
    }

    /** Number of items matched by the event query (one event per item). */
    int countEventItems() throws IOException {
        int[] ids = new IPEDSearcher(source, EVENT_QUERY).search().getIds();
        return ids == null ? 0 : ids.length;
    }

    /** Secondary name source: contact book entries keyed by canonical phone/email key. Never changes identity keys. */
    private Map<String, String> loadNameBook() {
        Map<String, String> book = new HashMap<>();
        try {
            int[] ids = new IPEDSearcher(source, CONTACTS_QUERY).search().getIds();
            if (ids == null) return book;
            int limit = Math.min(ids.length, MAX_CONTACT_BOOK_ENTRIES);
            for (int i = 0; i < limit; i++) {
                int luceneId = source.getLuceneId(ids[i]);
                if (luceneId < 0) continue;
                Document doc = source.getReader().document(luceneId, CONTACT_FIELDS);
                String name = firstOf(doc, "name", "userName", "ufed:EntryName");
                if (name == null) continue;
                String phone = firstOf(doc, "phoneNumber", "userPhone", "ufed:EntryValue");
                if (CommunicationAnalytics.isPhoneLike(phone)) {
                    book.putIfAbsent("tel:" + CommunicationAnalytics.normalizePhone(phone, phoneRegion), name);
                }
                String email = firstOf(doc, "emailAddress", "userEmail");
                if (email != null && email.contains("@")) {
                    book.putIfAbsent("email:" + email.trim().toLowerCase(), name);
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Não foi possível carregar agenda de contatos: {}", e.getMessage());
        }
        return book;
    }

    private static String firstOf(Document doc, String... fields) {
        for (String f : fields) {
            String v = doc.get(f);
            if (v != null && !v.isBlank()) return v.trim();
        }
        return null;
    }
}
