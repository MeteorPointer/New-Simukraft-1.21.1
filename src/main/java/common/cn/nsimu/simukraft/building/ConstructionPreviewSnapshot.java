package common.cn.nsimu.simukraft.building;

import net.minecraft.core.BlockPos;

import java.util.UUID;

/**
 * ConstructionPreviewSnapshot: 施工中建筑的轻量快照，只含预览和悬浮信息，不含方块列表。
 */
public record ConstructionPreviewSnapshot(UUID taskId,
                                          String category,
                                          String buildingFileName,
                                          String structureFileName,
                                          BlockPos origin,
                                          int rotationDegrees,
                                          int currentBlockIndex,
                                          int totalBlocks,
                                          String builderName,
                                          String displayName) {
    public ConstructionPreviewSnapshot {
        category = category == null ? "" : category;
        buildingFileName = buildingFileName == null ? "" : buildingFileName;
        structureFileName = structureFileName == null ? "" : structureFileName;
        builderName = builderName == null ? "" : builderName;
        displayName = displayName == null ? "" : displayName;
        rotationDegrees = Math.floorMod(rotationDegrees, 360);
        currentBlockIndex = Math.max(0, currentBlockIndex);
        totalBlocks = Math.max(0, totalBlocks);
    }
}
