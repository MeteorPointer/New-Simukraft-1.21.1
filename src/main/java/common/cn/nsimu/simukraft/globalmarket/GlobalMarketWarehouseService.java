package common.cn.nsimu.simukraft.globalmarket;

import common.cn.nsimu.simukraft.commercial.CommercialTradeView;
import common.cn.nsimu.simukraft.storage.SimuSqliteStorage;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * GlobalMarketWarehouseService: 城市全球市场仓库，合并同种物品，数量可超过 64。
 */
public final class GlobalMarketWarehouseService {
    private static final Map<String, CopyOnWriteArrayList<ItemStack>> CACHE = new ConcurrentHashMap<>();

    private GlobalMarketWarehouseService() {
    }

    /**
     * snapshot: 仓库物品快照，供界面展示。
     */
    public static List<ItemStack> snapshot(ServerLevel level, UUID cityId) {
        return copyOf(stacks(level, cityId));
    }

    /**
     * entries: 转成交易视图仓库条目。
     */
    public static List<CommercialTradeView.WarehouseEntry> entries(ServerLevel level, UUID cityId) {
        List<CommercialTradeView.WarehouseEntry> entries = new ArrayList<>();
        HolderLookup.Provider registries = level.registryAccess();
        for (ItemStack stack : snapshot(level, cityId)) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            ItemStack unit = stack.copy();
            unit.setCount(1);
            entries.add(new CommercialTradeView.WarehouseEntry(id.toString(), stack.getCount(), GlobalMarketItemStacks.encode(registries, unit)));
        }
        return List.copyOf(entries);
    }

    /**
     * insert: 把物品并入仓库。
     */
    public static synchronized void insert(ServerLevel level, UUID cityId, ItemStack incoming) {
        if (level == null || cityId == null || incoming == null || incoming.isEmpty()) {
            return;
        }
        CopyOnWriteArrayList<ItemStack> stacks = stacks(level, cityId);
        boolean merged = false;
        for (ItemStack stack : stacks) {
            if (ItemStack.isSameItemSameComponents(stack, incoming)) {
                stack.setCount(stack.getCount() + incoming.getCount());
                merged = true;
                break;
            }
        }
        if (!merged) {
            stacks.add(incoming.copy());
        }
        persist(level, cityId, stacks);
    }

    /**
     * take: 从仓库扣走指定物品，数量不够则失败。
     */
    public static synchronized boolean take(ServerLevel level, UUID cityId, ItemStack listed) {
        if (level == null || cityId == null || listed == null || listed.isEmpty()) {
            return false;
        }
        CopyOnWriteArrayList<ItemStack> stacks = stacks(level, cityId);
        int remaining = listed.getCount();
        if (countMatching(stacks, listed) < remaining) {
            return false;
        }
        for (ItemStack stack : stacks) {
            if (!ItemStack.isSameItemSameComponents(stack, listed)) {
                continue;
            }
            int take = Math.min(remaining, stack.getCount());
            stack.shrink(take);
            remaining -= take;
            if (remaining <= 0) {
                break;
            }
        }
        stacks.removeIf(ItemStack::isEmpty);
        persist(level, cityId, stacks);
        return true;
    }

    /**
     * availableResults: 报价产物在仓库里还剩多少，按最紧缺的一种算。
     */
    public static synchronized int availableResults(ServerLevel level, GlobalMarketListing listing) {
        if (level == null || listing == null || listing.cityId() == null) {
            return 0;
        }
        HolderLookup.Provider registries = level.registryAccess();
        int available = Integer.MAX_VALUE;
        boolean any = false;
        for (CommercialTradeView.ResourceEntry resource : listing.result()) {
            if ("money".equalsIgnoreCase(resource.type())) {
                continue;
            }
            ItemStack template = GlobalMarketItemStacks.decode(registries, resource);
            if (template.isEmpty()) {
                continue;
            }
            any = true;
            available = Math.min(available, countMatching(stacks(level, listing.cityId()), template));
        }
        return any ? available : 0;
    }

    /**
     * extract: 取出最多 count 个匹配物品，返回实际取出的栈。
     */
    public static synchronized ItemStack extract(ServerLevel level, UUID cityId, ItemStack template, int count) {
        if (level == null || cityId == null || template == null || template.isEmpty() || count <= 0) {
            return ItemStack.EMPTY;
        }
        int available = countMatching(stacks(level, cityId), template);
        int take = Math.min(count, available);
        if (take <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack listed = template.copy();
        listed.setCount(take);
        if (!take(level, cityId, listed)) {
            return ItemStack.EMPTY;
        }
        ItemStack given = template.copy();
        given.setCount(take);
        return given;
    }

    /**
     * clearCache: 关服清理仓库缓存。
     */
    public static void clearCache() {
        CACHE.clear();
    }

    private static int countMatching(List<ItemStack> stacks, ItemStack listed) {
        int total = 0;
        for (ItemStack stack : stacks) {
            if (ItemStack.isSameItemSameComponents(stack, listed)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static CopyOnWriteArrayList<ItemStack> stacks(ServerLevel level, UUID cityId) {
        String key = key(level, cityId);
        return CACHE.computeIfAbsent(key, ignored -> {
            CopyOnWriteArrayList<ItemStack> loaded = new CopyOnWriteArrayList<>();
            HolderLookup.Provider registries = level.registryAccess();
            for (CommercialTradeView.WarehouseEntry entry : SimuSqliteStorage.loadGlobalMarketWarehouse(level, cityId)) {
                ItemStack stack = GlobalMarketItemStacks.decode(registries, entry.stackNbt(), entry.itemId(), 1);
                if (stack.isEmpty()) {
                    continue;
                }
                stack.setCount(Math.max(1, entry.count()));
                loaded.add(stack);
            }
            return loaded;
        });
    }

    private static void persist(ServerLevel level, UUID cityId, List<ItemStack> stacks) {
        HolderLookup.Provider registries = level.registryAccess();
        List<CommercialTradeView.WarehouseEntry> entries = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            ItemStack unit = stack.copy();
            unit.setCount(1);
            entries.add(new CommercialTradeView.WarehouseEntry(id.toString(), stack.getCount(), GlobalMarketItemStacks.encode(registries, unit)));
        }
        SimuSqliteStorage.saveGlobalMarketWarehouse(level, cityId, entries);
    }

    private static List<ItemStack> copyOf(List<ItemStack> stacks) {
        List<ItemStack> copy = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (stack != null && !stack.isEmpty()) {
                copy.add(stack.copy());
            }
        }
        return copy;
    }

    private static String key(ServerLevel level, UUID cityId) {
        return level.dimension().location() + "|" + cityId;
    }
}
