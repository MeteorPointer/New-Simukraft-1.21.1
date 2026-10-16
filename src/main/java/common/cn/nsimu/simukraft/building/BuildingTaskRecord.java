package common.cn.nsimu.simukraft.building;

import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.UUID;

public record BuildingTaskRecord(UUID taskId,
                                 UUID citizenId,
                                 UUID cityId,
                                 String dimensionId,
                                 BlockPos buildBoxPos,
                                 String category,
                                 String buildingFileName,
                                 String displayName,
                                 String structureFileName,
                                 BlockPos origin,
                                 int rotationDegrees,
                                 int currentIndex,
                                 int totalBlocks,
                                 String status,
                                 long createdAt,
                                 long updatedAt,
                                 List<BuildingPoiDefinition> poiDefinitions) {
}
