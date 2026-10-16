package common.cn.nsimu.simukraft.city.group;

import java.util.UUID;

public record CityUserGroup(UUID cityId, CityUserGroupType type) {
    public CityUserGroup {
        type = type != null ? type : CityUserGroupType.MEMBERS;
    }

    // members: 创建城市全体成员用户组。
    public static CityUserGroup members(UUID cityId) {
        return new CityUserGroup(cityId, CityUserGroupType.MEMBERS);
    }

    // officials: 创建市长与官员用户组。
    public static CityUserGroup officials(UUID cityId) {
        return new CityUserGroup(cityId, CityUserGroupType.OFFICIALS);
    }

    // mayors: 创建市长用户组。
    public static CityUserGroup mayors(UUID cityId) {
        return new CityUserGroup(cityId, CityUserGroupType.MAYORS);
    }
}
