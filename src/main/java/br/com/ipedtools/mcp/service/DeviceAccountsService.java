package br.com.ipedtools.mcp.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.util.BytesRef;

import iped.engine.data.IPEDSource;
import iped.engine.search.IPEDSearcher;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;

/**
 * Device information and account identifiers recorded by the extraction and by IPED parsers (issue #3).
 * Every value carries its source items; no value is labeled as belonging to the device owner, and regex hits
 * or file names are never used as sources.
 */
public final class DeviceAccountsService {

    // Account media types, as classified by iped.engine.graph.GraphTask.getRelationType ("useraccount"),
    // plus the Discord account. Checked against the IPED constants by DeviceAccountsServiceTest.
    static final String SKYPE_ACCOUNT = "contact/x-skype-account"; // SkypeParser.ACCOUNT_MIME_TYPE
    static final String WHATSAPP_ACCOUNT = "application/x-whatsapp-account"; // WhatsAppParser.WHATSAPP_ACCOUNT
    static final String TELEGRAM_ACCOUNT = "application/x-telegram-account"; // TelegramParser.TELEGRAM_ACCOUNT
    static final String DISCORD_ACCOUNT = "application/x-discord-account"; // DiscordParser.DISCORD_ACCOUNT
    static final String UFED_USER = "application/x-ufed-user";
    static final String UFED_USER_ACCOUNT = "application/x-ufed-useraccount"; // MediaTypes.UFED_USER_ACCOUNT_MIME
    static final String UFED_DEVICE_INFO = "application/x-ufed-deviceinfoentry";

    static final Set<String> UFED_ACCOUNT_TYPES = Set.of(UFED_USER, UFED_USER_ACCOUNT);
    static final Set<String> PARSER_ACCOUNT_TYPES = Set.of(SKYPE_ACCOUNT, WHATSAPP_ACCOUNT, TELEGRAM_ACCOUNT, DISCORD_ACCOUNT);

    static final String KIND_UFED_ACCOUNT = "ufed_account";
    static final String KIND_PARSER_ACCOUNT = "iped_parser_account";
    static final String KIND_DEVICE_INFO = "device_info";

    static final String UFED_PHONE = ExtraProperties.UFED_META_PREFIX + "PhoneNumber";
    static final String UFED_USERNAME = ExtraProperties.UFED_META_PREFIX + "Username";
    static final String UFED_USER_ID = ExtraProperties.UFED_META_PREFIX + "UserID";
    static final String UFED_EMAIL = ExtraProperties.UFED_META_PREFIX + "EmailAddress";
    static final String UFED_SERVICE_TYPE = ExtraProperties.UFED_META_PREFIX + "ServiceType";
    static final String UFED_ENTRY_NAME = ExtraProperties.UFED_META_PREFIX + "EntryName";
    static final String UFED_ENTRY_VALUE = ExtraProperties.UFED_META_PREFIX + "EntryValue";

    /** Properties that hold a phone number by definition. */
    static final Set<String> PHONE_PROPERTIES = Set.of(UFED_PHONE, ExtraProperties.USER_PHONE);
    /** All account identifier properties read, in order. */
    static final List<String> IDENTIFIER_PROPERTIES = List.of(UFED_PHONE, ExtraProperties.USER_PHONE, UFED_USERNAME,
            ExtraProperties.USER_ACCOUNT, ExtraProperties.USER_NAME, UFED_USER_ID, UFED_EMAIL, ExtraProperties.USER_EMAIL);

    static final int MAX_IDENTIFIERS = 20;
    static final int MAX_SOURCES = 10;
    static final int MAX_DEVICE_INFO = 60;
    static final int MAX_PROFILES = 30;
    static final int MAX_HIVES = 30;

    /** Registry hive media types (IPED CustomSignatures.xml, subclasses of application/x-windows-registry-main). */
    static final List<String> HIVE_TYPES = List.of("application/x-windows-registry-sam",
            "application/x-windows-registry-software", "application/x-windows-registry-system",
            "application/x-windows-registry-security", "application/x-windows-registry-ntuser",
            "application/x-windows-registry-usrclass", "application/x-windows-registry-amcache");
    /** Highest position (0-based) of the Users/home segment in a profile path. */
    static final int MAX_PROFILE_PARENT_DEPTH = 2;
    static final Set<String> IGNORED_PROFILE_DIRS = Set.of("all users", "default", "default user", "public", "público",
            "todos os usuários", "defaultapppool", "localappdata", "application data", "appdata");

