package common.cn.nsimu.simukraft.building;

import common.cn.nsimu.simukraft.city.poi.CityPoiType;

public record BuildingPoiDefinition(String id, CityPoiType poiType, int capacity) {
}
