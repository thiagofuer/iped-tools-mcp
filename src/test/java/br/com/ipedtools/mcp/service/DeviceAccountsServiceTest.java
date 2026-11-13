package br.com.ipedtools.mcp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import br.com.ipedtools.mcp.test.TestCaseResolver;
import iped.engine.data.IPEDSource;
import iped.parsers.discord.DiscordParser;
import iped.parsers.skype.SkypeParser;
import iped.parsers.telegram.TelegramParser;
import iped.parsers.whatsapp.WhatsAppParser;
import iped.properties.MediaTypes;

/**
 * Device and account identifiers (issue #3). Unit tests use fictitious values; integration tests read every
 * expected value from the index of the configured test cases, so no case data is written here.
 */
public class DeviceAccountsServiceTest {

    static boolean isCaseAvailable() {
        return TestCaseResolver.isCaseAvailable();
    }

    static boolean isIosCaseAvailable() {
        return TestCaseResolver.isCaseAvailable() && TestCaseResolver.isLabeledCaseAvailable(TestCaseResolver.LABEL_IOS);
    }

    static boolean isFatCaseAvailable() {
        return TestCaseResolver.isCaseAvailable() && TestCaseResolver.isLabeledCaseAvailable(TestCaseResolver.LABEL_FAT);
    }

    @BeforeAll
    static void setUp() {
        if (isCaseAvailable()) {
            IpedCoreService.getInstance().openCase(TestCaseResolver.getPrimaryCaseDir());
        }
    }

    @AfterAll
    static void tearDown() {
        if (isCaseAvailable()) {
            IpedCoreService.getInstance().closeCase();
        }
    }

    // ---- unit ------------------------------------------------------------------------------------------------

    @Test
    void accountMediaTypesMatchIpedConstants() {
        assertEquals(SkypeParser.ACCOUNT_MIME_TYPE, DeviceAccountsService.SKYPE_ACCOUNT);
        assertEquals(WhatsAppParser.WHATSAPP_ACCOUNT.toString(), DeviceAccountsService.WHATSAPP_ACCOUNT);
        assertEquals(TelegramParser.TELEGRAM_ACCOUNT.toString(), DeviceAccountsService.TELEGRAM_ACCOUNT);
        assertEquals(DiscordParser.DISCORD_ACCOUNT.toString(), DeviceAccountsService.DISCORD_ACCOUNT);
        assertEquals(MediaTypes.UFED_USER_ACCOUNT_MIME.toString(), DeviceAccountsService.UFED_USER_ACCOUNT);
    }

    @Test
    void usernameInInternationalFormBecomesPhone() {
        List<DeviceAccountsService.Identifier> ids = DeviceAccountsService.classify("+55 11 90000-0000", DeviceAccountsService.UFED_USERNAME);
        assertEquals("phone", ids.getFirst().kind());
        assertEquals("5511900000000", ids.getFirst().key());
    }

    @Test
    void numericUsernameWithoutPlusStaysUsername() {
        // e.g. a messenger numeric user id, which is not a phone number
        assertEquals("username", DeviceAccountsService.classify("7000000001", DeviceAccountsService.UFED_USERNAME).getFirst().kind());
        assertEquals("phone", DeviceAccountsService.classify("5511900000000", DeviceAccountsService.UFED_PHONE).getFirst().kind());
    }

    @Test
    void messengerIdAlsoYieldsPhone() {
        List<DeviceAccountsService.Identifier> ids = DeviceAccountsService.classify("5511900000000@s.whatsapp.net", "userAccount");
        assertEquals(List.of("messenger_id", "phone"), ids.stream().map(DeviceAccountsService.Identifier::kind).toList());
        assertEquals("5511900000000", ids.get(1).key());
    }

    @Test
    void emailsInDifferentCaseAreMerged() {
        DeviceAccountsService.Aggregator agg = new DeviceAccountsService.Aggregator();
        agg.add("Usuario@Example.com", DeviceAccountsService.UFED_USERNAME, "Gmail", null, 1, DeviceAccountsService.KIND_UFED_ACCOUNT);
        agg.add("usuario@example.com", "emailAddress", "Chrome", null, 2, DeviceAccountsService.KIND_UFED_ACCOUNT);
        @SuppressWarnings("unchecked")
        Map<String, Object> email = (Map<String, Object>) agg.toResponse().get("email");
        assertEquals(1, email.get("total"));
        @SuppressWarnings("unchecked")
        Map<String, Object> first = ((List<Map<String, Object>>) email.get("items")).getFirst();
        assertEquals(2, first.get("app_count"));
        assertEquals(2, first.get("source_count"));
    }

