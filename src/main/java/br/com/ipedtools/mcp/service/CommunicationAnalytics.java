package br.com.ipedtools.mcp.service;

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

/**
 * Communication event model and aggregations (top contacts, network graph, pair relationship).
 * <p>
 * This layer has no Lucene dependency: events are built from a {@link FieldSource} and consumed in
 * streaming through {@link EventSource}, so every rule (channel, explicit direction, identity, groups,
 * evidence sampling, truncation) can be unit tested with synthetic events.
 * <p>
 * No device owner is ever assumed: the local side of an event with explicit direction is represented
 * as the {@code local:<channel>} endpoint, never as a person.
 */
public final class CommunicationAnalytics {

    public static final int DEFAULT_CONTACT_LIMIT = 20;
    public static final int MAX_CONTACT_LIMIT = 200;
    public static final int DEFAULT_EDGE_LIMIT = 50;
    public static final int MAX_EDGE_LIMIT = 200;
    public static final int DEFAULT_EVIDENCE_LIMIT = 5;
    public static final int MAX_EVIDENCE_LIMIT = 20;
    public static final int MAX_CANDIDATES = 10;
    public static final int MAX_GROUP_ACTIVITY = 20;
    public static final int MAX_OBSERVED_LOCAL_IDS = 5;
    public static final int MAX_ALIASES = 10;

    public static final String CHANNEL_UNKNOWN = "unknown";
    public static final String LOCAL_PREFIX = "local:";

    // Indexed properties read by the event model
    public static final String FROM = "Communication:From";
    public static final String FROM_ID = "Communication:From:id";
    public static final String FROM_NAME = "Communication:From:name";
    public static final String FROM_PHONE = "Communication:From:phoneNumber";
    public static final String TO = "Communication:To";
    public static final String TO_ID = "Communication:To:id";
    public static final String TO_NAME = "Communication:To:name";
    public static final String TO_PHONE = "Communication:To:phoneNumber";
    public static final String DIRECTION = "Communication:Direction";
    public static final String UFED_DIRECTION = "ufed:Direction";
    public static final String SOURCE_APP = "ufed:SourceApplication";
    public static final String ACCOUNT_TYPE = "accountType";
    public static final String CONTENT_TYPE = "contentType";
    public static final String CATEGORY = "category";
    public static final String DATE = "Communication:Date";
    public static final String TIMESTAMP = "timeStamp";
    public static final String CREATED = "created";
    public static final String IS_GROUP = "isGroupMessage";
    public static final String CONVERSATION_ID = "Conversation:ID";
    public static final String CONVERSATION_NAME = "Conversation:Name";

    /** Stored fields needed to build an event (selective loading). */
    public static final Set<String> EVENT_FIELDS = Set.of(
            FROM, FROM_ID, FROM_NAME, FROM_PHONE, TO, TO_ID, TO_NAME, TO_PHONE,
            DIRECTION, UFED_DIRECTION, SOURCE_APP, ACCOUNT_TYPE, CONTENT_TYPE, CATEGORY,
            DATE, TIMESTAMP, CREATED, IS_GROUP, CONVERSATION_ID, CONVERSATION_NAME);

    private static final Set<String> PLACEHOLDERS = Set.of(
            "system", "system message", "system_message", "broadcast", "status@broadcast", "null",
            "unknown", "desconhecido", "me", "voicemail", "none", "anonymous", "private number",
            "número privado");

    private static final Pattern PAREN_ID = Pattern.compile("^(.*?)\\s*\\(([^()]*)\\)\\s*$");
    private static final Pattern ANGLE_ID = Pattern.compile("^(.*?)\\s*<([^<>]+)>\\s*$");
    private static final Pattern PHONE_LIKE = Pattern.compile("^\\+?[0-9()\\-\\s.]+$");
    /** Internal chat ids (e.g. Viber {@code Communication:To:id} "2"); 5+ digits are short codes/phones. */
    private static final Pattern CHAT_ID = Pattern.compile("^[0-9]{1,4}$");
    private static final Pattern SHORT_CODE = Pattern.compile("^[0-9]{5,6}$");

    private CommunicationAnalytics() {
    }

    // =========================================================================
    // EVENT MODEL
    // =========================================================================

    public enum Direction { INCOMING, OUTGOING, UNKNOWN }

    public enum Kind { MESSAGE, CALL, EMAIL }

    /** Read access to the (possibly multi-valued) properties of one item. */
    @FunctionalInterface
    public interface FieldSource {
        String[] values(String field);

        default String first(String field) {
            String[] vals = values(field);
            if (vals == null) return null;
            for (String v : vals) {
                if (v != null && !v.isBlank()) return v.trim();
            }
            return null;
        }
    }

    /** Re-iterable stream of events; each call to {@code forEach} walks the whole source again. */
    @FunctionalInterface
    public interface EventSource {
        void forEach(Consumer<CommunicationEvent> consumer, Diagnostics diagnostics) throws IOException;
    }

    /** Shared settings for building events. */
    public record ExtractionContext(String phoneRegion, UnaryOperator<String> dateNormalizer, Map<String, String> nameBook) {
        public static ExtractionContext defaults() {
            return new ExtractionContext(null, UnaryOperator.identity(), Map.of());
        }
    }

    public record ChannelInfo(String channel, Kind kind, boolean resolved) {
    }

    public record DirectionInfo(Direction direction, String source) {
    }

    /** One side of a communication event. {@code key} is canonical and unique per identity. */
    public static final class Party {
        final String key;
        final String identifier;
        final String name;
        final boolean group;
        final String basis;
        final String raw;
        final Set<String> linkKeys;

        Party(String key, String identifier, String name, boolean group, String basis, String raw, Set<String> linkKeys) {
            this.key = key;
            this.identifier = identifier;
            this.name = name;
            this.group = group;
            this.basis = basis;
            this.raw = raw;
            this.linkKeys = linkKeys;
        }

        @Override
        public String toString() {
            return key;
        }
    }

    public static final class CommunicationEvent {
        final int itemId;
        final String timestamp;
        final String channel;
        final boolean channelResolved;
        final Kind kind;
        final Direction direction;
        final String directionSource;
        final Party sender;
        final List<Party> recipients;
        final boolean groupMessage;
        final Party group;

        CommunicationEvent(int itemId, String timestamp, ChannelInfo channel, DirectionInfo direction,
                           Party sender, List<Party> recipients, boolean groupMessage, Party group) {
            this.itemId = itemId;
            this.timestamp = timestamp;
            this.channel = channel.channel();
            this.channelResolved = channel.resolved();
            this.kind = channel.kind();
            this.direction = direction.direction();
            this.directionSource = direction.source();
            this.sender = sender;
            this.recipients = recipients;
            this.groupMessage = groupMessage;
            this.group = group;
        }

        boolean hasExplicitDirection() {
            return direction != Direction.UNKNOWN;
        }

        String localKey() {
            return LOCAL_PREFIX + channel;
        }
    }

    /**
     * Resolves the channel from {@code ufed:SourceApplication}, then {@code accountType}, then {@code contentType}.
     * The category is only used to tell calls and emails apart, never as the channel name.
     */
    public static ChannelInfo resolveChannel(String sourceApp, String accountType, String contentType, String category) {
        String ct = lower(contentType);
        String cat = lower(category);

        Kind kind = Kind.MESSAGE;
        if (ct.contains("call") || cat.contains("call") || cat.contains("chamada")) {
            kind = Kind.CALL;
        } else if (cat.contains("email") || cat.contains("e-mail") || ct.startsWith("message/rfc822")
                || ct.contains("outlook") || ct.contains("x-email")) {
            kind = Kind.EMAIL;
        }

        if (!isBlank(sourceApp)) return new ChannelInfo(sourceApp.trim(), kind, true);
        if (!isBlank(accountType)) return new ChannelInfo(accountType.trim(), kind, true);
        if (ct.startsWith("message/x-whatsapp")) return new ChannelInfo("WhatsApp", kind, true);
        if (ct.startsWith("message/x-telegram")) return new ChannelInfo("Telegram", kind, true);
        if (ct.startsWith("message/x-skype")) return new ChannelInfo("Skype", kind, true);
        if (kind == Kind.CALL) return new ChannelInfo("Phone", kind, true);
        if (kind == Kind.EMAIL) return new ChannelInfo("Email", kind, true);
        return new ChannelInfo(CHANNEL_UNKNOWN, kind, false);
    }

