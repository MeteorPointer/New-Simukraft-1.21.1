package common.cn.kafei.simukraft.mining;

import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenTeleportService;
import common.cn.kafei.simukraft.citizen.CitizenWorkplaceMoveService;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import common.cn.kafei.simukraft.material.GenericContainerAccess;
import common.cn.kafei.simukraft.path.CitizenNavigationService;
import common.cn.kafei.simukraft.path.MovementIntent;
import common.cn.kafei.simukraft.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/** MiningBoxWorkService: 矿工到选区挖掘，产物放入旁边的箱子，结束后回到挖掘盒上方。 */
public final class MiningBoxWorkService {
    private static final double WORK_DISTANCE_SQR = 9.0D;

    private MiningBoxWorkService() {
    }

    public static void tick(ServerLevel level) {
        if (level == null) {
            return;
        }
        MiningBoxManager manager = MiningBoxManager.get(level);
        for (MiningBoxState state : manager.states()) {
            if (!state.running() || !state.hasArea() || !MiningBoxService.isMiningBox(level, state.pos())) {
                continue;
            }
            CitizenData miner = MiningBoxService.findMiner(level, state.pos());
            if (miner == null) {
                continue;
            }
            BlockPos chest = adjacentChest(level, state.pos());
            if (chest == null) {
                continue;
            }
            CitizenEntity entity = CitizenTeleportService.findLoadedCitizenEntity(level, miner.uuid());
            if (entity == null) {
                continue;
            }
            if (state.mode().coversGlass() && !state.glassCoverPlaced()) {
                BlockPos glassPos = nextGlassPos(level, state);
                if (glassPos == null) {
                    manager.update(state.withGlassCoverPlaced());
                    continue;
                }
                if (!moveToWork(level, entity, miner, glassPos)) {
                    continue;
                }
                level.setBlock(glassPos, Blocks.GLASS.defaultBlockState(), 3);
                entity.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                continue;
            }
            BlockPos workPos = nextDigPos(level, state);
            if (workPos == null) {
                int skippedTo = skipEmptyLayers(level, state);
                if (skippedTo < level.getMinBuildHeight()) {
                    returnHome(level, manager, state, miner);
                } else {
                    manager.update(state.withDigY(skippedTo));
                }
                continue;
            }
            if (!moveToWork(level, entity, miner, workPos)) {
                continue;
            }
            if (digIntoChest(level, workPos, chest, entity) && state.currentDigY() != workPos.getY()) {
                manager.update(state.withDigY(workPos.getY()));
            }
        }
    }

    private static boolean moveToWork(ServerLevel level, CitizenEntity entity, CitizenData miner, BlockPos workPos) {
        if (entity.position().distanceToSqr(Vec3.atCenterOf(workPos)) <= WORK_DISTANCE_SQR) {
            return true;
        }
        Vec3 stand = CitizenWorkplaceMoveService.targetNearWorkplace(level, workPos)
                .orElse(Vec3.atBottomCenterOf(workPos));
        CitizenNavigationService.requestMove(level, miner.uuid(), stand, MovementIntent.WORK);
        return false;
    }

    @Nullable
    private static BlockPos adjacentChest(ServerLevel level, BlockPos boxPos) {
        for (Direction direction : Direction.values()) {
            BlockPos candidate = boxPos.relative(direction);
            if (GenericContainerAccess.isContainer(level, candidate)) {
                return candidate;
            }
        }
        return null;
    }

