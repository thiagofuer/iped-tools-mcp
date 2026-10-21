package br.com.ipedtools.mcp.test;

import java.util.List;
import java.util.Map;

import br.com.ipedtools.mcp.service.IpedCoreService;
import iped.engine.data.IPEDSource;
import iped.engine.search.IPEDSearcher;

/**
 * Discovers reference items in whatever IPED case is open, so integration tests do not depend on
 * item ids of one specific case. Results are cached per case path.
 */
public final class TestItems {

    private static final int MAX_CANDIDATES = 300;
    private static final String IMAGE_QUERY = "contentType:\"image/jpeg\" OR contentType:\"image/png\"";

    private static String cachedCase;
    private static Integer imageWithThumbnail;
    private static Integer imageWithFeatures;
    private static int[] childAndParent;

    private TestItems() {}

    /** An image whose thumbnail can be rendered by {@code getThumbnail}, or null if the case has none. */
    public static synchronized Integer imageWithThumbnail() {
        resetIfCaseChanged();
        if (imageWithThumbnail == null) {
            IpedCoreService service = IpedCoreService.getInstance();
            for (int id : candidates(IMAGE_QUERY)) {
                try {
                    service.getThumbnail(id, 64);
                    imageWithThumbnail = id;
                    break;
                } catch (Exception ignored) {
                }
            }
        }
        return imageWithThumbnail;
    }

    /** An image with similarity features ({@code searchSimilarImages} returns no error), or null if none. */
    public static synchronized Integer imageWithSimilarityFeatures() {
        resetIfCaseChanged();
        if (imageWithFeatures == null) {
            IpedCoreService service = IpedCoreService.getInstance();
            for (int id : candidates(IMAGE_QUERY)) {
                try {
                    Map<String, Object> res = service.searchSimilarImages(id, 1.0f, 1);
                    if (!res.containsKey("error")) {
                        service.getThumbnail(id, 64);
                        imageWithFeatures = id;
                        break;
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return imageWithFeatures;
    }

    /**
     * A sub-item and its parent, where the child is listed among the first children returned by
     * {@code getItemRelations(parent)}. Returns {@code [childId, parentId]} or null if not found.
     */
    @SuppressWarnings("unchecked")
    public static synchronized int[] childAndParent() {
        resetIfCaseChanged();
        if (childAndParent == null) {
            IpedCoreService service = IpedCoreService.getInstance();
            IPEDSource source = service.getSource();
            for (int id : candidates("subitem:true")) {
                try {
                    int parentId = source.getParentId(id);
                    if (parentId <= 0 || parentId == id) continue;
                    Map<String, Object> rel = service.getItemRelations(parentId);
                    List<Map<String, Object>> children = (List<Map<String, Object>>) rel.get("children");
                    if (children != null && !children.isEmpty()) {
                        childAndParent = new int[] {(int) children.getFirst().get("id"), parentId};
                        break;
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return childAndParent;
    }

    private static int[] candidates(String query) {
        try {
            int[] ids = new IPEDSearcher(IpedCoreService.getInstance().getSource(), query).search().getIds();
            if (ids == null) return new int[0];
            return ids.length > MAX_CANDIDATES ? java.util.Arrays.copyOf(ids, MAX_CANDIDATES) : ids;
        } catch (Exception e) {
            return new int[0];
        }
    }

    private static void resetIfCaseChanged() {
        String current = String.valueOf(TestCaseResolver.getPrimaryCaseDir());
        if (!current.equals(cachedCase)) {
            cachedCase = current;
            imageWithThumbnail = null;
            imageWithFeatures = null;
            childAndParent = null;
        }
    }
}
