package common.cn.nsimu.simukraft.storage.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyColumnCatchUpTest {
    @TempDir
    Path tempDir;

    @Test
    void alreadyMigratedSaveGainsColumnsThatBaselineNoLongerAdds() throws Exception {
        Path databasePath = tempDir.resolve("legacy-columns.sqlite");
        try (SqliteConnectionPool pool = SqliteConnectionPool.open(databasePath)) {
            try (Connection connection = pool.borrow();
                 Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE logistics_channels("
                        + "channel_id TEXT PRIMARY KEY, warehouse_id TEXT NOT NULL, client_id TEXT NOT NULL, "
                        + "direction TEXT NOT NULL, name TEXT NOT NULL DEFAULT '', enabled INTEGER NOT NULL DEFAULT 1, "
                        + "filters TEXT NOT NULL DEFAULT '[]', updated_at INTEGER NOT NULL DEFAULT 0, "
                        + "keep_quantity INTEGER NOT NULL DEFAULT 0, keep_source INTEGER NOT NULL DEFAULT 0)");
                statement.executeUpdate("INSERT INTO logistics_channels(channel_id, warehouse_id, client_id, direction) "
                        + "VALUES('channel', 'warehouse', 'client', 'TO_CLIENT')");
                statement.executeUpdate("CREATE TABLE building_tasks(task_id TEXT PRIMARY KEY)");
                statement.executeUpdate("CREATE TABLE city_pois(poi_id TEXT PRIMARY KEY)");
                statement.executeUpdate("CREATE TABLE citizens(uuid TEXT PRIMARY KEY)");
                statement.executeUpdate("CREATE TABLE industrial_boxes(box_pos_long INTEGER PRIMARY KEY)");
                statement.execute("PRAGMA user_version = 14");
            }

            new SchemaMigrator(connection -> {
            }, SimuMigrations.all()).migrate(pool);

            try (Connection connection = pool.borrow();
                 Statement statement = connection.createStatement()) {
                assertTrue(hasColumn(statement, "logistics_channels", "filter_mode"));
                assertTrue(hasColumn(statement, "building_tasks", "replace_with_air"));
                assertTrue(hasColumn(statement, "city_pois", "unit_id"));
                assertTrue(hasColumn(statement, "citizens", "pregnant"));
                assertTrue(hasColumn(statement, "citizens", "family_id"));
                assertTrue(hasColumn(statement, "industrial_boxes", "step_elapsed_ticks"));
                assertTrue(hasColumn(statement, "industrial_boxes", "worker_work_pos_long"));
                try (ResultSet resultSet = statement.executeQuery("PRAGMA user_version")) {
                    assertTrue(resultSet.next());
                    assertEquals(15, resultSet.getInt(1));
                }
                try (ResultSet resultSet = statement.executeQuery(
                        "SELECT filter_mode FROM logistics_channels WHERE channel_id = 'channel'")) {
                    assertTrue(resultSet.next());
                    assertEquals("ITEM", resultSet.getString(1));
                }
            }
        }
    }

    private static boolean hasColumn(Statement statement, String tableName, String columnName) throws Exception {
        try (ResultSet resultSet = statement.executeQuery("PRAGMA table_info(" + tableName + ")")) {
            while (resultSet.next()) {
                if (columnName.equalsIgnoreCase(resultSet.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }
}
