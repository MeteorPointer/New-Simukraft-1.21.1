package common.cn.nsimu.simukraft.path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.OptionalDouble;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.Test;

/**
 * A citizen standing on a bottom slab has feet half a block above the block underneath.
 * A passage whose ceiling is only 1.5 blocks above that surface must not be walkable,
 * even when it is the shorter route.
 */
class BottomSlabHeadroomTest {
    private static final ResourceLocation DIMENSION = ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState BOTTOM_SLAB = Blocks.OAK_SLAB.defaultBlockState()
            .setValue(SlabBlock.TYPE, SlabType.BOTTOM);
    private static final BlockState STAIR = Blocks.OAK_STAIRS.defaultBlockState()
            .setValue(StairBlock.FACING, Direction.NORTH);

    @Test
    void bottomSlabStandsOnItsOwnTopNotTheBlockBeneath() {
        VoxelShape slab = BOTTOM_SLAB.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        assertFalse(PathSnapshotBuilder.clearsNpcBodySlice(slab, 0.0D, 1.0D),
                "a bottom slab fills the centre of its block and cannot be occupied as air");

        PathSnapshot open = column(BOTTOM_SLAB, 66);
        PathCell feet = open.cell(0, 63, 0);
        assertNotNull(feet, "open air above a bottom slab must stay walkable");
        assertTrue(Math.abs(feet.standY() - 63.5D) < 1.0E-6D,
                () -> "bottom slab stand height was " + (feet == null ? "missing" : feet.standY())
                        + "; the block under the slab must not replace the slab top");
    }

    @Test
    void oneAndAHalfBlockCeilingAboveBottomSlabIsNotWalkable() {
        // Slab top is 63.5. A solid block at y=65 starts at 65.0, leaving 1.5 of headroom.
        PathSnapshot cramped = column(BOTTOM_SLAB, 65);
        assertTrue(cramped.cell(0, 63, 0) == null,
                "1.5 blocks above a bottom slab is shorter than the 1.8 citizen and must not be a path cell");
        assertTrue(cramped.cell(0, 62, 0) == null,
                "the full block under the slab must not become a stand-in floor cell");
        assertTrue(cramped.cell(0, 64, 0) == null,
                "the air block above the slab must not be treated as a floor at the integer grid");
    }

    @Test
    void shorterHeadBonkRouteLosesToTheOpenDetour() {
        // z=1 is a slab corridor with a 1.5 ceiling over x=2 and x=3. z=0 and z=2 stay open.
        Long2ObjectOpenHashMap<BlockState> states = new Long2ObjectOpenHashMap<>();
        Long2ObjectOpenHashMap<VoxelShape> shapes = new Long2ObjectOpenHashMap<>();
        for (int x = 0; x <= 5; x++) {
            for (int z = 0; z <= 2; z++) {
                for (int y = 62; y <= 67; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    boolean lowCeiling = z == 1 && (x == 2 || x == 3) && y == 65;
                    BlockState state = y == 62 ? STONE : y == 63 ? BOTTOM_SLAB : lowCeiling ? STONE : Blocks.AIR.defaultBlockState();
                    states.put(pos.asLong(), state);
                    shapes.put(pos.asLong(), state.getCollisionShape(EmptyBlockGetter.INSTANCE, pos));
                }
            }
        }
        PathSnapshot snapshot = build(states, shapes, 0, 5, 0, 2, 63, 66);
        BlockPos start = new BlockPos(0, 63, 1);
        BlockPos target = new BlockPos(5, 63, 1);
        PathResult result = HybridPathfinder.find(new PathRequest(UUID.randomUUID(), DIMENSION, start,
                new Vec3(5.5D, 63.5D, 1.5D), MovementIntent.WALK, 0L), snapshot);

        assertTrue(result.success(), () -> "open side route should reach the target, reason=" + result.reason());
        assertFalse(visits(result, 2, 63, 1) || visits(result, 3, 63, 1),
                "path preferred the closer 1.5-high gap over the open corridor beside it");
    }