    /** Direction only from exact (case-insensitive) values; anything else is {@code UNKNOWN}. */
    public static DirectionInfo resolveDirection(String communicationDirection, String ufedDirection) {
        Direction d = exactDirection(communicationDirection);
        if (d != Direction.UNKNOWN) return new DirectionInfo(d, DIRECTION);
        d = exactDirection(ufedDirection);
        if (d != Direction.UNKNOWN) return new DirectionInfo(d, UFED_DIRECTION);
        return new DirectionInfo(Direction.UNKNOWN, null);
    }

    private static Direction exactDirection(String value) {
        if (value == null) return Direction.UNKNOWN;
        String v = value.trim();
        if (v.equalsIgnoreCase("incoming")) return Direction.INCOMING;
        if (v.equalsIgnoreCase("outgoing")) return Direction.OUTGOING;
        return Direction.UNKNOWN;
    }

    /** First non-blank of {@code Communication:Date}, {@code timeStamp}, {@code created}; null when none. */
    public static String resolveTimestamp(String communicationDate, String timeStamp, String created, UnaryOperator<String> normalizer) {
        for (String candidate : new String[] {communicationDate, timeStamp, created}) {
            if (!isBlank(candidate)) {
                String norm = normalizer != null ? normalizer.apply(candidate.trim()) : candidate.trim();
                return isBlank(norm) ? null : norm;
            }
        }
        return null;
    }

    /** Builds the event of one item. Every item with From or To yields an event, even if no party is usable. */
    public static CommunicationEvent buildEvent(int itemId, FieldSource f, ExtractionContext ctx) {
        ExtractionContext c = ctx != null ? ctx : ExtractionContext.defaults();
        ChannelInfo channel = resolveChannel(f.first(SOURCE_APP), f.first(ACCOUNT_TYPE), f.first(CONTENT_TYPE), f.first(CATEGORY));
        DirectionInfo direction = resolveDirection(f.first(DIRECTION), f.first(UFED_DIRECTION));
        String ts = resolveTimestamp(f.first(DATE), f.first(TIMESTAMP), f.first(CREATED), c.dateNormalizer());

        // Sender: structured subproperties apply to the first raw value
        Party sender = null;
        String[] fromVals = nonNull(f.values(FROM));
        String fromId = f.first(FROM_ID);
        String fromName = f.first(FROM_NAME);
        String fromPhone = f.first(FROM_PHONE);
        if (fromVals.length == 0) {
            sender = parseParty(null, fromId, fromName, fromPhone, channel.channel(), false, c);
        } else {
            for (int i = 0; i < fromVals.length && sender == null; i++) {
                sender = i == 0
                        ? parseParty(fromVals[i], fromId, fromName, fromPhone, channel.channel(), false, c)
                        : parseParty(fromVals[i], null, null, null, channel.channel(), false, c);
            }
        }

        // Recipients: structured subproperties aligned by index
        String[] toVals = nonNull(f.values(TO));
        String[] toIds = nonNull(f.values(TO_ID));
        String[] toNames = nonNull(f.values(TO_NAME));
        String[] toPhones = nonNull(f.values(TO_PHONE));
        int toCount = Math.max(toVals.length, toIds.length);

        boolean groupMessage = "true".equalsIgnoreCase(f.first(IS_GROUP));
        if (!groupMessage) {
            for (int i = 0; i < toCount; i++) {
                if (looksLikeGroupRecipient(at(toVals, i), at(toIds, i), at(toPhones, i))) {
                    groupMessage = true;
                    break;
                }
            }
        }

        List<Party> recipients = new ArrayList<>();
        Party group = null;
        if (groupMessage) {
            String convId = f.first(CONVERSATION_ID);
            String convName = f.first(CONVERSATION_NAME);
            if (toCount > 0) {
                group = parseGroup(at(toVals, 0), at(toIds, 0), firstNonBlank(at(toNames, 0), convName), channel.channel());
            }
            if (group == null && (!isBlank(convId) || !isBlank(convName))) {
                group = parseGroup(convName, convId, convName, channel.channel());
            }
        } else {
            for (int i = 0; i < toCount; i++) {
                Party p = parseParty(at(toVals, i), at(toIds, i), at(toNames, i), at(toPhones, i), channel.channel(), false, c);
                if (p != null && recipients.stream().noneMatch(r -> r.key.equals(p.key))) {
                    recipients.add(p);
                }
            }
        }

        return new CommunicationEvent(itemId, ts, channel, direction, sender, List.copyOf(recipients), groupMessage, group);
    }

    /** A recipient is a group when it is a WhatsApp group jid, an explicit "Group ..." label, or a short chat id. */
    static boolean looksLikeGroupRecipient(String raw, String structuredId, String structuredPhone) {
        String r = lower(raw);
        if (r.contains("@g.us")) return true;
        if (r.startsWith("group ") && r.contains("(id:")) return true;
        return !isBlank(structuredId) && isBlank(structuredPhone) && CHAT_ID.matcher(structuredId.trim()).matches();
    }

    private static Party parseGroup(String raw, String structuredId, String structuredName, String channel) {
        String[] parsed = splitRaw(raw);
        String id = firstNonBlank(structuredId, parsed[1], parsed[0], structuredName);
        if (isBlank(id) || isPlaceholder(id)) return null;
        String name = firstNonBlank(structuredName, parsed[0], raw);
        String key = "group:" + channelKey(channel) + ":" + lower(id);
        return new Party(key, id.trim(), name != null ? name.trim() : null, true, "group", raw != null ? raw.trim() : id, Set.of());
    }

    /**
     * Identifies a party: structured {@code :id/:phoneNumber/:name} first, then the raw value
     * ({@code Name (ID:+55...)}, {@code Name <email>}, {@code 55...@s.whatsapp.net}, bare phone or account).
     * Returns null for placeholders and values without any letter or digit.
     */
    public static Party parseParty(String raw, String structuredId, String structuredName, String structuredPhone,
                                   String channel, boolean group, ExtractionContext ctx) {
        ExtractionContext c = ctx != null ? ctx : ExtractionContext.defaults();
        String[] parsed = splitRaw(raw);
        String rawName = parsed[0];
        String rawId = parsed[1];

        String id = firstNonBlank(structuredId, structuredPhone, rawId);
        if (isBlank(id) || isPlaceholder(id) || !hasLetterOrDigit(id)) {
            return null;
        }
        if (group) {
            return parseGroup(raw, structuredId, structuredName, channel);
        }

        String name = firstNonBlank(structuredName, rawName);
        if (name != null && (isPlaceholder(name) || name.equalsIgnoreCase(id))) name = null;

        String key;
        String basis;
        String identifier = id.trim();
        String idLower = lower(identifier);
        Set<String> links = new LinkedHashSet<>();

        if (idLower.endsWith("@s.whatsapp.net") || idLower.endsWith("@c.us")) {
            identifier = identifier.substring(0, identifier.indexOf('@'));
            key = "tel:" + normalizePhone(identifier, c.phoneRegion());
            basis = "phone";
        } else if (idLower.contains("@") && idLower.indexOf('@') > 0 && idLower.indexOf('.', idLower.indexOf('@')) > 0) {
            identifier = idLower;
            key = "email:" + idLower;
            basis = "email";
        } else if (isPhoneLike(identifier)) {
            key = "tel:" + normalizePhone(identifier, c.phoneRegion());
            basis = "phone";
        } else {
            key = "acct:" + channelKey(channel) + ":" + idLower;
            basis = "account";
        }

        if (!isBlank(structuredPhone) && isPhoneLike(structuredPhone)) {
            String phoneKey = "tel:" + normalizePhone(structuredPhone, c.phoneRegion());
            if (!phoneKey.equals(key)) links.add(phoneKey);
        }

        if (name == null && c.nameBook() != null) {
            name = c.nameBook().get(key);
            if (name == null) {
                for (String lk : links) {
                    name = c.nameBook().get(lk);
                    if (name != null) break;
                }
            }
        }

        String rawValue = raw != null && !raw.isBlank() ? raw.trim() : identifier;
        return new Party(key, identifier, name, false, basis, rawValue, links.isEmpty() ? Set.of() : Set.copyOf(links));
    }