    @Test
    void identifiersSupportedByMoreAppsComeFirst() {
        DeviceAccountsService.Aggregator agg = new DeviceAccountsService.Aggregator();
        agg.add("+5511900000001", DeviceAccountsService.UFED_PHONE, "Viber", null, 1, DeviceAccountsService.KIND_UFED_ACCOUNT);
        agg.add("+5511900000000", DeviceAccountsService.UFED_PHONE, "Viber", null, 2, DeviceAccountsService.KIND_UFED_ACCOUNT);
        agg.add("+5511900000000", DeviceAccountsService.UFED_PHONE, "Signal", null, 3, DeviceAccountsService.KIND_UFED_ACCOUNT);
        @SuppressWarnings("unchecked")
        Map<String, Object> phone = (Map<String, Object>) agg.toResponse().get("phone");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) phone.get("items");
        assertEquals("+5511900000000", items.getFirst().get("value"));
    }

    @Test
    void placeholdersAreDiscardedButIdentifiersAreKept() {
        assertTrue(DeviceAccountsService.isPlaceholder("Signal", "org.thoughtcrime.securesms", "org.thoughtcrime.securesms"));
        assertTrue(DeviceAccountsService.isPlaceholder("Signal", "Signal", null));
        assertTrue(DeviceAccountsService.isPlaceholder("vnd.sec.contact.phone", "unknown", "vnd.sec.contact.phone"));
        assertTrue(DeviceAccountsService.isPlaceholder("primary.sim.account_name", "unknown", "vnd.sec.contact.sim"));
        assertTrue(DeviceAccountsService.isPlaceholder("Rakuten Viber", "com.viber.voip", "com.viber.voip"));
        assertFalse(DeviceAccountsService.isPlaceholder("usuario@example.com", "com.google", "com.google"));
        assertFalse(DeviceAccountsService.isPlaceholder("usuario.exemplo", "Instagram", null));
        assertFalse(DeviceAccountsService.isPlaceholder("UsuarioExemplo123", "com.twitter.android.auth.login", "com.twitter.android.auth.login"));

        DeviceAccountsService.Aggregator agg = new DeviceAccountsService.Aggregator();
        agg.add("Signal", DeviceAccountsService.UFED_USERNAME, "org.thoughtcrime.securesms", "org.thoughtcrime.securesms", 1,
                DeviceAccountsService.KIND_UFED_ACCOUNT);
        assertEquals(1, agg.discarded());
        assertTrue(agg.isEmpty());
    }

    @Test
    void appComesFromAccountTypeThenUfedNameThenServiceType() {
        assertEquals("WhatsApp", DeviceAccountsService.appOf("WhatsApp", "x", "y"));
        assertEquals("Viber", DeviceAccountsService.appOf(null, "UserAccount-[Viber]-[Fulano | +5511900000000]", null));
        assertEquals("com.viber.voip", DeviceAccountsService.appOf(null, "UserAccount-[Accounts]-[0a1b2c3d-0000-0000-0000-000000000000]", "com.viber.voip"));
        assertEquals("com.viber.voip", DeviceAccountsService.appOf(null, "UserAccount-[0a1b2c3d-0000-0000-0000-000000000000]", "com.viber.voip"));
        assertEquals("unknown", DeviceAccountsService.appOf(null, "qualquer", null));
    }

    @Test
    void userProfileRuleSkipsSystemProfiles() {
        assertTrue(DeviceAccountsService.isUserProfileDir("fulano", "/img.E01/vol/Users/fulano"));
        assertTrue(DeviceAccountsService.isUserProfileDir("fulano", "img.E01\\home\\fulano"));
        assertFalse(DeviceAccountsService.isUserProfileDir("Public", "/img.E01/vol/Users/Public"));
        assertFalse(DeviceAccountsService.isUserProfileDir("Documents", "/img.E01/vol/Users/fulano/Documents"));
        assertFalse(DeviceAccountsService.isUserProfileDir("0", "/extracao/data/system/users/0"));
        assertFalse(DeviceAccountsService.isUserProfileDir("service", "/aparelho/extracao.zip/data/system/users/service"));
        assertFalse(DeviceAccountsService.isUserProfileDir("search", "/aparelho/extracao.zip/data/data/app/files/users/search"));
    }

    // ---- integration: Android case (primary) ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> identifiers(Map<String, Object> evidence, String kind) {
        Map<String, Object> ids = (Map<String, Object>) evidence.get("account_identifiers");
        Map<String, Object> block = (Map<String, Object>) ids.get(kind);
        return block == null ? List.of() : (List<Map<String, Object>>) block.get("items");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> evidences(Map<String, Object> res) {
        return (List<Map<String, Object>>) res.get("evidences");
    }

    private static List<Map<String, Object>> allPhones(Map<String, Object> res) {
        List<Map<String, Object>> phones = new ArrayList<>();
        for (Map<String, Object> e : evidences(res)) {
            phones.addAll(identifiers(e, "phone"));
        }
        return phones;
    }

    @SuppressWarnings("unchecked")
    private static Set<Object> sourceItemIds(Map<String, Object> res) {
        Set<Object> ids = new HashSet<>();
        for (Map<String, Object> e : evidences(res)) {
            for (String kind : List.of("phone", "email", "messenger_id", "username")) {
                for (Map<String, Object> idf : identifiers(e, kind)) {
                    for (Map<String, Object> s : (List<Map<String, Object>>) idf.get("sources")) {
                        ids.add(s.get("item_id"));
                    }
                }
            }
        }
        return ids;
    }

    /** Most frequent ufed:PhoneNumber among UFED account items, normalized; read from the index. */
    private static String mostFrequentUfedPhone() throws Exception {
        IPEDSource source = IpedCoreService.getInstance().getSource();
        IndexReader reader = source.getReader();
        Map<String, Integer> counts = new HashMap<>();
        for (int doc : DeviceAccountsService.docsByContentType(reader, Set.of(DeviceAccountsService.UFED_USER_ACCOUNT))
                .getOrDefault(DeviceAccountsService.UFED_USER_ACCOUNT, List.of())) {
            for (String v : reader.document(doc, Set.of(DeviceAccountsService.UFED_PHONE)).getValues(DeviceAccountsService.UFED_PHONE)) {
                counts.merge("+" + CommunicationAnalytics.normalizePhone(v, null), 1, Integer::sum);
            }
        }
        return counts.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void androidAccountsAreSourcesAndConfigFilesAreNot() throws Exception {
        Map<String, Object> res = DeviceAccountsService.getInstance().getDeviceAndAccounts();
        Set<Object> sources = sourceItemIds(res);

        IPEDSource source = IpedCoreService.getInstance().getSource();
        Set<String> configTypes = Set.of("application/x-whatsapp-user-xml", "application/x-gdrive-account-info");
        Map<String, List<Integer>> configDocs = DeviceAccountsService.docsByContentType(source.getReader(), configTypes);
        assertFalse(configDocs.isEmpty(), "caso de teste deveria ter arquivos de configuração na categoria de contas");
        Set<String> regexOnlyPhones = new HashSet<>();
        for (List<Integer> docs : configDocs.values()) {
            for (int doc : docs) {
                assertFalse(sources.contains(source.getId(doc)), "arquivo de configuração usado como fonte: item " + source.getId(doc));
                Document d = source.getReader().document(doc, Set.of("Regex:PHONE"));
                for (String v : d.getValues("Regex:PHONE")) {
                    regexOnlyPhones.add("+" + CommunicationAnalytics.normalizePhone(v, null));
                }
            }
        }

        Set<String> apps = new HashSet<>();
        for (Map<String, Object> e : evidences(res)) {
            for (Map<String, Object> idf : identifiers(e, "phone")) {
                @SuppressWarnings("unchecked")
                List<String> idfApps = (List<String>) idf.get("apps");
                idfApps.forEach(a -> apps.add(a.toLowerCase(Locale.ROOT)));
            }
        }
        for (String app : List.of("viber", "signal", "whatsapp", "telegram")) {
            assertTrue(apps.stream().anyMatch(a -> a.contains(app)), "conta " + app + " deveria ser fonte; apps: " + apps);
        }

        List<Map<String, Object>> phones = allPhones(res);
        Map<String, Object> first = phones.getFirst();
        assertEquals(mostFrequentUfedPhone(), first.get("value"), "o primeiro telefone deve ser o mais frequente nas contas");
        assertTrue((Integer) first.get("app_count") >= 5, "telefone principal deveria ter 5+ apps: " + first.get("app_count"));
        for (String regexPhone : regexOnlyPhones) {
            if (!regexPhone.equals(first.get("value"))) {
                assertTrue(phones.stream().noneMatch(p -> regexPhone.equals(p.get("value"))),
                        "número vindo só de Regex:PHONE apareceu entre os identificadores");
            }
        }
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void androidDeviceInformationIsReturnedAsRecorded() throws Exception {
        Map<String, Object> res = DeviceAccountsService.getInstance().getDeviceAndAccounts();
        Set<String> names = new HashSet<>();
        for (Map<String, Object> e : evidences(res)) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> info = (List<Map<String, Object>>) e.get("device_information");
            for (Map<String, Object> entry : info) {
                assertNotNull(entry.get("item_id"));
                names.add((String) entry.get("name"));
            }
        }
        assertTrue(names.containsAll(List.of("IMSI", "ICCID")), "Device Information sem IMSI/ICCID: " + names);
        assertEquals("mobile", res.get("evidence_type"));
        assertNotNull(res.get("note"));
        assertFalse(res.containsKey("no_structured_identifiers"));
    }

    @Test
    @EnabledIf("isCaseAvailable")
    void noOwnerFieldAnywhere() throws Exception {
        String json = String.valueOf(DeviceAccountsService.getInstance().getDeviceAndAccounts().keySet())
                + evidences(DeviceAccountsService.getInstance().getDeviceAndAccounts()).getFirst().keySet();
        assertFalse(json.toLowerCase(Locale.ROOT).contains("owner"), "resposta não pode ter chave com 'owner': " + json);
    }

    // ---- integration: labeled cases ----------------------------------------------------------------------------

    @Test
    @EnabledIf("isIosCaseAvailable")
    @SuppressWarnings("unchecked")
    void iosDevicePhoneAlsoHasAccountSources() throws Exception {
        try {
            IpedCoreService.getInstance().openCase(TestCaseResolver.getLabeledCaseDir(TestCaseResolver.LABEL_IOS));
            Map<String, Object> res = DeviceAccountsService.getInstance().getDeviceAndAccounts();
            Map<String, Object> devicePhone = allPhones(res).stream()
                    .filter(p -> ((List<Map<String, Object>>) p.get("sources")).stream()
                            .anyMatch(s -> DeviceAccountsService.KIND_DEVICE_INFO.equals(s.get("source_kind"))))
                    .findFirst().orElse(null);
            assertNotNull(devicePhone, "caso iOS deveria ter telefone vindo de Device Information");
            assertTrue(((List<Map<String, Object>>) devicePhone.get("sources")).stream()
                    .anyMatch(s -> !DeviceAccountsService.KIND_DEVICE_INFO.equals(s.get("source_kind"))),
                    "o telefone da Device Information deveria ter também fontes de conta");
        } finally {
            IpedCoreService.getInstance().openCase(TestCaseResolver.getPrimaryCaseDir());
        }
    }

    @Test
    @EnabledIf("isFatCaseAvailable")
    void fatCaseHasNoStructuredIdentifiers() throws Exception {
        try {
            IpedCoreService.getInstance().openCase(TestCaseResolver.getLabeledCaseDir(TestCaseResolver.LABEL_FAT));
            Map<String, Object> res = DeviceAccountsService.getInstance().getDeviceAndAccounts();
            assertEquals(true, res.get("no_structured_identifiers"));
            assertFalse(((List<?>) res.get("suggestions")).isEmpty());
            assertEquals("unknown", res.get("evidence_type"));
        } finally {
            IpedCoreService.getInstance().openCase(TestCaseResolver.getPrimaryCaseDir());
        }
    }
}
