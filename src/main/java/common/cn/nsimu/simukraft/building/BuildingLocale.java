package common.cn.nsimu.simukraft.building;

import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.Locale;

/**
 * BuildingLocale: 建筑包中文键与 {@code *_en} 英文字段的选择。
 */
public final class BuildingLocale {
    private BuildingLocale() {
    }

    /**
     * preferEnglish: 当前玩家语言不是中文时用英文字段。
     * 服务端类加载器看不到 Minecraft 客户端，所以优先读在线玩家上报的语言。
     */
    public static boolean preferEnglish() {
        String language = activeLanguage();
        return language == null || !language.toLowerCase(Locale.ROOT).startsWith("zh");
    }

    private static String activeLanguage() {
        String online = onlinePlayerLanguage();
        if (online != null) {
            return online;
        }
        if (FMLEnvironment.dist == null || FMLEnvironment.dist.isDedicatedServer()) {
            return "zh_cn";
        }
        return clientLanguage();
    }

    private static String onlinePlayerLanguage() {
        try {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) {
                return null;
            }
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                String language = player.clientInformation().language();
                if (language != null && !language.isBlank()) {
                    return language;
                }
            }
        } catch (RuntimeException exception) {
            return null;
        }
        return null;
    }

    private static String clientLanguage() {
        try {
            Class<?> minecraft = Class.forName("net.minecraft.client.Minecraft");
            Object instance = minecraft.getMethod("getInstance").invoke(null);
            Object languageManager = minecraft.getMethod("getLanguageManager").invoke(instance);
            Object selected = languageManager.getClass().getMethod("getSelected").invoke(languageManager);
            return selected instanceof String value ? value : null;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }

    /**
     * text: 英文客户端优先读 {@code key_en}，缺省回退 {@code key}。
     */
    public static String text(JsonObject object, String key, String fallback) {
        if (preferEnglish()) {
            String english = raw(object, key + "_en");
            if (english != null && !english.isBlank()) {
                return english;
            }
        }
        String value = raw(object, key);
        return value == null || value.isBlank() ? fallback : value;
    }

    /**
     * textAny: 按键名顺序取值，英文客户端先扫每个键的 {@code *_en}。
     */
    public static String textAny(JsonObject object, String fallback, String... keys) {
        if (preferEnglish()) {
            for (String key : keys) {
                String english = raw(object, key + "_en");
                if (english != null && !english.isBlank()) {
                    return english;
                }
            }
        }
        for (String key : keys) {
            String value = raw(object, key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return fallback;
    }

    private static String raw(JsonObject object, String key) {
        if (object == null || key == null || !object.has(key) || object.get(key).isJsonNull()) {
            return null;
        }
        try {
            return object.get(key).getAsString();
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
