package client.cn.kafei.simukraft.client.guidebook;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import common.cn.kafei.simukraft.SimuKraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 从 assets/simukraft/guidebook 读取书页。
 */
@OnlyIn(Dist.CLIENT)
public final class GuideBookLoader {
    private static final String GUIDEBOOK_PATH = "guidebook";
    private static final String INDEX_FILE = "index.json";
    private static final Map<String, GuideBookPage> PAGES = new ConcurrentHashMap<>();
    private static String indexPageId = "directory";

    private GuideBookLoader() {
    }

    public static int reload(ResourceManager resourceManager) {
        PAGES.clear();
        GuideBookImageCache.clear();
        Map<String, GuideBookPage> loadedPages = loadPages(resourceManager);
        PAGES.putAll(loadedPages);
        SimuKraft.LOGGER.info("Reloaded {} guidebook chapters", PAGES.size());
        return PAGES.size();
    }

    public static void loadIfEmpty(ResourceManager resourceManager) {
        if (PAGES.isEmpty()) {
            PAGES.putAll(loadPages(resourceManager));
            SimuKraft.LOGGER.info("Loaded {} guidebook chapters", PAGES.size());
        }
    }

    public static boolean isLoaded() {
        return !PAGES.isEmpty();
    }

    public static Optional<GuideBookPage> getPage(String id) {
        return Optional.ofNullable(PAGES.get(id));
    }

    public static GuideBookPage getIndexPage() {
        GuideBookPage index = PAGES.get(indexPageId);
        if (index != null) {
            return index;
        }
        return PAGES.values().stream().filter(GuideBookPage::isDirectory).findFirst().orElse(null);
    }

    public static Collection<GuideBookPage> getAllPages() {
        return Collections.unmodifiableCollection(PAGES.values());
    }

    public static List<GuideBookPage> getChildPages(String parentId) {
        List<GuideBookPage> children = new ArrayList<>();
        for (GuideBookPage page : PAGES.values()) {
            if (parentId.equals(page.getParentId())) {
                children.add(page);
            }
        }
        children.sort(Comparator.comparingInt(GuideBookPage::getTabIndex));
        return children;
    }

    private static Map<String, GuideBookPage> loadPages(ResourceManager resourceManager) {
        Map<String, GuideBookPage> loadedPages = new HashMap<>();
        ResourceLocation indexLocation = ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, GUIDEBOOK_PATH + "/" + INDEX_FILE);
        try {
            var resource = resourceManager.getResource(indexLocation);
            if (resource.isPresent()) {
                try (InputStreamReader reader = new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8)) {
                    JsonObject indexJson = JsonParser.parseReader(reader).getAsJsonObject();
                    if (indexJson.has("index_page")) {
                        indexPageId = indexJson.get("index_page").getAsString();
                    }
                    if (indexJson.has("pages")) {
                        for (var pageEntry : indexJson.getAsJsonArray("pages")) {
                            loadPageFile(resourceManager, pageEntry.getAsString(), loadedPages);
                        }
                    }
                }
            }
        } catch (Exception exception) {
            SimuKraft.LOGGER.error("Failed to load guidebook index", exception);
        }
        if (loadedPages.isEmpty()) {
            loadAllPages(resourceManager, loadedPages);
        }
        return loadedPages;
    }

    private static void loadAllPages(ResourceManager resourceManager, Map<String, GuideBookPage> loadedPages) {
        for (var entry : resourceManager.listResources(GUIDEBOOK_PATH, path -> path.getPath().endsWith(".json")).entrySet()) {
            String path = entry.getKey().getPath();
            if (path.endsWith(".json") && !path.endsWith(INDEX_FILE) && !path.contains("/lang/")) {
                loadPageFile(resourceManager, path.substring(path.lastIndexOf('/') + 1), loadedPages);
            }
        }
    }

    private static void loadPageFile(ResourceManager resourceManager, String fileName, Map<String, GuideBookPage> loadedPages) {
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, GUIDEBOOK_PATH + "/" + fileName);
        var resource = resourceManager.getResource(location);
        if (resource.isEmpty()) {
            SimuKraft.LOGGER.warn("Guidebook page missing: {}", location);
            return;
        }
        try (InputStreamReader reader = new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            String pageId = fileName.replace(".json", "");
            loadedPages.put(pageId, GuideBookPage.fromJson(pageId, json));
        } catch (Exception exception) {
            SimuKraft.LOGGER.error("Failed to load guidebook page {}", fileName, exception);
        }
    }
}
