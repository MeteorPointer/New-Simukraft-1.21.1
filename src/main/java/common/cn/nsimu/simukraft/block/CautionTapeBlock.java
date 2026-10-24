package common.cn.nsimu.simukraft.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 警戒线：无碰撞薄带。单独放置时贴着方块四边围成一圈，和相邻警戒线连接时改成直线或拐角。
 */
public final class CautionTapeBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;

    private static final VoxelShape RING = Shapes.or(
            Block.box(0.0, 8.5, 0.0, 16.0, 12.5, 1.0),
            Block.box(0.0, 8.5, 15.0, 16.0, 12.5, 16.0),
            Block.box(0.0, 8.5, 1.0, 1.0, 12.5, 15.0),
            Block.box(15.0, 8.5, 1.0, 16.0, 12.5, 15.0));
    private static final VoxelShape ARM_NORTH = Block.box(7.5, 8.5, 0.0, 8.5, 12.5, 8.0);
    private static final VoxelShape ARM_SOUTH = Block.box(7.5, 8.5, 8.0, 8.5, 12.5, 16.0);
    private static final VoxelShape ARM_WEST = Block.box(0.0, 8.5, 7.5, 8.0, 12.5, 8.5);
    private static final VoxelShape ARM_EAST = Block.box(8.0, 8.5, 7.5, 16.0, 12.5, 8.5);
    private static final VoxelShape[] SHAPES = makeShapes();

    public CautionTapeBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_YELLOW)
                .noCollission()
                .instabreak()
                .sound(SoundType.WOOL)
                .pushReaction(PushReaction.DESTROY)
                .isViewBlocking((state, level, pos) -> false)
                .isSuffocating((state, level, pos) -> false));
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(NORTH, false)
                .setValue(SOUTH, false)
                .setValue(EAST, false)
                .setValue(WEST, false));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[index(state)];
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return applyConnections(
                defaultBlockState().setValue(FACING, context.getHorizontalDirection()),
                context.getLevel(),
                context.getClickedPos());
    }

    @Override
    protected BlockState updateShape(
            BlockState state,
            Direction facing,
            BlockState facingState,
            LevelAccessor level,
            BlockPos currentPos,
            BlockPos facingPos) {
        if (!facing.getAxis().isHorizontal()) {
            return state;
        }
        return applyConnections(state, level, currentPos);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        BlockState rotated = state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
        return switch (rotation) {
            case CLOCKWISE_180 -> rotated
                    .setValue(NORTH, state.getValue(SOUTH))
                    .setValue(EAST, state.getValue(WEST))
                    .setValue(SOUTH, state.getValue(NORTH))
                    .setValue(WEST, state.getValue(EAST));
            case COUNTERCLOCKWISE_90 -> rotated
                    .setValue(NORTH, state.getValue(EAST))
                    .setValue(EAST, state.getValue(SOUTH))
                    .setValue(SOUTH, state.getValue(WEST))
                    .setValue(WEST, state.getValue(NORTH));
            case CLOCKWISE_90 -> rotated
                    .setValue(NORTH, state.getValue(WEST))
                    .setValue(EAST, state.getValue(NORTH))
                    .setValue(SOUTH, state.getValue(EAST))
                    .setValue(WEST, state.getValue(SOUTH));
            default -> rotated;
        };
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, NORTH, EAST, SOUTH, WEST);
    }

    private static boolean connectsTo(BlockState state) {
        return state.getBlock() instanceof CautionTapeBlock;
    }

    /**
     * 依据四周的警戒线重算本格自身的连接状态。
     * 程序化放置（绕过 getStateForPlacement）后需要调用它触发自身更新，
     * 因为 setBlock 的邻居更新标记只会刷新四周，不会刷新被放置的格子本身。
     */
    public static BlockState applyConnections(BlockState state, BlockGetter level, BlockPos pos) {
        boolean north = connectsTo(level.getBlockState(pos.north()));
        boolean east = connectsTo(level.getBlockState(pos.east()));
        boolean south = connectsTo(level.getBlockState(pos.south()));
        boolean west = connectsTo(level.getBlockState(pos.west()));

        boolean xr = north || south;
        boolean zr = east || west;
        boolean x = north && south || (xr && !zr);
        boolean z = east && west || (zr && !xr);

        north = x || north;
        south = x || south;
        east = z || east;
        west = z || west;
        return state.setValue(NORTH, north).setValue(EAST, east).setValue(SOUTH, south).setValue(WEST, west);
    }

    private static int index(BlockState state) {
        int i = 0;
        if (state.getValue(NORTH)) {
            i |= 1;
        }
        if (state.getValue(EAST)) {
            i |= 2;
        }
        if (state.getValue(SOUTH)) {
            i |= 4;
        }
        if (state.getValue(WEST)) {
            i |= 8;
        }
        return i;
    }

    private static VoxelShape[] makeShapes() {
        VoxelShape[] shapes = new VoxelShape[16];
        for (int i = 0; i < 16; i++) {
            VoxelShape shape = Shapes.empty();
            if ((i & 1) != 0) {
                shape = Shapes.or(shape, ARM_NORTH);
            }
            if ((i & 2) != 0) {
                shape = Shapes.or(shape, ARM_EAST);
            }
            if ((i & 4) != 0) {
                shape = Shapes.or(shape, ARM_SOUTH);
            }
            if ((i & 8) != 0) {
                shape = Shapes.or(shape, ARM_WEST);
            }
            shapes[i] = shape.isEmpty() ? RING : shape;
        }
        return shapes;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (level instanceof ServerLevel serverLevel && !state.is(newState.getBlock())) {
            CautionTapePlacers.get(serverLevel).remove(pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
