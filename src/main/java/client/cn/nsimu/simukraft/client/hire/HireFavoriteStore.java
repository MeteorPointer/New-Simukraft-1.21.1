package client.cn.nsimu.simukraft.client.hire;

import common.cn.nsimu.simukraft.SimuKraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@OnlyIn(Dist.CLIENT)
public final class HireFavoriteStore {
    private static final String DATABASE_FILE = "simukraft_client.sqlite";
    private static final String JDBC_PREFIX = "jdbc:sqlite:";
    private static final Set<String> FAVORITES = ConcurrentHashMap.newKeySet();
    private static final Object LOAD_LOCK = new Object();
    private static final AtomicBoolean DRIVER_LOADED = new AtomicBoolean();
    private static volatile boolean loaded;
    private static volatile boolean schemaReady;

    private HireFavoriteStore() {
    }

    public static boolean isFavorite(UUID citizenId) {
        ensureLoaded();
        return FAVORITES.contains(key(citizenId));
    }

    public static synchronized boolean toggleFavorite(UUID citizenId) {
        ensureLoaded();
        String key = key(citizenId);
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

    private static void loadFavorites() {
        try {
            ensureSchema();
            try (Connection connection = openConnection();
                 Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery("SELECT favorite_key FROM hire_favorites")) {
                while (resultSet.next()) {
                    String key = normalizeKey(resultSet.getString("favorite_key"));
                    if (!key.isBlank()) {
                        FAVORITES.add(key);
                    }
                }
            }
        } catch (IOException | SQLException | RuntimeException exception) {
            SimuKraft.LOGGER.warn("Simukraft: Failed to read client hire favorites from SQLite", exception);
        }
    }

    private static void saveFavorite(String key, boolean favorite) {
        try {
            ensureSchema();
            if (favorite) {
                try (Connection connection = openConnection();
                     PreparedStatement statement = connection.prepareStatement("INSERT OR REPLACE INTO hire_favorites(favorite_key, created_at) VALUES(?, ?)")) {
                    statement.setString(1, key);
                    statement.setLong(2, System.currentTimeMillis());
                    statement.executeUpdate();
                }
            } else {
                try (Connection connection = openConnection();
                     PreparedStatement statement = connection.prepareStatement("DELETE FROM hire_favorites WHERE favorite_key = ?")) {
                    statement.setString(1, key);
                    statement.executeUpdate();
                }
            }
        } catch (IOException | SQLException | RuntimeException exception) {
            SimuKraft.LOGGER.warn("Simukraft: Failed to save client hire favorite {}", key, exception);
        }
    }

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
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS hire_favorites(favorite_key TEXT PRIMARY KEY, created_at INTEGER NOT NULL)");
            }
            schemaReady = true;
        }
    }

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

    private static Path databasePath() {
        return FMLPaths.CONFIGDIR.get().resolve(DATABASE_FILE);
    }

    private static String key(UUID citizenId) {
        if (citizenId == null) {
            return "";
        }
        return normalizePart(citizenId.toString());
    }

    private static String normalizeKey(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizePart(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
