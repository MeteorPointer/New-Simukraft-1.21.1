package common.cn.nsimu.simukraft.job;

import java.util.UUID;

public record CityJobAssignment(UUID citizenId, UUID cityId, CityJobType jobType, UUID workplacePoiId,
                                long assignedGameTime) {
    public boolean assigned() {
        return jobType != null && jobType != CityJobType.RESIDENT && workplacePoiId != null;
    }
}
