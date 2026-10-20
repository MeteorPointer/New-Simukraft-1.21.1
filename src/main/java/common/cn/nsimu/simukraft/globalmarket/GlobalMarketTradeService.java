package common.cn.nsimu.simukraft.globalmarket;

import common.cn.nsimu.simukraft.city.CityService;
import common.cn.nsimu.simukraft.commercial.CommercialTradeService;
import common.cn.nsimu.simukraft.commercial.CommercialTradeView;
import common.cn.nsimu.simukraft.economy.EconomyService;
import common.cn.nsimu.simukraft.network.hud.HudSyncService;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * GlobalMarketTradeService: 成交全球市场上架报价，资金在城市之间划转。
 */
public final class GlobalMarketTradeService {
    private GlobalMarketTradeService() {
    }

    /**
     * execute: 玩家按上架报价成交一次或多次。
     */
    public static synchronized CommercialTradeService.TradeResult execute(ServerLevel level, ServerPlayer player, String listingId, int count) {
        int times = Math.clamp(count, 1, 64);
        GlobalMarketListing listing = GlobalMarketService.find(level, listingId);
        if (listing == null) {
            return CommercialTradeService.TradeResult.fail("message.simukraft.global_market.offer_missing");
        }
        UUID payerCityId = CityService.findPlayerCity(level, player.getUUID()).map(city -> city.cityId()).orElse(null);
        if (payerCityId == null) {
            return CommercialTradeService.TradeResult.fail("message.simukraft.commercial.visitor_no_city_funds");
        }
        times = affordableTimes(level, player, listing, payerCityId, times);
        if (times < 1) {
            return CommercialTradeService.TradeResult.fail(affordableFailKey(listing));
        }
        int soldItems = itemCount(listing.result(), times);
        int boughtItems = itemCount(listing.cost(), times);
        double moneyCost = moneyTotal(listing.cost(), times);
        if (moneyCost > 0.0D && !EconomyService.canAfford(level, payerCityId, moneyCost)) {
            return CommercialTradeService.TradeResult.fail("message.simukraft.commercial.not_enough_funds");
        }
        double moneyResult = moneyTotal(listing.result(), times);
        if (moneyResult > 0.0D && !EconomyService.canAfford(level, listing.cityId(), moneyResult)) {
            return CommercialTradeService.TradeResult.fail("message.simukraft.global_market.seller_no_funds");
        }
        if (!hasItems(player, listing.cost(), times)) {
            return CommercialTradeService.TradeResult.fail("message.simukraft.commercial.not_enough_items");
        }
        boolean tookShop = takeShopResults(level, listing, times);
        if (!tookShop) {
            return CommercialTradeService.TradeResult.fail("message.simukraft.global_market.warehouse_not_enough");
        }
        if (moneyCost > 0.0D && !EconomyService.withdrawCityFunds(level, payerCityId, player, moneyCost, "global_market_trade")) {
            giveBackShopResults(level, listing, times);
            return CommercialTradeService.TradeResult.fail("message.simukraft.commercial.not_enough_funds");
        }
        if (moneyResult > 0.0D && !EconomyService.withdrawCityFunds(level, listing.cityId(), player, moneyResult, "global_market_buy")) {
            if (moneyCost > 0.0D) {
                EconomyService.depositCityFunds(level, payerCityId, player, moneyCost, "global_market_trade_refund");
            }
            giveBackShopResults(level, listing, times);
            return CommercialTradeService.TradeResult.fail("message.simukraft.global_market.seller_no_funds");
        }
        removeItems(player, listing.cost(), times);
        depositBuyerItems(level, listing, times);
        if (moneyCost > 0.0D) {
            EconomyService.depositCityFunds(level, listing.cityId(), player, moneyCost, "global_market_sale");
        }
        if (moneyResult > 0.0D) {
            EconomyService.depositCityFunds(level, payerCityId, player, moneyResult, "global_market_buy");
        }
        giveItems(player, listing.result(), times);
        int nextStock = "buy".equals(listing.mode())
                ? listing.stock() + boughtItems
                : listing.stock() - soldItems;
        GlobalMarketService.updateStock(level, new GlobalMarketListing(
                listing.id(), listing.cityId(), listing.cityName(), listing.mode(),
                listing.cost(), listing.result(), nextStock, listing.maxStock()));
        HudSyncService.syncToPlayer(player, true);
        return CommercialTradeService.TradeResult.success(Component.translatable("message.simukraft.commercial.trade_success", times));
    }

