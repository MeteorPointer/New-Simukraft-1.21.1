package client.cn.kafei.simukraft.client.guidebook;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import common.cn.kafei.simukraft.SimuKraft;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
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
    private static String loadedLang = "";

    private GuideBookLoader() {
    }

    public static int reload(ResourceManager resourceManager) {
        PAGES.clear();
        GuideBookImageCache.clear();
        loadedLang = currentLang();
        Map<String, GuideBookPage> loadedPages = loadPages(resourceManager);
        PAGES.putAll(loadedPages);
        SimuKraft.LOGGER.info("Reloaded {} guidebook chapters for {}", PAGES.size(), loadedLang);
        return PAGES.size();
    }

    public static void loadIfEmpty(ResourceManager resourceManager) {
        String lang = currentLang();
        if (PAGES.isEmpty() || !lang.equals(loadedLang)) {
            reload(resourceManager);
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
        String langFolder = GUIDEBOOK_PATH + "/" + currentLang();
        for (var entry : resourceManager.listResources(langFolder, path -> path.getPath().endsWith(".md")).entrySet()) {
            String path = entry.getKey().getPath();
            loadPageFile(resourceManager, path.substring(path.lastIndexOf('/') + 1), loadedPages);
        }
        if (!loadedPages.isEmpty()) {
            return;
        }
        for (var entry : resourceManager.listResources(GUIDEBOOK_PATH, path -> {
            String filePath = path.getPath();
            return filePath.endsWith(".json") || filePath.endsWith(".md");
        }).entrySet()) {
            String path = entry.getKey().getPath();
            if (path.endsWith(INDEX_FILE) || path.contains("/lang/")) {
                continue;
            }
            loadPageFile(resourceManager, path.substring(path.lastIndexOf('/') + 1), loadedPages);
        }
    }

    private static void loadPageFile(ResourceManager resourceManager, String fileName, Map<String, GuideBookPage> loadedPages) {
        String pageId = stripExtension(fileName);
        ResolvedChapter resolved = resolveChapter(resourceManager, fileName);
        if (resolved == null) {
            SimuKraft.LOGGER.warn("Guidebook page missing: {}", fileName);
            return;
        }
        try (var stream = resolved.resource.open()) {
            String source = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            if (resolved.markdown) {
                loadedPages.put(pageId, GuideBookPage.fromMarkdown(pageId, source));
            } else {
                JsonObject json = JsonParser.parseString(source).getAsJsonObject();
                loadedPages.put(pageId, GuideBookPage.fromJson(pageId, json));
            }
        } catch (Exception exception) {
            SimuKraft.LOGGER.error("Failed to load guidebook page {}", fileName, exception);
        }
    }

    private static ResolvedChapter resolveChapter(ResourceManager resourceManager, String fileName) {
        String lang = currentLang();
        List<String> names = expandFileNames(fileName);
        List<String> folders = new ArrayList<>();
        folders.add(GUIDEBOOK_PATH + "/" + lang);
        if (!"en_us".equals(lang)) {
            folders.add(GUIDEBOOK_PATH + "/en_us");
        }
        if (!"zh_cn".equals(lang)) {
            folders.add(GUIDEBOOK_PATH + "/zh_cn");
        }
        folders.add(GUIDEBOOK_PATH);
        for (String folder : folders) {
            for (String name : names) {
                ResourceLocation location = ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, folder + "/" + name);
                Optional<Resource> resource = resourceManager.getResource(location);
                if (resource.isPresent()) {
                    return new ResolvedChapter(resource.get(), name.endsWith(".md"));
                }
            }
        }
        return null;
    }

    private static List<String> expandFileNames(String fileName) {
        List<String> names = new ArrayList<>();
        names.add(fileName);
        if (fileName.endsWith(".md")) {
            names.add(fileName.substring(0, fileName.length() - 3) + ".json");
        } else if (fileName.endsWith(".json")) {
            names.add(fileName.substring(0, fileName.length() - 5) + ".md");
        } else {
            names.add(fileName + ".md");
            names.add(fileName + ".json");
        }
        return names;
    }

    private static String stripExtension(String fileName) {
        String name = fileName.substring(fileName.lastIndexOf('/') + 1);
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    private static String currentLang() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getLanguageManager() == null) {
            return "en_us";
        }
        String lang = minecraft.getLanguageManager().getSelected();
        return lang == null || lang.isBlank() ? "en_us" : lang;
    }

    private record ResolvedChapter(Resource resource, boolean markdown) {
    }
}
