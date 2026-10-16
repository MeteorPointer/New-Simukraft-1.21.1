package common.cn.nsimu.simukraft.industrial;

import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.UUID;

public record IndustrialControlBoxView(BlockPos boxPos,
                                       boolean hasBuilding,
                                       String buildingName,
                                       boolean definitionValid,
                                       String definitionName,
                                       String statusKey,
                                       String statusText,
                                       boolean running,
                                       String selectedRecipeId,
                                       boolean hasWorker,
                                       UUID workerId,
                                       String workerName,
                                       boolean hasBuildingBounds,
                                       BlockPos boundsMin,
                                       BlockPos boundsMax,
                                       boolean integrityAvailable,
                                       double integrityPercent,
                                       int integrityRepairableBlocks,
                                       int integrityManualRepairBlocks,
                                       double integrityRepairCost,
                                       List<PointMarker> pointMarkers,
                                       List<RecipeEntry> recipes) {
    public record RecipeEntry(String id, String name, List<ItemEntry> inputs, List<ItemEntry> outputs) {
    }

    public record ItemEntry(String itemId, String potionId, int count, String connector, String itemSpec) {
        public ItemEntry(String itemId, String potionId, int count) {
            this(itemId, potionId, count, "", IndustrialItemStackSpec.of(itemId, potionId).serialized());
        }

        public ItemEntry(IndustrialItemStackSpec spec, int count, String connector) {
            this(spec.displayItemId(), spec.potionId(), count, connector, spec.serialized());
        }
    }

    public record PointMarker(String id, String kind, BlockPos pos, int color) {
    }
}
