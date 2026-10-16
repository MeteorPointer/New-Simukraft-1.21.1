package common.cn.nsimu.simukraft.building;

import net.minecraft.core.BlockPos;

public record BuildingPreviewSession(String buildingName,
                                     String category,
                                     String structureFileName,
                                     BlockPos origin,
                                     int rotationDegrees,
                                     int blockCount,
                                     String sizeText) {
}