    @Nullable
    private static BlockPos nextGlassPos(ServerLevel level, MiningBoxState state) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int coverY = state.topY();
        for (int x = state.minCorner().getX(); x <= state.maxCorner().getX(); x++) {
            for (int z = state.minCorner().getZ(); z <= state.maxCorner().getZ(); z++) {
                cursor.set(x, coverY, z);
                if (!level.isLoaded(cursor) || isProtected(level, state, cursor)) {
                    continue;
                }
                if (!level.getBlockState(cursor).is(Blocks.GLASS)) {
                    return cursor.immutable();
                }
            }
        }
        return null;
    }

    @Nullable
    private static BlockPos nextDigPos(ServerLevel level, MiningBoxState state) {
        int layerY = Math.min(state.currentDigY(), state.mode().coversGlass() ? state.topY() - 1 : state.topY());
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        if (layerY < level.getMinBuildHeight()) {
            return null;
        }
        for (int x = state.minCorner().getX(); x <= state.maxCorner().getX(); x++) {
            for (int z = state.minCorner().getZ(); z <= state.maxCorner().getZ(); z++) {
                cursor.set(x, layerY, z);
                if (!level.isLoaded(cursor) || isProtected(level, state, cursor)) {
                    continue;
                }
                if (isMineable(level, cursor, level.getBlockState(cursor))) {
                    return cursor.immutable();
                }
            }
        }
        return null;
    }

    private static int skipEmptyLayers(ServerLevel level, MiningBoxState state) {
        int layerY = Math.min(state.currentDigY(), state.mode().coversGlass() ? state.topY() - 1 : state.topY()) - 1;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int scanned = 0;
        while (layerY >= level.getMinBuildHeight() && scanned < 4) {
            for (int x = state.minCorner().getX(); x <= state.maxCorner().getX(); x++) {
                for (int z = state.minCorner().getZ(); z <= state.maxCorner().getZ(); z++) {
                    cursor.set(x, layerY, z);
                    if (!level.isLoaded(cursor) || isProtected(level, state, cursor)) {
                        continue;
                    }
                    if (isMineable(level, cursor, level.getBlockState(cursor))) {
                        return layerY;
                    }
                }
            }
            layerY--;
            scanned++;
        }
        return layerY;
    }

    private static boolean digIntoChest(ServerLevel level, BlockPos pos, BlockPos chest, CitizenEntity miner) {
        BlockState block = level.getBlockState(pos);
        if (!isMineable(level, pos, block)) {
            return false;
        }
        List<ItemStack> drops = copyStacks(Block.getDrops(block, level, pos, level.getBlockEntity(pos)));
        if (!drops.isEmpty() && !GenericContainerAccess.simulateInsert(level, chest, drops).isEmpty()) {
            return false;
        }
        if (!level.destroyBlock(pos, false, miner)) {
            return false;
        }
        miner.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        for (ItemStack drop : drops) {
            ItemStack remaining = GenericContainerAccess.insert(level, chest, drop);
            if (!remaining.isEmpty()) {
                net.minecraft.world.level.block.Block.popResource(level, chest, remaining);
            }
        }
        return true;
    }

    private static void returnHome(ServerLevel level, MiningBoxManager manager, MiningBoxState state, CitizenData miner) {
        BlockPos home = state.pos().above();
        Vec3 landing = CitizenTeleportService.safeLandingTarget(level, home);
        if (landing == null) {
            landing = Vec3.atBottomCenterOf(home);
        }
        CitizenNavigationService.stop(level, miner.uuid());
        CitizenTeleportService.teleportCitizen(level, miner.uuid(), landing);
        manager.update(state.toggleRunning());
    }

    private static boolean isProtected(ServerLevel level, MiningBoxState state, BlockPos pos) {
        return pos.equals(state.pos()) || level.getBlockState(pos).is(ModBlocks.MINING_BOX.get());
    }

    private static boolean isMineable(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            return false;
        }
        if (state.getDestroySpeed(level, pos) < 0.0F) {
            return false;
        }
        return !state.is(Blocks.GLASS) && !state.is(ModBlocks.MINING_BOX.get());
    }

    private static List<ItemStack> copyStacks(List<ItemStack> stacks) {
        List<ItemStack> copies = new ArrayList<>();
        if (stacks == null) {
            return copies;
        }
        for (ItemStack stack : stacks) {
            if (stack != null && !stack.isEmpty()) {
                copies.add(stack.copy());
            }
        }
        return copies;
    }
}
