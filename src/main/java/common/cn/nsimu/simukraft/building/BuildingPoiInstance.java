package common.cn.nsimu.simukraft.building;

import common.cn.nsimu.simukraft.city.poi.CityPoiType;
import net.minecraft.core.BlockPos;

public record BuildingPoiInstance(String key, CityPoiType poiType, int capacity, BlockPos worldPos) {
}
