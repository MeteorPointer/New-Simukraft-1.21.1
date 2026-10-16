package common.cn.nsimu.simukraft.citizen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import common.cn.nsimu.simukraft.SimuKraft;
import common.cn.nsimu.simukraft.building.BuildingLocale;
import common.cn.nsimu.simukraft.config.ServerConfig;
import net.minecraft.util.RandomSource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * CitizenNameLibrary: 英文名库来自 names/en_us.json。英文客户端或英式配置用这套名字。
 */
public final class CitizenNameLibrary {
    private static final String RESOURCE = "assets/simukraft/names/en_us.json";
    private static volatile Library english = Library.EMPTY;

    private CitizenNameLibrary() {
    }

    /**
     * useEnglish: 配置为英式，或当前客户端语言不是中文时，生成英文名。
     */
    public static boolean useEnglish() {
        if (ServerConfig.npcNameStyle() == CitizenNameStyle.ENGLISH) {
            return true;
        }
        return BuildingLocale.preferEnglish();
    }

    public static String givenName(String gender, RandomSource random, String[] fallback) {
        List<String> names = "female".equals(gender) ? library().female : library().male;
        return pick(names, fallback, random);
    }

    public static String familyName(RandomSource random, String[] fallback) {
        return pick(library().family, fallback, random);
    }

    /**
     * containsCjk: 姓氏仍是中文时，英文名不能接在这个姓后面。
     */
    public static boolean containsCjk(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            Character.UnicodeBlock block = Character.UnicodeBlock.of(value.charAt(i));
            if (block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                    || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                    || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS) {
                return true;
            }
        }
        return false;
    }

    private static String pick(List<String> names, String[] fallback, RandomSource random) {
        if (names != null && !names.isEmpty()) {
            return names.get(random.nextInt(names.size()));
        }
        if (fallback == null || fallback.length == 0) {
            return "";
        }
        return fallback[random.nextInt(fallback.length)];
    }

    private static Library library() {
        Library loaded = english;
        if (loaded != Library.EMPTY) {
            return loaded;
        }
        synchronized (CitizenNameLibrary.class) {
            if (english == Library.EMPTY) {
                english = read();
            }
            return english;
        }
    }

    private static Library read() {
        ClassLoader classLoader = CitizenNameLibrary.class.getClassLoader();
        InputStream stream = classLoader == null
                ? ClassLoader.getSystemResourceAsStream(RESOURCE)
                : classLoader.getResourceAsStream(RESOURCE);
        if (stream == null) {
            SimuKraft.LOGGER.warn("Simukraft: Missing English citizen name library {}", RESOURCE);
            return new Library(List.of(), List.of(), List.of());
        }
        try (InputStream inputStream = stream) {
            JsonObject root = JsonParser.parseString(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            return new Library(strings(root, "male"), strings(root, "female"), strings(root, "family"));
        } catch (Exception exception) {
            SimuKraft.LOGGER.warn("Simukraft: Failed to read English citizen name library", exception);
            return new Library(List.of(), List.of(), List.of());
        }
    }

    private static List<String> strings(JsonObject root, String key) {
        List<String> values = new ArrayList<>();
        if (root == null || !root.has(key) || !root.get(key).isJsonArray()) {
            return List.of();
        }
        JsonArray array = root.getAsJsonArray(key);
        for (JsonElement element : array) {
            if (element == null || !element.isJsonPrimitive()) {
                continue;
            }
            String value = element.getAsString().trim();
            if (!value.isBlank() && !containsCjk(value)) {
                values.add(value);
            }
        }
        return List.copyOf(values);
    }

    private record Library(List<String> male, List<String> female, List<String> family) {
        private static final Library EMPTY = new Library(null, null, null);
    }
}