    static final String NOTE = "Valores registrados pela extração e pelos parsers do IPED, com a origem de cada um. "
            + "O servidor não identifica o dono do aparelho: compare as fontes (identificadores sustentados por vários "
            + "apps pesam mais), confirme com list_properties(identity=true) e describe_property, e cite os item_id.";

    static final List<String> SUGGESTIONS = List.of(
            "Propriedades EXIF de câmera: list_properties(pattern=\"image:Make\") e list_properties(pattern=\"image:Model\").",
            "Estrutura de pastas da evidência: list_folder_contents.",
            "Conteúdo de documentos, mensagens e mídias: search_documents.");

    private static final Pattern PACKAGE_LIKE = Pattern.compile("^[a-z0-9_]+(\\.[a-z0-9_]+){2,}$");
    private static final Pattern UUID_LIKE = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-.*");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern UFED_ACCOUNT_APP = Pattern.compile("^UserAccount-\\[([^\\]]+)\\]");
    private static final Pattern DEVICE_PHONE_ENTRY = Pattern.compile("(?i).*(msisdn|phone|telefone).*");
    private static final Set<String> MESSENGER_DOMAINS = Set.of("s.whatsapp.net", "c.us", "g.us", "broadcast",
            "lid", "whatsapp.net");

    private static final DeviceAccountsService INSTANCE = new DeviceAccountsService();

    private DeviceAccountsService() {
    }

    public static DeviceAccountsService getInstance() {
        return INSTANCE;
    }

    // =========================================================================
    // CLASSIFICATION (pure functions, unit tested)
    // =========================================================================

    /** A classified identifier: kind (phone, email, messenger_id, username) and its aggregation key. */
    record Identifier(String kind, String key, String display) {
    }

    static boolean isPlaceholder(String value, String app, String serviceType) {
        String v = value.trim();
        if (v.isEmpty()) {
            return true;
        }
        if (v.equalsIgnoreCase(app) || v.equalsIgnoreCase(serviceType)) {
            return true;
        }
        if (PACKAGE_LIKE.matcher(v).matches()) {
            return true;
        }
        // Android account manager entries (package-like service type) carry display labels of the account type
        // ("Signal", "Duo", "Rakuten Viber") instead of identifiers.
        boolean accountManagerEntry = serviceType != null && PACKAGE_LIKE.matcher(serviceType.trim()).matches();
        return accountManagerEntry && Character.isUpperCase(v.charAt(0)) && !v.matches(".*[0-9@].*");
    }

    private static String phoneKey(String value) {
        String normalized = CommunicationAnalytics.normalizePhone(value, System.getProperty(IpedCoreService.PHONE_REGION_PROPERTY));
        return normalized.isEmpty() ? null : normalized;
    }

    /**
     * Classifies a value read from {@code property}. Values from phone properties are phones when phone-like;
     * values from other properties are phones only when written in international form ({@code +...}).
     */
    static List<Identifier> classify(String value, String property) {
        List<Identifier> out = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return out;
        }
        String v = value.trim();
        int at = v.indexOf('@');
        if (at > 0) {
            String local = v.substring(0, at);
            String domain = v.substring(at + 1).toLowerCase(Locale.ROOT);
            if (MESSENGER_DOMAINS.contains(domain)) {
                out.add(new Identifier("messenger_id", v.toLowerCase(Locale.ROOT), v.toLowerCase(Locale.ROOT)));
                if (CommunicationAnalytics.isPhoneLike(local)) {
                    String key = phoneKey(local);
                    if (key != null) {
                        out.add(new Identifier("phone", key, "+" + key));
                    }
                }
                return out;
            }
            if (EMAIL.matcher(v).matches()) {
                out.add(new Identifier("email", v.toLowerCase(Locale.ROOT), v.toLowerCase(Locale.ROOT)));
                return out;
            }
        }
        boolean phoneProperty = PHONE_PROPERTIES.contains(property);
        boolean looksPhone = CommunicationAnalytics.isPhoneLike(v) && (phoneProperty || v.startsWith("+"));
        if (looksPhone) {
            String key = phoneKey(v);
            if (key != null) {
                out.add(new Identifier("phone", key, "+" + key));
                return out;
            }
        }
        out.add(new Identifier("username", v, v));
        return out;
    }

    /** Application of an account item: account type, UFED account name, then UFED service type. */
    static String appOf(String accountType, String itemName, String serviceType) {
        if (accountType != null && !accountType.isBlank()) {
            return accountType.trim();
        }
        if (itemName != null) {
            Matcher m = UFED_ACCOUNT_APP.matcher(itemName);
            if (m.find()) {
                String label = m.group(1).trim();
                if (!label.equalsIgnoreCase("Accounts") && !UUID_LIKE.matcher(label).matches() && !label.isEmpty()) {
                    return label;
                }
            }
        }
        if (serviceType != null && !serviceType.isBlank()) {
            return serviceType.trim();
        }
        return "unknown";
    }

    // =========================================================================
    // AGGREGATION
    // =========================================================================

    /** Accumulates identifiers of one evidence container. */
    static final class Aggregator {
        private final Map<String, Map<String, Map<String, Object>>> byKind = new LinkedHashMap<>();
        private int discarded;

        void add(String value, String property, String app, String serviceType, int itemId, String sourceKind) {
            if (value == null || value.isBlank()) {
                return;
            }
            if (!KIND_DEVICE_INFO.equals(sourceKind) && isPlaceholder(value, app, serviceType)) {
                discarded++;
                return;
            }
            for (Identifier id : classify(value, property)) {
                addIdentifier(id, property, app, itemId, sourceKind);
            }
        }

        void addIdentifier(Identifier id, String property, String app, int itemId, String sourceKind) {
            Map<String, Map<String, Object>> kindMap = byKind.computeIfAbsent(id.kind(), k -> new LinkedHashMap<>());
            Map<String, Object> entry = kindMap.computeIfAbsent(id.key(), k -> {
                Map<String, Object> e = new LinkedHashMap<>();
                e.put("value", id.display());
                e.put("sources", new ArrayList<Map<String, Object>>());
                return e;
            });
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> sources = (List<Map<String, Object>>) entry.get("sources");
            for (Map<String, Object> s : sources) {
                if (s.get("item_id").equals(itemId) && s.get("property").equals(property)) {
                    return;
                }
            }
            Map<String, Object> src = new LinkedHashMap<>();
            src.put("item_id", itemId);
            src.put("app", app);
            src.put("property", property);
            src.put("source_kind", sourceKind);
            sources.add(src);
        }

        int discarded() {
            return discarded;
        }

        boolean isEmpty() {
            return byKind.isEmpty();
        }

        /** Identifiers per kind, ordered by distinct apps then distinct items, with limits and totals. */
        Map<String, Object> toResponse() {
            Map<String, Object> out = new LinkedHashMap<>();
            for (String kind : List.of("phone", "email", "messenger_id", "username")) {
                Map<String, Map<String, Object>> kindMap = byKind.get(kind);
                if (kindMap == null || kindMap.isEmpty()) {
                    continue;
                }
                List<Map<String, Object>> list = new ArrayList<>();
                for (Map<String, Object> e : kindMap.values()) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> sources = (List<Map<String, Object>>) e.get("sources");
                    Map<String, String> apps = new java.util.TreeMap<>();
                    Set<Object> items = new HashSet<>();
                    for (Map<String, Object> s : sources) {
                        String app = String.valueOf(s.get("app"));
                        apps.putIfAbsent(app.toLowerCase(Locale.ROOT), app);
                        items.add(s.get("item_id"));
                    }
                    Map<String, Object> r = new LinkedHashMap<>();
                    r.put("value", e.get("value"));
                    r.put("app_count", apps.size());
                    r.put("apps", new ArrayList<>(apps.values()));
                    r.put("source_count", items.size());
                    r.put("sources", sources.size() > MAX_SOURCES ? sources.subList(0, MAX_SOURCES) : sources);
                    if (sources.size() > MAX_SOURCES) {
                        r.put("sources_truncated", true);
                    }
                    list.add(r);
                }
                list.sort(Comparator.comparingInt((Map<String, Object> m) -> (Integer) m.get("app_count")).reversed()
                        .thenComparing(Comparator.comparingInt((Map<String, Object> m) -> (Integer) m.get("source_count")).reversed())
                        .thenComparing(m -> String.valueOf(m.get("value"))));
                Map<String, Object> block = new LinkedHashMap<>();
                block.put("total", list.size());
                block.put("items", list.size() > MAX_IDENTIFIERS ? list.subList(0, MAX_IDENTIFIERS) : list);
                if (list.size() > MAX_IDENTIFIERS) {
                    block.put("truncated", true);
                }
                out.put(kind, block);
            }
            return out;
        }
    }

    // =========================================================================
    // CASE READING
    // =========================================================================

    /** Per-container collector. */
    private static final class Container {
        final Map<String, Object> header = new LinkedHashMap<>();
        final List<Map<String, Object>> deviceInfo = new ArrayList<>();
        final Aggregator identifiers = new Aggregator();
        final List<Map<String, Object>> profiles = new ArrayList<>();
        final List<Map<String, Object>> hives = new ArrayList<>();

        boolean mobile() {
            return !deviceInfo.isEmpty() || !identifiers.isEmpty();
        }

        boolean computer() {
            return !profiles.isEmpty() || !hives.isEmpty();
        }
    }

    private static String typeOf(boolean mobile, boolean computer) {
        if (mobile && computer) {
            return "mixed";
        }
        return mobile ? "mobile" : computer ? "computer" : "unknown";
    }

    /** {@code get_device_and_accounts}. */
    public Map<String, Object> getDeviceAndAccounts() throws IOException {
        IpedCoreService core = IpedCoreService.getInstance();
        IPEDSource source = core.getSource();
        if (source == null) {
            throw new IllegalStateException("Nenhum caso do IPED está aberto no momento.");
        }
        IndexReader reader = source.getReader();
        Map<String, Container> containers = loadContainers(source);
        Container fallback = new Container();
        fallback.header.put("id", 0);
        fallback.header.put("name", core.getSourceId());
        fallback.header.put("path", core.getCaseDirectory() != null ? core.getCaseDirectory().getAbsolutePath() : "");

        Set<String> wanted = new LinkedHashSet<>(UFED_ACCOUNT_TYPES);
        wanted.addAll(PARSER_ACCOUNT_TYPES);
        wanted.add(UFED_DEVICE_INFO);
        Map<String, List<Integer>> docsByType = docsByContentType(reader, wanted);

        Set<String> accountFields = new HashSet<>(IDENTIFIER_PROPERTIES);
        accountFields.addAll(List.of(BasicProps.NAME, BasicProps.PATH, ExtraProperties.USER_ACCOUNT_TYPE, UFED_SERVICE_TYPE));
        for (String type : wanted) {
            if (UFED_DEVICE_INFO.equals(type)) {
                continue;
            }
            String kind = UFED_ACCOUNT_TYPES.contains(type) ? KIND_UFED_ACCOUNT : KIND_PARSER_ACCOUNT;
            for (int luceneId : docsByType.getOrDefault(type, List.of())) {
                Document doc = reader.document(luceneId, accountFields);
                int itemId = source.getId(luceneId);
                String serviceType = doc.get(UFED_SERVICE_TYPE);
                String app = appOf(doc.get(ExtraProperties.USER_ACCOUNT_TYPE), doc.get(BasicProps.NAME), serviceType);
                Container c = containerFor(doc.get(BasicProps.PATH), containers, fallback);
                for (String prop : IDENTIFIER_PROPERTIES) {
                    for (String value : doc.getValues(prop)) {
                        c.identifiers.add(value, prop, app, serviceType, itemId, kind);
                    }
                }
            }
        }

        Set<String> infoFields = Set.of(UFED_ENTRY_NAME, UFED_ENTRY_VALUE, BasicProps.PATH);
        for (int luceneId : docsByType.getOrDefault(UFED_DEVICE_INFO, List.of())) {
            Document doc = reader.document(luceneId, infoFields);
            String name = doc.get(UFED_ENTRY_NAME);
            String value = doc.get(UFED_ENTRY_VALUE);
            if (name == null || value == null || value.isBlank()) {
                continue;
            }
            int itemId = source.getId(luceneId);
            Container c = containerFor(doc.get(BasicProps.PATH), containers, fallback);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", name);
            entry.put("value", value);
            entry.put("item_id", itemId);
            c.deviceInfo.add(entry);
            String v = value.trim();
            if (DEVICE_PHONE_ENTRY.matcher(name).matches() && CommunicationAnalytics.isPhoneLike(v)) {
                String key = phoneKey(v);
                if (key != null) {
                    c.identifiers.addIdentifier(new Identifier("phone", key, "+" + key), UFED_ENTRY_VALUE, name, itemId,
                            KIND_DEVICE_INFO);
                }
            } else if (EMAIL.matcher(v).matches()) {
                c.identifiers.addIdentifier(new Identifier("email", v.toLowerCase(Locale.ROOT), v.toLowerCase(Locale.ROOT)),
                        UFED_ENTRY_VALUE, name, itemId, KIND_DEVICE_INFO);
            }
        }

        collectProfiles(source, containers, fallback);
        collectHives(source, containers, fallback);

        List<Container> all = new ArrayList<>(containers.values());
        if (fallback.mobile() || fallback.computer() || all.isEmpty()) {
            all.add(fallback);
        }
        List<Map<String, Object>> evidences = new ArrayList<>();
        boolean anyMobile = false;
        boolean anyComputer = false;
        for (Container c : all) {
            anyMobile |= c.mobile();
            anyComputer |= c.computer();
            Map<String, Object> e = new LinkedHashMap<>(c.header);
            e.put("type", typeOf(c.mobile(), c.computer()));
            e.put("device_information_total", c.deviceInfo.size());
            e.put("device_information", c.deviceInfo.size() > MAX_DEVICE_INFO ? c.deviceInfo.subList(0, MAX_DEVICE_INFO) : c.deviceInfo);
            e.put("account_identifiers", c.identifiers.toResponse());
            e.put("discarded_placeholders", c.identifiers.discarded());
            e.put("user_profiles", c.profiles.size() > MAX_PROFILES ? c.profiles.subList(0, MAX_PROFILES) : c.profiles);
            e.put("registry_hives", c.hives.size() > MAX_HIVES ? c.hives.subList(0, MAX_HIVES) : c.hives);
            evidences.add(e);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("source", core.getSourceId());
        result.put("evidence_type", typeOf(anyMobile, anyComputer));
        result.put("note", NOTE);
        result.put("total_evidences", evidences.size());
        result.put("evidences", evidences);
        if (!anyMobile && !anyComputer) {
            result.put("no_structured_identifiers", true);
            result.put("message", "Nenhum identificador estruturado foi encontrado (Device Information, contas de "
                    + "usuário, perfis ou hives do registro). Isso é comum em cartões de memória e mídias removíveis.");
            result.put("suggestions", SUGGESTIONS);
        }
        return result;
    }

    // Leaf readers belong to the case IndexReader and must not be closed here.
    @SuppressWarnings("resource")
    static Map<String, List<Integer>> docsByContentType(IndexReader reader, Set<String> types) throws IOException {
        Map<String, List<Integer>> out = new LinkedHashMap<>();
        for (LeafReaderContext ctx : reader.leaves()) {
            SortedDocValues dv = ctx.reader().getSortedDocValues(BasicProps.CONTENTTYPE);
            if (dv == null) {
                continue;
            }
            Map<Integer, String> ordToType = new LinkedHashMap<>();
            for (String type : types) {
                int ord = dv.lookupTerm(new BytesRef(type));
                if (ord >= 0) {
                    ordToType.put(ord, type);
                }
            }
            if (ordToType.isEmpty()) {
                continue;
            }
            for (int doc = dv.nextDoc(); doc != DocIdSetIterator.NO_MORE_DOCS; doc = dv.nextDoc()) {
                String type = ordToType.get(dv.ordValue());
                if (type != null && (ctx.reader().getLiveDocs() == null || ctx.reader().getLiveDocs().get(doc))) {
                    out.computeIfAbsent(type, t -> new ArrayList<>()).add(ctx.docBase + doc);
                }
            }
        }
        return out;
    }

    private static Map<String, Container> loadContainers(IPEDSource source) throws IOException {
        Map<String, Container> containers = new LinkedHashMap<>();
        IPEDSearcher rootSearcher = new IPEDSearcher(source, BasicProps.ISROOT + ":true");
        rootSearcher.setTreeQuery(true);
        int[] rootIds = rootSearcher.search().getIds();
        if (rootIds == null) {
            return containers;
        }
        for (int rootId : rootIds) {
            int luceneId = source.getLuceneId(rootId);
            if (luceneId < 0) {
                continue;
            }
            Document doc = source.getReader().document(luceneId, Set.of(BasicProps.NAME, BasicProps.PATH));
            String name = doc.get(BasicProps.NAME);
            if (name == null || name.isBlank()) {
                continue;
            }
            Container c = new Container();
            c.header.put("id", rootId);
            c.header.put("name", name);
            c.header.put("path", doc.get(BasicProps.PATH) != null ? doc.get(BasicProps.PATH) : "/" + name);
            containers.put(name.toLowerCase(Locale.ROOT), c);
        }
        return containers;
    }

    static String normalizedPath(String path) {
        return "/" + path.replace('\\', '/').replaceAll("^/+", "").toLowerCase(Locale.ROOT);
    }

    private static Container containerFor(String path, Map<String, Container> containers, Container fallback) {
        if (path != null) {
            String norm = normalizedPath(path);
            for (Map.Entry<String, Container> e : containers.entrySet()) {
                if (norm.startsWith("/" + e.getKey() + "/") || norm.equals("/" + e.getKey())) {
                    return e.getValue();
                }
            }
        }
        return fallback;
    }

    /** True when a directory is a user profile directly under {@code Users/} or {@code home/}. */
    static boolean isUserProfileDir(String name, String path) {
        if (name == null || name.isBlank() || path == null) {
            return false;
        }
        String norm = normalizedPath(path);
        String lower = name.toLowerCase(Locale.ROOT);
        if (!(norm.endsWith("/users/" + lower) || norm.endsWith("/home/" + lower)) || IGNORED_PROFILE_DIRS.contains(lower)
                || lower.matches("[0-9]+")) { // numeric names are Android user ids (e.g. data/system/users/0)
            return false;
        }
        // Profiles sit near the volume root (<evidence>/<volume>/Users/<name>); deeper "users" folders belong to apps.
        String[] segments = norm.substring(1).split("/");
        return segments.length - 2 <= MAX_PROFILE_PARENT_DEPTH;
    }

    private static void collectProfiles(IPEDSource source, Map<String, Container> containers, Container fallback) throws IOException {
        int[] ids = new IPEDSearcher(source, "isDir:true AND (path:*Users* OR path:*home*)").search().getIds();
        if (ids == null) {
            return;
        }
        Set<String> seen = new HashSet<>();
        for (int id : ids) {
            int luceneId = source.getLuceneId(id);
            if (luceneId < 0) {
                continue;
            }
            Document doc = source.getReader().document(luceneId, Set.of(BasicProps.NAME, BasicProps.PATH));
            String name = doc.get(BasicProps.NAME);
            String path = doc.get(BasicProps.PATH);
            if (isUserProfileDir(name, path) && seen.add(normalizedPath(path))) {
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("name", name);
                p.put("path", path);
                p.put("item_id", id);
                containerFor(path, containers, fallback).profiles.add(p);
            }
        }
    }

    private static void collectHives(IPEDSource source, Map<String, Container> containers, Container fallback) throws IOException {
        Map<String, List<Integer>> docs = docsByContentType(source.getReader(), new LinkedHashSet<>(HIVE_TYPES));
        for (List<Integer> list : docs.values()) {
            for (int luceneId : list) {
                Document doc = source.getReader().document(luceneId, Set.of(BasicProps.NAME, BasicProps.PATH));
                Map<String, Object> h = new LinkedHashMap<>();
                h.put("name", doc.get(BasicProps.NAME));
                h.put("path", doc.get(BasicProps.PATH));
                h.put("item_id", source.getId(luceneId));
                containerFor(doc.get(BasicProps.PATH), containers, fallback).hives.add(h);
            }
        }
    }
}
