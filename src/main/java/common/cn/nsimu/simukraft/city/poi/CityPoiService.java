package common.cn.nsimu.simukraft.city.poi;

import common.cn.nsimu.simukraft.city.CityService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class CityPoiService {
    private CityPoiService() {
    }

    public static Optional<CityPoiData> registerPoiAtCityCore(ServerLevel level, BlockPos cityCorePos, BlockPos poiPos, CityPoiType type, int capacity) {
        if (level == null || cityCorePos == null || poiPos == null) {
            return Optional.empty();
        }
        return CityService.findCityByCorePos(level, cityCorePos)
                .map(city -> registerPoi(level, city.cityId(), poiPos, type, capacity));
    }

    public static CityPoiData registerPoi(ServerLevel level, UUID cityId, BlockPos pos, CityPoiType type, int capacity) {
        return CityPoiManager.get(level).registerPoi(cityId, pos, type, capacity);
    }

    public static boolean deactivatePoi(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) {
            return false;
        }
        return CityPoiManager.get(level).deactivatePoi(pos);
    }

    public static List<CityPoiData> findPois(ServerLevel level, UUID cityId, CityPoiType type) {
        if (level == null || cityId == null || type == null) {
            return List.of();
        }
        return CityPoiManager.get(level).getCityPois(cityId, type);
    }

    public static int getCapacity(ServerLevel level, UUID cityId, CityPoiType type) {
        if (level == null || cityId == null || type == null) {
            return 0;
        }
        return CityPoiManager.get(level).getActiveCapacity(cityId, type);
    }
}
