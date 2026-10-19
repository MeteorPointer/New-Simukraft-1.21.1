package common.cn.nsimu.simukraft.block;

import com.mojang.serialization.MapCodec;
import common.cn.nsimu.simukraft.block.entity.ManifestBlockEntity;
import common.cn.nsimu.simukraft.network.manifest.ManifestOpenResponsePacket;
import common.cn.nsimu.simukraft.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 清单方块：16 高 × 14 宽 × 1 厚，可贴地面或挂墙，必须有支撑，不能贴天花板。
 */
public final class ManifestBlock extends FaceAttachedHorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<ManifestBlock> CODEC = simpleCodec(ManifestBlock::new);

    private static final VoxelShape FLOOR_NORTH_SOUTH = box(1.0, 0.0, 0.0, 15.0, 1.0, 16.0);
    private static final VoxelShape FLOOR_EAST_WEST = box(0.0, 0.0, 1.0, 16.0, 1.0, 15.0);
    private static final VoxelShape NORTH_WALL_SHAPE = box(1.0, 0.0, 15.0, 15.0, 16.0, 16.0);
    private static final VoxelShape SOUTH_WALL_SHAPE = box(1.0, 0.0, 0.0, 15.0, 16.0, 1.0);
    private static final VoxelShape WEST_WALL_SHAPE = box(15.0, 0.0, 1.0, 16.0, 16.0, 15.0);
    private static final VoxelShape EAST_WALL_SHAPE = box(0.0, 0.0, 1.0, 1.0, 16.0, 15.0);

    public ManifestBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACE, AttachFace.FLOOR)
                .setValue(FACING, Direction.NORTH));
    }

    public ManifestBlock() {
        this(BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .instabreak()
                .sound(SoundType.WOOD)
                .noOcclusion()
                .pushReaction(PushReaction.DESTROY)
                .isViewBlocking((state, level, pos) -> false)
                .isSuffocating((state, level, pos) -> false));
    }

    @Override
    @Nonnull
    protected MapCodec<? extends FaceAttachedHorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(@Nonnull BlockPos pos, @Nonnull BlockState state) {
        return new ManifestBlockEntity(pos, state);
    }

    /**
     * isAttachedTo: 判断清单是否贴在指定宿主方块的表面上。
     */
    public static boolean isAttachedTo(@Nonnull BlockState state, @Nonnull BlockPos manifestPos, @Nonnull BlockPos hostPos) {
        if (!(state.getBlock() instanceof ManifestBlock)) {
            return false;
        }
        Direction support = getConnectedDirection(state).getOpposite();
        return manifestPos.relative(support).equals(hostPos);
    }

    /**
     * getStateForPlacement: 只允许贴地或挂墙，跳过天花板附着。
     */
    @Override
    @Nullable
    public BlockState getStateForPlacement(@Nonnull BlockPlaceContext context) {
        for (Direction direction : context.getNearestLookingDirections()) {
            if (direction == Direction.UP) {
                continue;
            }
            BlockState state = direction == Direction.DOWN
                    ? defaultBlockState()
                    .setValue(FACE, AttachFace.FLOOR)
                    .setValue(FACING, context.getHorizontalDirection())
                    : defaultBlockState()
                    .setValue(FACE, AttachFace.WALL)
                    .setValue(FACING, direction.getOpposite());
            if (state.canSurvive(context.getLevel(), context.getClickedPos())) {
                return state;
            }
        }
        return null;
    }

    /**
     * canSurvive: 天花板状态直接失效，其余方向需要支撑面。
     */
    @Override
    protected boolean canSurvive(@Nonnull BlockState state, @Nonnull LevelReader level, @Nonnull BlockPos pos) {
        if (state.getValue(FACE) == AttachFace.CEILING) {
            return false;
        }
        return super.canSurvive(state, level, pos);
    }

    @Override
    @Nonnull
    protected VoxelShape getShape(@Nonnull BlockState state, @Nonnull BlockGetter level, @Nonnull BlockPos pos,
                                  @Nonnull CollisionContext context) {
        if (state.getValue(FACE) == AttachFace.FLOOR) {
            return state.getValue(FACING).getAxis() == Direction.Axis.X ? FLOOR_EAST_WEST : FLOOR_NORTH_SOUTH;
        }
        return switch (state.getValue(FACING)) {
            case SOUTH -> SOUTH_WALL_SHAPE;
            case WEST -> WEST_WALL_SHAPE;
            case EAST -> EAST_WALL_SHAPE;
            default -> NORTH_WALL_SHAPE;
        };
    }

    @Override
    public void setPlacedBy(@Nonnull Level level, @Nonnull BlockPos pos, @Nonnull BlockState state,
                            @Nullable LivingEntity placer, @Nonnull ItemStack stack) {
        if (level.getBlockEntity(pos) instanceof ManifestBlockEntity manifest) {
            manifest.loadFromItem(stack);
        }
    }

    @Override
    @Nonnull
    protected InteractionResult useWithoutItem(@Nonnull BlockState state, @Nonnull Level level, @Nonnull BlockPos pos,
                                               @Nonnull Player player, @Nonnull BlockHitResult hit) {
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            ManifestOpenResponsePacket.openFor(serverLevel, serverPlayer, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    @Nonnull
    protected List<ItemStack> getDrops(@Nonnull BlockState state, @Nonnull LootParams.Builder params) {
        List<ItemStack> drops = super.getDrops(state, params);
        if (!(params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof ManifestBlockEntity manifest)) {
            return drops;
        }
        ItemStack stored = manifest.toItem();
        if (drops.isEmpty()) {
            return List.of(stored);
        }
        List<ItemStack> result = new ArrayList<>(drops.size());
        for (ItemStack drop : drops) {
            if (drop.is(ModItems.MANIFEST.get())) {
                ItemStack copy = stored.copy();
                copy.setCount(drop.getCount());
                result.add(copy);
            } else {
                result.add(drop);
            }
        }
        return result;
    }

    @Override
    @Nonnull
    public ItemStack getCloneItemStack(@Nonnull BlockState state, @Nonnull HitResult target, @Nonnull LevelReader level,
                                       @Nonnull BlockPos pos, @Nonnull Player player) {
        if (level.getBlockEntity(pos) instanceof ManifestBlockEntity manifest) {
            return manifest.toItem();
        }
        return new ItemStack(ModItems.MANIFEST.get());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACE, FACING);
    }
}
