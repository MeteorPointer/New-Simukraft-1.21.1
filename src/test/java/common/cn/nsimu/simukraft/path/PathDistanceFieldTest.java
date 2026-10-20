package common.cn.nsimu.simukraft.path;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathDistanceFieldTest {
    private static final ResourceLocation DIMENSION = ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");

    @Test
    void secondStartReusesTheFieldAndReachesTheSameTarget() {
        PathSnapshot snapshot = floor(0, 8);
        BlockPos target = new BlockPos(8, 64, 0);
        PathRequest origin = request(new BlockPos(0, 64, 0), target);
        PathSectionStamp stamp = new PathSectionStamp(new long[]{0L}, new long[]{0L});
        PathDistanceField field = PathDistanceField.build(origin, snapshot, stamp);
        PathRequest other = request(new BlockPos(3, 64, 0), target);

        PathResult fromField = field.reconstruct(other, snapshot);
        PathResult direct = HybridPathfinder.find(other, snapshot);

        assertTrue(fromField.success());
        assertTrue(direct.success());
        assertEquals(target, fromField.waypoints().get(fromField.waypoints().size() - 1).blockPos());
        assertEquals(direct.waypoints().get(direct.waypoints().size() - 1).blockPos(),
                fromField.waypoints().get(fromField.waypoints().size() - 1).blockPos());
    }

    private static PathRequest request(BlockPos start, BlockPos target) {
        return new PathRequest(UUID.randomUUID(), DIMENSION, start,
                new Vec3(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D), MovementIntent.WALK, 0L);
    }

    private static PathSnapshot floor(int minX, int maxX) {
        Long2ObjectOpenHashMap<BlockState> states = new Long2ObjectOpenHashMap<>();
        Long2ObjectOpenHashMap<VoxelShape> shapes = new Long2ObjectOpenHashMap<>();
        BlockState stone = Blocks.STONE.defaultBlockState();
        for (int x = minX; x <= maxX; x++) {
            BlockPos pos = new BlockPos(x, 63, 0);
            states.put(pos.asLong(), stone);
            shapes.put(pos.asLong(), stone.getCollisionShape(EmptyBlockGetter.INSTANCE, pos));
        }
        PathSnapshotBuilder.ChunkDataCapture capture = new PathSnapshotBuilder.ChunkDataCapture(
                states, shapes, new PathSnapshotBuilder.SnapshotBounds(minX, maxX, 0, 0, 64, 66),
                DIMENSION, 0L, true);
        return PathSnapshotBuilder.buildFromCapture(capture, new BlockPos(minX, 64, 0), new BlockPos(maxX, 64, 0));
    }
}
