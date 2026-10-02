package br.com.ipedtools.mcp.service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.SortedSetDocValues;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.BytesRef;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.ToTextContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;

import iped.data.IItem;
import iped.data.IItemId;
import iped.engine.config.Configuration;
import iped.engine.config.ConfigurationManager;
import iped.engine.data.IPEDMultiSource;
import iped.engine.data.IPEDSource;
import iped.engine.data.ItemId;
import iped.engine.lucene.DocValuesUtil;
import iped.engine.search.IPEDSearcher;
import iped.engine.search.ImageSimilarityScorer;
import iped.engine.search.MultiSearchResult;
import iped.engine.search.SimilarDocumentSearch;
import iped.engine.search.SimilarFacesSearch;
import iped.engine.search.SimilarImagesSearch;
import iped.engine.task.ParsingTask;
import iped.engine.task.similarity.ImageSimilarityTask;
import iped.parsers.standard.StandardParser;
import iped.search.SearchResult;
import iped.utils.ImageUtil;

/**
 * Core forensic service that interacts directly with the in-process IPED engine,
 * Lucene index, metadata extractors, and bookmark storage.
 * Eliminates any need for an external HTTP server or Python bridge.
 */
public class IpedCoreService {

    private static final Logger LOGGER = LoggerFactory.getLogger(IpedCoreService.class);
    private static IpedCoreService INSTANCE;

    private static final Set<String> WHITELIST_PREFIXES = Set.of(
            "communication:", "conversation:", "common:", "image:", "video:", "audio:",
            "ufed:", "p2p:", "hashdb:", "meta:", "message-", "face"
    );

    private static final Set<String> EXPLICIT_ALLOWED_KEYS = Set.of(
            "id", "name", "path", "category", "type", "ext", "size", "length",
            "created", "modified", "accessed", "changed", "deleted", "carved", "isdir",
            "hash", "md5", "sha-256", "sha-1", "contenttype",
            "url", "visitdate", "downloaddate", "totalbytes", "receivedbytes", "localpath",
            "from", "to", "cc", "bcc", "subject", "groupid", "isgroupmessage",
            "latitude", "longitude",
            "childpornhashhits", "sharedhashes", "shareditems", "linkeditems"
    );

    private static final Set<String> BLACKLISTED_KEYS = Set.of(
            "content", "treenode", "offset", "timeout", "thumbnail", "thumbnailbase64",
            "subitem", "subitemid", "mftsequence", "filesystemid", "metaaddress",
            "parenttrackid", "containertrackid", "itemvirtualidentifier", "parentvirtualidentifier",
            "trackid", "timeeventords", "timeeventgroups", "x-reader", "ipedembeddefolder"
    );

    private static final Map<String, List<Map<String, Object>>> FORENSIC_DOMAINS = initForensicDomains();

    private static final Pattern EMAIL_PATTERN = Pattern.compile("[\\w\\.-]+@[\\w\\.-]+\\.\\w+");
    private static final Pattern PHONE_PATTERN = Pattern.compile("\\+?\\d{10,13}");

    public static final File ACTIVE_CASE_FILE = new File(
            System.getProperty("user.home") + File.separator + ".iped-tools-mcp",
            "active_case.txt"
    );

    private IPEDSource ipedSource;
    private File caseDirectory;
    private String sourceId = "caso1";
    private long lastActiveCaseFileCheck = 0;
    private long lastActiveCaseFileModTime = 0;

    private IpedCoreService() {
    }

