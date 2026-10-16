package client.cn.nsimu.simukraft.client.buildbox;

import common.cn.nsimu.simukraft.SimuKraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@OnlyIn(Dist.CLIENT)
public final class BuildingFavoriteStore {
    private static final String DATABASE_FILE = "simukraft_client.sqlite";
    private static final String JDBC_PREFIX = "jdbc:sqlite:";
    private static final Set<String> FAVORITES = ConcurrentHashMap.newKeySet();
    private static final Object LOAD_LOCK = new Object();
    private static final AtomicBoolean DRIVER_LOADED = new AtomicBoolean();
    private static volatile boolean loaded;
    private static volatile boolean schemaReady;

    private BuildingFavoriteStore() {
    }

    /**
     * isFavorite: 判断建筑是否已被客户端收藏。
     */
    public static boolean isFavorite(BuildingCacheService.BuildingMeta building) {
        ensureLoaded();
        return FAVORITES.contains(key(building));
    }

    /**
     * toggleFavorite: 切换收藏状态并立即保存到客户端 SQLite。
     */
    public static synchronized boolean toggleFavorite(BuildingCacheService.BuildingMeta building) {
        ensureLoaded();
        String key = key(building);
        if (key.isBlank()) {
            return false;
        }
        boolean favorite = !FAVORITES.remove(key);
        if (favorite) {
            FAVORITES.add(key);
        }
        saveFavorite(key, favorite);
        return favorite;
    }

    /**
     * ensureLoaded: 延迟加载客户端收藏表，避免界面打开前做无用 IO。
     */
    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        synchronized (LOAD_LOCK) {
            if (loaded) {
                return;
            }
            loaded = true;
            loadFavorites();
        }
    }

    /**
     * loadFavorites: 从客户端 SQLite 读取收藏键集合。
     */
    private static void loadFavorites() {
        try {
            ensureSchema();
            try (Connection connection = openConnection();
                 Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery("SELECT favorite_key FROM building_favorites")) {
                while (resultSet.next()) {
                    String key = normalizeKey(resultSet.getString("favorite_key"));
                    if (!key.isBlank()) {
                        FAVORITES.add(key);
                    }
                }
            }
        } catch (IOException | SQLException | RuntimeException exception) {
            SimuKraft.LOGGER.warn("Simukraft: Failed to read client building favorites from SQLite", exception);
        }
    }

    /**
     * saveFavorite: 将单个收藏状态写入客户端 SQLite。
     */
    private static void saveFavorite(String key, boolean favorite) {
        try {
            ensureSchema();
            if (favorite) {
                try (Connection connection = openConnection();
                     PreparedStatement statement = connection.prepareStatement("INSERT OR REPLACE INTO building_favorites(favorite_key, created_at) VALUES(?, ?)")) {
                    statement.setString(1, key);
                    statement.setLong(2, System.currentTimeMillis());
                    statement.executeUpdate();
                }
            } else {
                try (Connection connection = openConnection();
                     PreparedStatement statement = connection.prepareStatement("DELETE FROM building_favorites WHERE favorite_key = ?")) {
                    statement.setString(1, key);
                    statement.executeUpdate();
                }
            }
        } catch (IOException | SQLException | RuntimeException exception) {
            SimuKraft.LOGGER.warn("Simukraft: Failed to save client building favorite {}", key, exception);
        }
    }

    /**
     * ensureSchema: 初始化客户端收藏数据库和表结构。
     */
    private static void ensureSchema() throws IOException, SQLException {
        if (schemaReady) {
            return;
        }
        synchronized (LOAD_LOCK) {
            if (schemaReady) {
                return;
            }
            loadDriver();
            Files.createDirectories(databasePath().getParent());
            try (Connection connection = openConnection();
                 Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS building_favorites(favorite_key TEXT PRIMARY KEY, created_at INTEGER NOT NULL)");
            }
            schemaReady = true;
        }
    }

    /**
     * openConnection: 打开客户端 SQLite 连接并应用轻量 PRAGMA。
     */
    private static Connection openConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(JDBC_PREFIX + databasePath().toAbsolutePath().normalize());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA busy_timeout=5000");
        } catch (SQLException exception) {
            try {
                connection.close();
            } catch (SQLException ignored) {
            }
            throw exception;
        }
        return connection;
    }

    /**
     * loadDriver: 懒加载 SQLite JDBC 驱动。
     */
    private static void loadDriver() {
        if (DRIVER_LOADED.get()) {
            return;
        }
        synchronized (DRIVER_LOADED) {
            if (DRIVER_LOADED.get()) {
                return;
            }
            try {
                Class.forName("org.sqlite.JDBC");
                DRIVER_LOADED.set(true);
            } catch (ClassNotFoundException exception) {
                throw new IllegalStateException("SQLite JDBC driver is not available. Check sqlite-jdbc runtime dependency.", exception);
            }
        }
    }

    /**
     * databasePath: 返回客户端收藏数据库路径。
     */
    private static Path databasePath() {
        return FMLPaths.CONFIGDIR.get().resolve(DATABASE_FILE);
    }

    /**
     * key: 生成跨重启稳定的建筑收藏键。
     */
    private static String key(BuildingCacheService.BuildingMeta building) {
        if (building == null) {
            return "";
        }
        return String.join("|",
                normalizePart(building.category()),
                normalizePart(building.packageName()),
                normalizePart(building.metaFileName()),
                normalizePart(building.structureFileName()));
    }

    /**
     * normalizeKey: 规范化从数据库读取的收藏键。
     */
    private static String normalizeKey(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * normalizePart: 规范化收藏键的单个组成部分。
     */
    private static String normalizePart(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