    /**
     * The slab is only the approach. The short tunnel's own floor is the full block under that
     * slab height, so its 2.0 clearance passes an integer stand check, but the citizen is still
     * 0.5 higher until they step down and their head meets a 1.5 gap.
     */
    @Test
    void steppingOffABottomSlabDoesNotEnterATwoBlockTunnel() {
        Long2ObjectOpenHashMap<BlockState> states = new Long2ObjectOpenHashMap<>();
        Long2ObjectOpenHashMap<VoxelShape> shapes = new Long2ObjectOpenHashMap<>();
        for (int x = 0; x <= 5; x++) {
            for (int z = 0; z <= 2; z++) {
                for (int y = 62; y <= 67; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    boolean tunnel = z == 1 && x >= 1 && x <= 3;
                    boolean ceiling = tunnel && y == 65;
                    boolean slab = !tunnel && y == 63;
                    BlockState state = y == 62 || ceiling ? STONE
                            : slab ? BOTTOM_SLAB
                            : Blocks.AIR.defaultBlockState();
                    states.put(pos.asLong(), state);
                    shapes.put(pos.asLong(), state.getCollisionShape(EmptyBlockGetter.INSTANCE, pos));
                }
            }
        }
        PathSnapshot snapshot = build(states, shapes, 0, 5, 0, 2, 62, 66);
        PathCell approach = snapshot.cell(0, 63, 1);
        PathCell tunnel = snapshot.cell(1, 63, 1);
        assertNotNull(approach, "the slab approach must stay walkable");
        assertNotNull(tunnel, "the lower 2-block tunnel is tall enough for a citizen already at its floor");
        assertTrue(Math.abs(approach.standY() - 63.5D) < 1.0E-6D, () -> "approach standY=" + approach.standY());
        assertTrue(Math.abs(tunnel.standY() - 63.0D) < 1.0E-6D, () -> "tunnel standY=" + tunnel.standY());

        PathResult result = HybridPathfinder.find(new PathRequest(UUID.randomUUID(), DIMENSION,
                new BlockPos(0, 63, 1), new Vec3(5.5D, 63.5D, 1.5D), MovementIntent.WALK, 0L), snapshot);
        assertTrue(result.success(), () -> "side route should succeed, reason=" + result.reason());
        String listed = result.waypoints().stream()
                .map(waypoint -> waypoint.blockPos().toShortString() + "@" + waypoint.position().y)
                .reduce((left, right) -> left + " | " + right)
                .orElse("none");
        assertFalse(visits(result, 1, 63, 1) || visits(result, 2, 63, 1) || visits(result, 3, 63, 1)
                        || chordCrossesTunnel(result),
                () -> "path stepped off the slab into the closer tunnel whose ceiling is only 1.5 above the slab: " + listed);
    }

    @Test
    void openHalfSlabRiseStaysAWalk() {
        Long2ObjectOpenHashMap<BlockState> states = new Long2ObjectOpenHashMap<>();
        Long2ObjectOpenHashMap<VoxelShape> shapes = new Long2ObjectOpenHashMap<>();
        for (int x = 0; x <= 2; x++) {
            for (int y = 62; y <= 66; y++) {
                BlockPos pos = new BlockPos(x, y, 0);
                BlockState state = y == 62 ? STONE : y == 63 && x == 1 ? BOTTOM_SLAB : Blocks.AIR.defaultBlockState();
                states.put(pos.asLong(), state);
                shapes.put(pos.asLong(), state.getCollisionShape(EmptyBlockGetter.INSTANCE, pos));
            }
        }
        PathSnapshot snapshot = build(states, shapes, 0, 2, 0, 0, 63, 65);
        PathResult result = HybridPathfinder.find(new PathRequest(UUID.randomUUID(), DIMENSION,
                new BlockPos(0, 63, 0), new Vec3(2.5D, 63.0D, 0.5D), MovementIntent.WALK, 0L), snapshot);
        assertTrue(result.success(), () -> "open half-slab rise should stay reachable, reason=" + result.reason());
        assertTrue(result.waypoints().stream().noneMatch(waypoint -> waypoint.mode() == MovementMode.JUMP),
                "a 0.5 slab rise must stay a walk");
    }

