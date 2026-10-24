package common.cn.nsimu.simukraft.storage;

import common.cn.nsimu.simukraft.SimuKraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.sql.*;

/**
 * 农田盒配置的 SQLite 仓库。读写格式与 FarmlandBoxData.toTag/fromTag 对齐：
 * 列里的 NULL 表示"未设置"，作物/区域/箱子缺省时不写对应键，保证视图能正确显示"未配置"。
 * 主键是维度加坐标，关服时一个维度的快照不能覆盖另一个维度的同一坐标。
 */
public final class FarmlandBoxSqliteRepository {
    private final SimuSqliteDatabase database;

    public FarmlandBoxSqliteRepository(SimuSqliteDatabase database) {
        this.database = database;
    }

    /**
     * saveAll: 把当前维度内存中的农田盒全部 upsert 进库。
     * 删除只走 {@link #delete(Connection, long, String)}，缺行不会被其它维度的保存清掉。
     */
    public void saveAll(Connection connection, CompoundTag tag, String dimensionId) throws SQLException {
        String normalized = normalizeDimensionId(dimensionId);
        ListTag boxes = tag.getList("Boxes", CompoundTag.TAG_COMPOUND);
        for (int i = 0; i < boxes.size(); i++) {
            saveBox(connection, boxes.getCompound(i), normalized);
        }
    }

    // upsert: 保存当前维度的单个农田盒。
    public void upsert(Connection connection, CompoundTag boxTag, String dimensionId) throws SQLException {
        saveBox(connection, boxTag, normalizeDimensionId(dimensionId));
    }

    // delete: 只删除当前维度的这一格，避免拆掉其它维度同坐标的农田盒。
    public void delete(Connection connection, long boxPosLong, String dimensionId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM farmland_boxes WHERE dimension_id = ? AND box_pos_long = ?")) {
            statement.setString(1, normalizeDimensionId(dimensionId));
            statement.setLong(2, boxPosLong);
            statement.executeUpdate();
        }
    }

    // loadAll: 只读取当前维度的农田盒。
    public synchronized CompoundTag loadAll(String dimensionId) {
        CompoundTag tag = new CompoundTag();
        ListTag boxes = new ListTag();
        try (Connection connection = database.borrowConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT * FROM farmland_boxes WHERE dimension_id = ? ORDER BY box_pos_long")) {
            statement.setString(1, normalizeDimensionId(dimensionId));
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    CompoundTag box = new CompoundTag();
                    box.putLong("BoxPos", resultSet.getLong("box_pos_long"));
                    String crop = resultSet.getString("crop");
                    if (crop != null && !crop.isBlank()) {
                        box.putString("Crop", crop);
                    }
                    long plotMin = resultSet.getLong("plot_min_long");
                    boolean plotMinPresent = !resultSet.wasNull();
                    long plotMax = resultSet.getLong("plot_max_long");
                    boolean plotMaxPresent = !resultSet.wasNull();
                    if (plotMinPresent && plotMaxPresent) {
                        CompoundTag plot = new CompoundTag();
                        plot.putLong("Min", plotMin);
                        plot.putLong("Max", plotMax);
                        box.put("Plot", plot);
                    }
                    long chest = resultSet.getLong("chest_pos_long");
                    if (!resultSet.wasNull()) {
                        box.putLong("ChestPos", chest);
                    }
                    box.putBoolean("Running", resultSet.getInt("running") != 0);
                    boxes.add(box);
                }
            }
            tag.put("Boxes", boxes);
            return boxes.isEmpty() ? null : tag;
        } catch (SQLException exception) {
            database.markDegraded("loadAll(farmlandBoxes)", exception);
            SimuKraft.LOGGER.error("Failed to load farmland boxes from SQLite", exception);
            return null;
        }
    }

    private void saveBox(Connection connection, CompoundTag box, String dimensionId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO farmland_boxes(dimension_id, box_pos_long, crop, plot_min_long, plot_max_long, chest_pos_long, running) VALUES(?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT(dimension_id, box_pos_long) DO UPDATE SET crop = excluded.crop, plot_min_long = excluded.plot_min_long, plot_max_long = excluded.plot_max_long, chest_pos_long = excluded.chest_pos_long, running = excluded.running")) {
            statement.setString(1, dimensionId);
            statement.setLong(2, box.getLong("BoxPos"));
            if (box.contains("Crop")) {
                statement.setString(3, box.getString("Crop"));
            } else {
                statement.setNull(3, Types.VARCHAR);
            }
            if (box.contains("Plot")) {
                CompoundTag plot = box.getCompound("Plot");
                statement.setLong(4, plot.getLong("Min"));
                statement.setLong(5, plot.getLong("Max"));
            } else {
                statement.setNull(4, Types.INTEGER);
                statement.setNull(5, Types.INTEGER);
            }
            if (box.contains("ChestPos")) {
                statement.setLong(6, box.getLong("ChestPos"));
            } else {
                statement.setNull(6, Types.INTEGER);
            }
            statement.setInt(7, box.getBoolean("Running") ? 1 : 0);
            statement.executeUpdate();
        }
    }

    // normalizeDimensionId: 空维度归入主世界，和仓库层其他表口径一致。
    private static String normalizeDimensionId(String dimensionId) {
        return dimensionId == null || dimensionId.isBlank() ? "minecraft:overworld" : dimensionId;
    }
}
