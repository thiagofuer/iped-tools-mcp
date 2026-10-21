package br.com.ipedtools.mcp.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

import org.junit.jupiter.api.Test;

import br.com.ipedtools.mcp.service.CommunicationAnalytics.*;

/**
 * Pure unit tests of the communication event model and aggregations (no IPED case required).
 */
class CommunicationAnalyticsTest {

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private static final class Item {
        final Map<String, String[]> fields = new HashMap<>();

        Item set(String field, String... values) {
            fields.put(field, values);
            return this;
        }

        CommunicationEvent event(int id) {
            return CommunicationAnalytics.buildEvent(id, fields::get, ExtractionContext.defaults());
        }
    }

    private static Item item() {
        return new Item();
    }

    /** WhatsApp-like 1:1 message between two phones with explicit direction. */
    private static CommunicationEvent msg(int id, String from, String to, String direction, String ts, String app) {
        Item it = item().set(CommunicationAnalytics.SOURCE_APP, app)
                .set(CommunicationAnalytics.CONTENT_TYPE, "application/x-ufed-instantmessage")
                .set(CommunicationAnalytics.DATE, ts);
        if (from != null) it.set(CommunicationAnalytics.FROM, from);
        if (to != null) it.set(CommunicationAnalytics.TO, to);
        if (direction != null) it.set(CommunicationAnalytics.DIRECTION, direction);
        return it.event(id);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Map<String, Object> m, String key) {
        return (List<Map<String, Object>>) m.get(key);
    }

    private static Map<String, Object> topContacts(List<CommunicationEvent> events, int limit) throws Exception {
        ContactAggregator agg = new ContactAggregator();
        CommunicationAnalytics.listSource(events, EventFilter.none()).forEach(agg, new Diagnostics());
        return agg.result(limit);
    }

    private static Map<String, Object> graph(List<CommunicationEvent> events, String focal, int min, int limit, boolean mermaid) throws Exception {
        GraphAggregator agg = new GraphAggregator();
        CommunicationAnalytics.listSource(events, EventFilter.none()).forEach(agg, new Diagnostics());
        return agg.result(focal, min, limit, mermaid);
    }

    // ---------------------------------------------------------------------
    // 1.1 Channel, direction and date
    // ---------------------------------------------------------------------

    @Test
    void outgoingIsNotIncoming() {
        CommunicationEvent ev = msg(1, "A (+15550000001)", "B (+15550000002)", "Outgoing", "2024-01-01T10:00:00Z", "Viber");
        assertEquals(Direction.OUTGOING, ev.direction);
        assertEquals(CommunicationAnalytics.DIRECTION, ev.directionSource);
        assertEquals(Direction.INCOMING, CommunicationAnalytics.resolveDirection("INCOMING", null).direction());
    }

    @Test
    void callDirectionFromUfedProperty() {
        CommunicationEvent ev = item().set(CommunicationAnalytics.CONTENT_TYPE, "application/x-ufed-call")
                .set(CommunicationAnalytics.CATEGORY, "Phone Calls")
                .set(CommunicationAnalytics.UFED_DIRECTION, "Incoming")
                .set(CommunicationAnalytics.TO, "5550101003").event(2);
        assertEquals(Direction.INCOMING, ev.direction);
        assertEquals(CommunicationAnalytics.UFED_DIRECTION, ev.directionSource);
        assertEquals(Kind.CALL, ev.kind);
        assertEquals("Phone", ev.channel);
    }

    @Test
    void missingOrUnexpectedDirectionIsUnknown() {
        assertEquals(Direction.UNKNOWN, CommunicationAnalytics.resolveDirection(null, null).direction());
        assertEquals(Direction.UNKNOWN, CommunicationAnalytics.resolveDirection("Missed", null).direction());
        assertEquals(Direction.UNKNOWN, CommunicationAnalytics.resolveDirection("in", "out").direction());
        CommunicationEvent ev = msg(3, "A (+15550000001)", "B (+15550000002)", null, "2024-01-01T10:00:00Z", "Viber");
        assertEquals(Direction.UNKNOWN, ev.direction);
        assertNull(ev.directionSource);
    }

