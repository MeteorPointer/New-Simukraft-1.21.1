package common.cn.kafei.simukraft.building;

import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record PlacedBuildingRecord(UUID buildingId,
                                   UUID cityId,
                                   UUID districtId,
                                   String dimensionId,
                                   String category,
                                   String buildingFileName,
                                   String displayName,
                                   String amount,
                                   String structureFileName,
                                   String facing,
                                   BlockPos worldOrigin,
                                   BlockPos structureAnchor,
                                   BlockPos minPos,
                                   BlockPos maxPos,
                                   long completedAt,
                                   BuildingVoxelSnapshot voxels,
                                   List<BuildingPoiDefinition> poiDefinitions,
                                   List<BuildingPoiInstance> poiInstances,
                                   List<BuildingUnitDefinition> unitDefinitions,
                                   List<BuildingUnitInstance> unitInstances) {
    public PlacedBuildingRecord {
        voxels = voxels != null ? voxels : BuildingVoxelSnapshot.empty();
    }

    public PlacedBuildingRecord(UUID buildingId,
                                UUID cityId,
                                String dimensionId,
                                String category,
                                String buildingFileName,
                                String displayName,
                                String amount,
                                String structureFileName,
                                String facing,
                                BlockPos worldOrigin,
                                BlockPos structureAnchor,
                                BlockPos minPos,
                                BlockPos maxPos,
                                long completedAt,
                                List<BuildingBlockData> blocks,
                                List<BuildingPoiDefinition> poiDefinitions,
                                List<BuildingPoiInstance> poiInstances,
                                List<BuildingUnitDefinition> unitDefinitions,
                                List<BuildingUnitInstance> unitInstances) {
        this(buildingId, cityId, null, dimensionId, category, buildingFileName, displayName, amount, structureFileName, facing,
                worldOrigin, structureAnchor, minPos, maxPos, completedAt, BuildingVoxelSnapshot.of(blocks),
                poiDefinitions, poiInstances, unitDefinitions, unitInstances);
    }

    public PlacedBuildingRecord(UUID buildingId,
                                UUID cityId,
                                UUID districtId,
                                String dimensionId,
                                String category,
                                String buildingFileName,
                                String displayName,
                                String amount,
                                String structureFileName,
                                String facing,
                                BlockPos worldOrigin,
                                BlockPos structureAnchor,
                                BlockPos minPos,
                                BlockPos maxPos,
                                long completedAt,
                                List<BuildingBlockData> blocks,
                                List<BuildingPoiDefinition> poiDefinitions,
                                List<BuildingPoiInstance> poiInstances,
                                List<BuildingUnitDefinition> unitDefinitions,
                                List<BuildingUnitInstance> unitInstances) {
        this(buildingId, cityId, districtId, dimensionId, category, buildingFileName, displayName, amount, structureFileName,
                facing, worldOrigin, structureAnchor, minPos, maxPos, completedAt, BuildingVoxelSnapshot.of(blocks),
                poiDefinitions, poiInstances, unitDefinitions, unitInstances);
    }

    public List<BuildingBlockData> blocks() {
        return voxels.blocks();
    }

    /** withDistrictId: 复制建筑记录并替换分区归属，其它字段保持不变。 */
    public PlacedBuildingRecord withDistrictId(UUID districtId) {
        if (Objects.equals(this.districtId, districtId)) {
            return this;
        }
        return new PlacedBuildingRecord(buildingId, cityId, districtId, dimensionId, category, buildingFileName,
                displayName, amount, structureFileName, facing, worldOrigin, structureAnchor, minPos, maxPos,
                completedAt, voxels, poiDefinitions, poiInstances, unitDefinitions, unitInstances);
    }
}
