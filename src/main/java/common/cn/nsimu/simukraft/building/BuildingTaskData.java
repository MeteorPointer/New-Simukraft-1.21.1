package common.cn.nsimu.simukraft.building;

import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.UUID;

public record BuildingTaskData(UUID taskId,
                               UUID citizenId,
                               UUID cityId,
                               String dimensionId,
                               BlockPos buildBoxPos,
                               String category,
                               String buildingFileName,
                               String displayName,
                               String amount,
                               String structureFileName,
                               BlockPos origin,
                               int rotationDegrees,
                               int currentBlockIndex,
                               int totalBlocks,
                               String status,
                               long createdAt,
                               long updatedAt,
                               List<BuildingPoiDefinition> poiDefinitions,
                               boolean replaceWithAir) {

    public BuildingTaskData withStatus(BuildingTaskStatus status) {
        return new BuildingTaskData(taskId, citizenId, cityId, dimensionId, buildBoxPos, category, buildingFileName, displayName, amount, structureFileName, origin, rotationDegrees, currentBlockIndex, totalBlocks, status.id(), createdAt, System.currentTimeMillis(), poiDefinitions, replaceWithAir);
    }

    public BuildingTaskData withProgress(int index, BuildingTaskStatus status) {
        return new BuildingTaskData(taskId, citizenId, cityId, dimensionId, buildBoxPos, category, buildingFileName, displayName, amount, structureFileName, origin, rotationDegrees, index, totalBlocks, status.id(), createdAt, System.currentTimeMillis(), poiDefinitions, replaceWithAir);
    }
}
