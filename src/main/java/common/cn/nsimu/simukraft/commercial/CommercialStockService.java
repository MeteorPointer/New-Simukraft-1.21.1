package common.cn.nsimu.simukraft.commercial;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class CommercialStockService {
    private static final long DAY_TICKS = 24_000L;

    private CommercialStockService() {
    }

    /**
     * ensureStock: 确保商业定义中的库存条目已经初始化。
     */
    public static void ensureStock(ServerLevel level, BlockPos boxPos, CommercialDefinition definition) {
        if (level == null || boxPos == null || definition == null) {
            return;
        }
        CommercialStockManager manager = CommercialStockManager.get(level);
        long gameTime = level.getGameTime();
        for (CommercialOffer.StockRule rule : uniqueStockRules(definition).values()) {
            CommercialStockData stock = manager.getOrCreate(boxPos, rule, gameTime);
            boolean changed = false;
            if (stock.maxStock() != rule.max()) {
                stock.setMaxStock(rule.max());
                changed = true;
            }
            if (stock.lastRestockGameTime() <= 0L) {
                stock.setLastRestockGameTime(gameTime);
                changed = true;
            }
            if (changed) {
                manager.persist(stock);
            }
        }
    }

    /**
     * restock: 按服务器运行 tick 间隔补货。
     */
    public static void restock(ServerLevel level, BlockPos boxPos, CommercialDefinition definition) {
        if (level == null || boxPos == null || definition == null) {
            return;
        }
        ensureStock(level, boxPos, definition);
        releaseAcquiredStock(level, boxPos, definition);
        CommercialStockManager manager = CommercialStockManager.get(level);
        long gameTime = level.getGameTime();
        for (CommercialOffer.StockRule rule : uniqueStockRules(definition).values()) {
            if (!rule.restockEnabled()) {
                continue;
            }
            CommercialStockData stock = manager.get(boxPos, rule.itemId());
            if (stock == null) {
                continue;
            }
            long lastRestock = stock.lastRestockGameTime();
            if (gameTime < lastRestock) {
                stock.setLastRestockGameTime(gameTime);
                manager.persist(stock);
                continue;
            }
            long elapsed = gameTime - lastRestock;
            long passed = elapsed / rule.restockInterval();
            if (passed <= 0L) {
                continue;
            }
            int added = stock.add(safeRestockAmount(passed, rule.restockAmount()));
            stock.setLastRestockGameTime(lastRestock + passed * rule.restockInterval());
            if (added > 0 || passed > 0) {
                manager.persist(stock);
            }
        }
    }

    /**
     * snapshot: 获取当前商业箱库存快照。
     */
    public static Map<String, CommercialStockData> snapshot(ServerLevel level, BlockPos boxPos) {
        return level == null || boxPos == null ? Map.of() : CommercialStockManager.get(level).allAt(boxPos);
    }

    /**
     * removeBox: 删除商业箱全部库存。
     */
    public static void removeBox(ServerLevel level, BlockPos boxPos) {
        if (level != null && boxPos != null) {
            CommercialStockManager.get(level).removeBox(boxPos);
        }
    }

    /**
     * releaseAcquiredStock: 收购库存每天清零。和出售共用的物品也清，补货计时不改。
     */
    private static void releaseAcquiredStock(ServerLevel level, BlockPos boxPos, CommercialDefinition definition) {
        Set<String> acquired = acquiredItemIds(definition);
        if (acquired.isEmpty()) {
            return;
        }
        CommercialStockManager manager = CommercialStockManager.get(level);
        long currentDay = Math.floorDiv(level.getDayTime(), DAY_TICKS);
        for (String itemId : acquired) {
            CommercialStockData stock = manager.get(boxPos, itemId);
            if (stock == null) {
                continue;
            }
            if (stock.lastReleaseDay() < 0L) {
                stock.setLastReleaseDay(currentDay);
                manager.persist(stock);
                continue;
            }
            if (stock.lastReleaseDay() >= currentDay) {
                continue;
            }
            stock.setLastReleaseDay(currentDay);
            if (stock.currentStock() > 0) {
                stock.setCurrentStock(0);
            }
            manager.persist(stock);
        }
    }

    /**
     * acquiredItemIds: 玩家交给商店的物品，包括同时用于出售的那一份。
     */
    static Set<String> acquiredItemIds(CommercialDefinition definition) {
        Set<String> enters = new LinkedHashSet<>();
        if (definition == null) {
            return Set.of();
        }
        for (CommercialOffer offer : definition.offers()) {
            CommercialOffer.StockRule rule = offer.stock();
            if (rule == null || !rule.sqliteBacked() || !offer.itemEntersStock()) {
                continue;
            }
            enters.add(rule.itemId());
        }
        return enters;
    }

    private static Map<String, CommercialOffer.StockRule> uniqueStockRules(CommercialDefinition definition) {
        Map<String, CommercialOffer.StockRule> rules = new LinkedHashMap<>();
        for (CommercialOffer offer : definition.offers()) {
            CommercialOffer.StockRule rule = offer.stock();
            if (rule != null && rule.sqliteBacked()) {
                rules.putIfAbsent(rule.itemId(), rule);
            }
        }
        return rules;
    }

    private static int safeRestockAmount(long passed, int amount) {
        long total = passed * (long) amount;
        return total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
    }
}
