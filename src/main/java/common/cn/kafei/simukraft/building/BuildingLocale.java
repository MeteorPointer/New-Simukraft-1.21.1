package common.cn.kafei.simukraft.building;

import com.google.gson.JsonObject;
import net.neoforged.fml.loading.FMLEnvironment;

import java.util.Locale;

/** BuildingLocale: 建筑包中文键与 {@code *_en} 英文字段的选择。 */
public final class BuildingLocale {
    private BuildingLocale() {
    }

    /** preferEnglish: 集成客户端且当前语言不是中文时读取英文字段。专用服务器始终用中文键。 */
    public static boolean preferEnglish() {
        if (FMLEnvironment.dist == null || FMLEnvironment.dist.isDedicatedServer()) {
            return false;
        }
        try {
            Class<?> minecraft = Class.forName("net.minecraft.client.Minecraft");
            Object instance = minecraft.getMethod("getInstance").invoke(null);
            Object languageManager = minecraft.getMethod("getLanguageManager").invoke(instance);
            Object selected = languageManager.getClass().getMethod("getSelected").invoke(languageManager);
            String language = selected instanceof String value ? value : null;
            return language == null || !language.toLowerCase(Locale.ROOT).startsWith("zh");
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return false;
        }
    }

    /** text: 英文客户端优先读 {@code key_en}，缺省回退 {@code key}。 */
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

    /** textAny: 按键名顺序取值，英文客户端先扫每个键的 {@code *_en}。 */
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
