package common.cn.nsimu.simukraft.job;

import common.cn.nsimu.simukraft.city.CityPopulationStats;
import common.cn.nsimu.simukraft.city.poi.CityPoiManager;
import common.cn.nsimu.simukraft.city.poi.CityPoiType;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class CityJobCapacityService {
    private CityJobCapacityService() {
    }

    public static List<JobCapacity> getJobCapacities(ServerLevel level, UUID cityId) {
        if (level == null || cityId == null) {
            return List.of();
        }
        CityPoiManager poiManager = CityPoiManager.get(level);
        List<JobCapacity> capacities = new ArrayList<>();
        addIfPresent(capacities, CityJobType.RESIDENT, 0, CityPopulationStats.population(level, cityId));
        addIfPresent(capacities, CityJobType.COMMERCIAL_WORKER, poiManager.getCityPois(cityId, CityPoiType.COMMERCIAL).size(), poiManager.getActiveCapacity(cityId, CityPoiType.COMMERCIAL));
        addIfPresent(capacities, CityJobType.INDUSTRIAL_WORKER, poiManager.getCityPois(cityId, CityPoiType.INDUSTRIAL).size(), poiManager.getActiveCapacity(cityId, CityPoiType.INDUSTRIAL));
        addIfPresent(capacities, CityJobType.FARMER, poiManager.getCityPois(cityId, CityPoiType.FARMLAND).size(), poiManager.getActiveCapacity(cityId, CityPoiType.FARMLAND));
        addIfPresent(capacities, CityJobType.LOGISTICS_WORKER, poiManager.getCityPois(cityId, CityPoiType.LOGISTICS).size(), poiManager.getActiveCapacity(cityId, CityPoiType.LOGISTICS));
        addIfPresent(capacities, CityJobType.STORAGE_WORKER, poiManager.getCityPois(cityId, CityPoiType.STORAGE).size(), poiManager.getActiveCapacity(cityId, CityPoiType.STORAGE));
        addIfPresent(capacities, CityJobType.GUARD, poiManager.getCityPois(cityId, CityPoiType.DEFENSE).size(), poiManager.getActiveCapacity(cityId, CityPoiType.DEFENSE));
        addIfPresent(capacities, CityJobType.GATHERER, poiManager.getCityPois(cityId, CityPoiType.GATHERING).size(), poiManager.getActiveCapacity(cityId, CityPoiType.GATHERING));
        addIfPresent(capacities, CityJobType.OTHER, poiManager.getCityPois(cityId, CityPoiType.OTHER).size(), poiManager.getActiveCapacity(cityId, CityPoiType.OTHER));
        addIfPresent(capacities, CityJobType.MARKET_TRADER, poiManager.getCityPois(cityId, CityPoiType.GLOBAL_MARKET).size(), poiManager.getActiveCapacity(cityId, CityPoiType.GLOBAL_MARKET));
        return List.copyOf(capacities);
    }

    private static void addIfPresent(List<JobCapacity> capacities, CityJobType type, int pointCount, int capacity) {
        if (pointCount > 0 || capacity > 0) {
            capacities.add(new JobCapacity(type, pointCount, capacity));
        }
    }

    public record JobCapacity(CityJobType type, int pointCount, int capacity) {
    }
}
