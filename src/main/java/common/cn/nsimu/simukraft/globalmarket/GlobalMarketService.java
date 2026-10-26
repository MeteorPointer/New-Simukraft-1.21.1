package common.cn.nsimu.simukraft.globalmarket;

import common.cn.nsimu.simukraft.building.PlacedBuildingRecord;
import common.cn.nsimu.simukraft.building.PublicBuildingTypes;
import common.cn.nsimu.simukraft.citizen.CitizenData;
import common.cn.nsimu.simukraft.citizen.CitizenService;
import common.cn.nsimu.simukraft.city.CityService;
import common.cn.nsimu.simukraft.commercial.CommercialTradeMenuProvider;
import common.cn.nsimu.simukraft.commercial.CommercialTradeOfferTab;
import common.cn.nsimu.simukraft.commercial.CommercialTradeView;
import common.cn.nsimu.simukraft.economy.EconomyService;
import common.cn.nsimu.simukraft.storage.SimuSqliteStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GlobalMarketService: 全球市场开关、权限和上架列表。
 */
public final class GlobalMarketService {
    private static final Map<String, ConcurrentHashMap<String, GlobalMarketListing>> CACHE = new ConcurrentHashMap<>();

    private GlobalMarketService() {
    }

    /**
     * isGlobalMarketBox: 该控制箱是否属于全球市场建筑。
     */
    public static boolean isGlobalMarketBox(ServerLevel level, BlockPos boxPos) {
        return GlobalMarketControlBoxService.isControlBox(level, boxPos)
                && PublicBuildingTypes.isGlobalMarket(resolveBuilding(level, boxPos));
    }

    /**
     * resolveBuilding: 解析控制箱所在的已完成建筑。
     */
    public static PlacedBuildingRecord resolveBuilding(ServerLevel level, BlockPos boxPos) {
        return GlobalMarketControlBoxService.resolveBuilding(level, boxPos);
    }

    /**
     * open: 打开全球市场交易界面。
     */
    public static boolean open(ServerLevel level, ServerPlayer player, BlockPos boxPos) {
        return open(level, player, boxPos, null);
    }

    /**
     * open: 打开全球市场交易界面，带上当前职员。
     */
    public static boolean open(ServerLevel level, ServerPlayer player, BlockPos boxPos, UUID workerId) {
        CommercialTradeView view = buildView(level, player, boxPos, workerId);
        return view != null && CommercialTradeMenuProvider.open(player, view);
    }

    /**
     * buildView: 组装全球市场快照。报价来自所有城市，余额是操作者自己的城市资金。
     */
    public static CommercialTradeView buildView(ServerLevel level, ServerPlayer player, BlockPos boxPos) {
        return buildView(level, player, boxPos, null);
    }

    /**
     * buildView: 组装全球市场快照，职员来自上岗 NPC。
     */
    public static CommercialTradeView buildView(ServerLevel level, ServerPlayer player, BlockPos boxPos, UUID workerId) {
        PlacedBuildingRecord building = resolveBuilding(level, boxPos);
        if (building == null || building.cityId() == null) {
            return null;
        }
        UUID payerCityId = CityService.findPlayerCity(level, player.getUUID()).map(city -> city.cityId()).orElse(null);
        boolean canList = CityService.canManageCity(level, building.cityId(), player.getUUID());
        CitizenData worker = workerId != null
                ? CitizenService.findCitizen(level, workerId).orElse(null)
                : GlobalMarketControlBoxService.findAssignedWorker(level, boxPos);
        List<CommercialTradeView.OfferEntry> offers = listings(level).stream()
                .map(listing -> listing.toOfferEntry(visibleStock(level, listing)))
                .toList();
        return new CommercialTradeView(
                boxPos.immutable(),
                worker != null ? worker.uuid() : null,
                "",
                worker != null ? worker.name() : "",
                payerCityId != null ? EconomyService.getCityBalance(level, payerCityId) : 0.0D,
                worker != null,
                offers,
                true,
                canList,
                building.cityId(),
                canList ? GlobalMarketWarehouseService.entries(level, building.cityId()) : List.of());
    }

    /**
     * listOffer: 市长或官员挂牌。仓库当箱子，上架不预扣，没货也能上架。
     */
    public static synchronized String listOffer(ServerLevel level, ServerPlayer player, BlockPos boxPos, CommercialTradeOfferTab tab,
                                                ItemStack costA, ItemStack costB, ItemStack result, double price, int quantity) {
        PlacedBuildingRecord building = resolveBuilding(level, boxPos);
        if (building == null || building.cityId() == null) {
            return "message.simukraft.global_market.no_building";
        }
        if (!CityService.canManageCity(level, building.cityId(), player.getUUID())) {
            return "message.simukraft.global_market.no_permission";
        }
        CommercialTradeView.ResourceEntry costResourceA = itemResource(level, costA);
        CommercialTradeView.ResourceEntry costResourceB = itemResource(level, costB);
        CommercialTradeView.ResourceEntry resultResource = itemResource(level, result);
        String error = GlobalMarketListing.validateDraft(tab, costResourceA, costResourceB, resultResource, price);
        if (error != null) {
            return error;
        }
        if (quantity < 1) {
            return "message.simukraft.global_market.need_quantity";
        }
        int listingQuantity = quantity;
        if (tab == CommercialTradeOfferTab.BARTER && listingQuantity < Math.max(1, result.getCount())) {
            return "message.simukraft.global_market.barter_quantity";
        }
        String cityName = CityService.findCity(level, building.cityId()).map(city -> city.cityName()).orElse("");
        GlobalMarketListing listing = GlobalMarketListing.fromDraft(
                UUID.randomUUID().toString(),
                building.cityId(),
                cityName,
                tab,
                costResourceA,
                costResourceB,
                resultResource,
                EconomyService.normalizeAmount(price),
                listingQuantity);
        cache(level).put(listing.id(), listing);
        SimuSqliteStorage.saveGlobalMarketListing(level, listing);
        return null;
    }

