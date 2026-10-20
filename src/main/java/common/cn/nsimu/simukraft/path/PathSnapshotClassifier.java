package common.cn.nsimu.simukraft.path;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * PathSnapshotClassifier: 搜索扩到哪一格，才分类那一格，并补上危险、身位和门扇阻挡。
 */
final class PathSnapshotClassifier {
    private final PathSnapshotBuilder.BlockDataSource data;
    private final PathSnapshotBuilder.SnapshotBounds bounds;
    private final Long2ObjectOpenHashMap<PathCell> cells;
    private final LongOpenHashSet bodyPassages;
    private final Long2ByteOpenHashMap barriers;
    private final LongOpenHashSet absentCells = new LongOpenHashSet();
    private final LongOpenHashSet knownPassages = new LongOpenHashSet();
    private final LongOpenHashSet knownBarriers = new LongOpenHashSet();
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    PathSnapshotClassifier(PathSnapshotBuilder.BlockDataSource data, PathSnapshotBuilder.SnapshotBounds bounds,
                           Long2ObjectOpenHashMap<PathCell> cells, LongOpenHashSet bodyPassages,
                           Long2ByteOpenHashMap barriers) {
        this.data = data;
        this.bounds = bounds;
        this.cells = cells;
        this.bodyPassages = bodyPassages;
        this.barriers = barriers;
    }

    /**
     * cell: 分类一个脚点。不可走的位置记下来，避免同一格重复判断。
     */
    PathCell cell(int x, int y, int z) {
        long key = PathCell.key(x, y, z);
        if (absentCells.contains(key) || !contains(x, y, z)) {
            absentCells.add(key);
            return null;
        }
        cursor.set(x, y, z);
        PathCell classified = PathSnapshotBuilder.withDanger(data, PathSnapshotBuilder.classify(data, cursor));
        if (classified == null) {
            absentCells.add(key);
            return null;
        }
        cells.put(key, classified);
        return classified;
    }

    /**
     * bodyPassage: 身体能否通过这一格。结果按是/否分开缓存。
     */
    boolean bodyPassage(int x, int y, int z) {
        long key = BlockPos.asLong(x, y, z);
        if (knownPassages.contains(key)) {
            return bodyPassages.contains(key);
        }
        knownPassages.add(key);
        if (!contains(x, y, z)) {
            return false;
        }
        cursor.set(x, y, z);
        if (!PathSnapshotBuilder.hasBodyPassage(data, cursor)) {
            return false;
        }
        bodyPassages.add(key);
        return true;
    }

    /**
     * ensureBarrier: 补上这一格的门和活板门阻挡。活板门的阻挡会写到下一格，所以同时看上一格。
     */
    /**
     * collisionShape: 给净空检查提供这一格的碰撞，不占用主线程。
     */
    VoxelShape collisionShape(int x, int y, int z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, y, z);
        return data.shape(pos, data.state(pos));
    }

    void ensureBarrier(int x, int y, int z) {
        long key = PathCell.key(x, y, z);
        if (!knownBarriers.add(key)) {
            return;
        }
        cursor.set(x, y, z);
        PathSnapshotBuilder.addOpenTrapdoorBarriers(data, cursor, bounds, barriers);
        PathSnapshotBuilder.addOpenDoorBarriers(data, cursor, bounds, barriers);
        cursor.set(x, y + 1, z);
        PathSnapshotBuilder.addOpenTrapdoorBarriers(data, cursor, bounds, barriers);
    }

    private boolean contains(int x, int y, int z) {
        return x >= bounds.minX() && x <= bounds.maxX()
                && y >= bounds.minY() && y <= bounds.maxY()
                && z >= bounds.minZ() && z <= bounds.maxZ();
    }
}
