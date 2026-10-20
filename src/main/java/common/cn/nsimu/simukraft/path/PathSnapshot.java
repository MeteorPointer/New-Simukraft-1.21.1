package common.cn.nsimu.simukraft.path;

import it.unimi.dsi.fastutil.longs.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public record PathSnapshot(ResourceLocation dimensionId, BlockPos startPos, BlockPos targetPos,
                           Long2ObjectOpenHashMap<PathCell> cells,
                           LongSet bodyPassages, Long2ByteMap horizontalBarriers,
                           int minY, int maxY, long createdAt, boolean complete,
                           Long2ObjectOpenHashMap<VoxelShape> collisionShapes,
                           @Nullable PathSnapshotClassifier classifier) {
    public PathSnapshot(ResourceLocation dimensionId, BlockPos startPos, BlockPos targetPos, Long2ObjectOpenHashMap<PathCell> cells,
                        int minY, int maxY, long createdAt, boolean complete) {
        this(dimensionId, startPos, targetPos, cells, LongSets.EMPTY_SET, Long2ByteMaps.EMPTY_MAP,
                minY, maxY, createdAt, complete, new Long2ObjectOpenHashMap<>(), null);
    }

    public PathSnapshot(ResourceLocation dimensionId, BlockPos startPos, BlockPos targetPos, Long2ObjectOpenHashMap<PathCell> cells,
                        LongSet bodyPassages, int minY, int maxY, long createdAt, boolean complete) {
        this(dimensionId, startPos, targetPos, cells, bodyPassages, Long2ByteMaps.EMPTY_MAP,
                minY, maxY, createdAt, complete, new Long2ObjectOpenHashMap<>(), null);
    }

    public PathSnapshot(ResourceLocation dimensionId, BlockPos startPos, BlockPos targetPos, Long2ObjectOpenHashMap<PathCell> cells,
                        LongSet bodyPassages, Long2ByteMap horizontalBarriers,
                        int minY, int maxY, long createdAt, boolean complete) {
        this(dimensionId, startPos, targetPos, cells, bodyPassages, horizontalBarriers,
                minY, maxY, createdAt, complete, new Long2ObjectOpenHashMap<>(), null);
    }

    public PathCell cell(BlockPos pos) {
        return cell(pos.getX(), pos.getY(), pos.getZ());
    }

    public PathCell cell(int x, int y, int z) {
        long key = PathCell.key(x, y, z);
        PathCell found = cells.get(key);
        if (found != null || classifier == null) {
            return found;
        }
        return classifier.cell(x, y, z);
    }

    public Collection<PathCell> allCells() {
        return cells.values();
    }

    public boolean contains(BlockPos pos) {
        return cell(pos) != null;
    }

    /**
     * collisionShape: 这一格的碰撞体。懒快照在工人线程现算，预先写好的快照直接查表。
     */
    public VoxelShape collisionShape(int x, int y, int z) {
        if (classifier != null) {
            return classifier.collisionShape(x, y, z);
        }
        return collisionShapes.get(BlockPos.asLong(x, y, z));
    }

    public boolean bodyPassage(int x, int y, int z) {
        if (bodyPassages.contains(BlockPos.asLong(x, y, z))) {
            return true;
        }
        return classifier != null && classifier.bodyPassage(x, y, z);
    }

    /**
     * Returns whether a horizontal move crosses a captured thin wall, such as an open trapdoor.
     */
    public boolean blocksHorizontalBoundary(int fromX, int y, int fromZ, int toX, int toZ) {
        int dx = Integer.compare(toX - fromX, 0);
        int dz = Integer.compare(toZ - fromZ, 0);
        if (Math.abs(toX - fromX) + Math.abs(toZ - fromZ) != 1) {
            return false;
        }
        byte forward = barrierMask(dx, dz);
        byte backward = barrierMask(-dx, -dz);
        if (classifier != null) {
            classifier.ensureBarrier(fromX, y, fromZ);
            classifier.ensureBarrier(toX, y, toZ);
        }
        return (horizontalBarriers.get(PathCell.key(fromX, y, fromZ)) & forward) != 0
                || (horizontalBarriers.get(PathCell.key(toX, y, toZ)) & backward) != 0;
    }

    static byte barrierMask(Direction direction) {
        return barrierMask(direction.getStepX(), direction.getStepZ());
    }

    static byte barrierMask(int dx, int dz) {
        if (dx == 0 && dz < 0) return 1;
        if (dx == 0 && dz > 0) return 2;
        if (dx < 0 && dz == 0) return 4;
        if (dx > 0 && dz == 0) return 8;
        return 0;
    }
}
