package common.cn.nsimu.simukraft.citizen.family;

public enum FamilyStatus {
    FORMING,   // 尚未完整（缺丈夫或妻子）
    ACTIVE,    // 至少一方在世
    DISSOLVED; // 夫妻双亡（数据保留）

    public static FamilyStatus fromName(String name) {
        for (FamilyStatus s : values()) {
            if (s.name().equalsIgnoreCase(name)) return s;
        }
        return FORMING;
    }
}