    private static int affordableTimes(ServerLevel level, ServerPlayer player, GlobalMarketListing listing, UUID payerCityId, int requested) {
        int times = requested;
        if ("buy".equals(listing.mode())) {
            int unit = Math.max(1, itemCount(listing.cost(), 1));
            times = Math.min(times, Math.max(0, listing.maxStock() - listing.stock()) / unit);
        } else if ("sell".equals(listing.mode()) || "barter".equals(listing.mode())) {
            int unit = Math.max(1, itemCount(listing.result(), 1));
            times = Math.min(times, listing.stock() / unit);
            times = Math.min(times, GlobalMarketWarehouseService.availableResults(level, listing) / unit);
        }
        double moneyCost = moneyTotal(listing.cost(), 1);
        if (moneyCost > 0.0D) {
            times = Math.min(times, (int) (EconomyService.getCityBalance(level, payerCityId) / moneyCost));
        }
        double moneyResult = moneyTotal(listing.result(), 1);
        if (moneyResult > 0.0D) {
            times = Math.min(times, (int) (EconomyService.getCityBalance(level, listing.cityId()) / moneyResult));
        }
        for (CommercialTradeView.ResourceEntry resource : listing.cost()) {
            if ("money".equalsIgnoreCase(resource.type()) || resource.count() <= 0) {
                continue;
            }
            ItemStack template = GlobalMarketItemStacks.decode(player.registryAccess(), resource);
            times = Math.min(times, countMatching(player, template) / resource.count());
        }
        return Math.max(0, times);
    }

    private static String affordableFailKey(GlobalMarketListing listing) {
        if ("buy".equals(listing.mode()) && listing.stock() >= listing.maxStock()) {
            return "message.simukraft.global_market.stock_full";
        }
        if ("sell".equals(listing.mode()) || "barter".equals(listing.mode())) {
            return "message.simukraft.global_market.warehouse_not_enough";
        }
        return "message.simukraft.commercial.not_enough_items";
    }

    private static boolean takeShopResults(ServerLevel level, GlobalMarketListing listing, int times) {
        if (!"sell".equals(listing.mode()) && !"barter".equals(listing.mode())) {
            return true;
        }
        HolderLookup.Provider registries = level.registryAccess();
        List<ItemStack> taken = new ArrayList<>();
        for (CommercialTradeView.ResourceEntry resource : listing.result()) {
            if ("money".equalsIgnoreCase(resource.type())) {
                continue;
            }
            ItemStack template = GlobalMarketItemStacks.decode(registries, resource);
            if (template.isEmpty()) {
                continue;
            }
            ItemStack need = template.copy();
            need.setCount(resource.count() * times);
            if (!GlobalMarketWarehouseService.take(level, listing.cityId(), need)) {
                for (ItemStack stack : taken) {
                    GlobalMarketWarehouseService.insert(level, listing.cityId(), stack);
                }
                return false;
            }
            taken.add(need);
        }
        return taken.isEmpty() ? itemCount(listing.result(), times) <= 0 : true;
    }