    @Test
    void channelResolution() {
        assertEquals("Viber", CommunicationAnalytics.resolveChannel("Viber", null, "application/x-ufed-instantmessage", "Instant Messages").channel());
        assertEquals("WhatsApp", CommunicationAnalytics.resolveChannel(null, "WhatsApp", "message/x-whatsapp-message", "Instant Messages").channel());
        assertEquals("WhatsApp", CommunicationAnalytics.resolveChannel(null, null, "message/x-whatsapp-message", null).channel());
        assertEquals("Telegram", CommunicationAnalytics.resolveChannel(null, null, "message/x-telegram-message", null).channel());

        ChannelInfo email = CommunicationAnalytics.resolveChannel(null, null, "multipart/related", "Emails");
        assertEquals(Kind.EMAIL, email.kind());
        assertEquals("Email", email.channel());

        ChannelInfo unknown = CommunicationAnalytics.resolveChannel(null, null, "application/octet-stream", "Chat messages");
        assertEquals(CommunicationAnalytics.CHANNEL_UNKNOWN, unknown.channel());
        assertFalse(unknown.resolved());
        assertEquals(Kind.MESSAGE, unknown.kind());
    }

    @Test
    void timestampFallbackOrderAndNoCurrentTime() {
        assertEquals("2024-01-01T00:00:00Z", CommunicationAnalytics.resolveTimestamp("2024-01-01T00:00:00Z", "2023-01-01T00:00:00Z", null, null));
        assertEquals("2023-01-01T00:00:00Z", CommunicationAnalytics.resolveTimestamp(" ", "2023-01-01T00:00:00Z", "2022-01-01T00:00:00Z", null));
        assertEquals("2022-01-01T00:00:00Z", CommunicationAnalytics.resolveTimestamp(null, null, "2022-01-01T00:00:00Z", null));
        assertNull(CommunicationAnalytics.resolveTimestamp(null, "", null, null));
    }

    // ---------------------------------------------------------------------
    // 1.2 Identity resolution
    // ---------------------------------------------------------------------

    @Test
    void structuredIdentifierIsPreferred() {
        CommunicationEvent ev = item().set(CommunicationAnalytics.SOURCE_APP, "Viber")
                .set(CommunicationAnalytics.FROM, "Carlos Teste (ID:+15550101181)")
                .set(CommunicationAnalytics.FROM_ID, "+15550101181")
                .set(CommunicationAnalytics.FROM_NAME, "Carlos Teste").event(10);
        assertEquals("tel:15550101181", ev.sender.key);
        assertEquals("+15550101181", ev.sender.identifier);
        assertEquals("Carlos Teste", ev.sender.name);
    }

    @Test
    void rawFormatsAreParsed() {
        Party paren = CommunicationAnalytics.parseParty("Bruno Exemplo (15550107779)", null, null, null, "WhatsApp", false, null);
        assertNotNull(paren);
        assertEquals("tel:15550107779", paren.key);
        assertEquals("Bruno Exemplo", paren.name);

        Party email = CommunicationAnalytics.parseParty("\"Ana Souza\" <Ana.Souza@Example.com>", null, null, null, "Email", false, null);

        assertNotNull(email);
        assertEquals("email:ana.souza@example.com", email.key);
        assertEquals("Ana Souza", email.name);

        Party jid = CommunicationAnalytics.parseParty("5511988887777@s.whatsapp.net", null, null, null, "WhatsApp", false, null);

        assertNotNull(jid);
        assertEquals("tel:5511988887777", jid.key);

        Party account = CommunicationAnalytics.parseParty("Paula Exemplo(a1b2c3d4-0000)", null, null, null, "Snapchat", false, null);

        assertNotNull(account);
        assertEquals("acct:snapchat:a1b2c3d4-0000", account.key);
    }

    @Test
    void phoneInTwoFormatsIsUnified() {
        List<CommunicationEvent> events = List.of(
                msg(1, "Carlos (+1 555-010-1181)", "Local (+15550000000)", "Incoming", "2024-01-01T10:00:00Z", "Viber"),
                msg(2, "15550101181", "Local (+15550000000)", "Incoming", "2024-01-02T10:00:00Z", "Signal"));
        assertEquals(events.getFirst().sender.key, events.get(1).sender.key);

        IdentityIndex index = new IdentityIndex();
        events.forEach(index);
        Identity id = index.registry.identity(events.getFirst().sender.key);
        assertTrue(id.aliases.contains("15550101181") || id.identifier.equals("15550101181"));
        assertTrue(id.aliases.stream().anyMatch(a -> a.contains("555-010-1181")) || id.identifier.contains("555-010-1181"));
    }