    public static synchronized IpedCoreService getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new IpedCoreService();
        }
        return INSTANCE;
    }

    /**
     * Checks if the active case was updated by another process (e.g. GUI) and reloads it dynamically.
     */
    public synchronized void syncActiveCaseIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastActiveCaseFileCheck < 500) {
            return;
        }
        lastActiveCaseFileCheck = now;

        try {
            if (!ACTIVE_CASE_FILE.exists()) {
                return;
            }
            long modTime = ACTIVE_CASE_FILE.lastModified();
            if (modTime == lastActiveCaseFileModTime) {
                return;
            }
            lastActiveCaseFileModTime = modTime;

            String path = java.nio.file.Files.readString(ACTIVE_CASE_FILE.toPath(), java.nio.charset.StandardCharsets.UTF_8).trim();
            if (!path.isBlank()) {
                File target = new File(path);
                if (target.exists() && IPEDSource.checkIfIsCaseFolder(target)) {
                    if (this.caseDirectory == null || !this.caseDirectory.getCanonicalPath().equalsIgnoreCase(target.getCanonicalPath())) {
                        LOGGER.info("Sincronizando caso ativo detectado via GUI/Arquivo de Estado: {}", target.getAbsolutePath());
                        openCaseInternal(target, false);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Erro ao verificar arquivo active_case.txt: {}", e.getMessage());
        }
    }

    /**
     * Opens and validates an IPED case folder.
     */
    public synchronized void openCase(File caseDir) throws Exception {
        openCaseInternal(caseDir, true);
    }

    private synchronized void openCaseInternal(File caseDir, boolean persistState) throws Exception {
        if (caseDir == null || !caseDir.exists()) {
            throw new IllegalArgumentException("O diretório do caso não existe: " + caseDir);
        }

        if (!IPEDSource.checkIfIsCaseFolder(caseDir)) {
            throw new IllegalArgumentException(
                    "O diretório selecionado não é uma pasta de caso válida do IPED (subpastas 'iped/index', 'iped/data' ou 'iped/lib' não encontradas): "
                            + caseDir.getAbsolutePath());
        }

        closeCase();

        this.caseDirectory = caseDir;
        this.sourceId = caseDir.getName();

        LOGGER.info("Carregando configurações do caso: {}", caseDir.getAbsolutePath());
        try {
            Configuration.getInstance().loadConfigurables(caseDir.getAbsolutePath() + File.separator + "iped", true);
        } catch (Throwable t) {
            LOGGER.warn("Aviso ao carregar configuráveis do IPED (ignorado em JDKs modernos sem SecurityManager): {}", t.getMessage());
        }

        LOGGER.info("Inicializando IPEDSource e abrindo leitores Lucene...");
        this.ipedSource = new IPEDSource(caseDir);

        LOGGER.info("Caso carregado com sucesso. Total de documentos no leitor: {}",
                this.ipedSource.getReader().numDocs());

        if (persistState) {
            try {
                if (!ACTIVE_CASE_FILE.getParentFile().exists()) {
                    ACTIVE_CASE_FILE.getParentFile().mkdirs();
                }
                java.nio.file.Files.writeString(ACTIVE_CASE_FILE.toPath(), caseDir.getAbsolutePath(), java.nio.charset.StandardCharsets.UTF_8);
                lastActiveCaseFileModTime = ACTIVE_CASE_FILE.lastModified();
            } catch (Exception ex) {
                LOGGER.debug("Falha ao salvar active_case.txt: {}", ex.getMessage());
            }
        }
    }

    /**
     * Closes the active case and releases Lucene readers and thread pools.
     */
    public synchronized void closeCase() {
        if (this.ipedSource != null) {
            try {
                LOGGER.info("Fechando caso IPED atual...");
                this.ipedSource.close();
            } catch (Exception e) {
                LOGGER.warn("Erro ao fechar leitores do caso: {}", e.getMessage());
            } finally {
                this.ipedSource = null;
                this.caseDirectory = null;
            }
        }
    }

    public synchronized boolean isCaseOpen() {
        syncActiveCaseIfNeeded();
        return this.ipedSource != null && this.ipedSource.getReader() != null;
    }

    public synchronized File getCaseDirectory() {
        syncActiveCaseIfNeeded();
        return this.caseDirectory;
    }

    public synchronized String getSourceId() {
        syncActiveCaseIfNeeded();
        return this.sourceId;
    }

    public synchronized IPEDSource getSource() {
        checkCaseOpen();
        return this.ipedSource;
    }

    /**
     * Lists active forensic evidence sources/containers. If the case contains multiple
     * root evidence images (isRoot:true, e.g. Mantooth.E01, E01Capture.E01), lists each container.
     */
    public synchronized List<Map<String, Object>> listSources() {
        checkCaseOpen();
        List<Map<String, Object>> sourcesList = new ArrayList<>();
        try {
            IPEDSearcher rootSearcher = new IPEDSearcher(ipedSource, "isRoot:true");
            rootSearcher.setTreeQuery(true);
            int[] rootIds = rootSearcher.search().getIds();
            if (rootIds != null && rootIds.length > 0) {
                for (int rId : rootIds) {
                    int luceneId = ipedSource.getLuceneId(rId);
                    if (luceneId >= 0) {
                        Document doc = ipedSource.getReader().document(luceneId);
                        String rName = doc.get("name");
                        if (rName != null && !rName.isBlank()) {
                            Map<String, Object> item = new LinkedHashMap<>();
                            item.put("id", getSourceId());
                            item.put("name", rName);
                            item.put("container", rName);
                            item.put("path", doc.get("path") != null ? doc.get("path") : "/" + rName);
                            sourcesList.add(item);
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Erro ao listar raízes de evidência em listSources: {}", e.getMessage());
        }

        if (sourcesList.isEmpty()) {
            sourcesList.add(Map.of(
                    "id", getSourceId(),
                    "name", getSourceId(),
                    "path", getCaseDirectory().getAbsolutePath()
            ));
        }
        return sourcesList;
    }

    private Map<String, Object> resolveContainerForDoc(Document doc, Map<String, Map<String, Object>> evidenceContainers) {
        if (evidenceContainers == null || evidenceContainers.isEmpty() || doc == null) {
            return null;
        }
        String path = doc.get("path");
        if (path != null) {
            String normPath = "/" + path.replace('\\', '/').replaceAll("^/+", "").toLowerCase();
            for (Map.Entry<String, Map<String, Object>> entry : evidenceContainers.entrySet()) {
                String cName = entry.getKey();
                if (normPath.startsWith("/" + cName + "/") || normPath.equals("/" + cName)) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    // =========================================================================
    // SEARCH OPERATIONS
    // =========================================================================

    /**
     * Executes a Lucene query against the open IPED case index.
     */
    public Map<String, Object> executeSearch(String queryText, int limit) throws IOException {
        checkCaseOpen();

        int maxLimit = Math.max(1, Math.min(limit, 100));
        String sanitizedQuery = (queryText == null || queryText.isBlank()) ? "*:*" : queryText.replaceAll("/", "\\\\/");

        IPEDSearcher searcher = new IPEDSearcher(ipedSource, sanitizedQuery);
        SearchResult result = searcher.search();

        int[] allIds = result.getIds();
        int totalFound = (allIds != null) ? allIds.length : 0;

        List<Map<String, Object>> items = new ArrayList<>();
        if (allIds != null && totalFound > 0) {
            int returnCount = Math.min(totalFound, maxLimit);
            for (int i = 0; i < returnCount; i++) {
                items.add(Map.of("source", sourceId, "id", allIds[i]));
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("query", queryText);
        response.put("source_id", sourceId);
        response.put("total_found", totalFound);
        response.put("returned_count", items.size());
        response.put("limit", maxLimit);
        response.put("items", items);
        response.put("note", totalFound > items.size()
                ? "Exibindo os primeiros " + items.size() + " de " + totalFound + " itens encontrados."
                : "Todos os itens encontrados foram exibidos.");

        return response;
    }

    /**
     * Returns the total count of indexed items.
     */
    public int getTotalIndexedItems() {
        if (!isCaseOpen()) {
            return 0;
        }
        try {
            IPEDSearcher searcher = new IPEDSearcher(ipedSource, "*:*");
            SearchResult res = searcher.search();
            return res.getLength();
        } catch (Exception e) {
            return ipedSource.getReader().numDocs();
        }
    }

    // =========================================================================
    // METADATA OPERATIONS
    // =========================================================================

    /**
     * Retrieves token-efficient sanitized metadata for a single item ID.
     */
    public Map<String, Object> getDocumentMetadata(int itemId) throws IOException {
        checkCaseOpen();

        int luceneId = ipedSource.getLuceneId(itemId);
        if (luceneId < 0) {
            return Map.of("id", itemId, "source", sourceId, "error", "Documento não encontrado no índice.");
        }

        Document doc = ipedSource.getReader().document(luceneId);
        Map<String, Object> rawProps = new LinkedHashMap<>();

        for (IndexableField field : doc.getFields()) {
            String name = field.name();
            if (rawProps.containsKey(name)) {
                continue;
            }
            String[] vals = doc.getValues(name);
            if (vals != null && vals.length > 0) {
                if (vals.length == 1) {
                    rawProps.put(name, vals[0]);
                } else {
                    rawProps.put(name, Arrays.asList(vals));
                }
            } else if (field.stringValue() != null) {
                rawProps.put(name, field.stringValue());
            } else if (field.numericValue() != null) {
                rawProps.put(name, field.numericValue());
            }
        }

        Map<String, Object> cleanProps = sanitizeProperties(rawProps);

        List<String> bookmarks = ipedSource.getBookmarks().getBookmarkList(itemId);
        boolean selected = ipedSource.getBookmarks().isChecked(itemId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", itemId);
        result.put("source", sourceId);
        result.put("selected", selected);
        result.put("bookmarks", bookmarks != null ? bookmarks : List.of());
        result.put("properties", cleanProps);

        return result;
    }

    /**
     * Retrieves sanitized metadata in batch for a list of document IDs.
     */
    public List<Map<String, Object>> getDocumentMetadataBatch(List<Integer> itemIds) {
        checkCaseOpen();
        List<Map<String, Object>> list = new ArrayList<>();
        if (itemIds == null || itemIds.isEmpty()) {
            return list;
        }

        for (Integer id : itemIds) {
            try {
                list.add(getDocumentMetadata(id));
            } catch (Exception e) {
                list.add(Map.of("id", id, "source", sourceId, "error", "Falha ao ler metadados: " + e.getMessage()));
            }
        }
        return list;
    }

    // =========================================================================
    // TEXT EXTRACTION
    // =========================================================================

    /**
     * Extracts text content using IPED's StandardParser and Tika with pagination chunking.
     */
    public String getDocumentText(int itemId, int offset, int maxChars) {
        checkCaseOpen();

        int safeOffset = Math.max(0, offset);
        int safeMaxChars = Math.max(100, Math.min(maxChars, 6000));

        try {
            IItem item = ipedSource.getItemByID(itemId);
            if (item == null) {
                return "[Item não encontrado com o ID " + itemId + "]";
            }

            StandardParser parser = new StandardParser();
            ParsingTask expander = new ParsingTask(item, parser);
            expander.init(ConfigurationManager.get());
            ParseContext context = expander.getTikaContext(ipedSource);
            expander.setExtractEmbedded(false);

            Metadata metadata = new Metadata();
            ParsingTask.fillMetadata(item, metadata);
            parser.setPrintMetadata(false);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ContentHandler handler = new ToTextContentHandler(baos, "UTF-8");

            try (TikaInputStream is = item.getTikaStream()) {
                if (is != null) {
                    parser.parse(is, handler, metadata, context);
                }
            } catch (Exception e) {
                LOGGER.debug("Erro na extração Tika para item {}: {}", itemId, e.getMessage());
            }

            String fullText = baos.toString(StandardCharsets.UTF_8).trim();
            if (fullText.isEmpty()) {
                return "[Nenhum conteúdo textual extraído para este item]";
            }

            int totalLen = fullText.length();
            if (safeOffset >= totalLen) {
                return "[Fim do texto atingido. Offset " + safeOffset + " maior que o tamanho total de " + totalLen + " caracteres]";
            }

            int end = Math.min(safeOffset + safeMaxChars, totalLen);
            String chunk = fullText.substring(safeOffset, end);

            if (end < totalLen) {
                return chunk + "\n\n[... Truncado: exibindo caracteres " + safeOffset + " a " + end + " de " + totalLen
                        + " no total. Chame get_document_text com offset=" + end + " para ler o próximo bloco ...]";
            }

            return chunk;

        } catch (Exception e) {
            LOGGER.error("Falha ao extrair texto do documento ID {}: {}", itemId, e.getMessage());
            return "[Erro ao extrair texto: " + e.getMessage() + "]";
        }
    }

    // =========================================================================
    // CATEGORIES & BOOKMARKS
    // =========================================================================

    public List<String> listCategories() {
        checkCaseOpen();
        List<String> categories = ipedSource.getLeafCategories();
        return categories != null ? categories : List.of();
    }

    public List<String> listBookmarks() {
        checkCaseOpen();
        Map<Integer, String> map = ipedSource.getBookmarks().getBookmarkMap();
        if (map == null || map.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(map.values());
    }

    public synchronized String addBookmark(String bookmarkName, List<Integer> docIds) {
        checkCaseOpen();
        if (bookmarkName == null || bookmarkName.isBlank()) {
            return "Erro: O nome do marcador não pode ser vazio.";
        }
        if (docIds == null || docIds.isEmpty()) {
            return "Erro: Nenhum ID de documento fornecido para adicionar ao marcador.";
        }

        try {
            int bmId = ipedSource.getBookmarks().newBookmark(bookmarkName);
            ipedSource.getBookmarks().addBookmark(docIds, bmId);
            ipedSource.getBookmarks().saveState(true);
            return "Sucesso: " + docIds.size() + " documento(s) adicionado(s) ao marcador '" + bookmarkName + "'.";
        } catch (Exception e) {
            LOGGER.error("Erro ao adicionar aos marcadores: {}", e.getMessage(), e);
            return "Falha ao adicionar aos marcadores: " + e.getMessage();
        }
    }

    /**
     * Flags or unflags an item as checked (selected) in the IPED case state database,
     * mirroring the checkmark column in IPED Desktop.
     * Persists the updated state immediately to disk.
     */
    public synchronized Map<String, Object> setItemChecked(int itemId, boolean checked) throws IOException {
        checkCaseOpen();
        int luceneId = ipedSource.getLuceneId(itemId);
        if (luceneId < 0) {
            throw new IllegalArgumentException("Item ID não encontrado no caso aberto: " + itemId);
        }

        boolean previousState = ipedSource.getBookmarks().isChecked(itemId);
        ipedSource.getBookmarks().setChecked(checked, itemId);
        try {
            ipedSource.getBookmarks().saveState(true);
        } catch (Exception e) {
            LOGGER.warn("Aviso ao persistir estado de seleção do item {}: {}", itemId, e.getMessage());
        }

        Document doc = ipedSource.getReader().document(luceneId);
        String name = doc.get("name") != null ? doc.get("name") : "";
        String path = doc.get("path") != null ? doc.get("path") : "";
        String category = doc.get("category") != null ? doc.get("category") : "";

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", itemId);
        response.put("source", sourceId);
        response.put("name", name);
        response.put("path", path);
        response.put("category", category);
        response.put("checked", checked);
        response.put("previous_state", previousState);
        response.put("success", true);
        response.put("message", "Item " + itemId + " (" + name + ") marcado com sucesso como "
                + (checked ? "selecionado (checked)" : "não selecionado (unchecked)") + ".");

        return response;
    }

    // =========================================================================
    // SPECIALIZED DFIR ANALYSIS
    // =========================================================================

    /**
     * Special forensic tool that identifies device ownership, user accounts,
     * phone numbers, and hardware properties.
     */
    public Map<String, Object> getDeviceAndOwnerInfo() {
        checkCaseOpen();
        try {
            Map<String, String> deviceProperties = new LinkedHashMap<>();
            Map<String, String> systemInfo = new LinkedHashMap<>();
            Set<String> likelyOwnerNames = new LinkedHashSet<>();
            Set<String> ownerPhoneNumbers = new LinkedHashSet<>();
            Set<String> ownerEmails = new LinkedHashSet<>();
            List<Map<String, Object>> primaryAccounts = new ArrayList<>();
            List<Map<String, Object>> userAccounts = new ArrayList<>();
            Set<String> userProfileDirs = new LinkedHashSet<>();

            final Set<String> SYSTEM_ACCOUNTS = Set.of(
                    "defaultaccount", "guest", "wdagutilityaccount", "systemprofile",
                    "localservice", "networkservice", "nobody", "daemon"
            );

            final Set<String> IGNORED_PROFILE_DIRS = Set.of(
                    "all users", "default", "default user", "public", "público",
                    "todos os usuários", "defaultapppool", "localappdata",
                    "application data", "appdata"
            );

            // 0. Discover evidence containers (root items in the evidence tree, e.g. Mantooth.E01, E01Capture.E01)
            Map<String, Map<String, Object>> evidenceContainers = new LinkedHashMap<>();
            try {
                IPEDSearcher rootSearcher = new IPEDSearcher(ipedSource, "isRoot:true");
                rootSearcher.setTreeQuery(true);
                int[] rootIds = rootSearcher.search().getIds();
                if (rootIds != null && rootIds.length > 0) {
                    for (int rId : rootIds) {
                        int luceneId = ipedSource.getLuceneId(rId);
                        if (luceneId >= 0) {
                            Document doc = ipedSource.getReader().document(luceneId);
                            String rName = doc.get("name");
                            if (rName != null && !rName.isBlank()) {
                                Map<String, Object> container = new LinkedHashMap<>();
                                container.put("id", rId);
                                container.put("name", rName);
                                container.put("path", doc.get("path") != null ? doc.get("path") : "/" + rName);
                                container.put("system_info", new LinkedHashMap<String, String>());
                                container.put("device_properties", new LinkedHashMap<String, String>());
                                container.put("likely_owners", new LinkedHashSet<String>());
                                container.put("owner_phone_numbers", new LinkedHashSet<String>());
                                container.put("owner_emails", new LinkedHashSet<String>());
                                container.put("user_accounts", new ArrayList<Map<String, Object>>());
                                container.put("primary_accounts", new ArrayList<Map<String, Object>>());
                                evidenceContainers.put(rName.toLowerCase(), container);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.debug("Erro ao identificar raízes de evidência: {}", e.getMessage());
            }

            // 1. Mobile Device Information (UFED / Cellebrite)
            SearchResult devRes = new IPEDSearcher(ipedSource, "category:\"device information\"").search();
            int[] devIds = devRes.getIds();
            if (devIds != null && devIds.length > 0) {
                int count = Math.min(devIds.length, 40);
                for (int i = 0; i < count; i++) {
                    int luceneId = ipedSource.getLuceneId(devIds[i]);
                    Document doc = ipedSource.getReader().document(luceneId);
                    String entryName = doc.get("ufed:EntryName");
                    String entryValue = doc.get("ufed:EntryValue");
                    if (entryName != null && entryValue != null && !entryValue.isBlank()) {
                        deviceProperties.put(entryName, entryValue);
                        Map<String, Object> matchedContainer = resolveContainerForDoc(doc, evidenceContainers);
                        if (matchedContainer != null) {
                            @SuppressWarnings("unchecked")
                            Map<String, String> cDevProps = (Map<String, String>) matchedContainer.get("device_properties");
                            cDevProps.put(entryName, entryValue);
                        }
                    }
                }
            }

            // 2. Computer & OS Identification (Windows / Linux / macOS)
            SearchResult osRes = new IPEDSearcher(ipedSource,
                    "registeredOwner:* OR computerName:* OR hostName:* OR productName:* OR (name:SOFTWARE AND path:*system32*)").search();
            int[] osIds = osRes.getIds();
            if (osIds != null && osIds.length > 0) {
                int count = Math.min(osIds.length, 20);
                for (int i = 0; i < count; i++) {
                    int luceneId = ipedSource.getLuceneId(osIds[i]);
                    Document doc = ipedSource.getReader().document(luceneId);
                    Map<String, Object> matchedContainer = resolveContainerForDoc(doc, evidenceContainers);
                    @SuppressWarnings("unchecked")
                    Map<String, String> cSysInfo = matchedContainer != null ? (Map<String, String>) matchedContainer.get("system_info") : null;
                    @SuppressWarnings("unchecked")
                    Set<String> cLikelyOwners = matchedContainer != null ? (Set<String>) matchedContainer.get("likely_owners") : null;

                    String compName = doc.get("computerName");
                    if (compName == null || compName.isBlank()) compName = doc.get("hostName");
                    if (compName == null || compName.isBlank()) compName = doc.get("system:computer_name");
                    if (compName != null && !compName.isBlank() && !systemInfo.containsKey("computer_name")) {
                        systemInfo.put("computer_name", compName.trim());
                    }
                    if (compName != null && !compName.isBlank() && cSysInfo != null && !cSysInfo.containsKey("computer_name")) {
                        cSysInfo.put("computer_name", compName.trim());
                    }

                    String regOwner = doc.get("registeredOwner");
                    if (regOwner != null && !regOwner.isBlank() && !systemInfo.containsKey("registered_owner")) {
                        systemInfo.put("registered_owner", regOwner.trim());
                        if (!regOwner.equalsIgnoreCase("Microsoft") && !regOwner.equalsIgnoreCase("Windows User")) {
                            likelyOwnerNames.add(regOwner.trim());
                        }
                    }
                    if (regOwner != null && !regOwner.isBlank() && cSysInfo != null && !cSysInfo.containsKey("registered_owner")) {
                        cSysInfo.put("registered_owner", regOwner.trim());
                        if (!regOwner.equalsIgnoreCase("Microsoft") && !regOwner.equalsIgnoreCase("Windows User") && cLikelyOwners != null) {
                            cLikelyOwners.add(regOwner.trim());
                        }
                    }

                    String regOrg = doc.get("registeredOrganization");
                    if (regOrg != null && !regOrg.isBlank() && !systemInfo.containsKey("registered_organization")) {
                        systemInfo.put("registered_organization", regOrg.trim());
                    }
                    if (regOrg != null && !regOrg.isBlank() && cSysInfo != null && !cSysInfo.containsKey("registered_organization")) {
                        cSysInfo.put("registered_organization", regOrg.trim());
                    }

                    String prodName = doc.get("productName");
                    if (prodName == null || prodName.isBlank()) prodName = doc.get("operatingSystem");
                    if (prodName != null && !prodName.isBlank() && !systemInfo.containsKey("operating_system")) {
                        systemInfo.put("operating_system", prodName.trim());
                    }
                    if (prodName != null && !prodName.isBlank() && cSysInfo != null && !cSysInfo.containsKey("operating_system")) {
                        cSysInfo.put("operating_system", prodName.trim());
                    }

                    String installDate = doc.get("installDate");
                    if (installDate == null || installDate.isBlank()) installDate = doc.get("installationDate");
                    if (installDate != null && !installDate.isBlank() && !systemInfo.containsKey("install_date")) {
                        systemInfo.put("install_date", installDate.trim());
                    }
                    if (installDate != null && !installDate.isBlank() && cSysInfo != null && !cSysInfo.containsKey("install_date")) {
                        cSysInfo.put("install_date", installDate.trim());
                    }
                }
            }

            // 3. User Profile Directories (e.g. Users/john or home/john)
            SearchResult profRes = new IPEDSearcher(ipedSource, "isDir:true AND (path:*Users* OR path:*home*)").search();
            int[] profIds = profRes.getIds();
            if (profIds != null && profIds.length > 0) {
                int count = Math.min(profIds.length, 60);
                for (int i = 0; i < count; i++) {
                    int luceneId = ipedSource.getLuceneId(profIds[i]);
                    Document doc = ipedSource.getReader().document(luceneId);
                    String dirName = doc.get("name");
                    String dirPath = doc.get("path");
                    if (dirName != null && !dirName.isBlank() && dirPath != null) {
                        String normPath = dirPath.replace('\\', '/').toLowerCase();
                        if ((normPath.endsWith("/users/" + dirName.toLowerCase()) || normPath.endsWith("/home/" + dirName.toLowerCase()))
                                && !IGNORED_PROFILE_DIRS.contains(dirName.toLowerCase())) {
                            userProfileDirs.add(dirName);
                            likelyOwnerNames.add(dirName);
                            Map<String, Object> matchedContainer = resolveContainerForDoc(doc, evidenceContainers);
                            if (matchedContainer != null) {
                                @SuppressWarnings("unchecked")
                                Set<String> cLikelyOwners = (Set<String>) matchedContainer.get("likely_owners");
                                cLikelyOwners.add(dirName);
                            }
                        }
                    }
                }
            }

            // 4. User Accounts & Messenger Configurations (SAM, passwd, Android/iOS preferences)
            SearchResult accRes = new IPEDSearcher(ipedSource,
                    "category:\"user accounts\" OR (name:*preferences.xml AND (name:*whatsapp* OR name:*telegram* OR name:*signal*))").search();
            int[] accIds = accRes.getIds();
            if (accIds != null && accIds.length > 0) {
                int count = Math.min(accIds.length, 60);
                for (int i = 0; i < count; i++) {
                    int docId = accIds[i];
                    int luceneId = ipedSource.getLuceneId(docId);
                    Document doc = ipedSource.getReader().document(luceneId);
                    Map<String, Object> matchedContainer = resolveContainerForDoc(doc, evidenceContainers);

                    String name = doc.get("name") != null ? doc.get("name") : "";
                    String user = doc.get("userName") != null ? doc.get("userName") : "";
                    String acctType = doc.get("accountType") != null ? doc.get("accountType") : "";

                    // Extract phone number from standard fields or IPED Regex fields
                    String phone = doc.get("phoneNumber");
                    if (phone == null || phone.isBlank()) phone = doc.get("Regex:PHONE");
                    if (phone == null || phone.isBlank()) phone = doc.get("phone");
                    if (phone == null || phone.isBlank()) phone = doc.get("cellPhone");
                    if (phone == null || phone.isBlank()) phone = doc.get("telephone");
                    if (phone == null || phone.isBlank()) phone = doc.get("contact:phone");
                    if (phone == null) phone = "";
                    phone = phone.trim();

                    // Check all document fields for any Regex:PHONE match if phone is still empty
                    if (phone.isBlank()) {
                        for (IndexableField f : doc.getFields()) {
                            String fName = f.name();
                            if (fName.equalsIgnoreCase("Regex:PHONE") || fName.toLowerCase().endsWith(":phone")) {
                                String val = f.stringValue();
                                if (val != null && !val.isBlank()) {
                                    phone = val.trim();
                                    break;
                                }
                            }
                        }
                    }

                    // Extract email from standard fields or IPED Regex fields
                    String email = doc.get("email");
                    if (email == null || email.isBlank()) email = doc.get("Regex:EMAIL");
                    if (email != null && !email.isBlank()) {
                        String em = email.trim();
                        ownerEmails.add(em);
                        if (matchedContainer != null) {
                            @SuppressWarnings("unchecked")
                            Set<String> cEmails = (Set<String>) matchedContainer.get("owner_emails");
                            cEmails.add(em);
                        }
                    }

                    String effectiveUser = user;
                    if (effectiveUser.isBlank() && name.startsWith("UserAccount-")) {
                        int bracketIdx = name.indexOf('[');
                        if (bracketIdx > 0) {
                            effectiveUser = name.substring("UserAccount-".length(), bracketIdx).trim();
                        } else {
                            effectiveUser = name.substring("UserAccount-".length()).trim();
                        }
                    }

                    if (!effectiveUser.isBlank() && !SYSTEM_ACCOUNTS.contains(effectiveUser.toLowerCase())) {
                        likelyOwnerNames.add(effectiveUser);
                        if (matchedContainer != null) {
                            @SuppressWarnings("unchecked")
                            Set<String> cLikelyOwners = (Set<String>) matchedContainer.get("likely_owners");
                            cLikelyOwners.add(effectiveUser);
                        }
                    }
                    if (!phone.isBlank()) {
                        ownerPhoneNumbers.add(phone);
                        if (matchedContainer != null) {
                            @SuppressWarnings("unchecked")
                            Set<String> cPhones = (Set<String>) matchedContainer.get("owner_phone_numbers");
                            cPhones.add(phone);
                        }
                    }

                    Matcher emailMatcher = EMAIL_PATTERN.matcher(name);
                    while (emailMatcher.find()) {
                        String em = emailMatcher.group();
                        ownerEmails.add(em);
                        if (matchedContainer != null) {
                            @SuppressWarnings("unchecked")
                            Set<String> cEmails = (Set<String>) matchedContainer.get("owner_emails");
                            cEmails.add(em);
                        }
                    }

                    Matcher phoneMatcher = PHONE_PATTERN.matcher(name);
                    while (phoneMatcher.find()) {
                        String ph = phoneMatcher.group();
                        ownerPhoneNumbers.add(ph);
                        if (matchedContainer != null) {
                            @SuppressWarnings("unchecked")
                            Set<String> cPhones = (Set<String>) matchedContainer.get("owner_phone_numbers");
                            cPhones.add(ph);
                        }
                    }

                    String lowerName = name.toLowerCase();
                    String detectedApp = acctType != null ? acctType.trim() : "";
                    if (detectedApp.isBlank()) {
                        if (lowerName.contains("whatsapp")) {
                            detectedApp = "WhatsApp";
                        } else if (lowerName.contains("telegram")) {
                            detectedApp = "Telegram";
                        } else if (lowerName.contains("signal")) {
                            detectedApp = "Signal";
                        } else if (name.contains("[SAM]")) {
                            detectedApp = "Windows SAM";
                        } else if (name.contains("-")) {
                            String[] parts = name.split("-");
                            detectedApp = parts.length > 1 ? parts[1].replace("[", "").replace("]", "").trim() : "App";
                        } else {
                            detectedApp = "General";
                        }
                    }

                    Map<String, Object> acctItem = new LinkedHashMap<>();
                    acctItem.put("id", docId);
                    acctItem.put("app", detectedApp);
                    acctItem.put("name", name);
                    acctItem.put("user", effectiveUser.isBlank() ? user : effectiveUser);
                    if (!phone.isBlank()) {
                        acctItem.put("phone", phone);
                    }
                    userAccounts.add(acctItem);
                    if (matchedContainer != null) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> cUserAccts = (List<Map<String, Object>>) matchedContainer.get("user_accounts");
                        cUserAccts.add(acctItem);
                    }

                    boolean isPrimary = name.contains("UserAccount-") || name.contains("Account:")
                            || lowerName.contains("whatsapp") || lowerName.contains("telegram") || lowerName.contains("signal")
                            || lowerName.endsWith("preferences.xml") || !phone.isBlank();
                    if (isPrimary) {
                        primaryAccounts.add(acctItem);
                        if (matchedContainer != null) {
                            @SuppressWarnings("unchecked")
                            List<Map<String, Object>> cPrimAccts = (List<Map<String, Object>>) matchedContainer.get("primary_accounts");
                            cPrimAccts.add(acctItem);
                        }
                    }
                }
            }

            // 5. Evidence Container Classification & Packaging
            List<Map<String, Object>> evidencesList = new ArrayList<>();
            for (Map<String, Object> c : evidenceContainers.values()) {
                @SuppressWarnings("unchecked")
                Map<String, String> cDevProps = (Map<String, String>) c.get("device_properties");
                @SuppressWarnings("unchecked")
                Map<String, String> cSysInfo = (Map<String, String>) c.get("system_info");
                @SuppressWarnings("unchecked")
                Set<String> cPhones = (Set<String>) c.get("owner_phone_numbers");
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> cPrimAccts = (List<Map<String, Object>>) c.get("primary_accounts");
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> cUserAccts = (List<Map<String, Object>>) c.get("user_accounts");

                boolean cMobile = !cDevProps.isEmpty() || !cPhones.isEmpty() || cPrimAccts.stream().anyMatch(a -> {
                    String app = String.valueOf(a.get("app")).toLowerCase();
                    return app.contains("whatsapp") || app.contains("telegram") || app.contains("signal") || app.contains("ufed");
                });
                boolean cComputer = !cSysInfo.isEmpty() || cUserAccts.stream().anyMatch(a -> {
                    String app = String.valueOf(a.get("app")).toLowerCase();
                    String aName = String.valueOf(a.get("name")).toLowerCase();
                    return app.contains("sam") || aName.contains("[sam]") || aName.contains("windows");
                });

                String cType;
                if (cMobile && cComputer) cType = "hybrid";
                else if (cMobile) cType = "mobile";
                else if (cComputer) cType = "computer";
                else cType = "generic";

                Map<String, Object> packaged = new LinkedHashMap<>();
                packaged.put("id", c.get("id"));
                packaged.put("name", c.get("name"));
                packaged.put("path", c.get("path"));
                packaged.put("type", cType);
                packaged.put("system_info", cSysInfo);
                packaged.put("device_properties", cDevProps);
                @SuppressWarnings("unchecked")
                Set<String> cLikelyOwners = (Set<String>) c.get("likely_owners");
                packaged.put("likely_owners", new ArrayList<>(cLikelyOwners));
                packaged.put("owner_phone_numbers", new ArrayList<>(cPhones));
                @SuppressWarnings("unchecked")
                Set<String> cEmails = (Set<String>) c.get("owner_emails");
                packaged.put("owner_emails", new ArrayList<>(cEmails));
                packaged.put("total_accounts_found", cUserAccts.size());
                packaged.put("user_accounts", cUserAccts.stream().limit(20).collect(Collectors.toList()));
                packaged.put("primary_accounts", cPrimAccts.stream().limit(12).collect(Collectors.toList()));
                evidencesList.add(packaged);
            }

            // 6. Overall Evidence Type Classification
            boolean hasMobile = !deviceProperties.isEmpty() || !ownerPhoneNumbers.isEmpty() || primaryAccounts.stream().anyMatch(a -> {
                String app = String.valueOf(a.get("app")).toLowerCase();
                return app.contains("whatsapp") || app.contains("telegram") || app.contains("signal") || app.contains("ufed");
            });
            boolean hasComputer = !systemInfo.isEmpty() || !userProfileDirs.isEmpty() || userAccounts.stream().anyMatch(a -> {
                String app = String.valueOf(a.get("app")).toLowerCase();
                String name = String.valueOf(a.get("name")).toLowerCase();
                return app.contains("sam") || name.contains("[sam]") || name.contains("windows");
            });

            String evidenceType;
            if ((hasMobile && hasComputer) || evidencesList.size() > 1) {
                boolean anyMobile = evidencesList.stream().anyMatch(e -> "mobile".equals(e.get("type")) || "hybrid".equals(e.get("type")));
                boolean anyComputer = evidencesList.stream().anyMatch(e -> "computer".equals(e.get("type")) || "hybrid".equals(e.get("type")));
                if (anyMobile && anyComputer) {
                    evidenceType = "hybrid";
                } else if (hasMobile && hasComputer) {
                    evidenceType = "hybrid";
                } else if (hasMobile) {
                    evidenceType = "mobile";
                } else if (hasComputer) {
                    evidenceType = "computer";
                } else {
                    evidenceType = "generic";
                }
            } else if (hasMobile) {
                evidenceType = "mobile";
            } else if (hasComputer) {
                evidenceType = "computer";
            } else {
                evidenceType = "generic";
            }

            if (evidencesList.isEmpty()) {
                Map<String, Object> defaultContainer = new LinkedHashMap<>();
                defaultContainer.put("id", 0);
                defaultContainer.put("name", sourceId);
                defaultContainer.put("path", caseDirectory != null ? caseDirectory.getAbsolutePath() : "");
                defaultContainer.put("type", evidenceType);
                defaultContainer.put("system_info", systemInfo);
                defaultContainer.put("device_properties", deviceProperties);
                defaultContainer.put("likely_owners", new ArrayList<>(likelyOwnerNames));
                defaultContainer.put("owner_phone_numbers", new ArrayList<>(ownerPhoneNumbers));
                defaultContainer.put("owner_emails", new ArrayList<>(ownerEmails));
                defaultContainer.put("total_accounts_found", userAccounts.size());
                defaultContainer.put("user_accounts", userAccounts.stream().limit(20).collect(Collectors.toList()));
                defaultContainer.put("primary_accounts", primaryAccounts.stream().limit(12).collect(Collectors.toList()));
                evidencesList.add(defaultContainer);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("source", sourceId);
            result.put("evidence_type", evidenceType);
            result.put("total_evidences", evidencesList.size());
            result.put("evidences", evidencesList);
            result.put("system_info", systemInfo);
            result.put("likely_owner_names", new ArrayList<>(likelyOwnerNames));
            result.put("user_profile_dirs", new ArrayList<>(userProfileDirs));
            result.put("owner_phone_numbers", new ArrayList<>(ownerPhoneNumbers));
            result.put("owner_emails", new ArrayList<>(ownerEmails));
            result.put("device_properties", deviceProperties);
            result.put("total_accounts_found", userAccounts.size());
            result.put("user_accounts", userAccounts.stream().limit(20).collect(Collectors.toList()));
            result.put("primary_accounts", primaryAccounts.stream().limit(12).collect(Collectors.toList()));

            return result;

        } catch (Exception e) {
            LOGGER.error("Falha ao analisar proprietário do aparelho: {}", e.getMessage(), e);
            return Map.of("error", "Falha ao obter dados do proprietário do dispositivo: " + e.getMessage());
        }
    }


    /**
     * Returns an executive case summary.
     */
    public Map<String, Object> getCaseSummary() {
        checkCaseOpen();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("case_source_id", sourceId);
        summary.put("case_path", caseDirectory != null ? caseDirectory.getAbsolutePath() : "");
        summary.put("total_indexed_items", getTotalIndexedItems());

        List<String> categories = listCategories();
        summary.put("total_categories_count", categories.size());
        summary.put("top_categories", categories.stream().limit(20).collect(Collectors.toList()));

        List<String> bookmarks = listBookmarks();
        summary.put("total_bookmarks_count", bookmarks.size());
        summary.put("bookmarks", bookmarks);
        summary.put("note", "Caso indexado e pronto para consultas forenses via IA.");

        return summary;
    }

    // =========================================================================
    // FORENSIC TIMELINE & RELATIONAL ITEM ANALYSIS
    // =========================================================================

    /**
     * Retrieves the relational lineage and exact hash duplicates for an item.
     * Identifies parent container, direct child sub-items, and cross-source duplicate items.
     */
    public Map<String, Object> getItemRelations(int itemId) throws IOException {
        checkCaseOpen();

        int luceneId = ipedSource.getLuceneId(itemId);
        if (luceneId < 0) {
            return Map.of("id", itemId, "source", sourceId, "error", "Documento não encontrado no índice.");
        }

        Document doc = ipedSource.getReader().document(luceneId);

        String name = doc.get("name") != null ? doc.get("name") : "";
        String path = doc.get("path") != null ? doc.get("path") : "";
        String category = doc.get("category") != null ? doc.get("category") : "";
        String type = doc.get("type") != null ? doc.get("type") : "";
        String hash = doc.get("hash");
        if (hash == null || hash.isBlank()) {
            hash = doc.get("md5");
        }
        if (hash == null || hash.isBlank()) {
            hash = doc.get("sha-256");
        }

        Map<String, Object> targetSummary = new LinkedHashMap<>();
        targetSummary.put("id", itemId);
        targetSummary.put("name", name);
        targetSummary.put("path", path);
        targetSummary.put("category", category);
        targetSummary.put("type", type);
        targetSummary.put("hash", hash != null ? hash : "");

        // 1. Parent resolution
        Map<String, Object> parentSummary = null;
        int parentId = ipedSource.getParentId(itemId);
        if (parentId > 0 && parentId != itemId) {
            int parentLuceneId = ipedSource.getLuceneId(parentId);
            if (parentLuceneId >= 0) {
                Document parentDoc = ipedSource.getReader().document(parentLuceneId);
                parentSummary = new LinkedHashMap<>();
                parentSummary.put("id", parentId);
                parentSummary.put("name", parentDoc.get("name") != null ? parentDoc.get("name") : "");
                parentSummary.put("path", parentDoc.get("path") != null ? parentDoc.get("path") : "");
                parentSummary.put("category", parentDoc.get("category") != null ? parentDoc.get("category") : "");
                parentSummary.put("type", parentDoc.get("type") != null ? parentDoc.get("type") : "");
                IndexableField sizeField = parentDoc.getField("size");
                parentSummary.put("size", sizeField != null && sizeField.numericValue() != null ? sizeField.numericValue() : 0);
            }
        }

        // 2. Child sub-items resolution
        List<Map<String, Object>> directChildren = new ArrayList<>();
        int totalChildren = 0;
        try {
            IPEDSearcher childSearcher = new IPEDSearcher(ipedSource, "parentIds:" + itemId);
            SearchResult childRes = childSearcher.search();
            int[] childIds = childRes.getIds();
            if (childIds != null && childIds.length > 0) {
                for (int cId : childIds) {
                    if (cId == itemId) {
                        continue;
                    }
                    if (ipedSource.getParentId(cId) == itemId) {
                        totalChildren++;
                        if (directChildren.size() < 50) {
                            int cLuceneId = ipedSource.getLuceneId(cId);
                            if (cLuceneId >= 0) {
                                Document cDoc = ipedSource.getReader().document(cLuceneId);
                                Map<String, Object> childMap = new LinkedHashMap<>();
                                childMap.put("id", cId);
                                childMap.put("name", cDoc.get("name") != null ? cDoc.get("name") : "");
                                childMap.put("path", cDoc.get("path") != null ? cDoc.get("path") : "");
                                childMap.put("category", cDoc.get("category") != null ? cDoc.get("category") : "");
                                childMap.put("type", cDoc.get("type") != null ? cDoc.get("type") : "");
                                IndexableField sField = cDoc.getField("size");
                                childMap.put("size", sField != null && sField.numericValue() != null ? sField.numericValue() : 0);
                                directChildren.add(childMap);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Erro ao consultar subitens para item {}: {}", itemId, e.getMessage());
        }

        // 3. Hash duplicates resolution
        List<Map<String, Object>> duplicates = new ArrayList<>();
        int totalDuplicates = 0;
        if (hash != null && !hash.isBlank()) {
            try {
                IPEDSearcher dupSearcher = new IPEDSearcher(ipedSource, "hash:\"" + hash + "\"");
                SearchResult dupRes = dupSearcher.search();
                int[] dupIds = dupRes.getIds();
                if (dupIds != null && dupIds.length > 0) {
                    for (int dId : dupIds) {
                        if (dId == itemId) {
                            continue;
                        }
                        totalDuplicates++;
                        if (duplicates.size() < 50) {
                            int dLuceneId = ipedSource.getLuceneId(dId);
                            if (dLuceneId >= 0) {
                                Document dDoc = ipedSource.getReader().document(dLuceneId);
                                Map<String, Object> dupMap = new LinkedHashMap<>();
                                dupMap.put("id", dId);
                                dupMap.put("name", dDoc.get("name") != null ? dDoc.get("name") : "");
                                dupMap.put("path", dDoc.get("path") != null ? dDoc.get("path") : "");
                                dupMap.put("category", dDoc.get("category") != null ? dDoc.get("category") : "");
                                dupMap.put("type", dDoc.get("type") != null ? dDoc.get("type") : "");
                                IndexableField sField = dDoc.getField("size");
                                dupMap.put("size", sField != null && sField.numericValue() != null ? sField.numericValue() : 0);
                                duplicates.add(dupMap);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.debug("Erro ao consultar duplicatas por hash para item {}: {}", itemId, e.getMessage());
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("source", sourceId);
        result.put("target_item", targetSummary);
        result.put("parent", parentSummary);
        result.put("children_count", totalChildren);
        result.put("children", directChildren);
        result.put("duplicates_count", totalDuplicates);
        result.put("duplicates", duplicates);

        return result;
    }

    // =========================================================================
    // EVIDENCE TREE & FOLDER NAVIGATION
    // =========================================================================

    /**
     * Lists folder contents (subdirectories and files) within the evidence tree.
     *
     * @param folderPath Directory path to inspect. If null, empty, or "/", inspects the root of the evidence.
     * @param recursive If true, returns all nested descendants; if false, returns only immediate children.
     * @param limit Maximum number of items to return in the response lists.
     * @return Structured map containing folder information, subdirectories, and files.
     */
    public Map<String, Object> listFolderContents(String folderPath, boolean recursive, int limit) throws IOException {
        checkCaseOpen();

        int maxLimit = Math.max(1, Math.min(limit > 0 ? limit : 100, 1000));
        String rawPath = folderPath != null ? folderPath.trim() : "";
        boolean isRoot = rawPath.isEmpty() || rawPath.equals("/") || rawPath.equals(".") || rawPath.equalsIgnoreCase("root");

        Integer targetFolderId = null;
        String resolvedFolderPath = "/";
        String resolvedFolderName = "Root";

        if (!isRoot) {
            String cleanPath = rawPath.replace('\\', '/').replaceAll("^/+", "").replaceAll("/+$", "");
            if (cleanPath.isEmpty()) {
                isRoot = true;
            } else if (cleanPath.matches("\\d+")) {
                int candId = Integer.parseInt(cleanPath);
                int candLuceneId = ipedSource.getLuceneId(candId);
                if (candLuceneId >= 0) {
                    targetFolderId = candId;
                    Document candDoc = ipedSource.getReader().document(candLuceneId);
                    resolvedFolderName = candDoc.get("name") != null ? candDoc.get("name") : cleanPath;
                    resolvedFolderPath = candDoc.get("path") != null ? candDoc.get("path") : cleanPath;
                }
            } else {
                // Find folder by path matching
                String lastSegment = cleanPath.contains("/") ? cleanPath.substring(cleanPath.lastIndexOf('/') + 1) : cleanPath;
                String query = "name:\"" + lastSegment.replace("\"", "\\\"") + "\" && (isDir:true || hasChildren:true)";
                IPEDSearcher searcher = new IPEDSearcher(ipedSource, query);
                searcher.setTreeQuery(true);
                int[] candIds = searcher.search().getIds();

                if (candIds == null || candIds.length == 0) {
                    // Fallback to name without isDir filter
                    searcher = new IPEDSearcher(ipedSource, "name:\"" + lastSegment.replace("\"", "\\\"") + "\"");
                    searcher.setTreeQuery(true);
                    candIds = searcher.search().getIds();
                }

                if (candIds != null && candIds.length > 0) {
                    for (int cId : candIds) {
                        String itemPath = ipedSource.getItemProperty(cId, "path");
                        if (itemPath != null) {
                            String normCandPath = itemPath.replace('\\', '/').replaceAll("^/+|/+$", "");
                            if (normCandPath.equalsIgnoreCase(cleanPath)) {
                                targetFolderId = cId;
                                resolvedFolderPath = itemPath;
                                resolvedFolderName = ipedSource.getItemProperty(cId, "name");
                                break;
                            } else if (normCandPath.endsWith("/" + cleanPath)) {
                                if (targetFolderId == null) {
                                    targetFolderId = cId;
                                    resolvedFolderPath = itemPath;
                                    resolvedFolderName = ipedSource.getItemProperty(cId, "name");
                                }
                            }
                        }
                    }
                }

                if (targetFolderId == null) {
                    Map<String, Object> errResult = new LinkedHashMap<>();
                    errResult.put("folder_path", folderPath);
                    errResult.put("error", "Diretório não encontrado na árvore de evidências: " + folderPath);
                    errResult.put("recursive", recursive);
                    errResult.put("total_subdirectories", 0);
                    errResult.put("total_files", 0);
                    errResult.put("returned_subdirectories_count", 0);
                    errResult.put("returned_files_count", 0);
                    errResult.put("subdirectories", List.of());
                    errResult.put("files", List.of());
                    return errResult;
                }
            }
        }

        List<Map<String, Object>> subdirectories = new ArrayList<>();
        List<Map<String, Object>> files = new ArrayList<>();
        int totalSubdirectories = 0;
        int totalFiles = 0;

        if (isRoot) {
            if (!recursive) {
                // Non-recursive root: list root items (isRoot:true or fallback item 0)
                IPEDSearcher rootSearcher = new IPEDSearcher(ipedSource, "isRoot:true");
                rootSearcher.setTreeQuery(true);
                int[] rootIds = rootSearcher.search().getIds();
                if (rootIds == null || rootIds.length == 0) {
                    if (ipedSource.getReader().numDocs() > 0) {
                        rootIds = new int[] { 0 };
                    }
                }
                if (rootIds != null) {
                    for (int rId : rootIds) {
                        int luceneId = ipedSource.getLuceneId(rId);
                        if (luceneId < 0) continue;
                        Document doc = ipedSource.getReader().document(luceneId);
                        String isDirStr = doc.get("isDir");
                        boolean isDir = "true".equalsIgnoreCase(isDirStr)
                                || "true".equalsIgnoreCase(doc.get("hasChildren"))
                                || "true".equalsIgnoreCase(doc.get("isRoot"));
                        if (isDir) {
                            totalSubdirectories++;
                            if (subdirectories.size() < maxLimit) {
                                subdirectories.add(buildFolderEntry(rId, doc));
                            }
                        } else {
                            totalFiles++;
                            if (files.size() < maxLimit) {
                                files.add(buildFileEntry(rId, doc));
                            }
                        }
                    }
                }
            } else {
                // Recursive root: search *:* with treeQuery enabled
                IPEDSearcher allSearcher = new IPEDSearcher(ipedSource, "*:*");
                allSearcher.setTreeQuery(true);
                SearchResult allRes = allSearcher.search();
                int[] allIds = allRes.getIds();
                int totalIndexed = allIds != null ? allIds.length : getTotalIndexedItems();
                if (allIds != null) {
                    int fetchCount = Math.min(allIds.length, maxLimit * 2);
                    for (int i = 0; i < fetchCount; i++) {
                        int rId = allIds[i];
                        int luceneId = ipedSource.getLuceneId(rId);
                        if (luceneId < 0) continue;
                        Document doc = ipedSource.getReader().document(luceneId);
                        boolean isDir = "true".equalsIgnoreCase(doc.get("isDir"));
                        if (isDir) {
                            totalSubdirectories++;
                            if (subdirectories.size() < maxLimit) {
                                subdirectories.add(buildFolderEntry(rId, doc));
                            }
                        } else {
                            totalFiles++;
                            if (files.size() < maxLimit) {
                                files.add(buildFileEntry(rId, doc));
                            }
                        }
                    }
                }
                totalFiles = Math.max(totalFiles, totalIndexed);
            }
        } else {
            // Specific target folder
            int tFolderId = targetFolderId;
            IPEDSearcher searcher = new IPEDSearcher(ipedSource, "parentIds:" + tFolderId);
            searcher.setTreeQuery(true);
            SearchResult result = searcher.search();
            int[] allDescendantIds = result.getIds();

            if (allDescendantIds != null && allDescendantIds.length > 0) {
                for (int cId : allDescendantIds) {
                    if (cId == tFolderId) continue;

                    if (!recursive) {
                        if (ipedSource.getParentId(cId) != tFolderId) {
                            continue;
                        }
                    }

                    int cLuceneId = ipedSource.getLuceneId(cId);
                    if (cLuceneId < 0) continue;
                    Document cDoc = ipedSource.getReader().document(cLuceneId);
                    boolean isDir = "true".equalsIgnoreCase(cDoc.get("isDir"));

                    if (isDir) {
                        totalSubdirectories++;
                        if (subdirectories.size() < maxLimit) {
                            subdirectories.add(buildFolderEntry(cId, cDoc));
                        }
                    } else {
                        totalFiles++;
                        if (files.size() < maxLimit) {
                            files.add(buildFileEntry(cId, cDoc));
                        }
                    }
                }
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("folder_path", rawPath.isEmpty() ? "/" : rawPath);
        response.put("folder_id", targetFolderId);
        response.put("folder_name", resolvedFolderName);
        response.put("resolved_path", resolvedFolderPath);
        response.put("recursive", recursive);
        response.put("total_subdirectories", totalSubdirectories);
        response.put("total_files", totalFiles);
        response.put("returned_subdirectories_count", subdirectories.size());
        response.put("returned_files_count", files.size());
        response.put("limit", maxLimit);
        response.put("subdirectories", subdirectories);
        response.put("files", files);

        return response;
    }

    private Map<String, Object> buildFolderEntry(int id, Document doc) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", id);
        entry.put("name", doc.get("name") != null ? doc.get("name") : "");
        entry.put("path", doc.get("path") != null ? doc.get("path") : "");
        entry.put("isDir", true);
        entry.put("hasChildren", "true".equalsIgnoreCase(doc.get("hasChildren")));
        return entry;
    }

    private Map<String, Object> buildFileEntry(int id, Document doc) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", id);
        entry.put("name", doc.get("name") != null ? doc.get("name") : "");
        entry.put("path", doc.get("path") != null ? doc.get("path") : "");
        entry.put("category", doc.get("category") != null ? doc.get("category") : "");
        entry.put("type", doc.get("type") != null ? doc.get("type") : "");
        IndexableField sizeField = doc.getField("size");
        entry.put("size", sizeField != null && sizeField.numericValue() != null ? sizeField.numericValue() : 0);
        String hash = doc.get("hash");
        if (hash == null || hash.isBlank()) hash = doc.get("md5");
        if (hash == null || hash.isBlank()) hash = doc.get("sha-256");
        entry.put("hash", hash != null ? hash : "");
        entry.put("selected", ipedSource.getBookmarks().isChecked(id));
        return entry;
    }

    /**
     * Executes an ascending chronological event retrieval across indexed case timestamps.
     */
    public Map<String, Object> getTimeline(String start, String end, String category, int limit) throws IOException {
        checkCaseOpen();

        int maxLimit = Math.max(1, Math.min(limit <= 0 ? 50 : limit, 200));
        String startNorm = normalizeIsoDate(start, false);
        String endNorm = normalizeIsoDate(end, true);

        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append("timeStamp:[\"").append(startNorm).append("\" TO \"").append(endNorm).append("\"]");

        if (category != null && !category.isBlank() && !category.equalsIgnoreCase("all") && !category.equals("*")) {
            queryBuilder.append(" AND category:\"").append(category.trim().replace("\"", "\\\"")).append("\"");
        }

        String luceneQuery = queryBuilder.toString();
        IPEDSearcher searcher = new IPEDSearcher(ipedSource, luceneQuery, "created");
        SearchResult res = searcher.search();
        int[] ids = res.getIds();
        int totalDocsFound = ids != null ? ids.length : 0;

        List<Map<String, Object>> events = new ArrayList<>();

        if (ids != null && totalDocsFound > 0) {
            int candidatePoolSize = Math.min(totalDocsFound, Math.max(maxLimit * 4, 300));
            for (int i = 0; i < candidatePoolSize; i++) {
                int id = ids[i];
                int luceneId = ipedSource.getLuceneId(id);
                if (luceneId < 0) continue;

                Document doc = ipedSource.getReader().document(luceneId);
                String docCat = doc.get("category") != null ? doc.get("category") : "";
                String docName = doc.get("name") != null ? doc.get("name") : "";
                String docPath = doc.get("path") != null ? doc.get("path") : "";
                String summary = extractEventSummary(doc, docCat);

                String[] timeStamps = doc.getValues("timeStamp");
                String[] timeEvents = doc.getValues("timeEvent");

                if (timeStamps != null && timeStamps.length > 0) {
                    for (int tIdx = 0; tIdx < timeStamps.length; tIdx++) {
                        String ts = timeStamps[tIdx];
                        if (ts != null && ts.compareTo(startNorm) >= 0 && ts.compareTo(endNorm) <= 0) {
                            String te = (timeEvents != null && tIdx < timeEvents.length) ? timeEvents[tIdx] : "";
                            String eventType = resolveEventType(te, docCat);

                            Map<String, Object> evt = new LinkedHashMap<>();
                            evt.put("id", id);
                            evt.put("timestamp", ts);
                            evt.put("event_type", eventType);
                            evt.put("category", docCat);
                            evt.put("name", docName);
                            evt.put("path", docPath);
                            evt.put("source", sourceId);
                            if (!summary.isBlank()) {
                                evt.put("summary", summary);
                            }
                            events.add(evt);
                        }
                    }
                } else {
                    for (String dateKey : List.of("Communication:Date", "created", "modified", "accessed", "date")) {
                        String ts = doc.get(dateKey);
                        if (ts != null && ts.compareTo(startNorm) >= 0 && ts.compareTo(endNorm) <= 0) {
                            Map<String, Object> evt = new LinkedHashMap<>();
                            evt.put("id", id);
                            evt.put("timestamp", ts);
                            evt.put("event_type", resolveEventType(dateKey, docCat));
                            evt.put("category", docCat);
                            evt.put("name", docName);
                            evt.put("path", docPath);
                            evt.put("source", sourceId);
                            if (!summary.isBlank()) {
                                evt.put("summary", summary);
                            }
                            events.add(evt);
                            break;
                        }
                    }
                }
            }
        }

        events.sort((e1, e2) -> {
            String t1 = (String) e1.get("timestamp");
            String t2 = (String) e2.get("timestamp");
            int cmp = t1.compareTo(t2);
            if (cmp != 0) return cmp;
            return Integer.compare((int) e1.get("id"), (int) e2.get("id"));
        });

        int totalEventsFound = events.size();
        List<Map<String, Object>> pagedEvents = events.stream().limit(maxLimit).collect(Collectors.toList());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("start", startNorm);
        response.put("end", endNorm);
        if (category != null && !category.isBlank()) {
            response.put("category", category);
        }
        response.put("total_matching_items", totalDocsFound);
        response.put("total_events_extracted", totalEventsFound);
        response.put("returned_count", pagedEvents.size());
        response.put("limit", maxLimit);
        response.put("events", pagedEvents);

        return response;
    }

    /**
     * Reconstructs evidentiary activity around a pivotal milestone timestamp (+/- windowMinutes).
     */
    public Map<String, Object> getEventsAroundTime(String target, int windowMinutes, int limit) throws IOException {
        checkCaseOpen();

        if (target == null || target.isBlank()) {
            throw new IllegalArgumentException("O timestamp de referência (target) não pode ser vazio.");
        }

        int safeWindow = (windowMinutes <= 0) ? 30 : Math.min(windowMinutes, 1440);
        String targetNorm = normalizeIsoDate(target, false);

        Instant targetInstant;
        try {
            targetInstant = Instant.parse(targetNorm);
        } catch (Exception e) {
            Date d = iped.utils.DateUtil.tryToParseDate(target);
            if (d != null) {
                targetInstant = d.toInstant();
            } else {
                targetInstant = Instant.now();
            }
        }

        Instant startInstant = targetInstant.minus(safeWindow, ChronoUnit.MINUTES);
        Instant endInstant = targetInstant.plus(safeWindow, ChronoUnit.MINUTES);

        String startIso = startInstant.toString();
        String endIso = endInstant.toString();

        Map<String, Object> timeline = getTimeline(startIso, endIso, null, limit);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("target_time", targetNorm);
        result.put("window_minutes", safeWindow);
        result.put("window_start", startIso);
        result.put("window_end", endIso);
        result.putAll(timeline);

        return result;
    }

    private String normalizeIsoDate(String input, boolean isEnd) {
        if (input == null || input.isBlank()) {
            return isEnd ? "9999-12-31T23:59:59Z" : "0000-01-01T00:00:00Z";
        }
        String s = input.trim().replace(" ", "T");
        if (s.matches("^\\d{4}-\\d{2}-\\d{2}$")) {
            return isEnd ? s + "T23:59:59Z" : s + "T00:00:00Z";
        }
        if (s.matches("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}$")) {
            return isEnd ? s + ":59Z" : s + ":00Z";
        }
        if (s.matches("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}$")) {
            return s + "Z";
        }
        if (s.endsWith("Z")) {
            return s;
        }
        try {
            Date d = iped.utils.DateUtil.tryToParseDate(s);
            if (d != null) {
                return iped.utils.DateUtil.dateToString(d);
            }
        } catch (Exception ignored) {
        }
        return s.endsWith("Z") ? s : s + "Z";
    }

    private String resolveEventType(String timeEvent, String category) {
        if (timeEvent != null && !timeEvent.isBlank()) {
            String te = timeEvent.trim().toLowerCase();
            switch (te) {
                case "created": return "FILE_CREATION";
                case "modified": return "FILE_MODIFICATION";
                case "accessed": return "FILE_ACCESS";
                case "changed": return "FILE_METADATA_CHANGE";
                case "communication:date":
                case "date": return "COMMUNICATION";
                case "visitdate":
                case "visited": return "WEB_HISTORY";
                case "downloaddate": return "DOWNLOAD";
                case "photodate":
                case "exif": return "MEDIA_CAPTURE";
                case "calldate": return "CALL";
            }
        }
        if (category != null) {
            String cat = category.toLowerCase();
            if (cat.contains("whatsapp") || cat.contains("telegram") || cat.contains("signal") || cat.contains("messages") || cat.contains("chat")) {
                return "MESSAGE";
            }
            if (cat.contains("calls") || cat.contains("phone calls")) {
                return "CALL";
            }
            if (cat.contains("email")) {
                return "EMAIL";
            }
            if (cat.contains("history") || cat.contains("browser") || cat.contains("searches")) {
                return "WEB_HISTORY";
            }
            if (cat.contains("locations") || cat.contains("journeys")) {
                return "GEOLOCATION";
            }
            if (cat.contains("images") || cat.contains("photos") || cat.contains("videos") || cat.contains("audios")) {
                return "MEDIA";
            }
        }
        return (timeEvent != null && !timeEvent.isBlank())
                ? timeEvent.toUpperCase().replace(':', '_').replace(' ', '_')
                : "EVENT";
    }

    private String extractEventSummary(Document doc, String category) {
        String from = doc.get("from");
        String to = doc.get("to");
        String subject = doc.get("subject");
        String url = doc.get("url");

        if (from != null || to != null || subject != null) {
            StringBuilder sb = new StringBuilder();
            if (from != null && !from.isBlank()) sb.append("De: ").append(from).append(" ");
            if (to != null && !to.isBlank()) sb.append("Para: ").append(to).append(" ");
            if (subject != null && !subject.isBlank()) sb.append("Assunto: ").append(subject);
            return sb.toString().trim();
        }
        if (url != null && !url.isBlank()) {
            return "URL: " + url;
        }
        String ufedEntry = doc.get("ufed:EntryValue");
        if (ufedEntry != null && !ufedEntry.isBlank()) {
            return ufedEntry;
        }
        IndexableField sizeField = doc.getField("size");
        if (sizeField != null && sizeField.numericValue() != null) {
            return "Tamanho: " + sizeField.numericValue() + " bytes";
        }
        return "";
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    private void checkCaseOpen() {
        if (!isCaseOpen()) {
            throw new IllegalStateException("Nenhum caso do IPED está aberto no momento. Use openCase(File) primeiro.");
        }
    }

    // =========================================================================
    // COMMUNICATIONS GRAPH & CONTACT ANALYTICS
    // =========================================================================

    private static class ContactIdentity {
        final String raw;
        final String id;
        final String name;
        final boolean isGroup;

        ContactIdentity(String raw, String id, String name, boolean isGroup) {
            this.raw = raw;
            this.id = id;
            this.name = name;
            this.isGroup = isGroup;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ContactIdentity that = (ContactIdentity) o;
            return Objects.equals(id, that.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }

    private static class CommunicationEvent {
        final ContactIdentity sender;
        final List<ContactIdentity> recipients;
        final String channel;
        final boolean isCall;
        final boolean isEmail;
        final boolean isIncoming;
        final boolean isOutgoing;
        final String timestamp;

        CommunicationEvent(ContactIdentity sender, List<ContactIdentity> recipients, String channel,
                           boolean isCall, boolean isEmail, boolean isIncoming, boolean isOutgoing, String timestamp) {
            this.sender = sender;
            this.recipients = recipients;
            this.channel = channel;
            this.isCall = isCall;
            this.isEmail = isEmail;
            this.isIncoming = isIncoming;
            this.isOutgoing = isOutgoing;
            this.timestamp = timestamp;
        }
    }

    private static final Set<String> IGNORED_CONTACTS = Set.of(
            "system", "system_message", "broadcast", "status@broadcast", "null",
            "unknown", "me", "voicemail", "none", "anonymous", ""
    );

    private ContactIdentity parseContact(String raw, String convName, Map<String, String> contactBook) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String clean = raw.trim();
        if ((clean.startsWith("\"") && clean.endsWith("\"")) || (clean.startsWith("'") && clean.endsWith("'"))) {
            clean = clean.substring(1, clean.length() - 1).trim();
        }
        if (clean.isBlank()) {
            return null;
        }
        String lower = clean.toLowerCase();
        if (IGNORED_CONTACTS.contains(lower)) {
            return null;
        }

        // WhatsApp Group: e.g. 120363024888888888@g.us
        if (lower.endsWith("@g.us") || lower.contains("@g.us")) {
            String name = (convName != null && !convName.isBlank()) ? convName.trim() : ("Grupo (" + clean + ")");
            return new ContactIdentity(clean, clean, name, true);
        }

        // WhatsApp individual: e.g. 5511988887777@s.whatsapp.net
        if (lower.endsWith("@s.whatsapp.net")) {
            String phoneId = clean.substring(0, clean.indexOf('@')).trim();
            String name = (convName != null && !convName.isBlank()) ? convName.trim() : (contactBook != null ? contactBook.getOrDefault(phoneId, phoneId) : phoneId);
            return new ContactIdentity(clean, phoneId, name, false);
        }

        // Email / Name format: Name <email@domain.com>
        if (clean.contains("<") && clean.contains(">")) {
            int start = clean.indexOf('<');
            int end = clean.indexOf('>', start);
            String email = clean.substring(start + 1, end).trim().toLowerCase();
            String name = clean.substring(0, start).trim();
            if (name.startsWith("\"") && name.endsWith("\"") && name.length() > 2) {
                name = name.substring(1, name.length() - 1).trim();
            }
            if (name.isBlank()) {
                name = (convName != null && !convName.isBlank()) ? convName.trim() : (contactBook != null ? contactBook.getOrDefault(email, email) : email);
            }
            return new ContactIdentity(clean, email, name, false);
        }

        // Parentheses format: Name (11999998888)
        if (clean.contains("(") && clean.contains(")")) {
            int p1 = clean.indexOf('(');
            int p2 = clean.indexOf(')', p1);
            String inP = clean.substring(p1 + 1, p2).trim();
            String outP = clean.substring(0, p1).trim();
            String digits = inP.replaceAll("[^0-9+]", "");
            if (digits.length() >= 7) {
                String name = !outP.isBlank() ? outP : (convName != null && !convName.isBlank() ? convName.trim() : inP);
                return new ContactIdentity(clean, digits, name, false);
            }
        }

        // Phone digits check
        String digitsOnly = clean.replaceAll("[^0-9]", "");
        if (digitsOnly.length() >= 7 && (clean.startsWith("+") || clean.matches("^[0-9\\+\\-\\(\\)\\s]+$"))) {
            String phoneId = clean.startsWith("+") ? ("+" + digitsOnly) : digitsOnly;
            String name = (convName != null && !convName.isBlank()) ? convName.trim() : (contactBook != null ? contactBook.getOrDefault(phoneId, phoneId) : phoneId);
            return new ContactIdentity(clean, phoneId, name, false);
        }

        // General identity
        String name = (convName != null && !convName.isBlank()) ? convName.trim() : (contactBook != null ? contactBook.getOrDefault(clean, clean) : clean);
        return new ContactIdentity(clean, clean, name, false);
    }

    private Map<String, String> loadContactBook() {
        Map<String, String> book = new HashMap<>();
        try {
            IPEDSearcher searcher = new IPEDSearcher(ipedSource, "category:(\"contacts\" OR \"contatos\" OR \"phonebook\") OR type:\"text/vcard\" OR type:\"application/x-ufed-contact\"");
            SearchResult res = searcher.search();
            int[] ids = res.getIds();
            if (ids != null) {
                int limit = Math.min(ids.length, 500);
                for (int i = 0; i < limit; i++) {
                    int luceneId = ipedSource.getLuceneId(ids[i]);
                    if (luceneId < 0) continue;
                    Document doc = ipedSource.getReader().document(luceneId);
                    String name = doc.get("name");
                    if (name == null || name.isBlank()) name = doc.get("userName");
                    if (name == null || name.isBlank()) name = doc.get("ufed:EntryName");

                    String phone = doc.get("phoneNumber");
                    if (phone == null || phone.isBlank()) phone = doc.get("userPhone");
                    if (phone == null || phone.isBlank()) phone = doc.get("ufed:EntryValue");

                    String email = doc.get("emailAddress");
                    if (email == null || email.isBlank()) email = doc.get("userEmail");

                    if (name != null && !name.isBlank()) {
                        String cleanName = name.trim();
                        if (phone != null && !phone.isBlank()) {
                            String pDigits = phone.replaceAll("[^0-9+]", "");
                            if (pDigits.length() >= 7) {
                                book.put(pDigits, cleanName);
                                if (!pDigits.startsWith("+")) book.put("+" + pDigits, cleanName);
                            }
                        }
                        if (email != null && !email.isBlank()) {
                            book.put(email.trim().toLowerCase(), cleanName);
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Não foi possível carregar agenda de contatos: {}", e.getMessage());
        }
        return book;
    }

    private List<CommunicationEvent> collectCommunicationEvents() throws IOException {
        checkCaseOpen();
        Map<String, String> contactBook = loadContactBook();

        String query = "Communication\\:From:* OR Communication\\:To:* OR from:* OR to:* OR category:(\"chat messages\" OR \"calls\" OR \"emails\" OR \"messages\" OR \"whatsapp\" OR \"telegram\" OR \"signal\" OR \"skype\" OR \"discord\")";
        IPEDSearcher searcher = new IPEDSearcher(ipedSource, query);
        SearchResult res = searcher.search();
        int[] ids = res.getIds();
        if (ids == null || ids.length == 0) {
            return List.of();
        }

        List<CommunicationEvent> events = new ArrayList<>(ids.length);
        for (int id : ids) {
            int luceneId = ipedSource.getLuceneId(id);
            if (luceneId < 0) continue;

            Document doc = ipedSource.getReader().document(luceneId);
            String convName = doc.get("Conversation:Name");
            String category = doc.get("category");
            String catLower = category != null ? category.toLowerCase() : "";

            boolean isCall = catLower.contains("call") || catLower.contains("chamada");
            boolean isEmail = catLower.contains("email") || catLower.contains("e-mail");
            String channel = isCall ? "calls" : (isEmail ? "emails" : (catLower.contains("whatsapp") ? "whatsapp" : (catLower.contains("telegram") ? "telegram" : "chat messages")));

            String direction = doc.get("Communication:Direction");
            boolean isIncoming = direction != null && direction.toUpperCase().contains("IN");
            boolean isOutgoing = direction != null && direction.toUpperCase().contains("OUT");

            String ts = doc.get("Communication:Date");
            if (ts == null || ts.isBlank()) ts = doc.get("timeStamp");
            if (ts == null || ts.isBlank()) ts = doc.get("created");
            if (ts == null || ts.isBlank()) ts = doc.get("date");
            if (ts != null) ts = normalizeIsoDate(ts, false);

            // Senders
            String fromRaw = doc.get("Communication:From");
            if (fromRaw == null || fromRaw.isBlank()) fromRaw = doc.get("from");
            if (fromRaw == null || fromRaw.isBlank()) fromRaw = doc.get("Message-From");

            ContactIdentity sender = parseContact(fromRaw, convName, contactBook);

            // Recipients
            List<ContactIdentity> recipients = new ArrayList<>();
            String[] toValues = doc.getValues("Communication:To");
            if (toValues != null && toValues.length > 0) {
                for (String tVal : toValues) {
                    ContactIdentity ci = parseContact(tVal, convName, contactBook);
                    if (ci != null) recipients.add(ci);
                }
            }
            if (recipients.isEmpty()) {
                String[] toSimple = doc.getValues("to");
                if (toSimple != null && toSimple.length > 0) {
                    for (String tVal : toSimple) {
                        ContactIdentity ci = parseContact(tVal, convName, contactBook);
                        if (ci != null) recipients.add(ci);
                    }
                }
            }
            if (recipients.isEmpty()) {
                String toMsg = doc.get("Message-To");
                if (toMsg != null && !toMsg.isBlank()) {
                    ContactIdentity ci = parseContact(toMsg, convName, contactBook);
                    if (ci != null) recipients.add(ci);
                }
            }

            if (sender == null && recipients.isEmpty()) {
                continue;
            }

            events.add(new CommunicationEvent(sender, recipients, channel, isCall, isEmail, isIncoming, isOutgoing, ts));
        }

        return events;
    }

    /**
     * Aggregates message and call volume by contact identity, ranking the most active communicators in the case.
     *
     * @param limit Maximum number of top contacts to return (default 20, max 200)
     * @return Map containing ranked contacts with interaction counts, channels, and time bounds
     */
    public Map<String, Object> getTopContacts(int limit) throws IOException {
        checkCaseOpen();
        int maxLimit = Math.max(1, Math.min(limit <= 0 ? 20 : limit, 200));

        List<CommunicationEvent> events = collectCommunicationEvents();

        // Get likely owner identities to avoid ranking the owner as their own interlocutor
        Set<String> ownerIdentities = new HashSet<>();
        try {
            Map<String, Object> ownerInfo = getDeviceAndOwnerInfo();
            if (ownerInfo != null) {
                @SuppressWarnings("unchecked")
                Collection<String> likelyNames = (Collection<String>) ownerInfo.get("likely_owner_names");
                if (likelyNames != null) {
                    for (String n : likelyNames) ownerIdentities.add(n.toLowerCase());
                }
                @SuppressWarnings("unchecked")
                Map<String, String> devProps = (Map<String, String>) ownerInfo.get("device_properties");
                if (devProps != null) {
                    for (String val : devProps.values()) {
                        String digits = val.replaceAll("[^0-9]", "");
                        if (digits.length() >= 7) ownerIdentities.add(digits);
                    }
                }
            }
        } catch (Exception ignored) {
        }

        class ContactStats {
            String id;
            String name;
            boolean isGroup;
            int totalInteractions = 0;
            int messagesCount = 0;
            int callsCount = 0;
            int incomingCount = 0;
            int outgoingCount = 0;
            final Set<String> channels = new LinkedHashSet<>();
            String firstInteraction = null;
            String lastInteraction = null;

            ContactStats(String id, String name, boolean isGroup) {
                this.id = id;
                this.name = name;
                this.isGroup = isGroup;
            }

            void record(boolean isCall, boolean incoming, boolean outgoing, String channel, String timestamp) {
                totalInteractions++;
                if (isCall) callsCount++;
                else messagesCount++;
                if (incoming) incomingCount++;
                if (outgoing) outgoingCount++;
                if (channel != null && !channel.isBlank()) channels.add(channel);
                if (timestamp != null && !timestamp.isBlank()) {
                    if (firstInteraction == null || timestamp.compareTo(firstInteraction) < 0) {
                        firstInteraction = timestamp;
                    }
                    if (lastInteraction == null || timestamp.compareTo(lastInteraction) > 0) {
                        lastInteraction = timestamp;
                    }
                }
            }
        }

        Map<String, ContactStats> statsMap = new HashMap<>();

        for (CommunicationEvent ev : events) {
            // Incoming: interlocutor is sender
            if (ev.isIncoming && ev.sender != null) {
                ContactStats cs = statsMap.computeIfAbsent(ev.sender.id, k -> new ContactStats(ev.sender.id, ev.sender.name, ev.sender.isGroup));
                if (cs.name.equals(cs.id) && !ev.sender.name.equals(ev.sender.id)) {
                    cs.name = ev.sender.name;
                }
                cs.record(ev.isCall, true, false, ev.channel, ev.timestamp);
            }
            // Outgoing: interlocutor is each recipient
            else if (ev.isOutgoing) {
                for (ContactIdentity r : ev.recipients) {
                    ContactStats cs = statsMap.computeIfAbsent(r.id, k -> new ContactStats(r.id, r.name, r.isGroup));
                    if (cs.name.equals(cs.id) && !r.name.equals(r.id)) {
                        cs.name = r.name;
                    }
                    cs.record(ev.isCall, false, true, ev.channel, ev.timestamp);
                }
            }
            // Direction unknown:
            else {
                boolean senderIsOwner = ev.sender != null && ownerIdentities.contains(ev.sender.id.toLowerCase());
                if (senderIsOwner) {
                    for (ContactIdentity r : ev.recipients) {
                        ContactStats cs = statsMap.computeIfAbsent(r.id, k -> new ContactStats(r.id, r.name, r.isGroup));
                        if (cs.name.equals(cs.id) && !r.name.equals(r.id)) cs.name = r.name;
                        cs.record(ev.isCall, false, true, ev.channel, ev.timestamp);
                    }
                } else if (ev.sender != null) {
                    ContactStats cs = statsMap.computeIfAbsent(ev.sender.id, k -> new ContactStats(ev.sender.id, ev.sender.name, ev.sender.isGroup));
                    if (cs.name.equals(cs.id) && !ev.sender.name.equals(ev.sender.id)) cs.name = ev.sender.name;
                    cs.record(ev.isCall, true, false, ev.channel, ev.timestamp);
                }
                if (ev.sender == null) {
                    for (ContactIdentity r : ev.recipients) {
                        ContactStats cs = statsMap.computeIfAbsent(r.id, k -> new ContactStats(r.id, r.name, r.isGroup));
                        if (cs.name.equals(cs.id) && !r.name.equals(r.id)) cs.name = r.name;
                        cs.record(ev.isCall, false, false, ev.channel, ev.timestamp);
                    }
                }
            }
        }

        // Sort descending by total interactions
        List<ContactStats> sorted = statsMap.values().stream()
                .sorted((a, b) -> Integer.compare(b.totalInteractions, a.totalInteractions))
                .collect(Collectors.toList());

        List<Map<String, Object>> topList = new ArrayList<>();
        int rank = 1;
        for (int i = 0; i < Math.min(sorted.size(), maxLimit); i++) {
            ContactStats cs = sorted.get(i);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("rank", rank++);
            item.put("identifier", cs.id);
            item.put("name", cs.name);
            item.put("total_interactions", cs.totalInteractions);
            item.put("messages_count", cs.messagesCount);
            item.put("calls_count", cs.callsCount);
            item.put("incoming_count", cs.incomingCount);
            item.put("outgoing_count", cs.outgoingCount);
            item.put("channels", new ArrayList<>(cs.channels));
            item.put("first_interaction", cs.firstInteraction != null ? cs.firstInteraction : "");
            item.put("last_interaction", cs.lastInteraction != null ? cs.lastInteraction : "");
            if (cs.isGroup) {
                item.put("is_group", true);
            }
            topList.add(item);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("total_contacts_discovered", statsMap.size());
        response.put("returned_count", topList.size());
        response.put("limit", maxLimit);
        response.put("contacts", topList);
        return response;
    }

    /**
     * Assembles a communications network topology containing nodes and weighted interaction edges.
     *
     * @param focalContact (Optional) Center the graph on interactions involving this contact
     * @param minInteractions Minimum interactions required to include an edge (default 5, min 1)
     * @param limitEdges Maximum number of edges to return (default 50, max 200)
     * @return Map containing graph nodes and edges suitable for cognitive reasoning or network visualization
     */
    public Map<String, Object> getCommunicationsGraph(String focalContact, int minInteractions, int limitEdges) throws IOException {
        checkCaseOpen();
        int safeMinInteractions = Math.max(1, minInteractions <= 0 ? 5 : minInteractions);
        int maxLimitEdges = Math.max(1, Math.min(limitEdges <= 0 ? 50 : limitEdges, 200));

        List<CommunicationEvent> events = collectCommunicationEvents();

        // Device owner representation for local endpoints
        String defaultOwnerId = "Dispositivo_Local";
        String defaultOwnerName = "Dispositivo / Titular (Local)";
        try {
            Map<String, Object> ownerInfo = getDeviceAndOwnerInfo();
            if (ownerInfo != null) {
                @SuppressWarnings("unchecked")
                Collection<String> likelyNames = (Collection<String>) ownerInfo.get("likely_owner_names");
                if (likelyNames != null && !likelyNames.isEmpty()) {
                    defaultOwnerName = likelyNames.iterator().next();
                    defaultOwnerId = defaultOwnerName.replaceAll("\\s+", "_");
                }
            }
        } catch (Exception ignored) {
        }
        ContactIdentity ownerNode = new ContactIdentity(defaultOwnerId, defaultOwnerId, defaultOwnerName, false);

        class EdgeAggregator {
            ContactIdentity source;
            ContactIdentity target;
            int weight = 0;
            int messagesCount = 0;
            int callsCount = 0;
            final Set<String> channels = new LinkedHashSet<>();
            String firstInteraction = null;
            String lastInteraction = null;

            EdgeAggregator(ContactIdentity source, ContactIdentity target) {
                this.source = source;
                this.target = target;
            }

            void record(boolean isCall, String channel, String timestamp) {
                weight++;
                if (isCall) callsCount++;
                else messagesCount++;
                if (channel != null && !channel.isBlank()) channels.add(channel);
                if (timestamp != null && !timestamp.isBlank()) {
                    if (firstInteraction == null || timestamp.compareTo(firstInteraction) < 0) {
                        firstInteraction = timestamp;
                    }
                    if (lastInteraction == null || timestamp.compareTo(lastInteraction) > 0) {
                        lastInteraction = timestamp;
                    }
                }
            }
        }

        Map<String, EdgeAggregator> edgesMap = new HashMap<>();
        Map<String, ContactIdentity> allKnownNodes = new HashMap<>();
        allKnownNodes.put(ownerNode.id, ownerNode);

        for (CommunicationEvent ev : events) {
            ContactIdentity src = null;
            List<ContactIdentity> targets = new ArrayList<>();

            if (ev.isIncoming) {
                src = ev.sender;
                targets.add(ownerNode);
            } else if (ev.isOutgoing) {
                src = ownerNode;
                targets.addAll(ev.recipients);
            } else {
                if (ev.sender != null) {
                    src = ev.sender;
                    if (!ev.recipients.isEmpty()) {
                        targets.addAll(ev.recipients);
                    } else {
                        targets.add(ownerNode);
                    }
                } else if (!ev.recipients.isEmpty()) {
                    src = ownerNode;
                    targets.addAll(ev.recipients);
                }
            }

            if (src == null || targets.isEmpty()) continue;
            final ContactIdentity finalSrc = src;
            allKnownNodes.putIfAbsent(finalSrc.id, finalSrc);

            for (ContactIdentity tgt : targets) {
                if (tgt == null || finalSrc.id.equals(tgt.id)) continue;
                allKnownNodes.putIfAbsent(tgt.id, tgt);

                String edgeKey = finalSrc.id + "->" + tgt.id;
                EdgeAggregator ea = edgesMap.computeIfAbsent(edgeKey, k -> new EdgeAggregator(finalSrc, tgt));
                ea.record(ev.isCall, ev.channel, ev.timestamp);
            }
        }

        // Focal contact filtering
        final String focalClean = (focalContact != null && !focalContact.isBlank()) ? focalContact.trim().toLowerCase() : null;

        List<EdgeAggregator> eligibleEdges = edgesMap.values().stream()
                .filter(e -> {
                    if (focalClean == null) return true;
                    boolean srcMatches = e.source.id.toLowerCase().contains(focalClean) || e.source.name.toLowerCase().contains(focalClean);
                    boolean tgtMatches = e.target.id.toLowerCase().contains(focalClean) || e.target.name.toLowerCase().contains(focalClean);
                    return srcMatches || tgtMatches;
                })
                .filter(e -> e.weight >= safeMinInteractions)
                .sorted((a, b) -> Integer.compare(b.weight, a.weight))
                .limit(maxLimitEdges)
                .collect(Collectors.toList());

        // Extract nodes connected by retained edges
        Map<String, Map<String, Object>> connectedNodes = new LinkedHashMap<>();
        for (EdgeAggregator ea : eligibleEdges) {
            for (ContactIdentity ci : List.of(ea.source, ea.target)) {
                connectedNodes.computeIfAbsent(ci.id, k -> {
                    Map<String, Object> node = new LinkedHashMap<>();
                    node.put("id", ci.id);
                    node.put("label", ci.name != null && !ci.name.isBlank() ? ci.name : ci.id);
                    node.put("name", ci.name);
                    node.put("phone_or_account", ci.id);
                    node.put("total_interactions", 0);
                    node.put("is_focal", focalClean != null && (ci.id.toLowerCase().contains(focalClean) || ci.name.toLowerCase().contains(focalClean)));
                    if (ci.isGroup) node.put("is_group", true);
                    return node;
                });
                Map<String, Object> node = connectedNodes.get(ci.id);
                node.put("total_interactions", (int) node.get("total_interactions") + ea.weight);
            }
        }

        // If focal contact was specified and matched a known node, ensure it is in connectedNodes even if 0 edges met threshold
        if (focalClean != null && connectedNodes.isEmpty()) {
            for (ContactIdentity ci : allKnownNodes.values()) {
                if (ci.id.toLowerCase().contains(focalClean) || ci.name.toLowerCase().contains(focalClean)) {
                    Map<String, Object> node = new LinkedHashMap<>();
                    node.put("id", ci.id);
                    node.put("label", ci.name != null && !ci.name.isBlank() ? ci.name : ci.id);
                    node.put("name", ci.name);
                    node.put("phone_or_account", ci.id);
                    node.put("total_interactions", 0);
                    node.put("is_focal", true);
                    if (ci.isGroup) node.put("is_group", true);
                    connectedNodes.put(ci.id, node);
                    break;
                }
            }
        }

        List<Map<String, Object>> edgesList = new ArrayList<>();
        for (EdgeAggregator ea : eligibleEdges) {
            Map<String, Object> edge = new LinkedHashMap<>();
            edge.put("source", ea.source.id);
            edge.put("target", ea.target.id);
            edge.put("weight", ea.weight);
            edge.put("channels", new ArrayList<>(ea.channels));
            edge.put("messages_count", ea.messagesCount);
            edge.put("calls_count", ea.callsCount);
            edge.put("first_interaction", ea.firstInteraction != null ? ea.firstInteraction : "");
            edge.put("last_interaction", ea.lastInteraction != null ? ea.lastInteraction : "");
            edgesList.add(edge);
        }

        List<Map<String, Object>> nodesList = new ArrayList<>(connectedNodes.values());
        nodesList.sort((a, b) -> Integer.compare((int) b.get("total_interactions"), (int) a.get("total_interactions")));

        Map<String, Object> response = new LinkedHashMap<>();
        if (focalClean != null) {
            response.put("focal_contact", focalContact.trim());
        }
        response.put("min_interactions", safeMinInteractions);
        response.put("limit_edges", maxLimitEdges);
        response.put("total_nodes", nodesList.size());
        response.put("total_edges", edgesList.size());
        response.put("nodes", nodesList);
        response.put("edges", edgesList);

        if (focalClean != null && edgesList.isEmpty()) {
            response.put("message", "Nenhuma conexão encontrada para o contato focal com o limite mínimo de " + safeMinInteractions + " interações. Tente reduzir 'min_interactions' para 1.");
        }

        return response;
    }

    // =========================================================================
    // METADATA DICTIONARY & PROPERTY DISCOVERY
    // =========================================================================

    /**
     * Returns the forensic property dictionary for a specific domain or all domains.
     * Supported domains: chats, browsers, emails, media, system, gps, ufed, ai, all.
     */
    public Map<String, Object> getPropertyDictionary(String domain) {
        String target = (domain == null || domain.isBlank()) ? "all" : domain.trim().toLowerCase();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("escaping_note", "Campos com dois-pontos (ex: 'Communication:From') DEVEM ser escapados em consultas Lucene com barra invertida: Communication\\:From:*");

        if (target.equals("all")) {
            response.put("domains", FORENSIC_DOMAINS);
            response.put("available_domains", new ArrayList<>(FORENSIC_DOMAINS.keySet()));
            return response;
        }

        if (FORENSIC_DOMAINS.containsKey(target)) {
            response.put("domain", target);
            response.put("properties", FORENSIC_DOMAINS.get(target));
            response.put("available_domains", new ArrayList<>(FORENSIC_DOMAINS.keySet()));
            return response;
        }

        response.put("error", "Domínio forense desconhecido: '" + domain + "'. Domínios válidos são: " + FORENSIC_DOMAINS.keySet());
        response.put("available_domains", new ArrayList<>(FORENSIC_DOMAINS.keySet()));
        return response;
    }

    /**
     * Inspects populated metadata properties in the active case Lucene index for a given category.
     */
    public Map<String, Object> listAvailableProperties(String category) throws IOException {
        checkCaseOpen();

        String catClean = (category != null) ? category.trim() : "";
        String queryStr;
        if (catClean.isEmpty() || catClean.equalsIgnoreCase("all") || catClean.equals("*")) {
            queryStr = "*:*";
        } else {
            queryStr = "category:\"" + catClean.replace("\"", "\\\"") + "\"";
        }

        IPEDSearcher searcher = new IPEDSearcher(ipedSource, queryStr);
        SearchResult res = searcher.search();
        int[] ids = res.getIds();
        int totalFound = (ids != null) ? ids.length : 0;

        int sampleLimit = Math.min(totalFound, 100);
        Map<String, Integer> propCounts = new TreeMap<>();
        Map<String, String> propSamples = new HashMap<>();

        for (int i = 0; i < sampleLimit; i++) {
            int luceneId = ipedSource.getLuceneId(ids[i]);
            if (luceneId < 0) {
                continue;
            }

            Document doc = ipedSource.getReader().document(luceneId);
            for (IndexableField f : doc.getFields()) {
                String name = f.name();
                if (isBlacklistedKey(name)) {
                    continue;
                }
                propCounts.put(name, propCounts.getOrDefault(name, 0) + 1);
                if (!propSamples.containsKey(name)) {
                    String val = doc.get(name);
                    if (val != null && !val.isBlank()) {
                        String cleanSample = val.trim();
                        if (cleanSample.length() > 100) {
                            cleanSample = cleanSample.substring(0, 100) + "...";
                        }
                        propSamples.put(name, cleanSample);
                    }
                }
            }
        }

        List<Map<String, Object>> propList = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : propCounts.entrySet()) {
            String propName = entry.getKey();
            boolean escapeReq = propName.contains(":") || propName.contains(" ") || propName.contains("-");
            String luceneExample = escapeReq
                    ? propName.replace(":", "\\:").replace(" ", "\\ ") + ":*"
                    : propName + ":*";

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", propName);
            item.put("sample_count", entry.getValue());
            item.put("sample_value", propSamples.getOrDefault(propName, ""));
            item.put("escape_required", escapeReq);
            item.put("lucene_example", luceneExample);
            propList.add(item);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("category", catClean.isEmpty() ? "all" : catClean);
        response.put("total_matching_items", totalFound);
        response.put("sampled_items", sampleLimit);
        response.put("distinct_properties_count", propList.size());
        response.put("properties", propList);

        return response;
    }

    private Map<String, Object> sanitizeProperties(Map<String, Object> props) {
        Map<String, Object> basicBlock = new LinkedHashMap<>();
        Map<String, Object> commBlock = new LinkedHashMap<>();
        Map<String, Object> geoBlock = new LinkedHashMap<>();
        Map<String, Object> forensicBlock = new LinkedHashMap<>();
        Map<String, Object> extraBlock = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : props.entrySet()) {
            String key = entry.getKey();
            if (!isAllowedKey(key)) {
                continue;
            }
            Object sanitizedVal = sanitizeValue(entry.getValue());
            if (sanitizedVal == null) {
                continue;
            }

            String lower = key.toLowerCase();

            // 1. Geo
            if (lower.equals("latitude") || lower.equals("longitude") || lower.startsWith("common:geo:")
                    || lower.contains("coordinate") || lower.contains("gps")) {
                geoBlock.put(key, sanitizedVal);
            }
            // 2. Communication
            else if (lower.startsWith("communication:") || lower.startsWith("conversation:")
                    || lower.startsWith("message-") || lower.equals("from") || lower.equals("to")
                    || lower.equals("cc") || lower.equals("bcc") || lower.equals("subject")
                    || lower.equals("groupid") || lower.equals("isgroupmessage")
                    || ((lower.contains("user") || lower.contains("phone") || lower.contains("email") || lower.contains("account"))
                    && !lower.startsWith("ufed:"))) {
                commBlock.put(key, sanitizedVal);
            }
            // 3. Forensic
            else if (lower.equals("hash") || lower.equals("md5") || lower.equals("sha-256") || lower.equals("sha-1")
                    || lower.equals("deleted") || lower.equals("carved") || lower.equals("childpornhashhits")
                    || lower.startsWith("hashdb:") || lower.startsWith("p2p:")) {
                forensicBlock.put(key, sanitizedVal);
            }
            // 4. Basic
            else if (lower.equals("name") || lower.equals("path") || lower.equals("category")
                    || lower.equals("type") || lower.equals("ext") || lower.equals("size")
                    || lower.equals("length") || lower.equals("created") || lower.equals("modified")
                    || lower.equals("accessed") || lower.equals("changed") || lower.equals("isdir")
                    || lower.equals("contenttype") || lower.equals("url") || lower.equals("visitdate")
                    || lower.equals("downloaddate") || lower.equals("totalbytes") || lower.equals("receivedbytes")
                    || lower.equals("localpath")) {
                basicBlock.put(key, sanitizedVal);
            }
            // 5. Extra (image:, video:, audio:, ufed:, etc.)
            else {
                extraBlock.put(key, sanitizedVal);
            }
        }

        Map<String, Object> partitioned = new LinkedHashMap<>();
        if (!basicBlock.isEmpty()) {
            partitioned.put("basic", basicBlock);
        }
        if (!commBlock.isEmpty()) {
            partitioned.put("communication", commBlock);
        }
        if (!geoBlock.isEmpty()) {
            partitioned.put("geo", geoBlock);
        }
        if (!forensicBlock.isEmpty()) {
            partitioned.put("forensic", forensicBlock);
        }
        if (!extraBlock.isEmpty()) {
            partitioned.put("extra", extraBlock);
        }

        return partitioned;
    }

    private static boolean isBlacklistedKey(String key) {
        if (key == null || key.isBlank() || key.startsWith("_")) {
            return true;
        }
        return BLACKLISTED_KEYS.contains(key.toLowerCase());
    }

    private static boolean isAllowedKey(String key) {
        if (isBlacklistedKey(key)) {
            return false;
        }
        String lower = key.toLowerCase();
        for (String prefix : WHITELIST_PREFIXES) {
            if (lower.startsWith(prefix)) {
                return true;
            }
        }
        if (EXPLICIT_ALLOWED_KEYS.contains(lower)) {
            return true;
        }
        return lower.contains("user") || lower.contains("phone") || lower.contains("email")
                || lower.contains("account") || lower.contains("owner") || lower.contains("author")
                || lower.contains("coord") || lower.contains("gps") || lower.contains("exif");
    }

    private static Object sanitizeValue(Object val) {
        if (val == null) {
            return null;
        }
        if (val instanceof String str) {
            String trimmed = str.trim();
            if (trimmed.isEmpty()) {
                return null;
            }
            if (trimmed.length() > 500) {
                return trimmed.substring(0, 500) + "... [truncated]";
            }
            return trimmed;
        } else if (val instanceof List<?> list) {
            if (list.isEmpty()) {
                return null;
            }
            List<Object> cleanList = new ArrayList<>();
            int count = 0;
            for (Object item : list) {
                if (count >= 10) {
                    cleanList.add("... [" + (list.size() - 10) + " more items truncated]");
                    break;
                }
                Object cleanItem = sanitizeValue(item);
                if (cleanItem != null) {
                    cleanList.add(cleanItem);
                    count++;
                }
            }
            return cleanList.isEmpty() ? null : cleanList;
        }
        return val;
    }

    private static Map<String, Object> propDef(String field, String type, String desc, String example, boolean escape) {
        Map<String, Object> def = new LinkedHashMap<>();
        def.put("field", field);
        def.put("type", type);
        def.put("description", desc);
        def.put("lucene_example", example);
        def.put("escape_required", escape);
        return def;
    }

    private static Map<String, List<Map<String, Object>>> initForensicDomains() {
        Map<String, List<Map<String, Object>>> domains = new LinkedHashMap<>();

        // 1. Chats
        domains.put("chats", List.of(
                propDef("Communication:Direction", "string", "Direção do fluxo da mensagem: INCOMING (recebida), OUTGOING (enviada) ou UNKNOWN", "Communication\\:Direction:INCOMING", true),
                propDef("Communication:From", "string", "Remetente da mensagem (número de telefone, identificador ou nome)", "Communication\\:From:*11988887777*", true),
                propDef("Communication:To", "string", "Destinatário da mensagem (número de telefone, identificador ou nome)", "Communication\\:To:*11988887777*", true),
                propDef("Communication:Date", "date", "Data e hora do envio ou recebimento da mensagem", "Communication\\:Date:[2023-01-01 TO 2023-12-31]", true),
                propDef("Communication:Participants", "string", "Lista de participantes associados à mensagem ou chamada", "Communication\\:Participants:*Silva*", true),
                propDef("Conversation:id", "string", "Identificador unívoco do chat ou thread de conversa", "Conversation\\:id:*", true),
                propDef("Conversation:Name", "string", "Nome da conversa, contato ou grupo de bate-papo", "Conversation\\:Name:*Operacao*", true),
                propDef("Conversation:Account", "string", "Identificador da conta local/proprietária que originou o chat", "Conversation\\:Account:*", true),
                propDef("GroupID", "string", "Identificador do grupo de mensagens (WhatsApp, Telegram)", "GroupID:*@g.us", false),
                propDef("isGroupMessage", "boolean", "Indica se a mensagem pertence a um grupo (true ou false)", "isGroupMessage:true", false),
                propDef("Message-Body", "text", "Corpo do texto da mensagem instantânea ou SMS", "Message-Body:*pix*", false)
        ));

        // 2. Browsers
        domains.put("browsers", List.of(
                propDef("url", "string", "URL completa da página web ou recurso", "url:*banco*", false),
                propDef("visitDate", "date", "Data e hora da visita/acesso ao site no histórico do navegador", "visitDate:[2023-01-01 TO 2023-12-31]", false),
                propDef("downloadDate", "date", "Data e hora em que o download de um arquivo foi realizado", "downloadDate:*", false),
                propDef("totalBytes", "integer", "Tamanho total em bytes do arquivo baixado", "totalBytes:>1048576", false),
                propDef("receivedBytes", "integer", "Quantidade de bytes efetivamente recebidos no download", "receivedBytes:>0", false),
                propDef("localPath", "string", "Caminho local no disco para onde o arquivo baixado foi salvo", "localPath:*Downloads*", false)
        ));

        // 3. Emails
        domains.put("emails", List.of(
                propDef("from", "string", "Endereço ou nome do remetente do e-mail", "from:*@governo.gov.br", false),
                propDef("to", "string", "Endereço ou nome do destinatário principal", "to:*@gmail.com", false),
                propDef("cc", "string", "Destinatários em cópia carbono (CC)", "cc:*", false),
                propDef("bcc", "string", "Destinatários em cópia oculta (BCC)", "bcc:*", false),
                propDef("subject", "string", "Assunto do e-mail indexado", "subject:*sigiloso*", false),
                propDef("Message-Subject", "string", "Assunto do e-mail padronizado pelo parser Tika", "Message-Subject:*urgente*", false),
                propDef("Message-IsEmailAttachment", "boolean", "Indica se o arquivo é um anexo extraído de e-mail (true/false)", "Message-IsEmailAttachment:true", false),
                propDef("Message-AttachmentCount", "integer", "Número de arquivos anexados à mensagem de e-mail", "Message-AttachmentCount:>0", false)
        ));

        // 4. Media
        domains.put("media", List.of(
                propDef("image:width", "integer", "Largura da imagem em pixels", "image\\:width:>1920", true),
                propDef("image:height", "integer", "Altura da imagem em pixels", "image\\:height:>1080", true),
                propDef("image:make", "string", "Fabricante da câmera ou dispositivo fotográfico (EXIF)", "image\\:make:Apple", true),
                propDef("image:model", "string", "Modelo do dispositivo fotográfico (EXIF)", "image\\:model:iPhone*", true),
                propDef("video:duration", "string", "Duração do arquivo de vídeo", "video\\:duration:*", true),
                propDef("audio:transcription", "text", "Transcrição fonética/textual do áudio produzida por IA (Whisper/IPED)", "audio\\:transcription:*reunião*", true),
                propDef("audio:transcriptConfidence", "number", "Nível de confiança da transcrição fonética (0.0 a 1.0)", "audio\\:transcriptConfidence:>0.8", true),
                propDef("face_count", "integer", "Número de faces humanas detectadas na imagem ou vídeo", "face_count:>0", false),
                propDef("faceAge:labels", "string", "Classificação etária estimada dos rostos detectados (ex: child, adult)", "faceAge\\:labels:child", true)
        ));

        // 5. System
        domains.put("system", List.of(
                propDef("name", "string", "Nome do arquivo ou item com extensão", "name:*.xlsx", false),
                propDef("path", "string", "Caminho completo de diretório dentro da imagem forense", "path:*Windows*System32*", false),
                propDef("category", "string", "Categoria forense atribuída pelo IPED", "category:\"chat messages\"", false),
                propDef("type", "string", "Tipo MIME do conteúdo identificado por assinatura", "type:application/pdf", false),
                propDef("size", "integer", "Tamanho do arquivo em bytes", "size:[1048576 TO 104857600]", false),
                propDef("created", "date", "Data de criação no sistema de arquivos", "created:[2023-01-01 TO 2023-06-30]", false),
                propDef("modified", "date", "Data da última alteração no sistema de arquivos", "modified:[2023-01-01 TO 2023-06-30]", false),
                propDef("accessed", "date", "Data do último acesso registrado", "accessed:*", false),
                propDef("deleted", "boolean", "Indica se o arquivo estava marcado como deletado no sistema de arquivos", "deleted:true", false),
                propDef("carved", "boolean", "Indica se o arquivo foi recuperado por data carving do espaço não alocado", "carved:true", false),
                propDef("isDir", "boolean", "Indica se o item é um diretório", "isDir:false", false)
        ));

        // 6. GPS
        domains.put("gps", List.of(
                propDef("common:geo:locations", "coordinates", "Localizações geográficas e coordenadas (latitude, longitude, topônimos)", "common\\:geo\\:locations:*", true),
                propDef("latitude", "number", "Latitude decimal em coordenadas WGS84", "latitude:[-23.6 TO -23.5]", false),
                propDef("longitude", "number", "Longitude decimal em coordenadas WGS84", "longitude:[-46.7 TO -46.6]", false),
                propDef("ufed:coordinate_id", "string", "ID de coordenadas geográficas extraído de relatórios Cellebrite UFED", "ufed\\:coordinate_id:*", true)
        ));

        // 7. UFED
        domains.put("ufed", List.of(
                propDef("ufed:EntryName", "string", "Nome da propriedade do dispositivo em extrações Cellebrite UFED (ex: Device Serial, IMEI)", "ufed\\:EntryName:\"Device IMEI\"", true),
                propDef("ufed:EntryValue", "string", "Valor da propriedade do dispositivo na extração UFED", "ufed\\:EntryValue:*", true),
                propDef("ufed:id", "string", "Identificador do registro na extração Cellebrite UFED", "ufed\\:id:*", true),
                propDef("ufed:file_id", "string", "Identificador do arquivo de origem na extração UFED", "ufed\\:file_id:*", true),
                propDef("ufed:jumpTargets", "string", "Alvos de hiperlink e navegação cruzada UFED", "ufed\\:jumpTargets:*", true)
        ));

        // 8. AI
        domains.put("ai", List.of(
                propDef("audio:transcription", "text", "Transcrição textual de mensagens de voz e arquivos de áudio por IA", "audio\\:transcription:*dinheiro*", true),
                propDef("audio:transcriptConfidence", "number", "Nível de confiança da transcrição de áudio por IA", "audio\\:transcriptConfidence:>0.8", true),
                propDef("face_count", "integer", "Quantidade de faces detectadas na imagem por visão computacional", "face_count:>0", false),
                propDef("faceAge:labels", "string", "Predição de faixa etária das faces detectadas (ex: child, teen, adult)", "faceAge\\:labels:*child*", true),
                propDef("childPornHashHits", "integer", "Contagem de correspondências com bancos de hashes de exploração sexual infantil", "childPornHashHits:>0", false),
                propDef("hashDb:status", "string", "Status forense no banco de hashes conhecido (ex: alert, ignore)", "hashDb\\:status:alert", true),
                propDef("hashDb:set", "string", "Nome da base forense de hashes onde houve match", "hashDb\\:set:*", true)
        ));

        return Collections.unmodifiableMap(domains);
    }

    // =========================================================================
    // MULTIMODAL & COMPUTER VISION CAPABILITIES
    // =========================================================================

    /**
     * Retrieves the visual thumbnail for an item as a Base64-encoded JPEG image,
     * scaled down to maxDim (default: 512) preserving aspect ratio.
     */
    public Map<String, Object> getThumbnail(int itemId, int maxDim) throws IOException {
        checkCaseOpen();
        IItem item = ipedSource.getItemByID(itemId);
        if (item == null) {
            throw new IllegalArgumentException("Item com ID " + itemId + " não encontrado no caso ativo.");
        }

        byte[] thumbBytes = item.getThumb();
        BufferedImage image = null;

        if (thumbBytes != null && thumbBytes.length > 0) {
            try (ByteArrayInputStream bais = new ByteArrayInputStream(thumbBytes)) {
                image = ImageIO.read(bais);
            } catch (Exception e) {
                LOGGER.debug("Erro ao decodificar thumbnail em cache do item {}: {}", itemId, e.getMessage());
            }
        }

        // Fallback 1: try reading directly from item stream (if image or document)
        if (image == null) {
            try (InputStream is = item.getBufferedInputStream()) {
                if (is != null) {
                    image = ImageIO.read(is);
                }
            } catch (Exception e) {
                LOGGER.debug("Erro ao ler imagem original do item {}: {}", itemId, e.getMessage());
            }
        }

        // Fallback 2: try viewFile
        if (image == null && item.getViewFile() != null && item.getViewFile().exists()) {
            try {
                image = ImageIO.read(item.getViewFile());
            } catch (Exception e) {
                LOGGER.debug("Erro ao ler viewFile do item {}: {}", itemId, e.getMessage());
            }
        }

        if (image == null) {
            throw new IllegalStateException("O item ID " + itemId + " (" + item.getName() + ") não possui miniatura visual pré-computada nem pôde ser decodificado como imagem.");
        }

        int targetMax = maxDim > 0 ? Math.min(maxDim, 2048) : 512;
        int origW = image.getWidth();
        int origH = image.getHeight();

        // Ensure RGB color space for clean JPEG output without alpha artifacts
        if (origW > targetMax || origH > targetMax) {
            image = ImageUtil.resizeImage(image, targetMax, targetMax, BufferedImage.TYPE_INT_RGB);
        } else if (image.getType() != BufferedImage.TYPE_INT_RGB) {
            BufferedImage rgb = new BufferedImage(origW, origH, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = rgb.createGraphics();
            try {
                g2d.setColor(Color.WHITE);
                g2d.fillRect(0, 0, origW, origH);
                g2d.drawImage(image, 0, 0, null);
            } finally {
                g2d.dispose();
            }
            image = rgb;
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", baos);
        byte[] jpegBytes = baos.toByteArray();
        String base64 = Base64.getEncoder().encodeToString(jpegBytes);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", itemId);
        res.put("name", item.getName() != null ? item.getName() : "");
        res.put("mime_type", "image/jpeg");
        res.put("width", image.getWidth());
        res.put("height", image.getHeight());
        res.put("size_bytes", jpegBytes.length);
        res.put("base64", base64);
        return res;
    }

    /**
     * Searches for visually similar images across the case using IPED's perceptual image hashing.
     */
    public Map<String, Object> searchSimilarImages(int itemId, float minScore, int limit) throws IOException {
        checkCaseOpen();
        IItem refItem = ipedSource.getItemByID(itemId);
        if (refItem == null) {
            throw new IllegalArgumentException("Item de referência com ID " + itemId + " não encontrado.");
        }

        byte[] similarityFeatures = (byte[]) refItem.getExtraAttribute(ImageSimilarityTask.IMAGE_FEATURES);
        if (similarityFeatures == null) {
            LeafReader leafReader = ipedSource.getLeafReader();
            int luceneId = ipedSource.getLuceneId(refItem.getId());
            if (luceneId >= 0) {
                BinaryDocValues similarityFeaturesValues = leafReader.getBinaryDocValues(ImageSimilarityTask.IMAGE_FEATURES);
                BytesRef bytesRef = DocValuesUtil.getBytesRef(similarityFeaturesValues, luceneId);
                if (bytesRef != null && bytesRef.length > 0) {
                    similarityFeatures = bytesRef.bytes;
                    refItem.setExtraAttribute(ImageSimilarityTask.IMAGE_FEATURES, similarityFeatures);
                }
            }
        }

        if (similarityFeatures == null) {
            Map<String, Object> errResult = new LinkedHashMap<>();
            errResult.put("reference_id", itemId);
            errResult.put("error", "O item ID " + itemId + " (" + refItem.getName() + ") não possui vetores de características visuais (image_features) calculados no índice.");
            errResult.put("total_found", 0);
            errResult.put("items", List.of());
            return errResult;
        }

        Query query = new SimilarImagesSearch().getQueryForSimilarImages(refItem);
        if (query == null) {
            Map<String, Object> errResult = new LinkedHashMap<>();
            errResult.put("reference_id", itemId);
            errResult.put("error", "Não foi possível construir a query de similaridade de imagem para o item ID " + itemId);
            errResult.put("total_found", 0);
            errResult.put("items", List.of());
            return errResult;
        }

        IPEDMultiSource multiSource = ipedSource instanceof IPEDMultiSource ? (IPEDMultiSource) ipedSource
                : new IPEDMultiSource(Collections.singletonList(ipedSource));
        IPEDSearcher searcher = new IPEDSearcher(multiSource, query);
        MultiSearchResult multiResult = searcher.multiSearch();
        new ImageSimilarityScorer(ipedSource, multiResult, refItem).score();

        int len = multiResult.getLength();
        float threshold = minScore > 0 ? minScore : 1.0f;
        int maxLimit = limit > 0 ? Math.min(limit, 500) : 50;

        List<Map<String, Object>> matches = new ArrayList<>();
        for (int i = 0; i < len; i++) {
            float score = multiResult.getScore(i);
            if (score >= threshold) {
                IItemId id = multiResult.getItem(i);
                int cId = id.getId();
                Map<String, Object> itemMap = buildSimilarItemEntry(cId, score);
                if (itemMap != null) {
                    matches.add(itemMap);
                }
            }
        }

        matches.sort((a, b) -> Float.compare(((Number) b.get("similarity_score")).floatValue(), ((Number) a.get("similarity_score")).floatValue()));
        List<Map<String, Object>> returnedList = matches.stream().limit(maxLimit).collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reference_id", itemId);
        result.put("reference_name", refItem.getName());
        result.put("min_score", threshold);
        result.put("total_found", matches.size());
        result.put("returned_count", returnedList.size());
        result.put("items", returnedList);
        return result;
    }

    /**
     * Searches for occurrences of the same human face across all case media using deep facial embeddings.
     */
    public Map<String, Object> searchSimilarFaces(int itemId, float minScore, int limit) throws IOException {
        checkCaseOpen();
        IItem refImage = ipedSource.getItemByID(itemId);
        if (refImage == null) {
            throw new IllegalArgumentException("Item de referência com ID " + itemId + " não encontrado.");
        }

        Object faceFeatures = refImage.getExtraAttribute(SimilarFacesSearch.FACE_FEATURES);
        if (faceFeatures == null) {
            LeafReader leafReader = ipedSource.getLeafReader();
            int luceneId = ipedSource.getLuceneId(refImage.getId());
            if (luceneId >= 0) {
                SortedSetDocValues faceValues = leafReader.getSortedSetDocValues(SimilarFacesSearch.FACE_FEATURES);
                if (faceValues != null && faceValues.advanceExact(luceneId)) {
                    List<byte[]> encs = new ArrayList<>();
                    long ord;
                    while ((ord = faceValues.nextOrd()) != SortedSetDocValues.NO_MORE_ORDS) {
                        BytesRef br = faceValues.lookupOrd(ord);
                        encs.add(br.bytes.clone());
                    }
                    if (!encs.isEmpty()) {
                        faceFeatures = encs;
                        refImage.setExtraAttribute(SimilarFacesSearch.FACE_FEATURES, encs);
                    }
                }
            }
        }

        if (faceFeatures == null) {
            Map<String, Object> errResult = new LinkedHashMap<>();
            errResult.put("reference_id", itemId);
            errResult.put("error", "O item ID " + itemId + " (" + refImage.getName() + ") não possui vetores faciais (face_encodings) detectados no índice.");
            errResult.put("total_found", 0);
            errResult.put("items", List.of());
            return errResult;
        }

        SimilarFacesSearch sfs = new SimilarFacesSearch(ipedSource, refImage);
        int targetMinScore = minScore > 0 ? (int) minScore : 50;
        SimilarFacesSearch.setMinScore(targetMinScore);

        MultiSearchResult multiResult = sfs.search();
        int len = multiResult.getLength();
        int maxLimit = limit > 0 ? Math.min(limit, 500) : 50;

        List<Map<String, Object>> matches = new ArrayList<>();
        for (int i = 0; i < len; i++) {
            float score = multiResult.getScore(i);
            if (score >= targetMinScore) {
                IItemId id = multiResult.getItem(i);
                int cId = id.getId();
                Map<String, Object> itemMap = buildSimilarItemEntry(cId, score);
                if (itemMap != null) {
                    matches.add(itemMap);
                }
            }
        }

        matches.sort((a, b) -> Float.compare(((Number) b.get("similarity_score")).floatValue(), ((Number) a.get("similarity_score")).floatValue()));
        List<Map<String, Object>> returnedList = matches.stream().limit(maxLimit).collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reference_id", itemId);
        result.put("reference_name", refImage.getName());
        result.put("min_score", targetMinScore);
        result.put("total_found", matches.size());
        result.put("returned_count", returnedList.size());
        result.put("items", returnedList);
        return result;
    }

    /**
     * Searches for text documents with statistically similar phrasing (MoreLikeThis / term vector analysis).
     */
    public Map<String, Object> searchSimilarDocuments(int itemId, int matchPercent, int limit) throws IOException {
        checkCaseOpen();
        IItem refDoc = ipedSource.getItemByID(itemId);
        if (refDoc == null) {
            throw new IllegalArgumentException("Item de documento de referência com ID " + itemId + " não encontrado.");
        }

        int percent = matchPercent > 0 ? Math.min(matchPercent, 100) : 50;
        int maxLimit = limit > 0 ? Math.min(limit, 500) : 50;

        SimilarDocumentSearch sds = new SimilarDocumentSearch();
        Query query = null;
        try {
            ItemId iItemId = new ItemId(ipedSource.getSourceId(), itemId);
            query = sds.getQueryForSimilarDocs(iItemId, percent, ipedSource);
        } catch (Exception e) {
            LOGGER.debug("Erro ao gerar query de similaridade de documentos para item {}: {}", itemId, e.getMessage());
        }

        if (query == null) {
            Map<String, Object> errResult = new LinkedHashMap<>();
            errResult.put("reference_id", itemId);
            errResult.put("error", "Não foi possível gerar termos representativos para o documento ID " + itemId + " (" + refDoc.getName() + "). O arquivo pode não conter texto indexado ou vetores de termos.");
            errResult.put("total_found", 0);
            errResult.put("items", List.of());
            return errResult;
        }

        IPEDSearcher searcher = new IPEDSearcher(ipedSource, query);
        SearchResult searchResult = searcher.search();
        int[] docIds = searchResult.getIds();

        List<Map<String, Object>> matches = new ArrayList<>();
        if (docIds != null) {
            for (int i = 0; i < docIds.length; i++) {
                int cId = docIds[i];
                if (cId == itemId) {
                    continue;
                }
                float score = searchResult.getScore(i);
                Map<String, Object> itemMap = buildSimilarItemEntry(cId, score);
                if (itemMap != null) {
                    matches.add(itemMap);
                }
            }
        }

        List<Map<String, Object>> returnedList = matches.stream().limit(maxLimit).collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reference_id", itemId);
        result.put("reference_name", refDoc.getName());
        result.put("match_percent", percent);
        result.put("total_found", matches.size());
        result.put("returned_count", returnedList.size());
        result.put("items", returnedList);
        return result;
    }

    private Map<String, Object> buildSimilarItemEntry(int id, float score) {
        int luceneId = ipedSource.getLuceneId(id);
        if (luceneId < 0) {
            return null;
        }
        try {
            Document doc = ipedSource.getReader().document(luceneId);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", id);
            entry.put("name", doc.get("name") != null ? doc.get("name") : "");
            entry.put("path", doc.get("path") != null ? doc.get("path") : "");
            entry.put("category", doc.get("category") != null ? doc.get("category") : "");
            entry.put("type", doc.get("type") != null ? doc.get("type") : "");
            IndexableField sizeField = doc.getField("size");
            entry.put("size", sizeField != null && sizeField.numericValue() != null ? sizeField.numericValue() : 0);
            String hash = doc.get("hash");
            if (hash == null || hash.isBlank()) hash = doc.get("md5");
            if (hash == null || hash.isBlank()) hash = doc.get("sha-256");
            entry.put("hash", hash != null ? hash : "");
            float normScore = score >= 1000.0f ? 100.0f : (Math.round(score * 10.0f) / 10.0f);
            entry.put("similarity_score", normScore);
            entry.put("selected", ipedSource.getBookmarks().isChecked(id));
            return entry;
        } catch (IOException e) {
            LOGGER.debug("Erro ao ler documento lucene para item similar {}: {}", id, e.getMessage());
            return null;
        }
    }

    // =========================================================================
    // AI DETECTIONS DISCOVERY & QUERY
    // =========================================================================

    public static class AiFilterDef {
        public final String id;
        public final String name;
        public final String description;
        public final String query;
        public final String scoreField;

        public AiFilterDef(String id, String name, String description, String query, String scoreField) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.query = query;
            this.scoreField = scoreField;
        }
    }

    private static final Map<String, AiFilterDef> AI_FILTERS = new LinkedHashMap<>();
    static {
        AI_FILTERS.put("weapons", new AiFilterDef(
                "weapons",
                "Armas de Fogo e Munições",
                "Itens com detecção positiva de armas de fogo ou munições por modelo de visão computacional ou categoria.",
                "hasWeapon:true OR category:\"weapons\" OR category:\"armas\"",
                null
        ));
        AI_FILTERS.put("drugs", new AiFilterDef(
                "drugs",
                "Drogas e Entorpecentes",
                "Itens identificados como substâncias ilícitas ou materiais associados ao tráfico de drogas.",
                "hasDrug:true OR category:\"drugs\" OR category:\"drogas\"",
                null
        ));
        AI_FILTERS.put("nudity", new AiFilterDef(
                "nudity",
                "Nudez e Conteúdo Adulto",
                "Imagens e vídeos detectados com pontuação de conteúdo explícito pelo modelo Deep Image Evaluator (DIE).",
                "isNudity:true OR category:\"nudity\" OR nudityScore:[0.5 TO 1.0]",
                "nudityScore"
        ));
        AI_FILTERS.put("faces", new AiFilterDef(
                "faces",
                "Faces Humanas Detectadas",
                "Imagens e quadros de vídeo onde foram localizadas e vetorizadas faces humanas pelo FaceNet/dlib.",
                "hasFace:true OR face_count:[1 TO *]",
                "face_count"
        ));
        AI_FILTERS.put("audio_transcripts", new AiFilterDef(
                "audio_transcripts",
                "Transcrições de Áudio (IA)",
                "Áudios, mensagens de voz e vídeos com transcrição fonética gerada por inteligência artificial (Whisper/Vosk).",
                "hasAudioTranscript:true OR audio\\:transcription:*",
                "audio:transcriptConfidence"
        ));
        AI_FILTERS.put("csam", new AiFilterDef(
                "csam",
                "Exploração Sexual Infantil (CSAM)",
                "Arquivos com correspondência em bases de hashes forenses conhecidas de exploração infantil.",
                "childPornHashHits:[1 TO *] OR hashDb\\:status:alert",
                "childPornHashHits"
        ));
        AI_FILTERS.put("ocr", new AiFilterDef(
                "ocr",
                "Documentos com OCR Reconhecido",
                "Imagens digitalizadas e documentos onde textos gráficos foram extraídos via Tesseract OCR.",
                "hasOCR:true OR hasOcrText:true",
                null
        ));
    }

    /**
     * Lists available AI detection categories in the case with counts.
     */
    public Map<String, Object> listAiFilters() {
        checkCaseOpen();
        List<Map<String, Object>> filterList = new ArrayList<>();
        for (AiFilterDef def : AI_FILTERS.values()) {
            Map<String, Object> fMap = new LinkedHashMap<>();
            fMap.put("filter_type", def.id);
            fMap.put("name", def.name);
            fMap.put("description", def.description);
            fMap.put("lucene_query", def.query);
            int count = 0;
            try {
                IPEDSearcher s = new IPEDSearcher(ipedSource, def.query);
                count = s.search().getLength();
            } catch (Exception e) {
                LOGGER.debug("Erro ao consultar contagem para filtro AI {}: {}", def.id, e.getMessage());
            }
            fMap.put("count", count);
            filterList.add(fMap);
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("total_filters", filterList.size());
        res.put("filters", filterList);
        return res;
    }

    /**
     * Queries evidence items detected by automated AI and machine learning models.
     */
    public Map<String, Object> queryAiDetections(String filterType, Float minScore, int limit, int offset) throws IOException {
        checkCaseOpen();
        if (filterType == null || filterType.isBlank()) {
            throw new IllegalArgumentException("filter_type é obrigatório. Tipos disponíveis: " + String.join(", ", AI_FILTERS.keySet()));
        }
        String cleanType = filterType.trim().toLowerCase();
        AiFilterDef def = AI_FILTERS.get(cleanType);
        if (def == null) {
            throw new IllegalArgumentException("Tipo de filtro AI '" + filterType + "' inválido. Tipos disponíveis: " + String.join(", ", AI_FILTERS.keySet()));
        }

        String effectiveQuery = def.query;
        if (minScore != null && minScore > 0 && def.scoreField != null) {
            if ("nudityScore".equalsIgnoreCase(def.scoreField)) {
                effectiveQuery = "nudityScore:[" + minScore + " TO 1.0]";
            } else if ("audio:transcriptConfidence".equalsIgnoreCase(def.scoreField)) {
                effectiveQuery = "audio\\:transcriptConfidence:[" + minScore + " TO 1.0]";
            } else if ("face_count".equalsIgnoreCase(def.scoreField)) {
                effectiveQuery = "face_count:[" + (int) minScore.floatValue() + " TO *]";
            } else if ("childPornHashHits".equalsIgnoreCase(def.scoreField)) {
                effectiveQuery = "childPornHashHits:[" + (int) minScore.floatValue() + " TO *]";
            }
        }

        int maxLimit = limit > 0 ? Math.min(limit, 500) : 50;
        int safeOffset = Math.max(0, offset);

        SearchResult sRes;
        try {
            IPEDSearcher searcher = new IPEDSearcher(ipedSource, effectiveQuery);
            sRes = searcher.search();
        } catch (Exception e) {
            LOGGER.debug("Filtro AI '{}' com query '{}' gerou exceção na pesquisa: {}", def.id, effectiveQuery, e.getMessage());
            Map<String, Object> emptyRes = new LinkedHashMap<>();
            emptyRes.put("filter_type", def.id);
            emptyRes.put("filter_name", def.name);
            emptyRes.put("query_applied", effectiveQuery);
            emptyRes.put("min_score", minScore);
            emptyRes.put("total_found", 0);
            emptyRes.put("offset", safeOffset);
            emptyRes.put("returned_count", 0);
            emptyRes.put("items", List.of());
            return emptyRes;
        }

        int totalHits = sRes.getLength();
        int[] docIds = sRes.getIds();

        List<Map<String, Object>> items = new ArrayList<>();
        if (docIds != null && docIds.length > safeOffset) {
            int end = Math.min(safeOffset + maxLimit, docIds.length);
            for (int i = safeOffset; i < end; i++) {
                int cId = docIds[i];
                int luceneId = ipedSource.getLuceneId(cId);
                if (luceneId < 0) continue;
                Document doc = ipedSource.getReader().document(luceneId);
                Map<String, Object> itemMap = buildFileEntry(cId, doc);
                if (doc.get("nudityScore") != null) {
                    itemMap.put("nudity_score", doc.get("nudityScore"));
                }
                if (doc.get("face_count") != null) {
                    itemMap.put("face_count", doc.get("face_count"));
                }
                if (doc.get("audio:transcription") != null) {
                    itemMap.put("audio_transcription", doc.get("audio:transcription"));
                }
                items.add(itemMap);
            }
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("filter_type", def.id);
        res.put("filter_name", def.name);
        res.put("query_applied", effectiveQuery);
        res.put("min_score", minScore);
        res.put("total_found", totalHits);
        res.put("offset", safeOffset);
        res.put("returned_count", items.size());
        res.put("items", items);
        return res;
    }
}