    /** Splits a raw party value into [name, id]. */
    static String[] splitRaw(String raw) {
        if (raw == null) return new String[] {null, null};
        String clean = raw.trim();
        if (clean.length() >= 2 && ((clean.startsWith("\"") && clean.endsWith("\"")) || (clean.startsWith("'") && clean.endsWith("'")))) {
            clean = clean.substring(1, clean.length() - 1).trim();
        }
        if (clean.isEmpty()) return new String[] {null, null};

        Matcher angle = ANGLE_ID.matcher(clean);
        if (angle.matches()) {
            String name = stripQuotes(angle.group(1));
            return new String[] {blankToNull(name), blankToNull(angle.group(2).trim())};
        }
        Matcher paren = PAREN_ID.matcher(clean);
        if (paren.matches()) {
            String inner = paren.group(2).trim();
            String innerLower = inner.toLowerCase(Locale.ROOT);
            if (innerLower.startsWith("id:")) inner = inner.substring(3).trim();
            if (inner.startsWith("@")) inner = inner.substring(1).trim();
            String name = stripQuotes(paren.group(1));
            if (!inner.isEmpty()) {
                return new String[] {blankToNull(name), inner};
            }
            clean = name;
        }
        return new String[] {null, blankToNull(clean)};
    }

    /**
     * Canonical phone digits: numbers with {@code +} or {@code 00} are parsed as international (E.164);
     * other numbers keep their digits unless a region is configured and the digits are not a valid
     * international number by themselves.
     */
    public static String normalizePhone(String value, String region) {
        if (value == null) return "";
        String trimmed = value.trim();
        String digits = trimmed.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return "";
        PhoneNumberUtil util = PhoneNumberUtil.getInstance();

        boolean international = trimmed.startsWith("+") || trimmed.startsWith("00");
        String intlDigits = trimmed.startsWith("00") ? digits.substring(2) : digits;
        if (international) {
            try {
                Phonenumber.PhoneNumber pn = util.parse("+" + intlDigits, null);
                return util.format(pn, PhoneNumberUtil.PhoneNumberFormat.E164).substring(1);
            } catch (NumberParseException e) {
                return intlDigits;
            }
        }
        if (!isBlank(region)) {
            try {
                Phonenumber.PhoneNumber asIntl = util.parse("+" + digits, null);
                if (util.isValidNumber(asIntl)) return digits;
            } catch (NumberParseException ignored) {
            }
            try {
                Phonenumber.PhoneNumber pn = util.parse(digits, region.trim().toUpperCase(Locale.ROOT));
                if (util.isValidNumber(pn)) {
                    return util.format(pn, PhoneNumberUtil.PhoneNumberFormat.E164).substring(1);
                }
            } catch (NumberParseException ignored) {
            }
        }
        return digits;
    }

    static boolean isPhoneLike(String value) {
        if (value == null) return false;
        String v = value.trim();
        if (SHORT_CODE.matcher(v).matches()) return true;
        return PHONE_LIKE.matcher(v).matches() && v.replaceAll("[^0-9]", "").length() >= 7;
    }

    static boolean isPlaceholder(String value) {
        return value == null || PLACEHOLDERS.contains(value.trim().toLowerCase(Locale.ROOT));
    }

    // =========================================================================
    // FILTERS & DIAGNOSTICS
    // =========================================================================

    public enum FilterOutcome { ACCEPTED, REJECTED, REJECTED_NO_DATE }

    /** Optional channel and inclusive period filters. Bounds are already normalized ISO-8601 strings. */
    public record EventFilter(String channel, String startDate, String endDate, String startBound, String endBound) {

        public static EventFilter none() {
            return new EventFilter(null, null, null, null, null);
        }

        public static EventFilter of(String channel, String startDate, String endDate, UnaryOperator<String> startNormalizer, UnaryOperator<String> endNormalizer) {
            String ch = isBlank(channel) ? null : channel.trim();
            String s = isBlank(startDate) ? null : startDate.trim();
            String e = isBlank(endDate) ? null : endDate.trim();
            return new EventFilter(ch, s, e,
                    s == null ? null : (startNormalizer != null ? startNormalizer.apply(s) : s),
                    e == null ? null : (endNormalizer != null ? endNormalizer.apply(e) : e));
        }

        boolean hasDateFilter() {
            return startBound != null || endBound != null;
        }

        FilterOutcome test(CommunicationEvent ev) {
            if (channel != null && !channel.equalsIgnoreCase(ev.channel)) return FilterOutcome.REJECTED;
            if (hasDateFilter()) {
                if (ev.timestamp == null) return FilterOutcome.REJECTED_NO_DATE;
                if (startBound != null && ev.timestamp.compareTo(startBound) < 0) return FilterOutcome.REJECTED;
                if (endBound != null && ev.timestamp.compareTo(endBound) > 0) return FilterOutcome.REJECTED;
            }
            return FilterOutcome.ACCEPTED;
        }

