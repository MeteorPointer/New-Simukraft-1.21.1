package common.cn.nsimu.simukraft.planner;

import common.cn.nsimu.simukraft.economy.EconomyService;
import common.cn.nsimu.simukraft.protection.NpcBlockProtectionPolicy;
import common.cn.nsimu.simukraft.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * 规划任务的预扣费用。金额与 {@code CreatePlanningTaskPacket} 开工扣款使用同一公式。
 */
public final class PlannerBilling {
    private PlannerBilling() {
    }

    /**
     * 按目标方块数和单价算出预扣金额，保留两位小数。
     */
    public static double cost(int targetBlocks, double moneyPerBlock) {
        if (targetBlocks <= 0 || !(moneyPerBlock > 0.0D) || !Double.isFinite(moneyPerBlock)) {
            return 0.0D;
        }
        return EconomyService.normalizeAmount(targetBlocks * moneyPerBlock);
    }

    /**
     * 统计清除操作里真正会计费的方块。空气、未加载、建筑盒、基岩、城市核心、黑名单和容器不计。
     * 与 {@code PlannerWorkService} 创建任务时的清除判定一致。
     */
    public static int countRemoveTargets(Level level, BlockPos boxPos, BlockPos min, BlockPos max) {
        if (level == null || boxPos == null || min == null || max == null) {
            return 0;
        }
        int minX = Math.min(min.getX(), max.getX());
        int minY = Math.min(min.getY(), max.getY());
        int minZ = Math.min(min.getZ(), max.getZ());
        int maxX = Math.max(min.getX(), max.getX());
        int maxY = Math.max(min.getY(), max.getY());
        int maxZ = Math.max(min.getZ(), max.getZ());
        int count = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    cursor.set(x, y, z);
                    if (isRemoveTarget(level, boxPos, cursor)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private static boolean isRemoveTarget(Level level, BlockPos boxPos, BlockPos pos) {
        if (!level.isLoaded(pos) || pos.equals(boxPos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir()
                || state.is(Blocks.BEDROCK)
                || state.is(ModBlocks.BUILD_BOX.get())
                || state.is(ModBlocks.CITY_CORE.get())
                || NpcBlockProtectionPolicy.isProtected(state)) {
            return false;
        }
        return !isContainer(level, pos, state);
    }

    private static boolean isContainer(Level level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof ChestBlock) {
            return true;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof Container) {
            return true;
        }
        if (blockEntity == null) {
            return false;
        }
        if (hasItemHandler(level, pos, null)) {
            return true;
        }
        for (Direction direction : Direction.values()) {
            if (hasItemHandler(level, pos, direction)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasItemHandler(Level level, BlockPos pos, Direction side) {
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side);
        return handler != null && handler.getSlots() > 0;
    }
}
