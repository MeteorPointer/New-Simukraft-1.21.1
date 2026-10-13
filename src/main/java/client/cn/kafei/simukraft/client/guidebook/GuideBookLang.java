package client.cn.kafei.simukraft.client.guidebook;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import common.cn.kafei.simukraft.SimuKraft;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import javax.annotation.Nonnull;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 指南书独立语言。正文来自 guidebook/lang，不走普通语言文件。
 */
@OnlyIn(Dist.CLIENT)
public final class GuideBookLang {
    private static final Gson GSON = new Gson();
    private static final String LANG_PATH = "guidebook/lang";
    private static final Map<String, String> TRANSLATIONS = new ConcurrentHashMap<>();
    private static boolean loaded;

    private GuideBookLang() {
    }

    public static void load(@Nonnull ResourceManager resourceManager) {
        TRANSLATIONS.clear();
        String langCode = Minecraft.getInstance().getLanguageManager().getSelected();
        ResourceLocation langLocation = ResourceLocation.fromNamespaceAndPath(
                SimuKraft.MOD_ID, LANG_PATH + "/" + langCode + ".json");
        if (!readLanguage(resourceManager, langLocation)) {
            SimuKraft.LOGGER.warn("GuideBook language file not found for '{}', loading fallback", langCode);
            readLanguage(resourceManager, ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, LANG_PATH + "/en_us.json"));
        }
        loaded = true;
    }

    public static void reload(@Nonnull ResourceManager resourceManager) {
        loaded = false;
        load(resourceManager);
    }

    public static boolean isLoaded() {
        return loaded;
    }

    public static boolean has(@Nonnull String key) {
        return TRANSLATIONS.containsKey(key);
    }

    public static @Nonnull String get(@Nonnull String key) {
        return replaceVariables(TRANSLATIONS.getOrDefault(key, key));
    }

    public static @Nonnull String get(@Nonnull String key, Object... args) {
        return String.format(get(key), args);
    }

    private static boolean readLanguage(ResourceManager resourceManager, ResourceLocation location) {
        var resource = resourceManager.getResource(location);
        if (resource.isEmpty()) {
            return false;
        }
        try (InputStreamReader reader = new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8)) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            if (json == null) {
                return false;
            }
            for (var entry : json.entrySet()) {
                if (entry.getKey().startsWith("_") || !entry.getValue().isJsonPrimitive()) {
                    continue;
                }
                TRANSLATIONS.put(entry.getKey(), entry.getValue().getAsString());
            }
            SimuKraft.LOGGER.info("GuideBook loaded language {} with {} entries", location, TRANSLATIONS.size());
            return true;
        } catch (Exception exception) {
            SimuKraft.LOGGER.error("GuideBook failed to load language {}", location, exception);
            return false;
        }
    }

    private static @Nonnull String replaceVariables(@Nonnull String text) {
        if (!text.contains("$player_name")) {
            return text;
        }
        Minecraft minecraft = Minecraft.getInstance();
        String playerName = minecraft.player != null ? minecraft.player.getName().getString() : "玩家";
        return text.replace("$player_name", playerName);
    }
}
