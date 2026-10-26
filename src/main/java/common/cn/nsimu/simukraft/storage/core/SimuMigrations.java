package common.cn.nsimu.simukraft.storage.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 主库的迁移清单。新增迁移只需往 {@link #all()} 里追加，版本号必须连续递增。
 */
public final class SimuMigrations {
    private SimuMigrations() {
    }

    public static List<Migration> all() {
        return List.of(
                new CityChunksDimensionPrimaryKey(),
                new CitizenReservedBabyBed(),
                new CommercialBoxesDimensionPrimaryKey(),
                new CitizenLastHospitalProgressDayTime(),
                new ExchangeTables(),
                new ResidentialOccupancyTable(),
                new DistrictTables(),
                new GlobalMarketListingsTable(),
                new GlobalMarketWarehouseTable(),
                new ReleaseGlobalMarketEscrow(),
                new FarmlandBoxesDimensionPrimaryKey(),
                new GlobalMarketBuildingWarehouse(),
                new CommercialStockReleaseDay());
    }

    /**
     * v2：把 {@code city_chunks} 的主键从 {@code (city_id, chunk_long)} 改成
     * {@code (dimension_id, city_id, chunk_long)}。
     *
     * <p>{@code dimension_id} 是后期用 ALTER TABLE 补上的列，主键没跟着改。结果是同一个城市
     * 在两个维度占用同坐标区块时会撞主键，整批保存事务回滚、保存静默失败。
     * SQLite 不支持改主键，只能重建表。
     */
    private static final class CityChunksDimensionPrimaryKey implements Migration {
        @Override
        public int version() {
            return 2;
        }

        @Override
        public String description() {
            return "rebuild city_chunks with dimension_id in the primary key";
        }

        @Override
        public void apply(Connection connection) throws SQLException {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE city_chunks_v2("
                        + "city_id TEXT NOT NULL, chunk_long INTEGER NOT NULL, "
                        + "dimension_id TEXT NOT NULL DEFAULT 'minecraft:overworld', "
                        + "PRIMARY KEY(dimension_id, city_id, chunk_long))");
                // 空维度归入主世界，和仓库层 normalizeDimensionId 的口径一致。
                statement.executeUpdate("INSERT OR IGNORE INTO city_chunks_v2(city_id, chunk_long, dimension_id) "
                        + "SELECT city_id, chunk_long, "
                        + "COALESCE(NULLIF(TRIM(dimension_id), ''), 'minecraft:overworld') FROM city_chunks");
                statement.executeUpdate("DROP TABLE city_chunks");
                statement.executeUpdate("ALTER TABLE city_chunks_v2 RENAME TO city_chunks");
                // DROP TABLE 会连带删掉旧索引，重建。
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_city_chunks_city ON city_chunks(city_id)");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_city_chunks_dimension ON city_chunks(dimension_id)");
            }
        }
    }

    /**
     * v3：持久化孕妇已预约的婴儿床位，避免重启后丢失分娩前置条件。
     */
    private static final class CitizenReservedBabyBed implements Migration {
        @Override
        public int version() {
            return 3;
        }

        @Override
        public String description() {
            return "add reserved baby bed to citizens";
        }

        @Override
        public void apply(Connection connection) throws SQLException {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("ALTER TABLE citizens ADD COLUMN reserved_baby_bed_poi_id TEXT");
            }
        }
    }

    /**
     * v4：给商业箱 / 商业库存补上 {@code dimension_id} 并改成复合主键。
     *
     * <p>这两张表原先只有 {@code box_pos_long}。管理器按维度加载，tick 时若坐标处不是本维度的
     * 控制箱就会删库。跨维度同坐标或区块卸载时，会把别的维度商店一并抹掉。
     */
    private static final class CommercialBoxesDimensionPrimaryKey implements Migration {
        @Override
        public int version() {
            return 4;
        }

        @Override
        public String description() {
            return "rebuild commercial boxes and stock with dimension_id in the primary key";
        }

        @Override
        public void apply(Connection connection) throws SQLException {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE commercial_boxes_v4("
                        + "dimension_id TEXT NOT NULL DEFAULT 'minecraft:overworld', "
                        + "box_pos_long INTEGER NOT NULL, "
                        + "building_id TEXT NOT NULL DEFAULT '', "
                        + "definition_id TEXT NOT NULL DEFAULT '', "
                        + "running INTEGER NOT NULL DEFAULT 1, "
                        + "status_key TEXT NOT NULL DEFAULT '', "
                        + "status_text TEXT NOT NULL DEFAULT '', "
                        + "updated_at INTEGER NOT NULL DEFAULT 0, "
                        + "PRIMARY KEY(dimension_id, box_pos_long))");
                statement.executeUpdate("INSERT OR IGNORE INTO commercial_boxes_v4("
                        + "dimension_id, box_pos_long, building_id, definition_id, running, status_key, status_text, updated_at) "
                        + "SELECT 'minecraft:overworld', box_pos_long, building_id, definition_id, running, status_key, status_text, updated_at "
                        + "FROM commercial_boxes");
                statement.executeUpdate("DROP TABLE commercial_boxes");
                statement.executeUpdate("ALTER TABLE commercial_boxes_v4 RENAME TO commercial_boxes");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_commercial_boxes_running ON commercial_boxes(dimension_id, running)");

                statement.executeUpdate("CREATE TABLE commercial_stock_v4("
                        + "dimension_id TEXT NOT NULL DEFAULT 'minecraft:overworld', "
                        + "box_pos_long INTEGER NOT NULL, "
                        + "item_id TEXT NOT NULL, "
                        + "current_stock INTEGER NOT NULL DEFAULT 0, "
                        + "max_stock INTEGER NOT NULL DEFAULT 0, "
                        + "last_restock_game_time INTEGER NOT NULL DEFAULT 0, "
                        + "updated_at INTEGER NOT NULL DEFAULT 0, "
                        + "PRIMARY KEY(dimension_id, box_pos_long, item_id))");
                statement.executeUpdate("INSERT OR IGNORE INTO commercial_stock_v4("
                        + "dimension_id, box_pos_long, item_id, current_stock, max_stock, last_restock_game_time, updated_at) "
                        + "SELECT 'minecraft:overworld', box_pos_long, item_id, current_stock, max_stock, last_restock_game_time, updated_at "
                        + "FROM commercial_stock");
                statement.executeUpdate("DROP TABLE commercial_stock");
                statement.executeUpdate("ALTER TABLE commercial_stock_v4 RENAME TO commercial_stock");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_commercial_stock_box ON commercial_stock(dimension_id, box_pos_long)");
            }
        }
    }

    /**
     * v5：记下住院治疗的世界时间锚点，睡觉跳过的区间才能在重启后继续结算。
     */
    private static final class CitizenLastHospitalProgressDayTime implements Migration {
        @Override
        public int version() {
            return 5;
        }

        @Override
        public String description() {
            return "add last hospital progress day time to citizens";
        }

        @Override
        public void apply(Connection connection) throws SQLException {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(
                        "ALTER TABLE citizens ADD COLUMN last_hospital_progress_day_time INTEGER NOT NULL DEFAULT 0");
            }
        }
    }

    /**
     * v6: 股市市况、行情、K 线与持仓。
     */
    private static final class ExchangeTables implements Migration {
        @Override
        public int version() {
            return 6;
        }

        @Override
        public String description() {
            return "add exchange market, quotes, candles and holdings";
        }

        @Override
        public void apply(Connection connection) throws SQLException {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS exchange_market("
                        + "dimension_id TEXT PRIMARY KEY, market_day INTEGER NOT NULL, regime TEXT NOT NULL, last_hour INTEGER NOT NULL)");
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS exchange_quotes("
                        + "dimension_id TEXT NOT NULL, company_id TEXT NOT NULL, price REAL NOT NULL, previous_close REAL NOT NULL, volume INTEGER NOT NULL, "
                        + "PRIMARY KEY(dimension_id, company_id))");
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS exchange_candles("
                        + "dimension_id TEXT NOT NULL, company_id TEXT NOT NULL, market_day INTEGER NOT NULL, hour_index INTEGER NOT NULL, "
                        + "open_price REAL NOT NULL, high_price REAL NOT NULL, low_price REAL NOT NULL, close_price REAL NOT NULL, volume INTEGER NOT NULL, "
                        + "PRIMARY KEY(dimension_id, company_id, market_day, hour_index))");
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS exchange_holdings("
                        + "city_id TEXT NOT NULL, company_id TEXT NOT NULL, shares INTEGER NOT NULL, cost_basis REAL NOT NULL, "
                        + "PRIMARY KEY(city_id, company_id))");
            }
        }
    }

    /**
     * v7: 旧存档基线没有住宅入住开关表，缺表会把整库打成降级，股市 K 线也无法落盘。
     */
    private static final class ResidentialOccupancyTable implements Migration {
        @Override
        public int version() {
            return 7;
        }

        @Override
        public String description() {
            return "add residential occupancy flags table";
        }

        @Override
        public void apply(Connection connection) throws SQLException {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS residential_occupancy("
                        + "building_id TEXT PRIMARY KEY, occupancy_allowed INTEGER NOT NULL, updated_at INTEGER NOT NULL)");
            }
        }
    }

    /**
     * v8: district tables and ownership columns for databases created before districts existed.
     */
    private static final class DistrictTables implements Migration {
        @Override
        public int version() {
            return 8;
        }

        @Override
        public String description() {
            return "add city district tables and ownership columns";
        }

        @Override
        public void apply(Connection connection) throws SQLException {
            try (Statement s = connection.createStatement()) {
                s.executeUpdate("CREATE TABLE IF NOT EXISTS districts(district_id TEXT PRIMARY KEY, parent_city_id TEXT NOT NULL, name TEXT NOT NULL, color INTEGER NOT NULL, dimension_id TEXT NOT NULL DEFAULT 'minecraft:overworld', FOREIGN KEY(parent_city_id) REFERENCES cities(city_id) ON DELETE CASCADE)");
                s.executeUpdate("CREATE TABLE IF NOT EXISTS district_chunks(district_id TEXT NOT NULL, chunk_long INTEGER NOT NULL, dimension_id TEXT NOT NULL DEFAULT 'minecraft:overworld', PRIMARY KEY(district_id, chunk_long), FOREIGN KEY(district_id) REFERENCES districts(district_id) ON DELETE CASCADE)");
                s.executeUpdate("CREATE TABLE IF NOT EXISTS district_cores(district_id TEXT NOT NULL, pos_long INTEGER NOT NULL, dimension_id TEXT NOT NULL DEFAULT 'minecraft:overworld', PRIMARY KEY(district_id, pos_long), FOREIGN KEY(district_id) REFERENCES districts(district_id) ON DELETE CASCADE)");
                s.executeUpdate("CREATE TABLE IF NOT EXISTS district_members(district_id TEXT NOT NULL, player_id TEXT NOT NULL, player_name TEXT NOT NULL, role INTEGER NOT NULL, dimension_id TEXT NOT NULL DEFAULT 'minecraft:overworld', PRIMARY KEY(district_id, player_id), FOREIGN KEY(district_id) REFERENCES districts(district_id) ON DELETE CASCADE)");
                s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_districts_parent ON districts(parent_city_id)");
                s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_district_chunks_chunk ON district_chunks(chunk_long)");
                s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_district_members_player ON district_members(player_id)");
            }
            addColumnIfMissing(connection, "citizens", "district_id", "TEXT");
            addColumnIfMissing(connection, "building_tasks", "district_id", "TEXT");
        }

        private static void addColumnIfMissing(Connection c, String table, String column, String definition) throws SQLException {
            try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("PRAGMA table_info(" + table + ")")) {
                while (rs.next()) if (column.equalsIgnoreCase(rs.getString("name"))) return;
            }
            try (Statement s = c.createStatement()) {
                s.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
            }
        }
    }

    /**
     * v9: 全球市场上架报价。
     */
    private static final class GlobalMarketListingsTable implements Migration {
        @Override
        public int version() {
            return 9;
        }

        @Override
        public String description() {
            return "add global market listings";
        }

        @Override
        public void apply(Connection connection) throws SQLException {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS global_market_listings("
                        + "listing_id TEXT PRIMARY KEY, dimension_id TEXT NOT NULL, city_id TEXT NOT NULL, city_name TEXT NOT NULL, "
                        + "mode TEXT NOT NULL, cost_json TEXT NOT NULL, result_json TEXT NOT NULL, stock INTEGER NOT NULL, "
                        + "max_stock INTEGER NOT NULL, created_at INTEGER NOT NULL)");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_global_market_dimension ON global_market_listings(dimension_id)");
            }
        }
    }

    /**
     * v10: 全球市场城市仓库。
     */
    private static final class GlobalMarketWarehouseTable implements Migration {
        @Override
        public int version() {
            return 10;
        }

        @Override
        public String description() {
            return "add global market warehouse";
        }

        @Override
        public void apply(Connection connection) throws SQLException {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS global_market_warehouse("
                        + "city_id TEXT NOT NULL, dimension_id TEXT NOT NULL, stacks_json TEXT NOT NULL, updated_at INTEGER NOT NULL, "
                        + "PRIMARY KEY(city_id, dimension_id))");
            }
        }
    }

    /**
     * v11: 把已经预扣的出售/交换库存退回仓库，之后上架不再预扣。
     */
    private static final class ReleaseGlobalMarketEscrow implements Migration {
        @Override
        public int version() {
            return 11;
        }

        @Override
        public String description() {
            return "move escrowed global market listings into warehouse";
        }

        @Override
        public void apply(Connection connection) throws SQLException {
            Map<String, List<JsonObject>> warehouses = new LinkedHashMap<>();
            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery(
                         "SELECT dimension_id, city_id, stacks_json FROM global_market_warehouse")) {
                while (resultSet.next()) {
                    warehouses.put(
                            resultSet.getString("dimension_id") + "|" + resultSet.getString("city_id"),
                            stacksFromJson(resultSet.getString("stacks_json")));
                }
            }
            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery(
                         "SELECT dimension_id, city_id, mode, result_json, stock FROM global_market_listings")) {
                while (resultSet.next()) {
                    String mode = resultSet.getString("mode");
                    int stock = resultSet.getInt("stock");
                    if (stock <= 0 || (!"sell".equals(mode) && !"barter".equals(mode))) {
                        continue;
                    }
                    String key = resultSet.getString("dimension_id") + "|" + resultSet.getString("city_id");
                    List<JsonObject> stacks = warehouses.computeIfAbsent(key, ignored -> new ArrayList<>());
                    addListingStock(stacks, resultSet.getString("result_json"), stock);
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO global_market_warehouse(city_id, dimension_id, stacks_json, updated_at) VALUES(?,?,?,?) "
                            + "ON CONFLICT(city_id, dimension_id) DO UPDATE SET stacks_json=excluded.stacks_json, updated_at=excluded.updated_at")) {
                long now = System.currentTimeMillis();
                for (Map.Entry<String, List<JsonObject>> entry : warehouses.entrySet()) {
                    int split = entry.getKey().indexOf('|');
                    String dimensionId = entry.getKey().substring(0, split);
                    String cityId = entry.getKey().substring(split + 1);
                    statement.setString(1, cityId);
                    statement.setString(2, dimensionId);
                    statement.setString(3, stacksToJson(entry.getValue()));
                    statement.setLong(4, now);
                    statement.executeUpdate();
                }
            }
        }

        private static void addListingStock(List<JsonObject> stacks, String resultJson, int stock) {
            if (resultJson == null || resultJson.isBlank() || stock <= 0) {
                return;
            }
            JsonArray array = JsonParser.parseString(resultJson).getAsJsonArray();
            array.forEach(element -> {
                JsonObject resource = element.getAsJsonObject();
                if (resource.has("type") && "money".equalsIgnoreCase(resource.get("type").getAsString())) {
                    return;
                }
                String itemId = resource.has("itemId") ? resource.get("itemId").getAsString() : "";
                if (itemId.isBlank()) {
                    return;
                }
                String stackNbt = resource.has("stackNbt") ? resource.get("stackNbt").getAsString() : "";
                for (JsonObject stack : stacks) {
                    String currentNbt = stack.has("stackNbt") ? stack.get("stackNbt").getAsString() : "";
                    if (itemId.equals(stack.get("itemId").getAsString()) && stackNbt.equals(currentNbt)) {
                        stack.addProperty("count", stack.get("count").getAsInt() + stock);
                        return;
                    }
                }
                JsonObject added = new JsonObject();
                added.addProperty("itemId", itemId);
                added.addProperty("count", stock);
                if (!stackNbt.isBlank()) {
                    added.addProperty("stackNbt", stackNbt);
                }
                stacks.add(added);
            });
        }

        private static List<JsonObject> stacksFromJson(String json) {
            List<JsonObject> stacks = new ArrayList<>();
            if (json == null || json.isBlank()) {
                return stacks;
            }
            JsonArray array = JsonParser.parseString(json).getAsJsonArray();
            array.forEach(element -> stacks.add(element.getAsJsonObject()));
            return stacks;
        }

        private static String stacksToJson(List<JsonObject> stacks) {
            JsonArray array = new JsonArray();
            if (stacks != null) {
                stacks.forEach(array::add);
            }
            return array.toString();
        }
    }

    /**
     * v12：给农田盒补上 {@code dimension_id}，主键改为 {@code (dimension_id, box_pos_long)}。
     *
     * <p>旧表只有坐标主键。三个维度各自把整表读进内存，关服时后保存的维度用启动时的旧作物和运行状态覆盖主世界。
     * 已有行归入主世界，和商业箱迁移的口径一致。
     */
    private static final class FarmlandBoxesDimensionPrimaryKey implements Migration {
        @Override
        public int version() {
            return 12;
        }

        @Override
        public String description() {
            return "rebuild farmland_boxes with dimension_id in the primary key";
        }

        @Override
        public void apply(Connection connection) throws SQLException {
            try (Statement statement = connection.createStatement()) {
                if (!tableExists(connection, "farmland_boxes")) {
                    statement.executeUpdate(newTableSql("farmland_boxes"));
                    return;
                }
                if (hasColumn(connection, "farmland_boxes", "dimension_id")) {
                    return;
                }
                statement.executeUpdate(newTableSql("farmland_boxes_v12"));
                statement.executeUpdate("INSERT OR IGNORE INTO farmland_boxes_v12("
                        + "dimension_id, box_pos_long, crop, plot_min_long, plot_max_long, chest_pos_long, running) "
                        + "SELECT 'minecraft:overworld', box_pos_long, crop, plot_min_long, plot_max_long, chest_pos_long, running "
                        + "FROM farmland_boxes");
                statement.executeUpdate("DROP TABLE farmland_boxes");
                statement.executeUpdate("ALTER TABLE farmland_boxes_v12 RENAME TO farmland_boxes");
            }
        }

        private static String newTableSql(String tableName) {
            return "CREATE TABLE " + tableName + "("
                    + "dimension_id TEXT NOT NULL DEFAULT 'minecraft:overworld', "
                    + "box_pos_long INTEGER NOT NULL, "
                    + "crop TEXT, "
                    + "plot_min_long INTEGER, "
                    + "plot_max_long INTEGER, "
                    + "chest_pos_long INTEGER, "
                    + "running INTEGER NOT NULL DEFAULT 0, "
                    + "PRIMARY KEY(dimension_id, box_pos_long))";
        }

        private static boolean tableExists(Connection connection, String tableName) throws SQLException {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?")) {
                statement.setString(1, tableName);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next();
                }
            }
        }

        private static boolean hasColumn(Connection connection, String tableName, String columnName) throws SQLException {
            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery("PRAGMA table_info(" + tableName + ")")) {
                while (resultSet.next()) {
                    if (columnName.equalsIgnoreCase(resultSet.getString("name"))) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    /**
     * v13：全球市场仓库按建筑分开，报价记下所属建筑。
     */
    private static final class GlobalMarketBuildingWarehouse implements Migration {
        @Override
        public int version() {
            return 13;
        }

        @Override
        public String description() {
            return "store each global market warehouse by building id";
        }

        @Override
        public void apply(Connection connection) throws SQLException {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS global_market_building_warehouse("
                        + "building_id TEXT NOT NULL, dimension_id TEXT NOT NULL, stacks_json TEXT NOT NULL, "
                        + "updated_at INTEGER NOT NULL, PRIMARY KEY(building_id, dimension_id))");
            }
            addColumnIfMissing(connection, "global_market_listings", "building_id", "TEXT");
        }

        private static void addColumnIfMissing(Connection connection, String tableName, String columnName, String definition) throws SQLException {
            if (hasColumn(connection, tableName, columnName)) {
                return;
            }
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + definition);
            }
        }

        private static boolean hasColumn(Connection connection, String tableName, String columnName) throws SQLException {
            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery("PRAGMA table_info(" + tableName + ")")) {
                while (resultSet.next()) {
                    if (columnName.equalsIgnoreCase(resultSet.getString("name"))) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    /**
     * v14: 收购库存记下上次清空的游戏日，避免和出售补货计时混用。
     */
    private static final class CommercialStockReleaseDay implements Migration {
        @Override
        public int version() {
            return 14;
        }

        @Override
        public String description() {
            return "remember the day acquired commercial stock was cleared";
        }

        @Override
        public void apply(Connection connection) throws SQLException {
            if (!tableExists(connection, "commercial_stock") || hasColumn(connection, "commercial_stock", "last_release_day")) {
                return;
            }
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("ALTER TABLE commercial_stock ADD COLUMN last_release_day INTEGER NOT NULL DEFAULT -1");
            }
        }

        private static boolean tableExists(Connection connection, String tableName) throws SQLException {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?")) {
                statement.setString(1, tableName);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next();
                }
            }
        }

        private static boolean hasColumn(Connection connection, String tableName, String columnName) throws SQLException {
            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery("PRAGMA table_info(" + tableName + ")")) {
                while (resultSet.next()) {
                    if (columnName.equalsIgnoreCase(resultSet.getString("name"))) {
                        return true;
                    }
                }
            }
            return false;
        }
    }
}