    @Test
    void accountLinkedToPhoneIsMerged() {
        Party withPhone = CommunicationAnalytics.parseParty("Bob", "bob_account", "Bob", "+15551112222", "Signal", false, null);
        assertNotNull(withPhone);
        assertEquals("acct:signal:bob_account", withPhone.key);
        assertTrue(withPhone.linkKeys.contains("tel:15551112222"));

        IdentityRegistry reg = new IdentityRegistry();
        reg.register(withPhone);
        reg.register(CommunicationAnalytics.parseParty("+1 555 111 2222", null, null, null, "Phone", false, null));
        assertEquals(reg.canonical("acct:signal:bob_account"), reg.canonical("tel:15551112222"));
    }

    @Test
    void shortCodesAreUnifiedAcrossChannelsButChatIdsAreGroups() {
        Party sms = CommunicationAnalytics.parseParty("32665", null, null, null, "Native Messages", false, null);
        assertNotNull(sms);
        Party log = CommunicationAnalytics.parseParty("32665", null, null, null, "Android CallLog database", false, null);
        assertNotNull(log);
        assertEquals("tel:32665", sms.key);
        assertEquals(sms.key, log.key);
        assertTrue(CommunicationAnalytics.looksLikeGroupRecipient("Club", "2", null));
        assertFalse(CommunicationAnalytics.looksLikeGroupRecipient("32665", "32665", null));
    }

    @Test
    void observedLocalIdentifiersAreGroupedByKey() throws Exception {
        List<CommunicationEvent> events = List.of(
                msg(1, "+15550108329", "Ann (+15550000001)", "Outgoing", "2024-01-01T10:00:00Z", "Native Messages"),
                msg(2, "15550108329", "Ann (+15550000001)", "Outgoing", "2024-01-02T10:00:00Z", "Native Messages"));
        Map<String, Object> g = graph(events, null, 1, 50, false);
        Map<String, Object> local = list(g, "nodes").stream().filter(n -> "LOCAL_ENDPOINT".equals(n.get("type"))).findFirst().orElseThrow();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> observed = (List<Map<String, Object>>) local.get("observed_local_identifiers");
        assertEquals(1, observed.size());
        assertEquals(2, observed.getFirst().get("count"));
    }

    @Test
    void placeholdersAreDiscarded() {
        assertNull(CommunicationAnalytics.parseParty("System Message", null, null, null, "WhatsApp", false, null));
        assertNull(CommunicationAnalytics.parseParty("status@broadcast", null, null, null, "WhatsApp", false, null));
        assertNull(CommunicationAnalytics.parseParty("  ", null, null, null, "WhatsApp", false, null));
        assertNull(CommunicationAnalytics.parseParty(">", null, null, null, "Email", false, null));
    }

    @Test
    void groupRecipientIdDoesNotBecomePerson() {
        CommunicationEvent viberGroup = item().set(CommunicationAnalytics.SOURCE_APP, "Viber")
                .set(CommunicationAnalytics.DIRECTION, "Incoming")
                .set(CommunicationAnalytics.FROM, "Carlos Teste (ID:+15550101181)")
                .set(CommunicationAnalytics.FROM_ID, "+15550101181")
                .set(CommunicationAnalytics.TO, "Clube Exemplo Viber")
                .set(CommunicationAnalytics.TO_ID, "2")
                .set(CommunicationAnalytics.TO_NAME, "Clube Exemplo Viber").event(20);
        assertTrue(viberGroup.groupMessage);
        assertTrue(viberGroup.recipients.isEmpty());
        assertEquals("group:viber:2", viberGroup.group.key);
        assertEquals("Clube Exemplo Viber", viberGroup.group.name);

        CommunicationEvent waGroup = item().set(CommunicationAnalytics.ACCOUNT_TYPE, "WhatsApp")
                .set(CommunicationAnalytics.IS_GROUP, "true")
                .set(CommunicationAnalytics.FROM, "DIEGO (15550100303)")
                .set(CommunicationAnalytics.TO, "Group Clube Exemplo WhatsApp (id:3)").event(21);
        assertTrue(waGroup.groupMessage);
        assertEquals("group:whatsapp:3", waGroup.group.key);
        assertEquals("tel:15550100303", waGroup.sender.key);
    }

