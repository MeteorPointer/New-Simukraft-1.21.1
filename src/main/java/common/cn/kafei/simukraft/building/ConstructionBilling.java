package common.cn.kafei.simukraft.building;

import common.cn.kafei.simukraft.economy.EconomyService;

/**
 * 建造费按建筑 JSON 总价平摊到 NBT 方块数，按分累计。
 * 5 级以下每处理一块扣一次。5 级及以上每秒结算这一段已盖方块。
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

    /** 处理到第 processedBlocks 块时累计应扣金额。最后一块补齐差额，使总额等于 JSON 总价。 */
    public static double costThrough(double totalPrice, int blockCount, int processedBlocks) {
        return centsThrough(totalPrice, blockCount, processedBlocks) / 100.0D;
    }

    /** 总价换成分。不足 1 分的零头留在累计里，不能每步先四舍五入再相减。 */
    public static long totalCents(double totalPrice) {
        if (totalPrice <= 0.0D) {
            return 0L;
        }
        return Math.round(EconomyService.normalizeAmount(totalPrice) * 100.0D);
    }

    /**
     * 处理到第 processedBlocks 块时累计应扣的分。
     * 用整数累计，避免每块不到 1 分时差额被收成 0、建造过程中完全不扣钱。
     */
    public static long centsThrough(double totalPrice, int blockCount, int processedBlocks) {
        if (totalPrice <= 0.0D || blockCount <= 0 || processedBlocks <= 0) {
            return 0L;
        }
        long totalCents = totalCents(totalPrice);
        if (processedBlocks >= blockCount) {
            return totalCents;
        }
        return totalCents * processedBlocks / blockCount;
    }

    /** 5 级以下每放一块扣一次。5 级及以上按秒把这一段已盖方块合并扣。 */
    public static boolean chargePerPlacedBlock(int npcLevel) {
        return npcLevel < 5;
    }

    /** 5 级及以上的结算间隔：每秒一次。 */
    public static int chargeIntervalTicks(int npcLevel) {
        return TICKS_PER_SECOND;
    }
}