    @Test
    void stairLowerTreadDoesNotInheritTheBlockUnderIt() {
        VoxelShape stair = STAIR.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        StringBuilder boxes = new StringBuilder();
        stair.toAabbs().forEach(box -> boxes.append('[').append(box.minY).append(',').append(box.maxY).append("] "));
        OptionalDouble top = PathSnapshotBuilder.supportTop(BlockPos.ZERO, stair);

        // Ceiling two blocks above the stair block: 1.5 above the lower tread, 1.0 above the high step.
        PathSnapshot cramped = column(STAIR, 65);
        PathCell onStair = cramped.cell(0, 63, 0);
        PathCell aboveStair = cramped.cell(0, 64, 0);
        assertTrue(onStair == null && aboveStair == null,
                () -> "stair headroom failed; supportTop=" + (top.isPresent() ? top.getAsDouble() : "empty")
                        + " boxes=" + boxes
                        + " cell63=" + describe(onStair)
                        + " cell64=" + describe(aboveStair));
    }

    private static String describe(PathCell cell) {
        return cell == null ? "null" : "standY=" + cell.standY();
    }

    private static boolean chordCrossesTunnel(PathResult result) {
        var waypoints = result.waypoints();
        for (int index = 1; index < waypoints.size(); index++) {
            Vec3 from = waypoints.get(index - 1).position();
            Vec3 to = waypoints.get(index).position();
            int samples = 8;
            for (int sample = 0; sample <= samples; sample++) {
                double ratio = sample / (double) samples;
                double x = from.x + (to.x - from.x) * ratio;
                double z = from.z + (to.z - from.z) * ratio;
                int cellX = (int) Math.floor(x);
                int cellZ = (int) Math.floor(z);
                if (cellZ == 1 && cellX >= 1 && cellX <= 3) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean visits(PathResult result, int x, int y, int z) {
        return result.waypoints().stream().anyMatch(waypoint -> waypoint.blockPos().getX() == x
                && waypoint.blockPos().getY() == y
                && waypoint.blockPos().getZ() == z);
    }

    /** Floor stone at y=62, {@code floor} at y=63, solid ceiling at {@code ceilingY}. */
    private static PathSnapshot column(BlockState floor, int ceilingY) {
        Long2ObjectOpenHashMap<BlockState> states = new Long2ObjectOpenHashMap<>();
        Long2ObjectOpenHashMap<VoxelShape> shapes = new Long2ObjectOpenHashMap<>();
        for (int y = 62; y <= Math.max(66, ceilingY); y++) {
            BlockPos pos = new BlockPos(0, y, 0);
            BlockState state = y == 62 ? STONE : y == 63 ? floor : y == ceilingY ? STONE : Blocks.AIR.defaultBlockState();
            states.put(pos.asLong(), state);
            shapes.put(pos.asLong(), state.getCollisionShape(EmptyBlockGetter.INSTANCE, pos));
        }
        return build(states, shapes, 0, 0, 0, 0, 63, 66);
    }

    private static PathSnapshot build(Long2ObjectOpenHashMap<BlockState> states,
                                      Long2ObjectOpenHashMap<VoxelShape> shapes,
                                      int minX, int maxX, int minZ, int maxZ, int minY, int maxY) {
        PathSnapshotBuilder.ChunkDataCapture capture = new PathSnapshotBuilder.ChunkDataCapture(
                states, shapes, new PathSnapshotBuilder.SnapshotBounds(minX, maxX, minZ, maxZ, minY, maxY),
                DIMENSION, 0L, true);
        return PathSnapshotBuilder.buildFromCapture(capture, new BlockPos(minX, minY, minZ), new BlockPos(maxX, minY, maxZ));
    }
}
