package common.cn.nsimu.simukraft.building;

/**
 * ConstructionFundsCheck: 判断建造费用是否高于当前城市资金。
 */
public final class ConstructionFundsCheck {
    private ConstructionFundsCheck() {
    }

    /**
     * isShort: 费用大于 0 且城市资金不够时返回 true。
     */
    public static boolean isShort(double cost, double funds) {
        return cost > 0.0001D && funds + 0.0001D < cost;
    }
}
