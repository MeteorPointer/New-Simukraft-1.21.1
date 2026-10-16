package common.cn.nsimu.simukraft.farmland;

enum FarmlandWorkPhase {
    DIG_WATER("dig_water", "gui.simukraft.farmland.status.dig_water"),
    TILL("till", "gui.simukraft.farmland.status.till"),
    PLANT("plant", "gui.simukraft.farmland.status.plant"),
    BONEMEAL("bonemeal", "gui.simukraft.farmland.status.bonemeal"),
    HARVEST("harvest", "gui.simukraft.farmland.status.harvest");

    static final FarmlandWorkPhase[] ORDERED = {DIG_WATER, TILL, PLANT, BONEMEAL, HARVEST};
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
