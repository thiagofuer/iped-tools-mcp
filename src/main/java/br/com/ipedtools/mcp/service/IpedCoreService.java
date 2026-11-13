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
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.SortedSetDocValues;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryVisitor;
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
import iped.engine.task.index.IndexItem;
import iped.utils.LocalizedFormat;
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

    private static final Set<String> BLACKLISTED_KEYS = Set.of(
            "content", "treenode", "offset", "timeout", "thumbnail", "thumbnailbase64",
            "subitem", "subitemid", "mftsequence", "filesystemid", "metaaddress",
            "parenttrackid", "containertrackid", "itemvirtualidentifier", "parentvirtualidentifier",
            "trackid", "timeeventords", "timeeventgroups", "x-reader", "ipedembeddefolder"
    );

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
    public synchronized void openCase(File caseDir) {
        openCaseInternal(caseDir, true);
    }

    private synchronized void openCaseInternal(File caseDir, boolean persistState) {
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

        // The QueryBuilder caches numeric PointsConfig per case instance the first time a query is parsed,
        // which happens inside the IPEDSource constructor before it loads metadataTypes.txt. Loading the
        // types first makes that cache include numeric extra properties (nudityScore, ocrCharCount...),
        // otherwise their range queries become textual and match nothing (issue #7).
        try {
            IndexItem.loadMetadataTypes(new File(caseDir, "iped" + File.separator + "conf"));
        } catch (Exception e) {
            LOGGER.warn("Falha ao pré-carregar metadataTypes.txt do caso: {}", e.getMessage());
        }

        LOGGER.info("Inicializando IPEDSource e abrindo leitores Lucene...");
        this.ipedSource = new IPEDSource(caseDir);

        LOGGER.info("Caso carregado com sucesso. Total de documentos no leitor: {}",
                this.ipedSource.getReader().numDocs());

        if (persistState) {
            try {
                if (ACTIVE_CASE_FILE.getParentFile() != null) {
                    java.nio.file.Files.createDirectories(ACTIVE_CASE_FILE.getParentFile().toPath());
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
            if (rootIds != null) {
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

    // =========================================================================
    // SEARCH OPERATIONS
    // =========================================================================

    /**
     * Executes a Lucene query against the open IPED case index.
     */
    public Map<String, Object> executeSearch(String queryText, int limit) throws IOException {
        checkCaseOpen();

        int maxLimit = Math.clamp(limit, 1, 100);
        String sanitizedQuery = prepareQuery(queryText);

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

        List<Map<String, Object>> warnings = unknownPropertyWarnings(searcher.getQuery());
        if (totalFound == 0 && !NumericRangeSupport.extractRanges(sanitizedQuery).isEmpty()) {
            Set<Object> alreadyWarned = new HashSet<>();
            warnings.forEach(w -> alreadyWarned.add(w.get("property")));
            for (Map<String, Object> w : NumericRangeSupport.zeroResultWarnings(sanitizedQuery,
                    ipedSource.getReader(), field -> IndexItem.getMetadataTypes().get(field))) {
                if (!alreadyWarned.contains(w.get("property"))) {
                    warnings.add(w);
                }
            }
        }
        if (!warnings.isEmpty()) {
            response.put("warnings", warnings);
        }

        return response;
    }

    /** Field names referenced by a parsed query, including those under NOT clauses. */
    static Set<String> queryFields(Query query) {
        Set<String> fields = new LinkedHashSet<>();
        if (query == null) {
            return fields;
        }
        query.visit(new QueryVisitor() {
            @Override
            public boolean acceptField(String field) {
                if (field != null && !field.isEmpty()) {
                    fields.add(field);
                }
                return true;
            }

            @Override
            public QueryVisitor getSubVisitor(BooleanClause.Occur occur, Query parent) {
                return this;
            }
        });
        return fields;
    }

    /**
     * One warning per field of the query that does not exist in the case index, so that an empty or partial result
     * is not read as absence of evidence.
     */
    private List<Map<String, Object>> unknownPropertyWarnings(Query query) throws IOException {
        List<Map<String, Object>> warnings = new ArrayList<>();
        PropertyCatalogService catalog = PropertyCatalogService.getInstance();
        for (String field : queryFields(query)) {
            if (catalog.fieldExists(field)) {
                continue;
            }
            Map<String, Object> w = new LinkedHashMap<>();
            w.put("type", "property_not_in_case");
            w.put("property", field);
            w.put("message", "A propriedade '" + field + "' não existe neste caso; essa parte da consulta não encontra "
                    + "nada, o que não significa ausência de evidência. Use list_properties para descobrir as "
                    + "propriedades existentes.");
            List<String> similar = PropertyCatalogService.similarNames(field, catalog.caseFields().keySet());
            if (!similar.isEmpty()) {
                w.put("similar", similar);
            }
            warnings.add(w);
        }
        return warnings;
    }

    /**
     * Applies the query preparation used by {@code search_documents}: blank becomes {@code *:*}, slashes are
     * escaped and dot decimals of Float/Double ranges are converted to the case locale.
     */
    static String prepareQuery(String queryText) {
        String q = (queryText == null || queryText.isBlank()) ? "*:*" : queryText.replaceAll("/", "\\\\/");
        return NumericRangeSupport.normalizeDecimalSeparators(q,
                field -> IndexItem.getMetadataTypes().get(field), LocalizedFormat.getNumberInstance());
    }

    /**
     * Runs a query exactly as {@code search_documents} does and returns the Lucene doc IDs of every matching item.
     */
    public int[] searchLuceneIds(String queryText) throws IOException {
        checkCaseOpen();
        SearchResult result = new IPEDSearcher(ipedSource, prepareQuery(queryText)).search();
        int[] ids = result.getIds();
        if (ids == null) {
            return new int[0];
        }
        int[] luceneIds = new int[ids.length];
        int n = 0;
        for (int id : ids) {
            int luceneId = ipedSource.getLuceneId(id);
            if (luceneId >= 0) {
                luceneIds[n++] = luceneId;
            }
        }
        return Arrays.copyOf(luceneIds, n);
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
        return getDocumentMetadata(itemId, false, null);
    }

    /**
     * Retrieves metadata for a single item ID, supporting full-fidelity raw extraction
     * and surgical key filtering.
     */
    public Map<String, Object> getDocumentMetadata(int itemId, boolean raw, List<String> keys) throws IOException {
        return getDocumentMetadata(itemId, raw, keys, null);
    }

    /**
     * Retrieves metadata for a single item ID. Standard mode groups properties by catalog group; {@code groups}
     * (canonical group names) keeps only those groups, in both modes.
     */
    public Map<String, Object> getDocumentMetadata(int itemId, boolean raw, List<String> keys, Set<String> groups) throws IOException {
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

        Map<String, Object> finalProps;
        if (raw) {
            Map<String, Object> rawFiltered = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : rawProps.entrySet()) {
                String k = entry.getKey();
                if (isBlacklistedKey(k)) {
                    continue;
                }
                if (keys != null && !keys.isEmpty() && !matchesAnyKeyPattern(k, keys)) {
                    continue;
                }
                if (groups != null && !groups.isEmpty() && !groups.contains(PropertyCatalogService.group(k))) {
                    continue;
                }
                rawFiltered.put(k, entry.getValue());
            }
            finalProps = rawFiltered;
        } else {
            Map<String, Object> candidateProps = rawProps;
            if (keys != null && !keys.isEmpty()) {
                candidateProps = new LinkedHashMap<>();
                for (Map.Entry<String, Object> entry : rawProps.entrySet()) {
                    if (matchesAnyKeyPattern(entry.getKey(), keys)) {
                        candidateProps.put(entry.getKey(), entry.getValue());
                    }
                }
            }
            finalProps = sanitizeProperties(candidateProps, groups);
        }

        List<String> bookmarks = ipedSource.getBookmarks().getBookmarkList(itemId);
        boolean selected = ipedSource.getBookmarks().isChecked(itemId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", itemId);
        result.put("source", sourceId);
        result.put("selected", selected);
        result.put("bookmarks", bookmarks != null ? bookmarks : List.of());
        if (raw) {
            result.put("raw", true);
        }
        result.put("properties", finalProps);

        return result;
    }

    /**
     * Retrieves metadata in batch for a list of document IDs with raw and key filtering options.
     */
    public List<Map<String, Object>> getDocumentMetadataBatch(List<Integer> itemIds, boolean raw, List<String> keys) {
        return getDocumentMetadataBatch(itemIds, raw, keys, null);
    }

    /**
     * Retrieves metadata in batch, optionally restricted to catalog groups (e.g. ["AI", "Image"]).
     */
    public List<Map<String, Object>> getDocumentMetadataBatch(List<Integer> itemIds, boolean raw, List<String> keys,
            List<String> groups) {
        checkCaseOpen();
        List<Map<String, Object>> list = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        Set<String> groupSet = resolveGroups(groups, unknown);
        if (!unknown.isEmpty()) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("error", "Grupo(s) desconhecido(s): " + unknown);
            err.put("available_groups", PropertyGroups.names());
            list.add(err);
            return list;
        }
        if (itemIds == null || itemIds.isEmpty()) {
            return list;
        }

        for (Integer id : itemIds) {
            try {
                list.add(getDocumentMetadata(id, raw, keys, groupSet));
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
        int safeMaxChars = Math.clamp(maxChars, 100, 6000);

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
            if (childIds != null) {
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
                if (dupIds != null) {
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

        int maxLimit = Math.clamp(limit > 0 ? limit : 100, 1, 1000);
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
                int candLuceneId = safeGetLuceneId(candId);
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

                if (candIds != null) {
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
            }

            if (!isRoot && targetFolderId == null) {
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

            if (allDescendantIds != null) {
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

        int maxLimit = Math.clamp(limit <= 0 ? 50 : limit, 1, 200);
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
            int maxPool = Math.max(maxLimit * 4, 300);
            int candidatePoolSize = Math.min(totalDocsFound, maxPool);
            for (int i = 0; i < candidatePoolSize; i++) {
                int id = ids[i];
                int luceneId = ipedSource.getLuceneId(id);
                if (luceneId < 0) continue;

                Document doc = ipedSource.getReader().document(luceneId);
                String docCat = doc.get("category") != null ? doc.get("category") : "";
                String docName = doc.get("name") != null ? doc.get("name") : "";
                String docPath = doc.get("path") != null ? doc.get("path") : "";
                String summary = extractEventSummary(doc);

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

    private String extractEventSummary(Document doc) {
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

    private int safeGetLuceneId(int id) {
        if (ipedSource == null || id < 0) {
            return -1;
        }
        try {
            return ipedSource.getLuceneId(id);
        } catch (Exception e) {
            return -1;
        }
    }

    // =========================================================================
    // COMMUNICATIONS GRAPH & CONTACT ANALYTICS
    // =========================================================================

    /** Optional system property with the default region (ISO 3166, e.g. BR) for phones without country code. */
    static final String PHONE_REGION_PROPERTY = "iped.mcp.phone.region";

    private CommunicationEventExtractor communicationExtractor(String channel, String startDate, String endDate) {
        CommunicationAnalytics.EventFilter filter = CommunicationAnalytics.EventFilter.of(channel, startDate, endDate,
                s -> normalizeIsoDate(s, false), s -> normalizeIsoDate(s, true));
        String region = System.getProperty(PHONE_REGION_PROPERTY);
        return new CommunicationEventExtractor(ipedSource, filter, region, s -> normalizeIsoDate(s, false));
    }

    /** Number of items carrying Communication:From or Communication:To (one communication event per item). */
    int countCommunicationEventItems() throws IOException {
        checkCaseOpen();
        return communicationExtractor(null, null, null).countEventItems();
    }

    public Map<String, Object> getTopContacts(int limit) throws IOException {
        return getTopContacts(limit, null, null, null);
    }

    /**
     * Ranks the interlocutors of events with explicit direction (sender when incoming, recipients when outgoing).
     * Events without explicit direction are reported in {@code unknown_direction_total} and {@code group_activity}.
     *
     * @param limit Maximum number of top contacts to return (default 20, max 200)
     * @param channel Optional channel filter (e.g. Viber, WhatsApp)
     * @param startDate Optional inclusive ISO-8601 start
     * @param endDate Optional inclusive ISO-8601 end
     */
    public Map<String, Object> getTopContacts(int limit, String channel, String startDate, String endDate) throws IOException {
        checkCaseOpen();
        CommunicationEventExtractor extractor = communicationExtractor(channel, startDate, endDate);
        CommunicationAnalytics.ContactAggregator aggregator = new CommunicationAnalytics.ContactAggregator();
        CommunicationAnalytics.Diagnostics diagnostics = new CommunicationAnalytics.Diagnostics();
        extractor.forEach(aggregator, diagnostics);

        Map<String, Object> response = aggregator.result(limit);
        response.put("filters", CommunicationAnalytics.EventFilter.of(channel, startDate, endDate, null, null).describe());
        response.put("diagnostics", diagnostics.toMap());
        return response;
    }

    public Map<String, Object> getCommunicationsGraph(String focalContact, int minInteractions, int limitEdges) throws IOException {
        return getCommunicationsGraph(focalContact, minInteractions, limitEdges, null, null, null, false);
    }

    /**
     * Assembles typed nodes (ACCOUNT, GROUP, LOCAL_ENDPOINT) and weighted directed edges from the indexed
     * communication properties, without assuming who owns the device.
     */
    public Map<String, Object> getCommunicationsGraph(String focalContact, int minInteractions, int limitEdges,
                                                      String channel, String startDate, String endDate,
                                                      boolean includeMermaid) throws IOException {
        checkCaseOpen();
        CommunicationEventExtractor extractor = communicationExtractor(channel, startDate, endDate);
        CommunicationAnalytics.GraphAggregator aggregator = new CommunicationAnalytics.GraphAggregator();
        CommunicationAnalytics.Diagnostics diagnostics = new CommunicationAnalytics.Diagnostics();
        extractor.forEach(aggregator, diagnostics);

        Map<String, Object> response = aggregator.result(focalContact, minInteractions, limitEdges, includeMermaid);
        response.put("filters", CommunicationAnalytics.EventFilter.of(channel, startDate, endDate, null, null).describe());
        response.put("diagnostics", diagnostics.toMap());
        return response;
    }

    /**
     * Details how two contacts relate: totals, channels, directions, monthly histogram, shared groups and evidence.
     * Ambiguous references are returned as candidates instead of being guessed.
     */
    public Map<String, Object> getContactRelationship(String contactA, String contactB, String channel,
                                                      String startDate, String endDate, int evidenceLimit) throws IOException {
        checkCaseOpen();
        if (contactA == null || contactA.isBlank() || contactB == null || contactB.isBlank()) {
            return Map.of("error", "Os parâmetros 'contact_a' e 'contact_b' são obrigatórios.");
        }
        CommunicationEventExtractor extractor = communicationExtractor(channel, startDate, endDate);
        CommunicationAnalytics.Diagnostics diagnostics = new CommunicationAnalytics.Diagnostics();
        Map<String, Object> response = CommunicationAnalytics.relationship(extractor, contactA, contactB,
                evidenceLimit, extractor.context(), diagnostics);
        response.put("filters", CommunicationAnalytics.EventFilter.of(channel, startDate, endDate, null, null).describe());
        response.put("diagnostics", diagnostics.toMap());
        return response;
    }

    // =========================================================================
    // METADATA GROUPING
    // =========================================================================

    /**
     * Groups properties by the catalog groups ({@link PropertyGroups}), dropping internal engine fields and applying
     * the token-safety limits of {@link #sanitizeValue(Object)}. With {@code groups}, only those groups are kept.
     */
    static Map<String, Object> sanitizeProperties(Map<String, Object> props, Set<String> groups) {
        Map<String, Map<String, Object>> byGroup = new LinkedHashMap<>();
        for (String g : PropertyGroups.names()) {
            byGroup.put(g, new LinkedHashMap<>());
        }
        for (Map.Entry<String, Object> entry : props.entrySet()) {
            String key = entry.getKey();
            if (isBlacklistedKey(key) || PropertyCatalogService.isInternal(key)) {
                continue;
            }
            String group = PropertyCatalogService.group(key);
            if (groups != null && !groups.isEmpty() && !groups.contains(group)) {
                continue;
            }
            Object sanitizedVal = sanitizeValue(entry.getValue());
            if (sanitizedVal != null) {
                byGroup.computeIfAbsent(group, g -> new LinkedHashMap<>()).put(key, sanitizedVal);
            }
        }
        Map<String, Object> partitioned = new LinkedHashMap<>();
        byGroup.forEach((g, m) -> {
            if (!m.isEmpty()) {
                partitioned.put(g, m);
            }
        });
        return partitioned;
    }

    /** Resolves group names given by the LLM (case-insensitive); unknown names are reported in {@code unknown}. */
    static Set<String> resolveGroups(List<String> groups, List<String> unknown) {
        if (groups == null || groups.isEmpty()) {
            return null;
        }
        Set<String> resolved = new LinkedHashSet<>();
        for (String g : groups) {
            String r = PropertyGroups.resolve(g);
            if (r != null) {
                resolved.add(r);
            } else if (g != null && !g.isBlank()) {
                unknown.add(g);
            }
        }
        return resolved;
    }

    public static boolean matchesAnyKeyPattern(String key, List<String> patterns) {
        if (patterns == null || patterns.isEmpty()) {
            return true;
        }
        if (key == null) {
            return false;
        }
        for (String pattern : patterns) {
            if (pattern == null || pattern.isBlank()) {
                continue;
            }
            String regex = "^" + Pattern.quote(pattern.trim()).replace("*", "\\E.*\\Q") + "$";
            if (Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(key).matches()) {
                return true;
            }
        }
        return false;
    }

    static boolean isBlacklistedKey(String key) {
        if (key == null || key.isBlank() || key.startsWith("_")) {
            return true;
        }
        return BLACKLISTED_KEYS.contains(key.toLowerCase());
    }

    private static Object sanitizeValue(Object val) {
        return switch (val) {
            case null -> null;
            case String str -> {
                String trimmed = str.trim();
                if (trimmed.isEmpty()) {
                    yield null;
                }
                if (trimmed.length() > 500) {
                    yield trimmed.substring(0, 500) + "... [truncated]";
                }
                yield trimmed;
            }
            case List<?> list -> {
                if (list.isEmpty()) {
                    yield null;
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
                yield cleanList.isEmpty() ? null : cleanList;
            }
            default -> val;
        };
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
}
