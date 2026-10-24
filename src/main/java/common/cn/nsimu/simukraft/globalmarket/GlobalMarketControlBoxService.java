package common.cn.nsimu.simukraft.globalmarket;

import common.cn.nsimu.simukraft.building.*;
import common.cn.nsimu.simukraft.citizen.CitizenData;
import common.cn.nsimu.simukraft.citizen.CitizenTeleportService;
import common.cn.nsimu.simukraft.economy.EconomyService;
import common.cn.nsimu.simukraft.job.CitizenEmploymentService;
import common.cn.nsimu.simukraft.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * GlobalMarketControlBoxService: 全球市场控制箱绑定、职员岗位和交易入口。
 */
public final class GlobalMarketControlBoxService {
    public static final String HIRE_SOURCE_TYPE = "global_market_control_box";
    public static final String HIRE_ROLE = "market_trader";
    private static final double TRADE_RANGE_SQR = 4.5D * 4.5D;

    private GlobalMarketControlBoxService() {
    }

    /**
     * buildView: 构建控制箱管理界面快照。
     */
    public static GlobalMarketControlBoxView buildView(ServerLevel level, BlockPos boxPos) {
        PlacedBuildingRecord building = resolveBuilding(level, boxPos);
        CitizenData worker = findAssignedWorker(level, boxPos);
        BuildingIntegrityService.IntegrityPreview integrity = BuildingIntegrityService.preview(level, building);
        String statusKey = building == null
                ? "gui.simukraft.global_market.status.no_building"
                : worker == null ? "gui.simukraft.global_market.status.no_worker"
                : "gui.simukraft.global_market.status.open";
        return new GlobalMarketControlBoxView(
                boxPos.immutable(),
                building != null,
                BuildingCatalog.localizedDisplayName(building),
                statusKey,
                worker != null,
                worker != null ? worker.uuid() : null,
                worker != null ? worker.name() : "",
                building != null && building.cityId() != null
                        ? EconomyService.getCityBalance(level, building.cityId())
                        : 0.0D,
                building != null,
                building != null ? building.minPos().immutable() : BlockPos.ZERO,
                building != null ? building.maxPos().immutable() : BlockPos.ZERO,
                integrity.available(),
                integrity.percent(),
                integrity.repairableBlocks(),
                integrity.manualRepairBlocks(),
                integrity.repairCost());
    }

    /**
     * resolveBuilding: 解析包含该控制箱的全球市场建筑。
     */
    public static PlacedBuildingRecord resolveBuilding(ServerLevel level, BlockPos boxPos) {
        if (!isControlBox(level, boxPos)) {
            return null;
        }
        PlacedBuildingRecord building = PlacedBuildingService.findByContainedPos(level, boxPos);
        return PublicBuildingTypes.isGlobalMarket(building) ? building : null;
    }

    /**
     * findAssignedWorker: 查询绑定的市场职员。
     */
    public static CitizenData findAssignedWorker(ServerLevel level, BlockPos boxPos) {
        return CitizenEmploymentService.findAssigned(level, HIRE_SOURCE_TYPE, HIRE_ROLE, boxPos).orElse(null);
    }

    /**
     * openForWorker: 玩家右键市场职员时打开全球市场交易界面。
     */
    public static boolean openForWorker(ServerLevel level, ServerPlayer player, CitizenData worker) {
        BlockPos boxPos = resolveWorkerBox(level, worker);
        if (level == null || player == null || boxPos == null || worker == null) {
            return false;
        }
        if (!canUseTradeMenu(level, player, boxPos, worker.uuid())) {
            return false;
        }
        return GlobalMarketService.open(level, player, boxPos, worker.uuid());
    }

    /**
     * canUseTradeMenu: 交易界面仍有效：职员在岗、靠近且没有隔墙。
     */
    public static boolean canUseTradeMenu(ServerLevel level, ServerPlayer player, BlockPos boxPos, UUID workerId) {
        return isValidWorker(level, boxPos, workerId)
                && isTradeReachable(level, player, boxPos, workerId)
                && isControlBox(level, boxPos);
    }

    /**
     * isValidWorker: 该职员仍绑定到这个控制箱。
     */
    public static boolean isValidWorker(ServerLevel level, BlockPos boxPos, UUID workerId) {
        CitizenData worker = findAssignedWorker(level, boxPos);
        return worker != null && workerId != null && workerId.equals(worker.uuid());
    }

    /**
     * resolveWorkerBox: 解析市场职员绑定的控制箱。
     */
    public static BlockPos resolveWorkerBox(ServerLevel level, CitizenData worker) {
        if (level == null || worker == null || worker.workplaceId() == null || worker.workplacePos() == null) {
            return null;
        }
        BlockPos boxPos = worker.workplacePos();
        UUID expected = CitizenEmploymentService.workplaceId(HIRE_SOURCE_TYPE, HIRE_ROLE, boxPos);
        if (!expected.equals(worker.workplaceId()) || !isControlBox(level, boxPos)) {
            return null;
        }
        CitizenData assigned = findAssignedWorker(level, boxPos);
        return assigned != null && worker.uuid().equals(assigned.uuid()) ? boxPos.immutable() : null;
    }

    /**
     * fireWorker: 解雇市场职员。
     */
    public static void fireWorker(ServerLevel level, BlockPos boxPos) {
        CitizenEmploymentService.fireAssigned(level,
                CitizenEmploymentService.workplaceId(HIRE_SOURCE_TYPE, HIRE_ROLE, boxPos),
                HIRE_SOURCE_TYPE, HIRE_ROLE, boxPos, "global_market_fired");
    }

    /**
     * onRemoved: 拆除时解雇职员并注销建筑。
     */
    public static void onRemoved(ServerLevel level, BlockPos boxPos) {
        if (level == null || boxPos == null) {
            return;
        }
        PlacedBuildingRecord building = resolveBuilding(level, boxPos);
        fireWorker(level, boxPos);
        if (building != null) {
            PlacedBuildingService.unregister(level, building.buildingId());
        }
    }

    /**
     * isControlBox: 坐标处是否为全球市场控制箱。
     */
    public static boolean isControlBox(ServerLevel level, BlockPos pos) {
        return level != null && pos != null && level.isLoaded(pos)
                && level.getBlockState(pos).is(ModBlocks.GLOBAL_MARKET_CONTROL_BOX.get());
    }

    private static boolean isTradeReachable(ServerLevel level, ServerPlayer player, BlockPos boxPos, UUID workerId) {
        var workerEntity = CitizenTeleportService.findCitizenEntity(level, workerId);
        return workerEntity != null
                && player.distanceToSqr(workerEntity) <= TRADE_RANGE_SQR
                && hasLineOfSight(level, player, workerEntity);
    }

    private static boolean hasLineOfSight(ServerLevel level, ServerPlayer player, Entity workerEntity) {
        Vec3 from = player.getEyePosition();
        Vec3 to = workerEntity.getEyePosition();
        HitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS;
    }
}
