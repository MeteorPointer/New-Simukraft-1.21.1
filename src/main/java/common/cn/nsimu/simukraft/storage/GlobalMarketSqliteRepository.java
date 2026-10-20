package common.cn.nsimu.simukraft.storage;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import common.cn.nsimu.simukraft.SimuKraft;
import common.cn.nsimu.simukraft.commercial.CommercialTradeView;
import common.cn.nsimu.simukraft.globalmarket.GlobalMarketListing;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * GlobalMarketSqliteRepository: 全球市场上架报价。
 */
public final class GlobalMarketSqliteRepository {
    private final SimuSqliteDatabase database;

    public GlobalMarketSqliteRepository(SimuSqliteDatabase database) {
        this.database = database;
    }

    /**
     * loadAll: 读取该维度全部上架报价。
     */
    public List<GlobalMarketListing> loadAll(String dimensionId) {
        List<GlobalMarketListing> listings = new ArrayList<>();
        try (Connection connection = database.borrowConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT listing_id, city_id, city_name, mode, cost_json, result_json, stock, max_stock "
                             + "FROM global_market_listings WHERE dimension_id=? ORDER BY created_at DESC")) {
            statement.setString(1, dimensionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    listings.add(readRow(resultSet));
                }
            }
        } catch (SQLException | IllegalArgumentException exception) {
            SimuKraft.LOGGER.warn("Failed to load global market listings", exception);
        }
        return listings;
    }

    /**
     * upsert: 写入或覆盖一条上架报价。
     */
    public void upsert(Connection connection, String dimensionId, GlobalMarketListing listing) throws SQLException {
        if (listing == null || listing.id().isBlank()) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO global_market_listings(listing_id, dimension_id, city_id, city_name, mode, cost_json, result_json, stock, max_stock, created_at) "
                        + "VALUES(?,?,?,?,?,?,?,?,?,?) "
                        + "ON CONFLICT(listing_id) DO UPDATE SET city_id=excluded.city_id, city_name=excluded.city_name, mode=excluded.mode, "
                        + "cost_json=excluded.cost_json, result_json=excluded.result_json, stock=excluded.stock, max_stock=excluded.max_stock")) {
            statement.setString(1, listing.id());
            statement.setString(2, dimensionId);
            statement.setString(3, listing.cityId().toString());
            statement.setString(4, listing.cityName());
            statement.setString(5, listing.mode());
            statement.setString(6, resourcesToJson(listing.cost()));
            statement.setString(7, resourcesToJson(listing.result()));
            statement.setInt(8, listing.stock());
            statement.setInt(9, listing.maxStock());
            statement.setLong(10, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }

    /**
     * delete: 下架一条报价。
     */
    public void delete(Connection connection, String listingId) throws SQLException {
        if (listingId == null || listingId.isBlank()) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM global_market_listings WHERE listing_id=?")) {
            statement.setString(1, listingId);
            statement.executeUpdate();
        }
    }

    /**
     * find: 按 ID 读取一条报价。
     */
    public GlobalMarketListing find(String listingId) {
        if (listingId == null || listingId.isBlank()) {
            return null;
        }
        try (Connection connection = database.borrowConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT listing_id, city_id, city_name, mode, cost_json, result_json, stock, max_stock "
                             + "FROM global_market_listings WHERE listing_id=?")) {
            statement.setString(1, listingId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return readRow(resultSet);
                }
            }
        } catch (SQLException | IllegalArgumentException exception) {
            SimuKraft.LOGGER.warn("Failed to find global market listing {}", listingId, exception);
        }
        return null;
    }

    private static GlobalMarketListing readRow(ResultSet resultSet) throws SQLException {
        return new GlobalMarketListing(
                resultSet.getString("listing_id"),
                UUID.fromString(resultSet.getString("city_id")),
                resultSet.getString("city_name"),
                resultSet.getString("mode"),
                resourcesFromJson(resultSet.getString("cost_json")),
                resourcesFromJson(resultSet.getString("result_json")),
                resultSet.getInt("stock"),
                resultSet.getInt("max_stock"));
    }

    private static String resourcesToJson(List<CommercialTradeView.ResourceEntry> resources) {
        JsonArray array = new JsonArray();
        for (CommercialTradeView.ResourceEntry resource : resources) {
            JsonObject object = new JsonObject();
            object.addProperty("type", resource.type());
            object.addProperty("itemId", resource.itemId());
            object.addProperty("count", resource.count());
            object.addProperty("money", resource.money());
            if (resource.stackNbt() != null && !resource.stackNbt().isBlank()) {
                object.addProperty("stackNbt", resource.stackNbt());
            }
            array.add(object);
        }
        return array.toString();
    }

    private static List<CommercialTradeView.ResourceEntry> resourcesFromJson(String json) {
        List<CommercialTradeView.ResourceEntry> resources = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return resources;
        }
        JsonArray array = JsonParser.parseString(json).getAsJsonArray();
        array.forEach(element -> {
            JsonObject object = element.getAsJsonObject();
            resources.add(new CommercialTradeView.ResourceEntry(
                    object.has("type") ? object.get("type").getAsString() : "item",
                    object.has("itemId") ? object.get("itemId").getAsString() : "",
                    object.has("count") ? object.get("count").getAsInt() : 0,
                    object.has("money") ? object.get("money").getAsDouble() : 0.0D,
                    object.has("stackNbt") ? object.get("stackNbt").getAsString() : ""));
        });
        return resources;
    }

    /**
     * loadWarehouse: 读取城市仓库。
     */
    public List<CommercialTradeView.WarehouseEntry> loadWarehouse(String dimensionId, String cityId) {
        if (cityId == null || cityId.isBlank()) {
            return List.of();
        }
        try (Connection connection = database.borrowConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT stacks_json FROM global_market_warehouse WHERE dimension_id=? AND city_id=?")) {
            statement.setString(1, dimensionId);
            statement.setString(2, cityId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return warehouseFromJson(resultSet.getString("stacks_json"));
                }
            }
        } catch (SQLException | IllegalArgumentException exception) {
            SimuKraft.LOGGER.warn("Failed to load global market warehouse for {}", cityId, exception);
        }
        return List.of();
    }

    /**
     * saveWarehouse: 覆盖写入城市仓库。
     */
    public void saveWarehouse(Connection connection, String dimensionId, String cityId, List<CommercialTradeView.WarehouseEntry> entries) throws SQLException {
        if (cityId == null || cityId.isBlank()) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO global_market_warehouse(city_id, dimension_id, stacks_json, updated_at) VALUES(?,?,?,?) "
                        + "ON CONFLICT(city_id, dimension_id) DO UPDATE SET stacks_json=excluded.stacks_json, updated_at=excluded.updated_at")) {
            statement.setString(1, cityId);
            statement.setString(2, dimensionId);
            statement.setString(3, warehouseToJson(entries));
            statement.setLong(4, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }

    private static String warehouseToJson(List<CommercialTradeView.WarehouseEntry> entries) {
        JsonArray array = new JsonArray();
        if (entries != null) {
            for (CommercialTradeView.WarehouseEntry entry : entries) {
                JsonObject object = new JsonObject();
                object.addProperty("itemId", entry.itemId());
                object.addProperty("count", entry.count());
                if (entry.stackNbt() != null && !entry.stackNbt().isBlank()) {
                    object.addProperty("stackNbt", entry.stackNbt());
                }
                array.add(object);
            }
        }
        return array.toString();
    }

    private static List<CommercialTradeView.WarehouseEntry> warehouseFromJson(String json) {
        List<CommercialTradeView.WarehouseEntry> entries = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return entries;
        }
        JsonArray array = JsonParser.parseString(json).getAsJsonArray();
        array.forEach(element -> {
            JsonObject object = element.getAsJsonObject();
            entries.add(new CommercialTradeView.WarehouseEntry(
                    object.has("itemId") ? object.get("itemId").getAsString() : "",
                    object.has("count") ? object.get("count").getAsInt() : 0,
                    object.has("stackNbt") ? object.get("stackNbt").getAsString() : ""));
        });
        return entries;
    }
}
