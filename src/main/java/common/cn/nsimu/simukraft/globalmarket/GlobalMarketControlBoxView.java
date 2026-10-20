package common.cn.nsimu.simukraft.globalmarket;

import net.minecraft.core.BlockPos;

import java.util.UUID;

/**
 * GlobalMarketControlBoxView: 全球市场控制箱管理界面快照。
 */
public record GlobalMarketControlBoxView(BlockPos boxPos,
                                         boolean hasBuilding,
                                         String buildingName,
                                         String statusKey,
                                         boolean hasWorker,
                                         UUID workerId,
                                         String workerName,
                                         double cityBalance,
                                         boolean hasBuildingBounds,
                                         BlockPos boundsMin,
                                         BlockPos boundsMax,
                                         boolean integrityAvailable,
                                         double integrityPercent,
                                         int integrityRepairableBlocks,
                                         int integrityManualRepairBlocks,
                                         double integrityRepairCost) {
    public GlobalMarketControlBoxView {
        buildingName = buildingName != null ? buildingName : "";
        statusKey = statusKey != null ? statusKey : "";
        workerName = workerName != null ? workerName : "";
        boundsMin = boundsMin != null ? boundsMin : BlockPos.ZERO;
        boundsMax = boundsMax != null ? boundsMax : BlockPos.ZERO;
    }
}
