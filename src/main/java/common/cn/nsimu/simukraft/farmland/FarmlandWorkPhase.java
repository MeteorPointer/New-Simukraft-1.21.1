package common.cn.nsimu.simukraft.farmland;

enum FarmlandWorkPhase {
    DIG_WATER("dig_water", "gui.simukraft.farmland.status.dig_water"),
    TILL("till", "gui.simukraft.farmland.status.till"),
    PLANT("plant", "gui.simukraft.farmland.status.plant"),
    BONEMEAL("bonemeal", "gui.simukraft.farmland.status.bonemeal"),
    HARVEST("harvest", "gui.simukraft.farmland.status.harvest");

    // 先收割成熟作物，再给幼苗施骨粉，避免骨粉把刚补种的苗排到收割前面。
    static final FarmlandWorkPhase[] ORDERED = {DIG_WATER, TILL, PLANT, HARVEST, BONEMEAL};
    private final String id;
    private final String translationKey;

    FarmlandWorkPhase(String id, String translationKey) {
        this.id = id;
        this.translationKey = translationKey;
    }

    String id() {
        return id;
    }

    // translationKey：返回客户端语言文件中的农田工作状态键。
    String translationKey() {
        return translationKey;
    }
}
