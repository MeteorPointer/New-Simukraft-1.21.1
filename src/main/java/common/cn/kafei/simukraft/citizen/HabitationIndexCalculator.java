package common.cn.kafei.simukraft.citizen;

import common.cn.kafei.simukraft.building.BuildingAbandonmentService;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.city.poi.CityPoiManager;
import common.cn.kafei.simukraft.city.poi.CityPoiType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Set;
import java.util.UUID;

public final class HabitationIndexCalculator {
    private HabitationIndexCalculator() {
    }

    public static double calculate(PlacedBuildingRecord building,
            CityPoiManager poiManager, Set<UUID> occupiedPoiIds) {
        return calculate(null, building, poiManager, occupiedPoiIds);
    }

    public static double calculate(ServerLevel level, PlacedBuildingRecord building,
            CityPoiManager poiManager, Set<UUID> occupiedPoiIds) {
        double vol = volumeScore(building);
        double vac = vacancyScore(building, poiManager, occupiedPoiIds);
        int abandonment = level != null
                ? BuildingAbandonmentService.get(level, building.buildingId()) : 0;
        double livability = 100.0 - abandonment;
        return (vol + vac + livability) / 3.0;
    }

    public static double preferenceScore(PlacedBuildingRecord building,
            CityPoiManager poiManager, Set<UUID> occupiedPoiIds, int expectedBeds) {
        return preferenceScore(null, building, poiManager, occupiedPoiIds, expectedBeds);
    }

    public static double preferenceScore(ServerLevel level, PlacedBuildingRecord building,
            CityPoiManager poiManager, Set<UUID> occupiedPoiIds, int expectedBeds) {
        double index = calculate(level, building, poiManager, occupiedPoiIds);
        int vacant = countVacantResidential(building, poiManager, occupiedPoiIds);
        double matchCoeff;
        if (vacant < expectedBeds) {
            matchCoeff = expectedBeds > 0 ? (double) vacant / expectedBeds : 0;
        } else {
            // 空床刚好或更多都是 1。再给富余加分会让同一户每天搬去下一栋更大的空房。
            matchCoeff = 1.0;
        }
        return index * matchCoeff;
    }

    static int countVacantResidential(PlacedBuildingRecord building,
            CityPoiManager poiManager, Set<UUID> occupiedPoiIds) {
        int count = 0;
        for (var instance : building.poiInstances()) {
            if (instance.poiType() != CityPoiType.RESIDENTIAL) continue;
            var poi = poiManager.getPoiAt(instance.worldPos());
            if (poi == null || !poi.active()) continue;
            if (!occupiedPoiIds.contains(poi.poiId())) count++;
        }
        return count;
    }

    static int countTotalResidential(PlacedBuildingRecord building) {
        int count = 0;
        for (var instance : building.poiInstances()) {
            if (instance.poiType() == CityPoiType.RESIDENTIAL) count++;
        }
        return count;
    }

    private static double volumeScore(PlacedBuildingRecord building) {
        BlockPos min = building.minPos();
        BlockPos max = building.maxPos();
        if (min == null || max == null) return 10;
        long volume = (long) Math.abs(max.getX() - min.getX() + 1)
                * Math.abs(max.getY() - min.getY() + 1)
                * Math.abs(max.getZ() - min.getZ() + 1);
        if (volume < 100) return 10;
        if (volume < 500) return 30;
        if (volume < 1000) return 50;
        if (volume < 3000) return 70;
        return 100;
    }

    private static double vacancyScore(PlacedBuildingRecord building,
            CityPoiManager poiManager, Set<UUID> occupiedPoiIds) {
        int total = countTotalResidential(building);
        if (total == 0) return 50; // 无床位：中性值
        int vacant = countVacantResidential(building, poiManager, occupiedPoiIds);
        return (double) vacant / total * 100.0;
    }
}
