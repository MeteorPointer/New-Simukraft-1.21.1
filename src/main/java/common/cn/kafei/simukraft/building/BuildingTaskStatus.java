package common.cn.kafei.simukraft.building;

import java.util.Locale;

public enum BuildingTaskStatus {
    QUEUED("queued"),
    BUILDING("building"),
    WAITING_MATERIALS("waiting_materials"),
    WAITING_FUNDS("waiting_funds"),
    PAUSED_RESTING("paused_resting"),
    PAUSED_OFFLINE("paused_offline"),
    COMPLETED("completed"),
    INTERRUPTED("interrupted");

    private final String id;

    BuildingTaskStatus(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public boolean isPaused() {
        return this == PAUSED_RESTING || this == PAUSED_OFFLINE;
    }

    public static BuildingTaskStatus from(String value) {
        if (value == null || value.isBlank()) {
            return QUEUED;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        for (BuildingTaskStatus status : values()) {
            if (status.id.equals(normalized) || status.name().equalsIgnoreCase(value)) {
                return status;
            }
        }
        return QUEUED;
    }
}
