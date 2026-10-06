package common.cn.kafei.simukraft.city;

public enum DistrictRole {
    RESIDENT(0),
    OFFICIAL(1),
    MAYOR(2);

    private final int power;

    DistrictRole(int power) {
        this.power = power;
    }

    public boolean atLeast(DistrictRole required) {
        return required != null && power >= required.power;
    }

    public int power() {
        return power;
    }

    public static DistrictRole fromPower(int power) {
        for (DistrictRole role : values()) {
            if (role.power == power) return role;
        }
        return RESIDENT;
    }
}
