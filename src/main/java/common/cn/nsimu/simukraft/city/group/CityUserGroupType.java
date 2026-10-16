package common.cn.nsimu.simukraft.city.group;

import common.cn.nsimu.simukraft.city.CityPermissionLevel;

public enum CityUserGroupType {
    MEMBERS,
    OFFICIALS,
    MAYORS;

    // includes: 判断指定权限是否属于当前城市用户组。
    public boolean includes(CityPermissionLevel permissionLevel) {
        if (permissionLevel == null) {
            return false;
        }
        return switch (this) {
            case MEMBERS -> true;
            case OFFICIALS -> permissionLevel.atLeast(CityPermissionLevel.OFFICIAL);
            case MAYORS -> permissionLevel == CityPermissionLevel.MAYOR;
        };
    }
}