    private static void giveBackShopResults(ServerLevel level, GlobalMarketListing listing, int times) {
        if (!"sell".equals(listing.mode()) && !"barter".equals(listing.mode())) {
            return;
        }
        HolderLookup.Provider registries = level.registryAccess();
        for (CommercialTradeView.ResourceEntry resource : listing.result()) {
            if ("money".equalsIgnoreCase(resource.type())) {
                continue;
            }
            ItemStack template = GlobalMarketItemStacks.decode(registries, resource);
            if (template.isEmpty()) {
                continue;
            }
            ItemStack restored = template.copy();
            restored.setCount(resource.count() * times);
            GlobalMarketWarehouseService.insert(level, listing.cityId(), restored);
        }
    }

    private static void depositBuyerItems(ServerLevel level, GlobalMarketListing listing, int times) {
        HolderLookup.Provider registries = level.registryAccess();
        for (CommercialTradeView.ResourceEntry resource : listing.cost()) {
            if ("money".equalsIgnoreCase(resource.type())) {
                continue;
            }
            ItemStack template = GlobalMarketItemStacks.decode(registries, resource);
            if (template.isEmpty()) {
                continue;
            }
            ItemStack deposited = template.copy();
            deposited.setCount(resource.count() * times);
            GlobalMarketWarehouseService.insert(level, listing.cityId(), deposited);
        }
    }

    private static int itemCount(List<CommercialTradeView.ResourceEntry> resources, int times) {
        return resources.stream()
                .filter(resource -> !"money".equalsIgnoreCase(resource.type()))
                .mapToInt(resource -> resource.count() * times)
                .sum();
    }

    private static double moneyTotal(List<CommercialTradeView.ResourceEntry> resources, int times) {
        return resources.stream()
                .filter(resource -> "money".equalsIgnoreCase(resource.type()))
                .mapToDouble(resource -> resource.money() * times)
                .sum();
    }

    private static boolean hasItems(ServerPlayer player, List<CommercialTradeView.ResourceEntry> resources, int times) {
        HolderLookup.Provider registries = player.registryAccess();
        for (CommercialTradeView.ResourceEntry resource : resources) {
            if ("money".equalsIgnoreCase(resource.type())) {
                continue;
            }
            ItemStack template = GlobalMarketItemStacks.decode(registries, resource);
            if (template.isEmpty() || countMatching(player, template) < resource.count() * times) {
                return false;
            }
        }
        return true;
    }

    private static void removeItems(ServerPlayer player, List<CommercialTradeView.ResourceEntry> resources, int times) {
        HolderLookup.Provider registries = player.registryAccess();
        for (CommercialTradeView.ResourceEntry resource : resources) {
            if ("money".equalsIgnoreCase(resource.type())) {
                continue;
            }
            ItemStack template = GlobalMarketItemStacks.decode(registries, resource);
            if (template.isEmpty()) {
                continue;
            }
            int remaining = resource.count() * times;
            for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (!ItemStack.isSameItemSameComponents(stack, template)) {
                    continue;
                }
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
            }
        }
    }

    private static void giveItems(ServerPlayer player, List<CommercialTradeView.ResourceEntry> resources, int times) {
        HolderLookup.Provider registries = player.registryAccess();
        for (CommercialTradeView.ResourceEntry resource : resources) {
            if ("money".equalsIgnoreCase(resource.type())) {
                continue;
            }
            ItemStack template = GlobalMarketItemStacks.decode(registries, resource);
            if (template.isEmpty()) {
                continue;
            }
            int remaining = resource.count() * times;
            int max = Math.max(1, template.getMaxStackSize());
            while (remaining > 0) {
                ItemStack stack = template.copy();
                int n = Math.min(remaining, max);
                stack.setCount(n);
                if (!player.getInventory().add(stack) && !stack.isEmpty()) {
                    player.level().addFreshEntity(new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), stack));
                }
                remaining -= n;
            }
        }
    }

    private static int countMatching(ServerPlayer player, ItemStack template) {
        int total = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (ItemStack.isSameItemSameComponents(stack, template)) {
                total += stack.getCount();
            }
        }
        return total;
    }
}
