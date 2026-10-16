package common.cn.kafei.simukraft.mining;

import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.job.CitizenEmploymentService;
import common.cn.kafei.simukraft.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** MiningBoxService: 挖矿盒的矿工岗位和开关状态。 */
public final class MiningBoxService {
    public static final String HIRE_SOURCE_TYPE = "mining_box";
    public static final String HIRE_ROLE = "miner";

    private MiningBoxService() {
    }

    public static boolean isMiningBox(ServerLevel level, BlockPos pos) {
        return level != null && pos != null && level.isLoaded(pos)
                && level.getBlockState(pos).is(ModBlocks.MINING_BOX.get());
    }

    public static CitizenData findMiner(ServerLevel level, BlockPos pos) {
        return CitizenEmploymentService.findAssigned(level, HIRE_SOURCE_TYPE, HIRE_ROLE, pos).orElse(null);
    }

    public static MiningBoxState state(ServerLevel level, BlockPos pos) {
        return MiningBoxManager.get(level).getOrCreate(pos);
    }

    /** setArea: 记下玩家的两个角点，并把挖掘进度放到更高的那一层。 */
    public static boolean setArea(ServerLevel level, BlockPos pos, BlockPos first, BlockPos second) {
        if (first == null || second == null || first.equals(second)) {
            return false;
        }
        MiningBoxManager.get(level).update(state(level, pos).withArea(first, second));
        return true;
    }

    public static MiningBoxState cycleMode(ServerLevel level, BlockPos pos) {
        return MiningBoxManager.get(level).update(state(level, pos).cycleMode());
    }

    public static MiningBoxState toggleRunning(ServerLevel level, BlockPos pos) {
        return MiningBoxManager.get(level).update(state(level, pos).toggleRunning());
    }

    public static void onRemoved(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) {
            return;
        }
        CitizenEmploymentService.fireAssigned(level,
                CitizenEmploymentService.workplaceId(HIRE_SOURCE_TYPE, HIRE_ROLE, pos),
                HIRE_SOURCE_TYPE, HIRE_ROLE, pos, "mining_box_removed");
        MiningBoxManager.get(level).remove(pos);
    }
}
