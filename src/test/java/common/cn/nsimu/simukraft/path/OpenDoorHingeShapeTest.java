package common.cn.nsimu.simukraft.path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.Test;

/**
 * An open door's panel sits on the hinge edge. The centre footprint misses it, so the path
 * used to walk through the hinge. The panel edge must stay blocked while the other three
 * sides remain a doorway.
 */
class OpenDoorHingeShapeTest {
    private static final ResourceLocation DIMENSION = ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");

    @Test
    void openDoorPanelEdgeMatchesCollision() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (DoorHingeSide hinge : DoorHingeSide.values()) {
                Direction edge = PathSnapshotBuilder.openDoorPanelEdge(facing, hinge);
                BlockState state = door(facing, hinge, true);
                VoxelShape shape = state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
                boolean panelOnPositive = edge.getAxisDirection() == net.minecraft.core.Direction.AxisDirection.POSITIVE;
                double outer = panelOnPositive ? shape.max(edge.getAxis()) : shape.min(edge.getAxis());
                double inner = panelOnPositive ? shape.min(edge.getAxis()) : shape.max(edge.getAxis());
                assertTrue(Math.abs(outer - (panelOnPositive ? 1.0D : 0.0D)) < 1.0E-4D
                                && Math.abs(inner - (panelOnPositive ? 0.8125D : 0.1875D)) < 1.0E-4D,
                        () -> facing + " " + hinge + " edge " + edge + " missed the panel");
                assertTrue(PathSnapshotBuilder.clearsNpcBodySlice(shape, 0.0D, 1.0D),
                        "open door panel must stay off the centre footprint");
            }
        }
    }

    @Test
    void pathAvoidsTheOpenDoorPanelEdge() {
        // Door at (1,64,0) faces north and opens left, so the panel occupies the west edge.
        PathSnapshot snapshot = corridor(Direction.NORTH, DoorHingeSide.LEFT);
        assertTrue(snapshot.blocksHorizontalBoundary(1, 64, 0, 0, 0),
                "the open panel must block the hinge side");
        assertFalse(snapshot.blocksHorizontalBoundary(1, 64, 0, 2, 0),
                "the opposite side of an open door stays clear");
        assertFalse(snapshot.blocksHorizontalBoundary(1, 64, 0, 1, 1),
                "walking through the doorway must stay allowed");

        PathResult throughDoor = HybridPathfinder.find(request(2, 64, 0, 1, 64, 1), snapshot);
        assertTrue(throughDoor.success(), () -> "doorway itself must remain usable, reason=" + throughDoor.reason());

        PathResult aroundHinge = HybridPathfinder.find(request(0, 64, 1, 0, 64, -1), snapshot);
        assertTrue(aroundHinge.success(), () -> "a route around the hinge must exist, reason=" + aroundHinge.reason());
        assertFalse(crosses(aroundHinge, 0, 64, 0, 1, 64, 0) || crosses(aroundHinge, 1, 64, 0, 0, 64, 0),
                "path walked across the open door panel");
    }

    private static boolean crosses(PathResult result, int fromX, int fromY, int fromZ, int toX, int toY, int toZ) {
        var waypoints = result.waypoints();
        for (int index = 1; index < waypoints.size(); index++) {
            BlockPos previous = waypoints.get(index - 1).blockPos();
            BlockPos current = waypoints.get(index).blockPos();
            if (previous.getX() == fromX && previous.getY() == fromY && previous.getZ() == fromZ
                    && current.getX() == toX && current.getY() == toY && current.getZ() == toZ) {
                return true;
            }
            if (previous.getX() == toX && previous.getY() == toY && previous.getZ() == toZ
                    && current.getX() == fromX && current.getY() == fromY && current.getZ() == fromZ) {
                return true;
            }
        }
        return false;
    }

    private static PathRequest request(int fromX, int fromY, int fromZ, int toX, int toY, int toZ) {
        return new PathRequest(UUID.randomUUID(), DIMENSION, new BlockPos(fromX, fromY, fromZ),
                new Vec3(toX + 0.5D, toY, toZ + 0.5D), MovementIntent.WALK, 0L);
    }

    private static PathSnapshot corridor(Direction facing, DoorHingeSide hinge) {
        Long2ObjectOpenHashMap<BlockState> states = new Long2ObjectOpenHashMap<>();
        Long2ObjectOpenHashMap<VoxelShape> shapes = new Long2ObjectOpenHashMap<>();
        for (int x = 0; x <= 2; x++) {
            for (int z = -1; z <= 1; z++) {
                for (int y = 63; y <= 66; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = y == 63 ? Blocks.STONE.defaultBlockState()
                            : x == 1 && y == 64 ? door(facing, hinge, true).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER)
                            : x == 1 && y == 65 ? door(facing, hinge, true).setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER)
                            : Blocks.AIR.defaultBlockState();
                    states.put(pos.asLong(), state);
                    shapes.put(pos.asLong(), state.getCollisionShape(EmptyBlockGetter.INSTANCE, pos));
                }
            }
        }
        PathSnapshotBuilder.ChunkDataCapture capture = new PathSnapshotBuilder.ChunkDataCapture(
                states, shapes, new PathSnapshotBuilder.SnapshotBounds(0, 2, -1, 1, 64, 65),
                DIMENSION, 0L, true);
        return PathSnapshotBuilder.buildFromCapture(capture, new BlockPos(0, 64, 0), new BlockPos(2, 64, 0));
    }

    private static BlockState door(Direction facing, DoorHingeSide hinge, boolean open) {
        return Blocks.OAK_DOOR.defaultBlockState()
                .setValue(DoorBlock.FACING, facing)
                .setValue(DoorBlock.HINGE, hinge)
                .setValue(DoorBlock.OPEN, open)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
    }
}
