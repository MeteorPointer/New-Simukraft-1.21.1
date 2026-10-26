package common.cn.nsimu.simukraft.globalmarket;

import common.cn.nsimu.simukraft.commercial.CommercialTradeView;
import common.cn.nsimu.simukraft.storage.SimuSqliteStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * GlobalMarketWarehouseService: 每座全球市场自己的仓库，合并同种物品，数量可超过 64。
 */
public final class GlobalMarketWarehouseService {
    private static final Map<String, CopyOnWriteArrayList<ItemStack>> CACHE = new ConcurrentHashMap<>();

    private GlobalMarketWarehouseService() {
    }

    /**
     * snapshot: 这座市场的仓库快照，供界面展示。
     */
    public static List<ItemStack> snapshot(ServerLevel level, UUID buildingId) {
        return copyOf(buildingStacks(level, buildingId));
    }

    /**
     * entries: 转成交易视图仓库条目。
     */
    public static List<CommercialTradeView.WarehouseEntry> entries(ServerLevel level, UUID buildingId) {
        List<CommercialTradeView.WarehouseEntry> entries = new ArrayList<>();
        HolderLookup.Provider registries = level.registryAccess();
        for (ItemStack stack : snapshot(level, buildingId)) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            ItemStack unit = stack.copy();
            unit.setCount(1);
            entries.add(new CommercialTradeView.WarehouseEntry(id.toString(), stack.getCount(), GlobalMarketItemStacks.encode(registries, unit)));
        }
        return List.copyOf(entries);
    }

    /**
     * insert: 把物品并入这座市场的仓库。
     */
    public static synchronized void insert(ServerLevel level, UUID buildingId, ItemStack incoming) {
        if (level == null || buildingId == null || incoming == null || incoming.isEmpty()) {
            return;
        }
        CopyOnWriteArrayList<ItemStack> stacks = buildingStacks(level, buildingId);
        mergeInto(stacks, incoming);
        persistBuilding(level, buildingId, stacks);
    }

    /**
     * insert: 按报价所属仓库入库。旧报价没有建筑时写入城市共用仓库。
     */
    public static synchronized void insert(ServerLevel level, GlobalMarketListing listing, ItemStack incoming) {
        if (listing == null || listing.buildingId() == null) {
            insertLegacy(level, listing != null ? listing.cityId() : null, incoming);
            return;
        }
        insert(level, listing.buildingId(), incoming);
    }

    /**
     * take: 从这座市场的仓库扣走指定物品，数量不够则失败。
     */
    public static synchronized boolean take(ServerLevel level, UUID buildingId, ItemStack listed) {
        if (level == null || buildingId == null || listed == null || listed.isEmpty()) {
            return false;
        }
        CopyOnWriteArrayList<ItemStack> stacks = buildingStacks(level, buildingId);
        if (!shrink(stacks, listed)) {
            return false;
        }
        persistBuilding(level, buildingId, stacks);
        return true;
    }

    /**
     * take: 按报价所属仓库扣物品。旧报价没有建筑时扣城市共用仓库。
     */
    public static synchronized boolean take(ServerLevel level, GlobalMarketListing listing, ItemStack listed) {
        if (listing == null || listing.buildingId() == null) {
            return takeLegacy(level, listing != null ? listing.cityId() : null, listed);
        }
        return take(level, listing.buildingId(), listed);
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
            available = Math.min(available, countMatching(stacksFor(level, listing), template));
        }
        return any ? available : 0;
    }

    /**
     * extract: 取出最多 count 个匹配物品，返回实际取出的栈。
     */
    public static synchronized ItemStack extract(ServerLevel level, UUID buildingId, ItemStack template, int count) {
        if (level == null || buildingId == null || template == null || template.isEmpty() || count <= 0) {
            return ItemStack.EMPTY;
        }
        int available = countMatching(buildingStacks(level, buildingId), template);
        int take = Math.min(count, available);
        if (take <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack listed = template.copy();
        listed.setCount(take);
        if (!take(level, buildingId, listed)) {
            return ItemStack.EMPTY;
        }
        ItemStack given = template.copy();
        given.setCount(take);
        return given;
    }

    /**
     * dropAll: 清空这座市场的仓库，并在指定位置按最大堆叠掉落。
     */
    public static synchronized void dropAll(ServerLevel level, UUID buildingId, BlockPos pos) {
        dropStored(level, pos, copyOf(buildingStacks(level, buildingId)), "b|" + key(level, buildingId),
                () -> SimuSqliteStorage.saveGlobalMarketBuildingWarehouse(level, buildingId, List.of()));
    }

    /**
     * claimLegacy: 这座市场是城市里编号最小的一座时，把旧的共用仓库并进来，只搬一次。
     */
    public static synchronized void claimLegacy(ServerLevel level, UUID cityId, UUID buildingId, boolean legacyOwner) {
        if (!legacyOwner || level == null || cityId == null || buildingId == null) {
            return;
        }
        CopyOnWriteArrayList<ItemStack> legacy = legacyStacks(level, cityId);
        if (legacy.isEmpty()) {
            return;
        }
        CopyOnWriteArrayList<ItemStack> building = buildingStacks(level, buildingId);
        for (ItemStack stack : copyOf(legacy)) {
            mergeInto(building, stack);
        }
        legacy.clear();
        persistBuilding(level, buildingId, building);
        persistLegacy(level, cityId, legacy);
    }

    /**
     * dropLegacy: 清空旧的城市共用仓库并掉落。只在最后一座市场拆除时调用。
     */
    public static synchronized void dropLegacy(ServerLevel level, UUID cityId, BlockPos pos) {
        dropStored(level, pos, copyOf(legacyStacks(level, cityId)), "c|" + key(level, cityId),
                () -> SimuSqliteStorage.saveGlobalMarketWarehouse(level, cityId, List.of()));
    }

    /**
     * clearCache: 关服清理仓库缓存。
     */
    public static void clearCache() {
        CACHE.clear();
    }

    private static void dropSplit(ServerLevel level, BlockPos pos, ItemStack source) {
        ItemStack remaining = source.copy();
        int maxStack = Math.max(1, remaining.getMaxStackSize());
        while (!remaining.isEmpty()) {
            int count = Math.min(maxStack, remaining.getCount());
            Block.popResource(level, pos, remaining.copyWithCount(count));
            remaining.shrink(count);
        }
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

    private static void dropStored(ServerLevel level, BlockPos pos, List<ItemStack> stacks, String cacheKey, Runnable clearStore) {
        if (level == null || pos == null || stacks.isEmpty()) {
            return;
        }
        CACHE.remove(cacheKey);
        clearStore.run();
        for (ItemStack stack : stacks) {
            dropSplit(level, pos, stack);
        }
    }

    private static void mergeInto(List<ItemStack> stacks, ItemStack incoming) {
        if (incoming == null || incoming.isEmpty()) {
            return;
        }
        for (ItemStack stack : stacks) {
            if (ItemStack.isSameItemSameComponents(stack, incoming)) {
                stack.setCount(stack.getCount() + incoming.getCount());
                return;
            }
        }
        stacks.add(incoming.copy());
    }

    private static boolean shrink(List<ItemStack> stacks, ItemStack listed) {
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
        return true;
    }

    private static void insertLegacy(ServerLevel level, UUID cityId, ItemStack incoming) {
        if (level == null || cityId == null || incoming == null || incoming.isEmpty()) {
            return;
        }
        CopyOnWriteArrayList<ItemStack> stacks = legacyStacks(level, cityId);
        mergeInto(stacks, incoming);
        persistLegacy(level, cityId, stacks);
    }

    private static boolean takeLegacy(ServerLevel level, UUID cityId, ItemStack listed) {
        if (level == null || cityId == null || listed == null || listed.isEmpty()) {
            return false;
        }
        CopyOnWriteArrayList<ItemStack> stacks = legacyStacks(level, cityId);
        if (!shrink(stacks, listed)) {
            return false;
        }
        persistLegacy(level, cityId, stacks);
        return true;
    }

    private static CopyOnWriteArrayList<ItemStack> stacksFor(ServerLevel level, GlobalMarketListing listing) {
        if (listing.buildingId() != null) {
            return buildingStacks(level, listing.buildingId());
        }
        return legacyStacks(level, listing.cityId());
    }

    private static CopyOnWriteArrayList<ItemStack> buildingStacks(ServerLevel level, UUID buildingId) {
        return loadStacks("b|" + key(level, buildingId), () -> SimuSqliteStorage.loadGlobalMarketBuildingWarehouse(level, buildingId), level);
    }

    private static CopyOnWriteArrayList<ItemStack> legacyStacks(ServerLevel level, UUID cityId) {
        return loadStacks("c|" + key(level, cityId), () -> SimuSqliteStorage.loadGlobalMarketWarehouse(level, cityId), level);
    }

    private static CopyOnWriteArrayList<ItemStack> loadStacks(String cacheKey,
                                                              java.util.function.Supplier<List<CommercialTradeView.WarehouseEntry>> loader,
                                                              ServerLevel level) {
        return CACHE.computeIfAbsent(cacheKey, ignored -> {
            CopyOnWriteArrayList<ItemStack> loaded = new CopyOnWriteArrayList<>();
            HolderLookup.Provider registries = level.registryAccess();
            for (CommercialTradeView.WarehouseEntry entry : loader.get()) {
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

    private static void persistBuilding(ServerLevel level, UUID buildingId, List<ItemStack> stacks) {
        SimuSqliteStorage.saveGlobalMarketBuildingWarehouse(level, buildingId, encode(level, stacks));
    }

    private static void persistLegacy(ServerLevel level, UUID cityId, List<ItemStack> stacks) {
        SimuSqliteStorage.saveGlobalMarketWarehouse(level, cityId, encode(level, stacks));
    }

    private static List<CommercialTradeView.WarehouseEntry> encode(ServerLevel level, List<ItemStack> stacks) {
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
        return entries;
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

    private static String key(ServerLevel level, UUID id) {
        return level.dimension().location() + "|" + id;
    }
}
