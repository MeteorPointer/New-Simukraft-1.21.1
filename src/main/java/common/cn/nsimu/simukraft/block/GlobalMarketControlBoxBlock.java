package common.cn.nsimu.simukraft.block;

import common.cn.nsimu.simukraft.globalmarket.GlobalMarketControlBoxService;
import common.cn.nsimu.simukraft.network.globalmarket.GlobalMarketControlBoxOpenRequestPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;

/**
 * GlobalMarketControlBoxBlock: 全球市场控制箱，打开雇佣管理界面。
 */
public final class GlobalMarketControlBoxBlock extends Block {
    public GlobalMarketControlBoxBlock() {
        super(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK));
        registerDefaultState(stateDefinition.any().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    }

    /**
     * getStateForPlacement: 点在墙面上时正面朝外，点在地面或顶面时正面朝向玩家。
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clicked = context.getClickedFace();
        Direction facing = clicked.getAxis().isHorizontal()
                ? clicked
                : context.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
    }

    /**
     * rotate: 结构旋转时同步水平朝向。
     */
    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(
                BlockStateProperties.HORIZONTAL_FACING,
                rotation.rotate(state.getValue(BlockStateProperties.HORIZONTAL_FACING)));
    }

    /**
     * mirror: 结构镜像时同步水平朝向。
     */
    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        return state.setValue(BlockStateProperties.HORIZONTAL_FACING, mirror.getRotation(facing).rotate(facing));
    }

    /**
     * useWithoutItem: 空手右键打开全球市场控制箱。
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            GlobalMarketControlBoxOpenRequestPacket.openFor(serverLevel, serverPlayer, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    /**
     * onRemove: 控制箱消失时清理市场，并在没有其他市场时掉落仓库。
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide() && !state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            GlobalMarketControlBoxService.onRemoved(serverLevel, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /**
     * createBlockStateDefinition: 注册控制箱的水平朝向属性。
     */
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.HORIZONTAL_FACING);
    }
}
