package common.cn.kafei.simukraft.mining;

/** MiningBoxMode: 敞开式直接下挖，覆盖玻璃式先在最高层铺玻璃再下挖。 */
public enum MiningBoxMode {
    OPEN("gui.simukraft.mining_box.mode.open"),
    GLASS_COVER("gui.simukraft.mining_box.mode.glass_cover");

    private final String translationKey;

    MiningBoxMode(String translationKey) {
        this.translationKey = translationKey;
    }

    public String translationKey() {
        return translationKey;
    }

    public boolean coversGlass() {
        return this == GLASS_COVER;
    }

    public MiningBoxMode next() {
        return this == OPEN ? GLASS_COVER : OPEN;
    }

    public static MiningBoxMode fromName(String name) {
        if (name == null || name.isBlank()) {
            return OPEN;
        }
        if ("GLASS".equalsIgnoreCase(name) || "COVER".equalsIgnoreCase(name) || "GLASS_COVER".equalsIgnoreCase(name)) {
            return GLASS_COVER;
        }
        for (MiningBoxMode mode : values()) {
            if (mode.name().equalsIgnoreCase(name)) {
                return mode;
            }
        }
        return OPEN;
    }
}
