package common.cn.kafei.simukraft.storage;

import common.cn.kafei.simukraft.SimuKraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.UUID;

/** SQLite persistence for district metadata. The NBT shape mirrors DistrictData.toTag(). */
public final class DistrictSqliteRepository {
    private final SimuSqliteDatabase database;

    public DistrictSqliteRepository(SimuSqliteDatabase database) {
        this.database = database;
    }

    public void saveAll(Connection connection, CompoundTag tag, String dimensionId) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM districts WHERE dimension_id = ?")) {
            delete.setString(1, dimensionId);
            delete.executeUpdate();
        }
        try (PreparedStatement district = connection.prepareStatement("INSERT INTO districts(district_id, parent_city_id, name, color, dimension_id) VALUES(?, ?, ?, ?, ?)");
             PreparedStatement chunk = connection.prepareStatement("INSERT INTO district_chunks(district_id, chunk_long, dimension_id) VALUES(?, ?, ?)");
             PreparedStatement core = connection.prepareStatement("INSERT INTO district_cores(district_id, pos_long, dimension_id) VALUES(?, ?, ?)");
             PreparedStatement member = connection.prepareStatement("INSERT INTO district_members(district_id, player_id, player_name, role, dimension_id) VALUES(?, ?, ?, ?, ?)");) {
            Set<String> liveCities = new HashSet<>();
            try (Statement cities = connection.createStatement(); ResultSet rows = cities.executeQuery("SELECT city_id FROM cities")) {
                while (rows.next()) liveCities.add(rows.getString(1));
            }
            ListTag districts = tag.getList("Districts", CompoundTag.TAG_COMPOUND);
            for (int i = 0; i < districts.size(); i++) {
                CompoundTag value = districts.getCompound(i);
                String parentCityId = value.getUUID("ParentCityId").toString();
                if (!liveCities.contains(parentCityId)) continue;
                String districtId = value.getUUID("DistrictId").toString();
                district.setString(1, districtId); district.setString(2, parentCityId);
                district.setString(3, value.getString("Name")); district.setInt(4, value.getInt("Color")); district.setString(5, dimensionId); district.addBatch();
                ListTag chunks = value.getList("Chunks", LongTag.TAG_LONG);
                for (int j = 0; j < chunks.size(); j++) { chunk.setString(1, districtId); chunk.setLong(2, ((LongTag) chunks.get(j)).getAsLong()); chunk.setString(3, dimensionId); chunk.addBatch(); }
                ListTag cores = value.getList("Cores", LongTag.TAG_LONG);
                for (int j = 0; j < cores.size(); j++) { core.setString(1, districtId); core.setLong(2, ((LongTag) cores.get(j)).getAsLong()); core.setString(3, dimensionId); core.addBatch(); }
                ListTag members = value.getList("Members", CompoundTag.TAG_COMPOUND);
                for (int j = 0; j < members.size(); j++) {
                    CompoundTag m = members.getCompound(j); member.setString(1, districtId); member.setString(2, m.getUUID("PlayerId").toString());
                    member.setString(3, m.getString("PlayerName")); member.setInt(4, m.getInt("Role")); member.setString(5, dimensionId); member.addBatch();
                }
            }
            district.executeBatch(); chunk.executeBatch(); core.executeBatch(); member.executeBatch();
        }
    }

    public CompoundTag loadAll(String dimensionId) {
        CompoundTag result = new CompoundTag();
        LinkedHashMap<UUID, CompoundTag> districts = new LinkedHashMap<>();
        try (Connection connection = database.borrowConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT district_id, parent_city_id, name, color FROM districts WHERE dimension_id = ? ORDER BY district_id")) {
            statement.setString(1, dimensionId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    CompoundTag tag = new CompoundTag(); UUID id = UUID.fromString(rows.getString("district_id"));
                    tag.putUUID("DistrictId", id); tag.putUUID("ParentCityId", UUID.fromString(rows.getString("parent_city_id")));
                    tag.putString("Name", rows.getString("name")); tag.putInt("Color", rows.getInt("color"));
                    tag.put("Chunks", new ListTag()); tag.put("Cores", new ListTag()); tag.put("Members", new ListTag()); districts.put(id, tag);
                }
            }
            loadLongs(connection, "SELECT district_id, chunk_long FROM district_chunks WHERE dimension_id = ?", dimensionId, districts, "Chunks");
            loadLongs(connection, "SELECT district_id, pos_long FROM district_cores WHERE dimension_id = ?", dimensionId, districts, "Cores");
            try (PreparedStatement members = connection.prepareStatement("SELECT district_id, player_id, player_name, role FROM district_members WHERE dimension_id = ?")) {
                members.setString(1, dimensionId);
                try (ResultSet rows = members.executeQuery()) {
                    while (rows.next()) {
                        CompoundTag district = districts.get(UUID.fromString(rows.getString("district_id"))); if (district == null) continue;
                        CompoundTag member = new CompoundTag(); member.putUUID("PlayerId", UUID.fromString(rows.getString("player_id")));
                        member.putString("PlayerName", rows.getString("player_name")); member.putInt("Role", rows.getInt("role")); district.getList("Members", CompoundTag.TAG_COMPOUND).add(member);
                    }
                }
            }
            ListTag list = new ListTag(); districts.values().forEach(list::add); result.put("Districts", list); return list.isEmpty() ? null : result;
        } catch (SQLException | IllegalArgumentException exception) {
            database.markDegraded("loadAll(districts)", exception); SimuKraft.LOGGER.error("Failed to load districts from SQLite", exception); return null;
        }
    }

    private static void loadLongs(Connection connection, String sql, String dimensionId, LinkedHashMap<UUID, CompoundTag> districts, String key) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, dimensionId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) { CompoundTag district = districts.get(UUID.fromString(rows.getString("district_id"))); if (district != null) district.getList(key, LongTag.TAG_LONG).add(LongTag.valueOf(rows.getLong(2))); }
            }
        }
    }
}
