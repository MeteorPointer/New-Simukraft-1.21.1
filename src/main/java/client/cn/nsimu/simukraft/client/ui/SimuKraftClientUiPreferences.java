package client.cn.nsimu.simukraft.client.ui;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import common.cn.nsimu.simukraft.SimuKraft;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

@OnlyIn(Dist.CLIENT)
public final class SimuKraftClientUiPreferences {
    private static final String FILE_NAME = "simukraft_client_ui.properties";
    private static final Properties VALUES = new Properties();
    private static boolean loaded;

    private SimuKraftClientUiPreferences() {
    }

    public static synchronized float getFloat(String key, float fallback, float min, float max) {
        load();
        String raw = VALUES.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return clamp(fallback, min, max);
        }
        try {
            return clamp(Float.parseFloat(raw), min, max);
        } catch (NumberFormatException exception) {
            return clamp(fallback, min, max);
        }
    }

    public static synchronized void setFloat(String key, float value, float min, float max) {
        load();
        float clamped = clamp(value, min, max);
        String next = String.format(java.util.Locale.ROOT, "%.3f", clamped);
        if (next.equals(VALUES.getProperty(key))) {
            return;
        }
        VALUES.setProperty(key, next);
        save();
    }

    private static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        Path path = filePath();
        if (!Files.isRegularFile(path)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            VALUES.load(reader);
        } catch (IOException exception) {
            SimuKraft.LOGGER.warn("无法读取客户端界面偏好设置: {}", path, exception);
        }
    }

    private static void save() {
        Path path = filePath();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                VALUES.store(writer, "SimuKraft client UI preferences");
            }
        } catch (IOException exception) {
            SimuKraft.LOGGER.warn("无法保存客户端界面偏好设置: {}", path, exception);
        }
    }

    private static Path filePath() {
        return FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
