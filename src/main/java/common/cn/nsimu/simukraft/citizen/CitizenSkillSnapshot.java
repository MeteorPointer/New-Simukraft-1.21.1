package common.cn.nsimu.simukraft.citizen;

import common.cn.nsimu.simukraft.job.CityJobType;

public record CitizenSkillSnapshot(CityJobType skillType, int level, int xp, int maxLevel) {
    public boolean maxLevelReached() {
        return level >= maxLevel;
    }
}