    /**
     * unlistOffer: 下架本城报价。物品一直在仓库里，下架不搬货。
     */
    public static synchronized String unlistOffer(ServerLevel level, ServerPlayer player, BlockPos boxPos, String listingId) {
        PlacedBuildingRecord building = resolveBuilding(level, boxPos);
        if (building == null || building.cityId() == null) {
            return "message.simukraft.global_market.no_building";
        }
        if (!CityService.canManageCity(level, building.cityId(), player.getUUID())) {
            return "message.simukraft.global_market.no_permission";
        }
        GlobalMarketListing listing = find(level, listingId);
        if (listing == null) {
            return "message.simukraft.global_market.offer_missing";
        }
        if (!building.cityId().equals(listing.cityId())) {
            return "message.simukraft.global_market.not_owner";
        }
        cache(level).remove(listingId);
        SimuSqliteStorage.deleteGlobalMarketListing(level, listingId);
        return null;
    }

    /**
     * find: 读取一条上架报价。
     */
    public static GlobalMarketListing find(ServerLevel level, String listingId) {
        if (listingId == null || listingId.isBlank()) {
            return null;
        }
        GlobalMarketListing cached = cache(level).get(listingId);
        if (cached != null) {
            return cached;
        }
        return SimuSqliteStorage.findGlobalMarketListing(level, listingId);
    }

    /**
     * updateStock: 成交后回写库存。
     */
    public static synchronized void updateStock(ServerLevel level, GlobalMarketListing listing) {
        if (listing == null) {
            return;
        }
        if (listing.stock() <= 0) {
            cache(level).remove(listing.id());
            SimuSqliteStorage.deleteGlobalMarketListing(level, listing.id());
            return;
        }
        cache(level).put(listing.id(), listing);
        SimuSqliteStorage.saveGlobalMarketListing(level, listing);
    }

    /**
     * listings: 当前维度全部上架报价。
     */
    public static List<GlobalMarketListing> listings(ServerLevel level) {
        return List.copyOf(cache(level).values());
    }

    /**
     * clearCache: 关服时丢掉上架缓存，避免下一局读到旧世界数据。
     */
    public static void clearCache() {
        CACHE.clear();
    }

    private static ConcurrentHashMap<String, GlobalMarketListing> cache(ServerLevel level) {
        String dimension = level.dimension().location().toString();
        return CACHE.computeIfAbsent(dimension, key -> {
            ConcurrentHashMap<String, GlobalMarketListing> loaded = new ConcurrentHashMap<>();
            for (GlobalMarketListing listing : SimuSqliteStorage.loadGlobalMarketListings(level)) {
                loaded.put(listing.id(), listing);
            }
            return loaded;
        });
    }

    /**
     * listSelected: 按页签处理选中物品。出售合并同种；交换取前两个付出、最后一个换得。上架不扣仓库。
     */
    public static synchronized String listSelected(ServerLevel level, ServerPlayer player, BlockPos boxPos,
                                                   CommercialTradeOfferTab tab, List<ItemStack> items, double price, int quantity) {
        if (items == null || items.isEmpty()) {
            return "message.simukraft.global_market.need_selection";
        }
        if (tab == CommercialTradeOfferTab.BARTER) {
            if (items.size() < 2 || items.size() > 3) {
                return "message.simukraft.global_market.need_barter_selection";
            }
            ItemStack costA = items.getFirst();
            ItemStack costB = items.size() == 3 ? items.get(1) : ItemStack.EMPTY;
            ItemStack result = items.getLast();
            return listOffer(level, player, boxPos, tab, costA, costB, result, 0.0D, quantity);
        }
        ItemStack merged = mergeSameItem(items);
        if (merged == null) {
            return "message.simukraft.global_market.one_item_type";
        }
        if (tab == CommercialTradeOfferTab.BUY) {
            return listOffer(level, player, boxPos, tab, merged, ItemStack.EMPTY, ItemStack.EMPTY, price, quantity);
        }
        return listOffer(level, player, boxPos, tab, ItemStack.EMPTY, ItemStack.EMPTY, merged, price, quantity);
    }

    private static ItemStack mergeSameItem(List<ItemStack> items) {
        ItemStack first = null;
        int total = 0;
        for (ItemStack item : items) {
            if (item == null || item.isEmpty()) {
                continue;
            }
            if (first == null) {
                first = item;
            } else if (!ItemStack.isSameItemSameComponents(first, item)) {
                return null;
            }
            total += item.getCount();
        }
        if (first == null || total <= 0) {
            return null;
        }
        ItemStack merged = first.copy();
        merged.setCount(total);
        return merged;
    }

    private static int visibleStock(ServerLevel level, GlobalMarketListing listing) {
        if ("buy".equals(listing.mode())) {
            return listing.stock();
        }
        return Math.min(listing.stock(), GlobalMarketWarehouseService.availableResults(level, listing));
    }

    private static CommercialTradeView.ResourceEntry itemResource(ServerLevel level, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return new CommercialTradeView.ResourceEntry("item", "", 0, 0.0D);
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        ItemStack unit = stack.copy();
        unit.setCount(1);
        String stackNbt = level != null ? GlobalMarketItemStacks.encode(level.registryAccess(), unit) : "";
        return new CommercialTradeView.ResourceEntry("item", id.toString(), Math.max(1, stack.getCount()), 0.0D, stackNbt);
    }
}
