package common.cn.nsimu.simukraft.building;

import java.util.List;
import java.util.UUID;

public record BuildingUnitInstance(UUID unitId, String label, List<UUID> poiIds) {
}
