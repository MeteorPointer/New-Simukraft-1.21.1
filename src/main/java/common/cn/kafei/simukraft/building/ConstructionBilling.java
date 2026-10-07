package common.cn.kafei.simukraft.building;

import common.cn.kafei.simukraft.economy.EconomyService;

/**
 * 建造费按建筑 JSON 总价平摊到 NBT 方块数。扣款按建筑师等级定时结算，不在每放一块时扣一次。
 */
public final class ConstructionBilling {
    public static final int TICKS_PER_SECOND = 20;

    private ConstructionBilling() {
    }

    /** 每块方块的价钱：总价 / NBT 方块数。 */
    public static double perBlockCost(double totalPrice, int blockCount) {
        if (totalPrice <= 0.0D || blockCount <= 0) {
            return 0.0D;
        }
        return EconomyService.normalizeAmount(totalPrice / blockCount);
    }

    /** 处理到第 processedBlocks 块时累计应扣金额。最后一块补齐四舍五入差额，使总额等于 JSON 总价。 */
    public static double costThrough(double totalPrice, int blockCount, int processedBlocks) {
        if (totalPrice <= 0.0D || blockCount <= 0 || processedBlocks <= 0) {
            return 0.0D;
        }
        if (processedBlocks >= blockCount) {
            return EconomyService.normalizeAmount(totalPrice);
        }
        return EconomyService.normalizeAmount(totalPrice * processedBlocks / blockCount);
    }

    /**
     * 结算间隔。5 级以下每秒一次，10 到 14 级每 5 秒，15 到 19 级每 20 秒，20 级及以上每 60 秒。
     * 5 到 9 级仍按每秒，因为下一段从 10 级开始。
     */
    public static int chargeIntervalTicks(int npcLevel) {
        if (npcLevel >= 20) {
            return TICKS_PER_SECOND * 60;
        }
        if (npcLevel >= 15) {
            return TICKS_PER_SECOND * 20;
        }
        if (npcLevel >= 10) {
            return TICKS_PER_SECOND * 5;
        }
        return TICKS_PER_SECOND;
    }
}