        Map<String, Object> describe() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("channel", channel != null ? channel : "");
            m.put("start_date", startDate != null ? startDate : "");
            m.put("end_date", endDate != null ? endDate : "");
            return m;
        }
    }

    /** Coverage of the event model over the analyzed (post-filter) events. */
    public static final class Diagnostics {
        long analyzed;
        long explicitDirection;
        long resolvedChannel;
        long withTimestamp;
        long filteredOut;
        long withoutDateExcluded;
        boolean dateFilter;

        void record(CommunicationEvent ev) {
            analyzed++;
            if (ev.hasExplicitDirection()) explicitDirection++;
            if (ev.channelResolved) resolvedChannel++;
            if (ev.timestamp != null) withTimestamp++;
        }

        void rejected(FilterOutcome outcome) {
            filteredOut++;
            if (outcome == FilterOutcome.REJECTED_NO_DATE) withoutDateExcluded++;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("events_analyzed", analyzed);
            m.put("with_explicit_direction", coverage(explicitDirection));
            m.put("with_resolved_channel", coverage(resolvedChannel));
            m.put("with_timestamp", coverage(withTimestamp));
            m.put("events_filtered_out", filteredOut);
            if (dateFilter) m.put("events_without_date_excluded", withoutDateExcluded);
            return m;
        }

        private Map<String, Object> coverage(long count) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("count", count);
            m.put("percent", analyzed == 0 ? 0.0 : Math.round(count * 1000.0 / analyzed) / 10.0);
            return m;
        }
    }

    /** Applies the filter, records diagnostics and forwards accepted events. */
    static void dispatch(CommunicationEvent ev, EventFilter filter, Diagnostics diagnostics, Consumer<CommunicationEvent> consumer) {
        FilterOutcome outcome = filter != null ? filter.test(ev) : FilterOutcome.ACCEPTED;
        if (diagnostics != null && filter != null) diagnostics.dateFilter = filter.hasDateFilter();
        if (outcome != FilterOutcome.ACCEPTED) {
            if (diagnostics != null) diagnostics.rejected(outcome);
            return;
        }
        if (diagnostics != null) diagnostics.record(ev);
        consumer.accept(ev);
    }

    /** In-memory event source (tests and small inputs). */
    public static EventSource listSource(List<CommunicationEvent> events, EventFilter filter) {
        return (consumer, diagnostics) -> {
            for (CommunicationEvent ev : events) dispatch(ev, filter, diagnostics, consumer);
        };
    }

    // =========================================================================
    // IDENTITY REGISTRY
    // =========================================================================

    /** Merged view of all keys united by shared phone/email/account links. */
    public static final class Identity {
        final String key;
        final String identifier;
        final String name;
        final boolean group;
        final String basis;
        final Set<String> keys;
        final List<String> aliases;

        Identity(String key, String identifier, String name, boolean group, String basis, Set<String> keys, List<String> aliases) {
            this.key = key;
            this.identifier = identifier;
            this.name = name;
            this.group = group;
            this.basis = basis;
            this.keys = keys;
            this.aliases = aliases;
        }

        String label() {
            return name != null && !name.isBlank() ? name : identifier;
        }

        boolean matchesSubstring(String needleLower) {
            if (needleLower == null) return false;
            if (identifier != null && identifier.toLowerCase(Locale.ROOT).contains(needleLower)) return true;
            if (name != null && name.toLowerCase(Locale.ROOT).contains(needleLower)) return true;
            for (String a : aliases) {
                if (a != null && a.toLowerCase(Locale.ROOT).contains(needleLower)) return true;
            }
            return false;
        }
    }

    /** Union-find over party keys; names and aliases are collected per key and merged on demand. */
    public static final class IdentityRegistry {
        private static final class KeyInfo {
            String identifier;
            String basis;
            boolean group;
            final Map<String, Integer> names = new HashMap<>();
            final Set<String> aliases = new LinkedHashSet<>();
        }

        private final Map<String, String> parent = new HashMap<>();
        private final Map<String, KeyInfo> info = new HashMap<>();
        private Map<String, Identity> built;

        void register(Party p) {
            if (p == null) return;
            built = null;
            KeyInfo ki = info.computeIfAbsent(p.key, k -> new KeyInfo());
            if (ki.identifier == null) {
                ki.identifier = p.identifier;
                ki.basis = p.basis;
                ki.group = p.group;
            }
            if (p.name != null && !p.name.isBlank()) ki.names.merge(p.name.trim(), 1, Integer::sum);
            if (ki.aliases.size() < 50) {
                ki.aliases.add(p.identifier);
                if (p.raw != null) ki.aliases.add(p.raw);
            }
            find(p.key);
            for (String link : p.linkKeys) {
                info.computeIfAbsent(link, k -> {
                    KeyInfo li = new KeyInfo();
                    li.identifier = k.substring(k.indexOf(':') + 1);
                    li.basis = "phone";
                    return li;
                });
                union(p.key, link);
            }
        }

        String find(String key) {
            String root = parent.get(key);
            if (root == null) {
                parent.put(key, key);
                return key;
            }
            if (root.equals(key)) return key;
            String r = find(root);
            parent.put(key, r);
            return r;
        }

        private void union(String a, String b) {
            String ra = find(a);
            String rb = find(b);
            if (ra.equals(rb)) return;
            // Deterministic root: phone keys win, then lexical order
            boolean aPhone = ra.startsWith("tel:");
            boolean bPhone = rb.startsWith("tel:");
            if (aPhone != bPhone ? aPhone : ra.compareTo(rb) < 0) parent.put(rb, ra);
            else parent.put(ra, rb);
        }

        /** Canonical key (root) of a party key; local endpoints and unknown keys map to themselves. */
        public String canonical(String key) {
            if (key == null || !parent.containsKey(key)) return key;
            return find(key);
        }

        public Identity identity(String key) {
            String root = canonical(key);
            Identity id = identities().get(root);
            if (id != null) return id;
            return new Identity(root, root, null, false, "unknown", Set.of(root), List.of());
        }

        public Collection<Identity> all() {
            return identities().values();
        }

        private Map<String, Identity> identities() {
            if (built != null) return built;
            Map<String, List<String>> members = new HashMap<>();
            for (String k : parent.keySet()) {
                members.computeIfAbsent(find(k), r -> new ArrayList<>()).add(k);
            }
            Map<String, Identity> result = new HashMap<>();
            for (Map.Entry<String, List<String>> e : members.entrySet()) {
                List<String> keys = e.getValue();
                Collections.sort(keys);
                KeyInfo rootInfo = info.get(e.getKey());
                Map<String, Integer> names = new HashMap<>();
                Set<String> aliases = new LinkedHashSet<>();
                boolean group = false;
                for (String k : keys) {
                    KeyInfo ki = info.get(k);
                    if (ki == null) continue;
                    ki.names.forEach((n, c) -> names.merge(n, c, Integer::sum));
                    aliases.addAll(ki.aliases);
                    group |= ki.group;
                }
                String name = names.entrySet().stream()
                        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                        .map(Map.Entry::getKey).findFirst().orElse(null);
                String identifier = rootInfo != null && rootInfo.identifier != null ? rootInfo.identifier : e.getKey();
                aliases.remove(identifier);
                List<String> aliasList = aliases.stream().limit(MAX_ALIASES).toList();
                String basis = rootInfo != null && rootInfo.basis != null ? rootInfo.basis : "unknown";
                if (keys.size() > 1) basis = basis + "+alias";
                result.put(e.getKey(), new Identity(e.getKey(), identifier, name, group, basis, Set.copyOf(keys), aliasList));
            }
            built = result;
            return built;
        }
    }

    /** Collects identities and the identifiers observed on the local side of each channel. */
    public static final class IdentityIndex implements Consumer<CommunicationEvent> {
        final IdentityRegistry registry = new IdentityRegistry();
        /** channel -> party key -> count */
        final Map<String, Map<String, Integer>> localObserved = new TreeMap<>();
        /** channel -> party key -> first identifier seen */
        final Map<String, Map<String, String>> localObservedKeys = new HashMap<>();

        @Override
        public void accept(CommunicationEvent ev) {
            registry.register(ev.sender);
            for (Party r : ev.recipients) registry.register(r);
            registry.register(ev.group);
            if (ev.direction == Direction.OUTGOING && ev.sender != null) {
                observeLocal(ev.channel, ev.sender);
            } else if (ev.direction == Direction.INCOMING && !ev.groupMessage) {
                for (Party r : ev.recipients) observeLocal(ev.channel, r);
            }
        }

        private void observeLocal(String channel, Party p) {
            localObserved.computeIfAbsent(channel, k -> new HashMap<>()).merge(p.key, 1, Integer::sum);
            localObservedKeys.computeIfAbsent(channel, k -> new HashMap<>()).putIfAbsent(p.key, p.identifier);
        }

        List<Map<String, Object>> observedFor(String channel) {
            Map<String, Integer> obs = localObserved.getOrDefault(channel, Map.of());
            Map<String, String> ids = localObservedKeys.getOrDefault(channel, Map.of());
            return obs.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                    .limit(MAX_OBSERVED_LOCAL_IDS)
                    .map(e -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("identifier", ids.getOrDefault(e.getKey(), e.getKey()));
                        m.put("contact_key", e.getKey());
                        m.put("count", e.getValue());
                        return m;
                    }).toList();
        }
    }

    // =========================================================================
    // AGGREGATION PRIMITIVES
    // =========================================================================

    /** Deterministic evidence sample: earliest and latest event plus the items with the smallest id hash. */
    public static final class EvidenceSampler {
        final int limit;
        String firstTs;
        int firstId = -1;
        String lastTs;
        int lastId = -1;
        final TreeMap<Long, Integer> byHash = new TreeMap<>();

        EvidenceSampler(int limit) {
            this.limit = Math.clamp(limit, 1, MAX_EVIDENCE_LIMIT);
        }

        void add(int itemId, String ts) {
            considerBounds(itemId, ts);
            byHash.put(hash(itemId), itemId);
            if (byHash.size() > limit) byHash.pollLastEntry();
        }

        private void considerBounds(int itemId, String ts) {
            if (ts == null || itemId < 0) return;
            int cf = firstTs == null ? -1 : ts.compareTo(firstTs);
            if (firstTs == null || cf < 0 || (cf == 0 && itemId < firstId)) {
                firstTs = ts;
                firstId = itemId;
            }
            int cl = lastTs == null ? 1 : ts.compareTo(lastTs);
            if (lastTs == null || cl > 0 || (cl == 0 && itemId > lastId)) {
                lastTs = ts;
                lastId = itemId;
            }
        }

        void merge(EvidenceSampler o) {
            considerBounds(o.firstId, o.firstTs);
            considerBounds(o.lastId, o.lastTs);
            byHash.putAll(o.byHash);
            while (byHash.size() > limit) byHash.pollLastEntry();
        }

        List<Integer> ids() {
            LinkedHashSet<Integer> out = new LinkedHashSet<>();
            if (firstId >= 0) out.add(firstId);
            if (lastId >= 0) out.add(lastId);
            for (Integer id : byHash.values()) {
                if (out.size() >= limit) break;
                out.add(id);
            }
            return new ArrayList<>(out);
        }

        static long hash(int itemId) {
            long z = itemId * 0x9E3779B97F4A7C15L;
            z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
            z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
            return z ^ (z >>> 31);
        }
    }

    /** Mergeable interaction counters. */
    static final class Stats {
        int total;
        int messages;
        int calls;
        int emails;
        int incoming;
        int outgoing;
        int unknown;
        final TreeMap<String, Integer> byChannel = new TreeMap<>();
        final TreeMap<String, Integer> byMonth = new TreeMap<>();
        final Set<String> days = new HashSet<>();
        String first;
        String last;
        final EvidenceSampler evidence;

        Stats(int evidenceLimit) {
            this.evidence = new EvidenceSampler(evidenceLimit);
        }

        void record(CommunicationEvent ev) {
            total++;
            switch (ev.kind) {
                case CALL -> calls++;
                case EMAIL -> emails++;
                default -> messages++;
            }
            switch (ev.direction) {
                case INCOMING -> incoming++;
                case OUTGOING -> outgoing++;
                default -> unknown++;
            }
            byChannel.merge(ev.channel, 1, Integer::sum);
            String ts = ev.timestamp;
            if (ts != null && ts.length() >= 10) {
                byMonth.merge(ts.substring(0, 7), 1, Integer::sum);
                days.add(ts.substring(0, 10));
                if (first == null || ts.compareTo(first) < 0) first = ts;
                if (last == null || ts.compareTo(last) > 0) last = ts;
            }
            evidence.add(ev.itemId, ts);
        }

        void merge(Stats o) {
            total += o.total;
            messages += o.messages;
            calls += o.calls;
            emails += o.emails;
            incoming += o.incoming;
            outgoing += o.outgoing;
            unknown += o.unknown;
            o.byChannel.forEach((k, v) -> byChannel.merge(k, v, Integer::sum));
            o.byMonth.forEach((k, v) -> byMonth.merge(k, v, Integer::sum));
            days.addAll(o.days);
            if (o.first != null && (first == null || o.first.compareTo(first) < 0)) first = o.first;
            if (o.last != null && (last == null || o.last.compareTo(last) > 0)) last = o.last;
            evidence.merge(o.evidence);
        }
    }

    // =========================================================================
    // TOP CONTACTS
    // =========================================================================

    /**
     * Ranks interlocutors: the sender of incoming events and the recipients of outgoing events.
     * Unknown-direction events are never counted as incoming/outgoing.
     */
    public static final class ContactAggregator implements Consumer<CommunicationEvent> {
        final IdentityIndex index = new IdentityIndex();
        final Map<String, Stats> explicit = new HashMap<>();
        final Map<String, Integer> unknownByKey = new HashMap<>();
        final Map<String, Stats> groups = new HashMap<>();
        final Map<String, Set<String>> groupSenders = new HashMap<>();
        long unknownDirectionTotal;

        @Override
        public void accept(CommunicationEvent ev) {
            index.accept(ev);
            if (ev.groupMessage) {
                if (ev.group != null) {
                    groups.computeIfAbsent(ev.group.key, k -> new Stats(DEFAULT_EVIDENCE_LIMIT)).record(ev);
                    if (ev.sender != null && ev.direction != Direction.OUTGOING) {
                        groupSenders.computeIfAbsent(ev.group.key, k -> new HashSet<>()).add(ev.sender.key);
                    }
                    if (ev.hasExplicitDirection()) {
                        explicit.computeIfAbsent(ev.group.key, k -> new Stats(DEFAULT_EVIDENCE_LIMIT)).record(ev);
                    }
                }
                if (!ev.hasExplicitDirection()) unknownDirectionTotal++;
                return;
            }
            switch (ev.direction) {
                case INCOMING -> {
                    if (ev.sender != null) explicit.computeIfAbsent(ev.sender.key, k -> new Stats(DEFAULT_EVIDENCE_LIMIT)).record(ev);
                }
                case OUTGOING -> {
                    for (Party r : ev.recipients) explicit.computeIfAbsent(r.key, k -> new Stats(DEFAULT_EVIDENCE_LIMIT)).record(ev);
                }
                default -> {
                    unknownDirectionTotal++;
                    if (ev.sender != null) unknownByKey.merge(ev.sender.key, 1, Integer::sum);
                    for (Party r : ev.recipients) unknownByKey.merge(r.key, 1, Integer::sum);
                }
            }
        }

        public Map<String, Object> result(int limit) {
            int maxLimit = Math.clamp(limit <= 0 ? DEFAULT_CONTACT_LIMIT : limit, 1, MAX_CONTACT_LIMIT);
            IdentityRegistry reg = index.registry;

            Map<String, Stats> merged = mergeByCanonical(explicit, reg);
            Map<String, Integer> unknownMerged = new HashMap<>();
            unknownByKey.forEach((k, v) -> unknownMerged.merge(reg.canonical(k), v, Integer::sum));

            List<Map.Entry<String, Stats>> ranked = merged.entrySet().stream()
                    .filter(e -> e.getValue().total > 0)
                    .sorted(Comparator.<Map.Entry<String, Stats>>comparingInt(e -> -e.getValue().total)
                            .thenComparing(e -> -unknownMerged.getOrDefault(e.getKey(), 0))
                            .thenComparing(Map.Entry::getKey))
                    .toList();

            List<Map<String, Object>> contacts = new ArrayList<>();
            int rank = 1;
            for (Map.Entry<String, Stats> e : ranked) {
                if (contacts.size() >= maxLimit) break;
                Identity id = reg.identity(e.getKey());
                Stats s = e.getValue();
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("rank", rank++);
                item.put("identifier", id.identifier);
                item.put("contact_key", id.key);
                item.put("name", id.label());
                item.put("aliases", id.aliases);
                item.put("identity_basis", id.basis);
                item.put("total_interactions", s.total);
                item.put("messages_count", s.messages);
                item.put("calls_count", s.calls);
                item.put("emails_count", s.emails);
                item.put("incoming_count", s.incoming);
                item.put("outgoing_count", s.outgoing);
                item.put("unknown_direction_count", unknownMerged.getOrDefault(e.getKey(), 0));
                item.put("channels", new ArrayList<>(s.byChannel.keySet()));
                item.put("by_channel", s.byChannel);
                item.put("by_month", s.byMonth);
                item.put("active_days", s.days.size());
                item.put("first_interaction", s.first != null ? s.first : "");
                item.put("last_interaction", s.last != null ? s.last : "");
                item.put("evidence_ids", s.evidence.ids());
                item.put("is_group", id.group);
                contacts.add(item);
            }

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("total_contacts_discovered", ranked.size());
            response.put("returned_count", contacts.size());
            response.put("limit", maxLimit);
            response.put("truncated", ranked.size() > contacts.size());
            response.put("unknown_direction_total", unknownDirectionTotal);
            response.put("contacts", contacts);
            response.put("group_activity", groupActivity());
            return response;
        }

        private Map<String, Object> groupActivity() {
            IdentityRegistry reg = index.registry;
            List<Map<String, Object>> list = groups.entrySet().stream()
                    .sorted(Comparator.<Map.Entry<String, Stats>>comparingInt(e -> -e.getValue().total).thenComparing(Map.Entry::getKey))
                    .limit(MAX_GROUP_ACTIVITY)
                    .map(e -> {
                        Identity id = reg.identity(e.getKey());
                        Stats s = e.getValue();
                        Map<String, Object> g = new LinkedHashMap<>();
                        g.put("group_key", id.key);
                        g.put("identifier", id.identifier);
                        g.put("name", id.label());
                        g.put("total_messages", s.total);
                        g.put("unknown_direction_count", s.unknown);
                        g.put("senders_observed", groupSenders.getOrDefault(e.getKey(), Set.of()).size());
                        g.put("by_channel", s.byChannel);
                        g.put("first_interaction", s.first != null ? s.first : "");
                        g.put("last_interaction", s.last != null ? s.last : "");
                        g.put("evidence_ids", s.evidence.ids());
                        return g;
                    }).toList();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("total_groups", groups.size());
            m.put("total_group_events", groups.values().stream().mapToInt(s -> s.total).sum());
            m.put("returned_count", list.size());
            m.put("groups", list);
            return m;
        }
    }

    private static Map<String, Stats> mergeByCanonical(Map<String, Stats> source, IdentityRegistry reg) {
        Map<String, Stats> merged = new HashMap<>();
        source.forEach((k, s) -> {
            String root = reg.canonical(k);
            Stats target = merged.get(root);
            if (target == null) {
                target = new Stats(s.evidence.limit);
                merged.put(root, target);
            }
            target.merge(s);
        });
        return merged;
    }

    // =========================================================================
    // COMMUNICATIONS GRAPH
    // =========================================================================

    public static final class GraphAggregator implements Consumer<CommunicationEvent> {
        final IdentityIndex index = new IdentityIndex();
        final Map<String, Stats> edges = new HashMap<>();
        final Map<String, Set<String>> groupSenders = new HashMap<>();
        final int evidenceLimit;
        long unlinkedEvents;

        public GraphAggregator() {
            this(DEFAULT_EVIDENCE_LIMIT);
        }

        public GraphAggregator(int evidenceLimit) {
            this.evidenceLimit = evidenceLimit;
        }

        @Override
        public void accept(CommunicationEvent ev) {
            index.accept(ev);
            String local = ev.localKey();
            boolean linked = false;
            if (ev.groupMessage) {
                if (ev.group != null) {
                    String src = ev.direction == Direction.OUTGOING ? local : (ev.sender != null ? ev.sender.key : null);
                    if (src != null) {
                        linked = addEdge(src, ev.group.key, ev);
                        if (ev.direction != Direction.OUTGOING) {
                            groupSenders.computeIfAbsent(ev.group.key, k -> new HashSet<>()).add(ev.sender.key);
                        }
                    }
                }
            } else {
                switch (ev.direction) {
                    case INCOMING -> {
                        if (ev.sender != null) linked = addEdge(ev.sender.key, local, ev);
                    }
                    case OUTGOING -> {
                        for (Party r : ev.recipients) linked |= addEdge(local, r.key, ev);
                    }
                    default -> {
                        if (ev.sender != null) {
                            for (Party r : ev.recipients) linked |= addEdge(ev.sender.key, r.key, ev);
                        }
                    }
                }
            }
            if (!linked) unlinkedEvents++;
        }

        private boolean addEdge(String src, String tgt, CommunicationEvent ev) {
            if (src.equals(tgt)) return false;
            edges.computeIfAbsent(src + "\u0000" + tgt, k -> new Stats(evidenceLimit)).record(ev);
            return true;
        }

        public Map<String, Object> result(String focalContact, int minInteractions, int limitEdges, boolean includeMermaid) {
            int safeMin = minInteractions <= 0 ? 5 : minInteractions;
            int maxEdges = Math.clamp(limitEdges <= 0 ? DEFAULT_EDGE_LIMIT : limitEdges, 1, MAX_EDGE_LIMIT);
            IdentityRegistry reg = index.registry;

            // Canonicalize endpoints and merge
            Map<String, Stats> merged = new HashMap<>();
            edges.forEach((k, s) -> {
                int sep = k.indexOf('\u0000');
                String src = reg.canonical(k.substring(0, sep));
                String tgt = reg.canonical(k.substring(sep + 1));
                if (src.equals(tgt)) return;
                merged.computeIfAbsent(src + "\u0000" + tgt, x -> new Stats(evidenceLimit)).merge(s);
            });

            String focal = isBlank(focalContact) ? null : focalContact.trim().toLowerCase(Locale.ROOT);
            List<Map.Entry<String, Stats>> eligible = merged.entrySet().stream()
                    .filter(e -> e.getValue().total >= safeMin)
                    .filter(e -> {
                        if (focal == null) return true;
                        int sep = e.getKey().indexOf('\u0000');
                        return nodeMatches(e.getKey().substring(0, sep), focal) || nodeMatches(e.getKey().substring(sep + 1), focal);
                    })
                    .sorted(Comparator.<Map.Entry<String, Stats>>comparingInt(e -> -e.getValue().total).thenComparing(Map.Entry::getKey))
                    .toList();
            List<Map.Entry<String, Stats>> kept = eligible.stream().limit(maxEdges).toList();

            Map<String, Map<String, Object>> nodes = new LinkedHashMap<>();
            List<Map<String, Object>> edgeList = new ArrayList<>();
            for (Map.Entry<String, Stats> e : kept) {
                int sep = e.getKey().indexOf('\u0000');
                String src = e.getKey().substring(0, sep);
                String tgt = e.getKey().substring(sep + 1);
                Stats s = e.getValue();
                for (String n : List.of(src, tgt)) {
                    Map<String, Object> node = nodes.computeIfAbsent(n, k -> buildNode(k, focal));
                    node.put("total_interactions", (int) node.get("total_interactions") + s.total);
                }
                Map<String, Object> edge = new LinkedHashMap<>();
                edge.put("source", src);
                edge.put("target", tgt);
                edge.put("weight", s.total);
                edge.put("channels", new ArrayList<>(s.byChannel.keySet()));
                edge.put("by_channel", s.byChannel);
                edge.put("messages_count", s.messages);
                edge.put("calls_count", s.calls);
                edge.put("emails_count", s.emails);
                edge.put("first_interaction", s.first != null ? s.first : "");
                edge.put("last_interaction", s.last != null ? s.last : "");
                edge.put("by_month", s.byMonth);
                edge.put("evidence_ids", s.evidence.ids());
                edgeList.add(edge);
            }

            if (focal != null && nodes.isEmpty()) {
                reg.all().stream()
                        .filter(id -> id.matchesSubstring(focal))
                        .min(Comparator.comparing(id -> id.key))
                        .ifPresent(id -> nodes.put(id.key, buildNode(id.key, focal)));
            }

            List<Map<String, Object>> nodeList = new ArrayList<>(nodes.values());
            nodeList.sort(Comparator.<Map<String, Object>>comparingInt(n -> -(int) n.get("total_interactions"))
                    .thenComparing(n -> (String) n.get("id")));

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("graph_source", "lucene-index");
            if (focal != null) response.put("focal_contact", focalContact.trim());
            response.put("min_interactions", safeMin);
            response.put("limit_edges", maxEdges);
            response.put("total_nodes", nodeList.size());
            response.put("total_edges", edgeList.size());
            response.put("total_eligible_edges", eligible.size());
            response.put("returned_edges", edgeList.size());
            response.put("truncated", eligible.size() > edgeList.size());
            response.put("unlinked_events", unlinkedEvents);
            response.put("nodes", nodeList);
            response.put("edges", edgeList);
            if (includeMermaid) response.put("mermaid", toMermaid(nodeList, edgeList));
            if (focal != null && edgeList.isEmpty()) {
                response.put("message", "Nenhuma conexão encontrada para o contato focal com o limite mínimo de "
                        + safeMin + " interações. Tente reduzir 'min_interactions' para 1.");
            }
            return response;
        }

        private boolean nodeMatches(String key, String focalLower) {
            if (key.startsWith(LOCAL_PREFIX)) {
                String channel = key.substring(LOCAL_PREFIX.length());
                return index.localObservedKeys.getOrDefault(channel, Map.of()).entrySet().stream()
                        .anyMatch(e -> e.getKey().contains(focalLower) || e.getValue().toLowerCase(Locale.ROOT).contains(focalLower));
            }
            return index.registry.identity(key).matchesSubstring(focalLower);
        }

        private Map<String, Object> buildNode(String key, String focalLower) {
            Map<String, Object> node = new LinkedHashMap<>();
            if (key.startsWith(LOCAL_PREFIX)) {
                String channel = key.substring(LOCAL_PREFIX.length());
                node.put("id", key);
                node.put("label", "Ponto local (" + channel + ")");
                node.put("name", null);
                node.put("aliases", List.of());
                node.put("type", "LOCAL_ENDPOINT");
                node.put("phone_or_account", "");
                node.put("total_interactions", 0);
                node.put("is_focal", focalLower != null && nodeMatches(key, focalLower));
                node.put("identity_resolved", false);
                node.put("observed_local_identifiers", index.observedFor(channel));
                return node;
            }
            Identity id = index.registry.identity(key);
            node.put("id", key);
            node.put("label", id.label());
            node.put("name", id.name);
            node.put("aliases", id.aliases);
            node.put("type", id.group ? "GROUP" : "ACCOUNT");
            node.put("phone_or_account", id.identifier);
            node.put("total_interactions", 0);
            node.put("is_focal", id.matchesSubstring(focalLower));
            node.put("identity_resolved", true);
            if (id.group) {
                node.put("is_group", true);
                int participants = 0;
                for (String k : id.keys) participants += groupSenders.getOrDefault(k, Set.of()).size();
                node.put("participants_observed", participants);
            }
            return node;
        }
    }

    /** Mermaid flowchart with exactly the returned nodes and edges. */
    public static String toMermaid(List<Map<String, Object>> nodes, List<Map<String, Object>> edges) {
        StringBuilder sb = new StringBuilder("flowchart LR\n");
        Map<String, String> ids = new HashMap<>();
        int i = 0;
        for (Map<String, Object> n : nodes) {
            String mid = "n" + i++;
            ids.put((String) n.get("id"), mid);
            String label = escapeMermaid(String.valueOf(n.get("label")));
            String type = String.valueOf(n.get("type"));
            switch (type) {
                case "LOCAL_ENDPOINT" -> sb.append("    ").append(mid).append("([\"").append(label).append("\"])\n");
                case "GROUP" -> sb.append("    ").append(mid).append("{{\"").append(label).append("\"}}\n");
                default -> sb.append("    ").append(mid).append("[\"").append(label).append("\"]\n");
            }
        }
        for (Map<String, Object> e : edges) {
            String s = ids.get((String) e.get("source"));
            String t = ids.get((String) e.get("target"));
            if (s == null || t == null) continue;
            sb.append("    ").append(s).append(" -->|").append(e.get("weight")).append("| ").append(t).append('\n');
        }
        return sb.toString();
    }

    static String escapeMermaid(String label) {
        return label.replace("\"", "#quot;").replace("\n", " ").replace("\r", " ")
                .replace("<", "#lt;").replace(">", "#gt;").replace("|", "#124;");
    }

    // =========================================================================
    // CONTACT RELATIONSHIP
    // =========================================================================

    /** Result of resolving a contact reference against the registry. */
    static final class Resolution {
        final String reference;
        final List<Identity> matches;
        final Set<String> localChannels;

        Resolution(String reference, List<Identity> matches, Set<String> localChannels) {
            this.reference = reference;
            this.matches = matches;
            this.localChannels = localChannels;
        }

        boolean ambiguous() {
            return matches.size() > 1;
        }

        boolean unresolved() {
            return matches.isEmpty() && localChannels.isEmpty();
        }

        Set<String> keys() {
            return matches.size() == 1 ? matches.getFirst().keys : Set.of();
        }
    }

    /** Resolves by key, identifier or alias equality first; only then by name substring. */
    static Resolution resolveReference(String reference, IdentityIndex index, ExtractionContext ctx) {
        String ref = reference.trim();
        String refLower = ref.toLowerCase(Locale.ROOT);
        Set<String> refKeys = new HashSet<>();
        refKeys.add(refLower);
        if (isPhoneLike(ref)) refKeys.add("tel:" + normalizePhone(ref, ctx != null ? ctx.phoneRegion() : null));
        if (refLower.contains("@")) refKeys.add("email:" + refLower);

        List<Identity> exact = new ArrayList<>();
        for (Identity id : index.registry.all()) {
            boolean match = false;
            for (String k : id.keys) {
                if (refKeys.contains(k) || k.endsWith(":" + refLower)) {
                    match = true;
                    break;
                }
            }
            if (!match && id.identifier != null && id.identifier.equalsIgnoreCase(ref)) match = true;
            if (!match) {
                for (String a : id.aliases) {
                    if (a.equalsIgnoreCase(ref)) {
                        match = true;
                        break;
                    }
                }
            }
            if (match) exact.add(id);
        }

        Set<String> localChannels = new TreeSet<>();
        index.localObservedKeys.forEach((channel, keyToId) -> {
            for (Map.Entry<String, String> e : keyToId.entrySet()) {
                if (refKeys.contains(e.getKey()) || e.getValue().equalsIgnoreCase(ref)) {
                    localChannels.add(channel);
                }
            }
        });

        List<Identity> matches = exact;
        if (matches.isEmpty() && localChannels.isEmpty()) {
            matches = index.registry.all().stream()
                    .filter(id -> id.name != null && id.name.toLowerCase(Locale.ROOT).contains(refLower))
                    .toList();
        }
        matches = matches.stream().sorted(Comparator.comparing(id -> id.key)).toList();
        return new Resolution(ref, matches, localChannels);
    }

    /**
     * Details how two contacts relate. Walks the source twice: once to resolve identities, once to aggregate.
     */
    public static Map<String, Object> relationship(EventSource source, String contactA, String contactB,
                                                   int evidenceLimit, ExtractionContext ctx, Diagnostics diagnostics) throws IOException {
        int safeEvidence = Math.clamp(evidenceLimit <= 0 ? DEFAULT_EVIDENCE_LIMIT : evidenceLimit, 1, MAX_EVIDENCE_LIMIT);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("contact_a", contactA);
        response.put("contact_b", contactB);

        IdentityIndex index = new IdentityIndex();
        source.forEach(index, null);

        Resolution a = resolveReference(contactA, index, ctx);
        Resolution b = resolveReference(contactB, index, ctx);

        if (a.ambiguous() || b.ambiguous()) {
            response.put("status", "ambiguous");
            if (a.ambiguous()) response.put("candidates_a", candidates(a));
            if (b.ambiguous()) response.put("candidates_b", candidates(b));
            response.put("message", "Uma ou mais referências correspondem a várias identidades. Repita a consulta usando o 'contact_key' ou o identificador de um dos candidatos.");
            return response;
        }
        if (a.unresolved() || b.unresolved()) {
            response.put("status", "not_found");
            List<String> missing = new ArrayList<>();
            if (a.unresolved()) missing.add(contactA);
            if (b.unresolved()) missing.add(contactB);
            response.put("unresolved_references", missing);
            response.put("message", "Referência não encontrada nos eventos de comunicação indexados (Communication:From/To): " + String.join(", ", missing) + ".");
            return response;
        }

        Set<String> keysA = a.keys();
        Set<String> keysB = b.keys();
        if (!keysA.isEmpty() && keysA.equals(keysB)) {
            response.put("status", "same_identity");
            response.put("message", "As duas referências correspondem à mesma identidade.");
            response.put("resolved_a", describeResolution(a));
            return response;
        }

        Stats direct = new Stats(safeEvidence);
        Stats inferred = new Stats(safeEvidence);
        Map<String, int[]> groupCounts = new HashMap<>();
        Map<String, String> groupChannel = new HashMap<>();
        IdentityRegistry reg = index.registry;

        final int[] counters = new int[2];
        source.forEach(ev -> {
            String s = ev.sender != null ? reg.canonical(ev.sender.key) : null;
            Set<String> rs = new HashSet<>();
            for (Party r : ev.recipients) rs.add(reg.canonical(r.key));
            boolean sA = s != null && keysA.contains(s);
            boolean sB = s != null && keysB.contains(s);

            if (ev.groupMessage) {
                if (ev.group == null) return;
                String g = reg.canonical(ev.group.key);
                boolean byA = sA || (ev.direction == Direction.OUTGOING && ev.sender == null && a.localChannels.contains(ev.channel));
                boolean byB = sB || (ev.direction == Direction.OUTGOING && ev.sender == null && b.localChannels.contains(ev.channel));
                // One reference is the group itself: the other's messages to it are direct interactions
                if (byA && keysB.contains(g)) {
                    direct.record(ev);
                    counters[0]++;
                    return;
                }
                if (byB && keysA.contains(g)) {
                    direct.record(ev);
                    counters[1]++;
                    return;
                }
                if (byA || byB) {
                    int[] c = groupCounts.computeIfAbsent(g, k -> new int[2]);
                    if (byA) c[0]++;
                    if (byB) c[1]++;
                    groupChannel.putIfAbsent(g, ev.channel);
                }
                return;
            }

            if (sA && !Collections.disjoint(rs, keysB)) {
                direct.record(ev);
                counters[0]++;
                return;
            }
            if (sB && !Collections.disjoint(rs, keysA)) {
                direct.record(ev);
                counters[1]++;
                return;
            }
            // Endpoint inferred: one reference is a local identifier of this channel, local side has no identity
            if (!ev.hasExplicitDirection()) return;
            for (int side = 0; side < 2; side++) {
                Resolution local = side == 0 ? a : b;
                Set<String> counterpart = side == 0 ? keysB : keysA;
                if (!local.localChannels.contains(ev.channel) || counterpart.isEmpty()) continue;
                if (ev.direction == Direction.INCOMING && ev.recipients.isEmpty() && s != null && counterpart.contains(s)) {
                    inferred.record(ev);
                    counters[side == 0 ? 1 : 0]++;
                    return;
                }
                if (ev.direction == Direction.OUTGOING && ev.sender == null && !Collections.disjoint(rs, counterpart)) {
                    inferred.record(ev);
                    counters[side]++;
                    return;
                }
            }
        }, diagnostics);
        int aToB = counters[0];
        int bToA = counters[1];

        Stats total = new Stats(safeEvidence);
        total.merge(direct);
        total.merge(inferred);

        List<Map<String, Object>> shared = groupCounts.entrySet().stream()
                .filter(e -> e.getValue()[0] > 0 && e.getValue()[1] > 0)
                .sorted(Comparator.<Map.Entry<String, int[]>>comparingInt(e -> -(e.getValue()[0] + e.getValue()[1])).thenComparing(Map.Entry::getKey))
                .map(e -> {
                    Identity g = reg.identity(e.getKey());
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("group_key", g.key);
                    m.put("name", g.label());
                    m.put("channel", groupChannel.get(e.getKey()));
                    m.put("messages_by_a", e.getValue()[0]);
                    m.put("messages_by_b", e.getValue()[1]);
                    return m;
                }).toList();

        response.put("status", total.total > 0 || !shared.isEmpty() ? "found" : "no_relationship");
        response.put("resolved_a", describeResolution(a));
        response.put("resolved_b", describeResolution(b));
        response.put("total_interactions", total.total);
        response.put("messages_count", total.messages);
        response.put("calls_count", total.calls);
        response.put("emails_count", total.emails);
        Map<String, Object> byDirection = new LinkedHashMap<>();
        byDirection.put("a_to_b", aToB);
        byDirection.put("b_to_a", bToA);
        response.put("by_direction", byDirection);
        Map<String, Object> byEventDirection = new LinkedHashMap<>();
        byEventDirection.put("incoming", total.incoming);
        byEventDirection.put("outgoing", total.outgoing);
        byEventDirection.put("unknown", total.unknown);
        response.put("by_event_direction", byEventDirection);
        response.put("by_channel", total.byChannel);
        response.put("by_month", total.byMonth);
        response.put("active_days", total.days.size());
        response.put("first_interaction", total.first != null ? total.first : "");
        response.put("last_interaction", total.last != null ? total.last : "");
        response.put("shared_groups", shared);
        response.put("evidence_ids", total.evidence.ids());
        response.put("endpoint_inferred", inferred.total > 0);
        response.put("endpoint_inferred_count", inferred.total);
        if (inferred.total > 0) response.put("endpoint_inferred_evidence_ids", inferred.evidence.ids());
        if (total.total == 0 && shared.isEmpty()) {
            response.put("message", "Nenhum vínculo entre as duas referências foi encontrado nas propriedades indexadas (Communication:From/To, grupos).");
        }
        return response;
    }

    private static List<Map<String, Object>> candidates(Resolution r) {
        return r.matches.stream().limit(MAX_CANDIDATES).map(id -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("contact_key", id.key);
            m.put("identifier", id.identifier);
            m.put("name", id.label());
            m.put("aliases", id.aliases);
            m.put("is_group", id.group);
            return m;
        }).toList();
    }

    private static Map<String, Object> describeResolution(Resolution r) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (r.matches.size() == 1) {
            Identity id = r.matches.getFirst();
            m.put("contact_key", id.key);
            m.put("identifier", id.identifier);
            m.put("name", id.label());
            m.put("aliases", id.aliases);
            m.put("is_group", id.group);
        }
        if (!r.localChannels.isEmpty()) {
            m.put("observed_on_local_side_of", new ArrayList<>(r.localChannels));
        }
        return m;
    }

    // =========================================================================
    // SMALL HELPERS
    // =========================================================================

    static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String lower(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    private static String channelKey(String channel) {
        return isBlank(channel) ? CHANNEL_UNKNOWN : channel.trim().toLowerCase(Locale.ROOT);
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (!isBlank(v)) return v.trim();
        }
        return null;
    }

    private static String blankToNull(String s) {
        return isBlank(s) ? null : s.trim();
    }

    private static String stripQuotes(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")) t = t.substring(1, t.length() - 1).trim();
        return t;
    }

    private static boolean hasLetterOrDigit(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isLetterOrDigit(s.charAt(i))) return true;
        }
        return false;
    }

    private static String[] nonNull(String[] values) {
        return values == null ? new String[0] : values;
    }

    private static String at(String[] values, int i) {
        return i < values.length ? values[i] : null;
    }
}
