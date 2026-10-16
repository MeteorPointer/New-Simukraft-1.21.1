package common.cn.nsimu.simukraft.commercial;

import net.minecraft.core.BlockPos;

import java.util.UUID;

public record CommercialControlBoxView(BlockPos boxPos,
                                       boolean hasBuilding,
                                       String buildingName,
                                       boolean definitionValid,
                                       String definitionName,
                                       String statusKey,
                                       String statusText,
                                       boolean running,
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
}