    // ---------------------------------------------------------------------
    // 1.4 Diagnostics and owner independence
    // ---------------------------------------------------------------------

    @Test
    void diagnosticsReportCoverage() throws Exception {
        List<CommunicationEvent> events = List.of(
                msg(1, "A (+15550000001)", null, "Incoming", "2024-01-01T10:00:00Z", "Viber"),
                msg(2, "A (+15550000001)", "B (+15550000002)", null, null, "Viber"),
                item().set(CommunicationAnalytics.FROM, "C (+15550000003)").event(3));
        Diagnostics d = new Diagnostics();
        CommunicationAnalytics.listSource(events, EventFilter.none()).forEach(e -> { }, d);
        Map<String, Object> m = d.toMap();
        assertEquals(3L, m.get("events_analyzed"));
        assertEquals(1L, ((Map<?, ?>) m.get("with_explicit_direction")).get("count"));
        assertEquals(2L, ((Map<?, ?>) m.get("with_resolved_channel")).get("count"));
        assertEquals(1L, ((Map<?, ?>) m.get("with_timestamp")).get("count"));
        assertEquals(33.3, ((Map<?, ?>) m.get("with_explicit_direction")).get("percent"));
    }

    @Test
    void resultsDoNotDependOnOwnerInformation() throws Exception {
        List<Item> items = List.of(
                item().set(CommunicationAnalytics.SOURCE_APP, "Viber").set(CommunicationAnalytics.DIRECTION, "Outgoing")
                        .set(CommunicationAnalytics.FROM, "Owner (+15550000000)").set(CommunicationAnalytics.TO, "X (+15550000009)")
                        .set(CommunicationAnalytics.DATE, "2024-02-01T00:00:00Z"),
                item().set(CommunicationAnalytics.SOURCE_APP, "Viber")
                        .set(CommunicationAnalytics.FROM, "Owner (+15550000000)").set(CommunicationAnalytics.TO, "Y (+15550000008)")
                        .set(CommunicationAnalytics.DATE, "2024-02-02T00:00:00Z"));
        List<CommunicationEvent> plain = new ArrayList<>();
        List<CommunicationEvent> withOwner = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            plain.add(items.get(i).event(i));
            Item copy = new Item();
            copy.fields.putAll(items.get(i).fields);
            copy.set("ufed:PhoneOwner", "Somebody Else").set("Conversation:Account", "+15550000009");
            withOwner.add(copy.event(i));
        }
        assertEquals(topContacts(plain, 20).toString(), topContacts(withOwner, 20).toString());
        assertEquals(graph(plain, null, 1, 50, true).toString(), graph(withOwner, null, 1, 50, true).toString());
    }

    // ---------------------------------------------------------------------
    // 2.1 Contacts
    // ---------------------------------------------------------------------

    @Test
    void localSideIsNeverRankedAndUnknownIsNotDirectional() throws Exception {
        List<CommunicationEvent> events = List.of(
                msg(1, "Me (+15550000000)", "Ann (+15550000001)", "Outgoing", "2024-01-01T10:00:00Z", "Viber"),
                msg(2, "Ann (+15550000001)", "Me (+15550000000)", "Incoming", "2024-01-03T10:00:00Z", "Viber"),
                msg(3, "Ann (+15550000001)", "Me (+15550000000)", "Incoming", "2024-02-03T10:00:00Z", "Viber"),
                msg(4, "Me (+15550000000)", "Ann (+15550000001)", null, "2024-02-04T10:00:00Z", "Viber"),
                msg(5, "Me (+15550000000)", "Bob (+15550000002)", null, "2024-02-05T10:00:00Z", "Viber"));
        Map<String, Object> r = topContacts(events, 20);
        List<Map<String, Object>> contacts = list(r, "contacts");
        assertEquals(1, contacts.size(), "Somente Ann tem eventos com direção explícita");
        Map<String, Object> ann = contacts.getFirst();
        assertEquals("+15550000001", ann.get("identifier"));
        assertEquals(3, ann.get("total_interactions"));
        assertEquals(2, ann.get("incoming_count"));
        assertEquals(1, ann.get("outgoing_count"));
        assertEquals(1, ann.get("unknown_direction_count"));
        assertEquals(3, ann.get("active_days"));
        assertEquals(Map.of("2024-01", 2, "2024-02", 1), ann.get("by_month"));
        assertEquals(List.of("Viber"), ann.get("channels"));
        assertEquals(2L, r.get("unknown_direction_total"));
        assertTrue(contacts.stream().noneMatch(c -> "+15550000000".equals(c.get("identifier"))));
    }

    @Test
    void contactLimitDefaultsAndMaximum() throws Exception {
        List<CommunicationEvent> events = new ArrayList<>();
        for (int i = 0; i < 250; i++) {
            events.add(msg(i, "P" + i + " (+1555" + String.format("%07d", i) + ")", null, "Incoming", "2024-01-01T00:00:00Z", "Viber"));
        }
        Map<String, Object> def = topContacts(events, 0);
        assertEquals(20, list(def, "contacts").size());
        assertEquals(true, def.get("truncated"));
        assertEquals(250, def.get("total_contacts_discovered"));
        assertEquals(200, list(topContacts(events, 1000), "contacts").size());
    }

    @Test
    void groupActivityIsReported() throws Exception {
        CommunicationEvent g = item().set(CommunicationAnalytics.ACCOUNT_TYPE, "WhatsApp").set(CommunicationAnalytics.IS_GROUP, "true")
                .set(CommunicationAnalytics.FROM, "DIEGO (15550100303)").set(CommunicationAnalytics.TO, "Group Club (id:3)")
                .set(CommunicationAnalytics.DATE, "2023-06-08T19:12:38Z").event(1);
        Map<String, Object> r = topContacts(List.of(g), 20);
        assertTrue(list(r, "contacts").isEmpty());
        assertEquals(1L, r.get("unknown_direction_total"));
        @SuppressWarnings("unchecked")
        Map<String, Object> ga = (Map<String, Object>) r.get("group_activity");
        assertEquals(1, ga.get("total_groups"));
        assertEquals(1, list(ga, "groups").getFirst().get("senders_observed"));
    }

    // ---------------------------------------------------------------------
    // 2.2 Evidence
    // ---------------------------------------------------------------------

    @Test
    void evidenceIncludesEarliestAndLatest() throws Exception {
        List<CommunicationEvent> events = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            events.add(msg(100 + i, "Ann (+15550000001)", null, "Incoming", String.format("2024-01-%02dT10:00:00Z", 10 + i), "Viber"));
        }
        Collections.shuffle(events, new Random(7));
        Map<String, Object> g = graph(events, null, 1, 50, false);
        Map<String, Object> edge = list(g, "edges").getFirst();
        @SuppressWarnings("unchecked")
        List<Integer> ids = (List<Integer>) edge.get("evidence_ids");
        assertEquals(5, ids.size());
        assertEquals(12, edge.get("weight"));
        assertTrue(ids.contains(100));
        assertTrue(ids.contains(111));
        assertEquals(5, new HashSet<>(ids).size());

        // Deterministic regardless of input order
        Collections.shuffle(events, new Random(99));
        assertEquals(ids, list(graph(events, null, 1, 50, false), "edges").getFirst().get("evidence_ids"));

        EvidenceSampler big = new EvidenceSampler(50);
        for (int i = 0; i < 100; i++) big.add(i, "2024-01-01T00:00:" + String.format("%02d", i % 60) + "Z");
        assertEquals(20, big.ids().size(), "Máximo configurável é 20");
    }

    // ---------------------------------------------------------------------
    // 2.3 Graph
    // ---------------------------------------------------------------------

    @Test
    void graphNodesAndEdges() throws Exception {
        CommunicationEvent group = item().set(CommunicationAnalytics.ACCOUNT_TYPE, "WhatsApp").set(CommunicationAnalytics.IS_GROUP, "true")
                .set(CommunicationAnalytics.FROM, "Carl (+15550000003)").set(CommunicationAnalytics.TO, "Group Club (id:3)").event(5);
        List<CommunicationEvent> events = List.of(
                msg(1, "Ann (+15550000001)", "Me (+15550000000)", "Incoming", "2024-01-01T10:00:00Z", "Viber"),
                msg(2, "Me (+15550000000)", "Bob (+15550000002)", "Outgoing", "2024-01-02T10:00:00Z", "Viber"),
                msg(3, "Ann (+15550000001)", "Bob (+15550000002)", null, "2024-01-03T10:00:00Z", "Signal"),
                msg(4, "Ann (+15550000001)", null, null, "2024-01-04T10:00:00Z", "Signal"),
                group);
        Map<String, Object> g = graph(events, null, 1, 50, false);
        List<Map<String, Object>> edges = list(g, "edges");
        Set<String> pairs = new HashSet<>();
        for (Map<String, Object> e : edges) pairs.add(e.get("source") + ">" + e.get("target"));
        assertEquals(Set.of("tel:15550000001>local:Viber", "local:Viber>tel:15550000002",
                "tel:15550000001>tel:15550000002", "tel:15550000003>group:whatsapp:3"), pairs);
        assertEquals(1L, g.get("unlinked_events"));
        assertEquals("lucene-index", g.get("graph_source"));
        assertFalse((boolean) g.get("truncated"));
        assertFalse(g.containsKey("mermaid"));

        Map<String, Map<String, Object>> nodes = new HashMap<>();
        for (Map<String, Object> n : list(g, "nodes")) nodes.put((String) n.get("id"), n);
        Map<String, Object> local = nodes.get("local:Viber");
        assertEquals("LOCAL_ENDPOINT", local.get("type"));
        assertEquals(false, local.get("identity_resolved"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> observed = (List<Map<String, Object>>) local.get("observed_local_identifiers");
        assertEquals("+15550000000", observed.getFirst().get("identifier"));
        assertEquals("GROUP", nodes.get("group:whatsapp:3").get("type"));
        assertEquals(1, nodes.get("group:whatsapp:3").get("participants_observed"));
        assertEquals("ACCOUNT", nodes.get("tel:15550000001").get("type"));
        assertFalse(nodes.containsKey("tel:15550000000"), "Lado local não vira nó de pessoa");
        assertTrue(nodes.values().stream().noneMatch(n -> String.valueOf(n.get("label")).toLowerCase().contains("dono")
                || String.valueOf(n.get("label")).toLowerCase().contains("titular")));
    }

    @Test
    void graphFocalMinAndTruncation() throws Exception {
        List<CommunicationEvent> events = new ArrayList<>();
        int id = 0;
        for (int p = 0; p < 60; p++) {
            for (int k = 0; k <= p % 3; k++) {
                events.add(msg(id++, "P" + p + " (+1555" + String.format("%07d", p) + ")", null, "Incoming", "2024-01-01T00:00:00Z", "Viber"));
            }
        }
        Map<String, Object> g = graph(events, null, 1, 0, false);
        assertEquals(50, list(g, "edges").size());
        assertEquals(60, g.get("total_eligible_edges"));
        assertEquals(true, g.get("truncated"));

        Map<String, Object> min3 = graph(events, null, 3, 200, false);
        assertEquals(20, list(min3, "edges").size());

        Map<String, Object> focal = graph(events, "P7", 1, 50, false);
        assertEquals(1, list(focal, "edges").size());
        assertTrue(list(focal, "nodes").stream().anyMatch(n -> Boolean.TRUE.equals(n.get("is_focal"))));

        // Anonymous contact (no name) does not break focal matching
        Map<String, Object> anon = graph(List.of(msg(1, "+15559990000", null, "Incoming", null, "Viber")), "999", 1, 50, false);
        assertEquals(1, list(anon, "edges").size());
    }

    // ---------------------------------------------------------------------
    // 2.4 Mermaid
    // ---------------------------------------------------------------------

    @Test
    void mermaidMatchesReturnedGraph() throws Exception {
        List<CommunicationEvent> events = List.of(
                msg(1, "Ann \"A\" (+15550000001)", "Me (+15550000000)", "Incoming", "2024-01-01T10:00:00Z", "Viber"),
                msg(2, "Ann \"A\" (+15550000001)", "Me (+15550000000)", "Incoming", "2024-01-02T10:00:00Z", "Viber"),
                msg(3, "Me (+15550000000)", "Bob (+15550000002)", "Outgoing", "2024-01-02T10:00:00Z", "Viber"));
        Map<String, Object> g = graph(events, null, 1, 50, true);
        String mermaid = (String) g.get("mermaid");
        assertTrue(mermaid.startsWith("flowchart LR\n"));
        List<Map<String, Object>> nodes = list(g, "nodes");
        List<Map<String, Object>> edges = list(g, "edges");
        long nodeLines = mermaid.lines().filter(l -> l.matches("\\s+n\\d+[\\[({].*")).count();
        long edgeLines = mermaid.lines().filter(l -> l.contains("-->")).count();
        assertEquals(nodes.size(), nodeLines);
        assertEquals(edges.size(), edgeLines);
        assertTrue(mermaid.contains("n0 -->|2| n1") || mermaid.contains("-->|2|"));
        assertFalse(mermaid.contains("\"A\""), "Aspas do rótulo devem ser escapadas");
        assertFalse(graph(events, null, 1, 50, false).containsKey("mermaid"));
    }

    // ---------------------------------------------------------------------
    // 2.5 Relationship
    // ---------------------------------------------------------------------

    private static Map<String, Object> relationship(List<CommunicationEvent> events, String a, String b) throws Exception {
        return CommunicationAnalytics.relationship(CommunicationAnalytics.listSource(events, EventFilter.none()),
                a, b, 5, ExtractionContext.defaults(), new Diagnostics());
    }

    @Test
    void relationshipBetweenPair() throws Exception {
        List<CommunicationEvent> events = new ArrayList<>(List.of(
                msg(1, "Ann (+15550000001)", "Bob (+15550000002)", null, "2024-01-01T10:00:00Z", "Signal"),
                msg(2, "Bob (+15550000002)", "Ann (+15550000001)", null, "2024-03-01T10:00:00Z", "Signal"),
                msg(3, "Ann (+15550000001)", "Bob (+15550000002)", "Outgoing", "2024-03-02T10:00:00Z", "Viber"),
                msg(4, "Ann (+15550000001)", "Carl (+15550000003)", null, "2024-03-03T10:00:00Z", "Signal")));
        for (String from : List.of("Ann (+15550000001)", "Bob (+15550000002)")) {
            events.add(item().set(CommunicationAnalytics.ACCOUNT_TYPE, "WhatsApp").set(CommunicationAnalytics.IS_GROUP, "true")
                    .set(CommunicationAnalytics.FROM, from).set(CommunicationAnalytics.TO, "Group Club (id:3)").event(10 + events.size()));
        }
        Map<String, Object> r = relationship(events, "+1 555 000 0001", "Bob");
        assertEquals("found", r.get("status"));
        assertEquals(3, r.get("total_interactions"));
        assertEquals(Map.of("a_to_b", 2, "b_to_a", 1), r.get("by_direction"));
        assertEquals(Map.of("Signal", 2, "Viber", 1), r.get("by_channel"));
        assertEquals("2024-01-01T10:00:00Z", r.get("first_interaction"));
        assertEquals("2024-03-02T10:00:00Z", r.get("last_interaction"));
        assertEquals(1, list(r, "shared_groups").size());
        @SuppressWarnings("unchecked")
        List<Integer> evidence = (List<Integer>) r.get("evidence_ids");
        assertEquals(Set.of(1, 2, 3), new HashSet<>(evidence));
        assertEquals(1, evidence.getFirst(), "Primeira evidência é a interação mais antiga");
        assertTrue(r.containsKey("by_month"));
    }

    @Test
    void relationshipBetweenPersonAndGroup() throws Exception {
        List<CommunicationEvent> events = List.of(
                item().set(CommunicationAnalytics.ACCOUNT_TYPE, "WhatsApp").set(CommunicationAnalytics.IS_GROUP, "true")
                        .set(CommunicationAnalytics.FROM, "Ann (+15550000001)").set(CommunicationAnalytics.TO, "Group Club (id:3)")
                        .set(CommunicationAnalytics.DATE, "2024-01-01T10:00:00Z").event(1),
                item().set(CommunicationAnalytics.ACCOUNT_TYPE, "WhatsApp").set(CommunicationAnalytics.IS_GROUP, "true")
                        .set(CommunicationAnalytics.FROM, "Bob (+15550000002)").set(CommunicationAnalytics.TO, "Group Club (id:3)")
                        .set(CommunicationAnalytics.DATE, "2024-01-02T10:00:00Z").event(2));
        Map<String, Object> r = relationship(events, "Ann", "group:whatsapp:3");
        assertEquals("found", r.get("status"));
        assertEquals(1, r.get("total_interactions"));
        assertEquals(Map.of("a_to_b", 1, "b_to_a", 0), r.get("by_direction"));
    }

    @Test
    void ambiguousReferenceReturnsCandidates() throws Exception {
        List<CommunicationEvent> events = List.of(
                msg(1, "Ana Silva (+15550000001)", "Bob (+15550000002)", null, "2024-01-01T10:00:00Z", "Signal"),
                msg(2, "Ana Souza (+15550000004)", "Bob (+15550000002)", null, "2024-01-01T10:00:00Z", "Signal"));
        Map<String, Object> r = relationship(events, "Ana", "Bob");
        assertEquals("ambiguous", r.get("status"));
        assertEquals(2, list(r, "candidates_a").size());
        assertFalse(r.containsKey("total_interactions"));
    }

    @Test
    void noRelationshipFound() throws Exception {
        List<CommunicationEvent> events = List.of(
                msg(1, "Ann (+15550000001)", "Carl (+15550000003)", null, "2024-01-01T10:00:00Z", "Signal"),
                msg(2, "Bob (+15550000002)", "Carl (+15550000003)", null, "2024-01-01T10:00:00Z", "Signal"));
        Map<String, Object> r = relationship(events, "Ann", "Bob");
        assertEquals("no_relationship", r.get("status"));
        assertEquals(0, r.get("total_interactions"));
        assertTrue(((String) r.get("message")).contains("Nenhum vínculo"));
    }

    @Test
    void localIdentifierMatchesAsEndpointInferred() throws Exception {
        List<CommunicationEvent> events = List.of(
                // Outgoing with From reveals the local identifier on Viber
                msg(1, "Paula (+15550108329)", "Astro (+15550000005)", "Outgoing", "2024-01-01T10:00:00Z", "Viber"),
                // Incoming without To: local side has no identity
                msg(2, "Carlos (+15550101181)", null, "Incoming", "2024-01-02T10:00:00Z", "Viber"),
                msg(3, "Carlos (+15550101181)", null, "Incoming", "2024-01-03T10:00:00Z", "Viber"));
        Map<String, Object> r = relationship(events, "+15550108329", "Carlos");
        assertEquals("found", r.get("status"));
        assertEquals(true, r.get("endpoint_inferred"));
        assertEquals(2, r.get("endpoint_inferred_count"));
        assertEquals(Map.of("a_to_b", 0, "b_to_a", 2), r.get("by_direction"));
    }

    // ---------------------------------------------------------------------
    // Filters
    // ---------------------------------------------------------------------

    @Test
    void channelAndDateFilters() throws Exception {
        List<CommunicationEvent> events = List.of(
                msg(1, "Ann (+15550000001)", null, "Incoming", "2024-01-01T10:00:00Z", "Viber"),
                msg(2, "Ann (+15550000001)", null, "Incoming", "2024-02-01T10:00:00Z", "Signal"),
                msg(3, "Ann (+15550000001)", null, "Incoming", null, "Viber"));
        EventFilter f = EventFilter.of("viber", "2023-12-31T00:00:00Z", null, null, null);
        Diagnostics d = new Diagnostics();
        ContactAggregator agg = new ContactAggregator();
        CommunicationAnalytics.listSource(events, f).forEach(agg, d);
        assertEquals(1, list(agg.result(20), "contacts").getFirst().get("total_interactions"));
        assertEquals(1L, d.toMap().get("events_without_date_excluded"));
        assertEquals("viber", f.describe().get("channel"));
    }
}
