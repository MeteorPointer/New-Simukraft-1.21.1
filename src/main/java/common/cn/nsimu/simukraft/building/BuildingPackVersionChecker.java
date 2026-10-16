package common.cn.nsimu.simukraft.building;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import common.cn.nsimu.simukraft.SimuKraft;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 建筑包版本比较工具
 * 比较 JAR 内置 official_building.zip 与本地 simukraftbuilding/official_building.zip 的版本
 */
public final class BuildingPackVersionChecker {
    private static final String PACK_JSON = "pack.json";

    private BuildingPackVersionChecker() {
    }

    /**
     * 读取 JAR 内置官方建筑目录里的 pack.json version 字段。
     */
    public static Optional<String> builtinVersion() {
        ClassLoader cl = BuildingPackVersionChecker.class.getClassLoader();
        InputStream stream = cl != null
                ? cl.getResourceAsStream("assets/simukraft/building/pack.json")
                : ClassLoader.getSystemResourceAsStream("assets/simukraft/building/pack.json");
        if (stream == null) {
            return Optional.empty();
        }
        try (InputStream inputStream = stream) {
            return readVersion(inputStream);
        } catch (IOException e) {
            SimuKraft.LOGGER.warn("Simukraft: Failed to read builtin building pack version", e);
            return Optional.empty();
        }
    }

    /**
     * 读取本地官方建筑目录的 pack.json；旧的 official_building.zip 仍可读取。
     */
    public static Optional<String> localVersion() {
        Path directoryPack = BuildingPackageCatalog.rootDirectory()
                .resolve(BuildingPackageCatalog.OFFICIAL_PACKAGE_NAME)
                .resolve(PACK_JSON);
        Path legacyZip = BuildingPackageCatalog.rootDirectory().resolve(BuildingPackageCatalog.OFFICIAL_PACKAGE_NAME + ".zip");
        try {
            if (Files.isRegularFile(directoryPack)) {
                try (InputStream inputStream = Files.newInputStream(directoryPack)) {
                    return readVersion(inputStream);
                }
            }
            if (Files.isRegularFile(legacyZip)) {
                try (InputStream inputStream = Files.newInputStream(legacyZip)) {
                    return readVersionFromZip(inputStream);
                }
            }
        } catch (IOException e) {
            SimuKraft.LOGGER.warn("Simukraft: Failed to read local building pack version", e);
        }
        return Optional.empty();
    }

    /**
     * 本地版本是否低于 JAR 内版本（语义化版本比较）
     */
    public static boolean isLocalOutdated() {
        Optional<String> local = localVersion();
        Optional<String> builtin = builtinVersion();
        if (local.isEmpty() || builtin.isEmpty()) {
            return false;
        }
        return isOlderThan(local.get(), builtin.get());
    }

    /**
     * 从 pack.json 文本读取 version 字段。
     */
    private static Optional<String> readVersion(InputStream inputStream) {
        try {
            String jsonText = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            JsonObject json = JsonParser.parseString(jsonText).getAsJsonObject();
            if (json.has("version") && json.get("version").isJsonPrimitive()) {
                return Optional.of(json.get("version").getAsString());
            }
        } catch (Exception e) {
            SimuKraft.LOGGER.warn("Simukraft: Failed to parse pack.json from building pack", e);
        }
        return Optional.empty();
    }

    /**
     * 从旧 ZIP 输入流中读取 pack.json 的 version 字段。
     */
    private static Optional<String> readVersionFromZip(InputStream zipStream) {
        try (ZipInputStream zis = new ZipInputStream(zipStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (PACK_JSON.equals(entry.getName()) || entry.getName().endsWith("/" + PACK_JSON)) {
                    return readVersion(zis);
                }
                zis.closeEntry();
            }
        } catch (Exception e) {
            SimuKraft.LOGGER.warn("Simukraft: Failed to parse pack.json from building pack", e);
        }
        return Optional.empty();
    }

    /**
     * 语义化版本比较：a < b 返回 true
     */
    public static boolean isOlderThan(String a, String b) {
        int[] va = parseVersion(a);
        int[] vb = parseVersion(b);
        for (int i = 0; i < Math.min(va.length, vb.length); i++) {
            if (va[i] < vb[i]) return true;
            if (va[i] > vb[i]) return false;
        }
        return va.length < vb.length;
    }

    /**
     * 解析版本号为整数数组（"2.1.0" → [2, 1, 0]）
     */
    private static int[] parseVersion(String version) {
        String[] parts = version.split("\\.");
        int[] result = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                result[i] = Integer.parseInt(parts[i].replaceAll("[^0-9]", ""));
            } catch (NumberFormatException e) {
                result[i] = 0;
            }
        }
        return result;
    }
}
