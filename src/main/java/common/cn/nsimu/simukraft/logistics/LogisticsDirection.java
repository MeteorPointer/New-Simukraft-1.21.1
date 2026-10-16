package common.cn.nsimu.simukraft.logistics;

public enum LogisticsDirection {
    WAREHOUSE_TO_CLIENT,
    CLIENT_TO_WAREHOUSE;

    public static LogisticsDirection fromName(String value) {
        for (LogisticsDirection direction : values()) {
            if (direction.name().equalsIgnoreCase(value)) {
                return direction;
            }
        }
        return WAREHOUSE_TO_CLIENT;
    }
}
