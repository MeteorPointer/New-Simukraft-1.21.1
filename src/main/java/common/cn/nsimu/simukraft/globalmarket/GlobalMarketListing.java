package common.cn.nsimu.simukraft.globalmarket;

import common.cn.nsimu.simukraft.commercial.CommercialTradeOfferTab;
import common.cn.nsimu.simukraft.commercial.CommercialTradeView;
import common.cn.nsimu.simukraft.economy.EconomyService;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * GlobalMarketListing: 一座城市在全球市场上架的报价。交换最多两个物品换一个。
 */
public record GlobalMarketListing(String id,
                                  UUID cityId,
                                  String cityName,
                                  String mode,
                                  List<CommercialTradeView.ResourceEntry> cost,
                                  List<CommercialTradeView.ResourceEntry> result,
                                  int stock,
                                  int maxStock,
                                  UUID buildingId) {
    /**
     * 旧报价没有所属建筑。
     */
    public GlobalMarketListing(String id,
                               UUID cityId,
                               String cityName,
                               String mode,
                               List<CommercialTradeView.ResourceEntry> cost,
                               List<CommercialTradeView.ResourceEntry> result,
                               int stock,
                               int maxStock) {
        this(id, cityId, cityName, mode, cost, result, stock, maxStock, null);
    }
    public GlobalMarketListing {
        id = id != null ? id : "";
        cityName = cityName != null ? cityName : "";
        mode = mode != null ? mode.toLowerCase() : "";
        cost = cost != null ? List.copyOf(cost) : List.of();
        result = result != null ? List.copyOf(result) : List.of();
        stock = Math.max(0, stock);
        maxStock = Math.max(stock, maxStock);
    }

    /**
     * tab: 报价所属出售/收购/交换分页。
     */
    public CommercialTradeOfferTab tab() {
        return switch (mode) {
            case "buy" -> CommercialTradeOfferTab.BUY;
            case "barter" -> CommercialTradeOfferTab.BARTER;
            default -> CommercialTradeOfferTab.SELL;
        };
    }

    /**
     * toOfferEntry: 转成商业交易列表行。
     */
    public CommercialTradeView.OfferEntry toOfferEntry() {
        return toOfferEntry(stock);
    }

    /**
     * toOfferEntry: 转成商业交易列表行，库存用仓库现货和挂牌上限里较小的那个。
     */
    public CommercialTradeView.OfferEntry toOfferEntry(int currentStock) {
        String stockItem = result.stream()
                .filter(resource -> !"money".equalsIgnoreCase(resource.type()))
                .map(CommercialTradeView.ResourceEntry::itemId)
                .findFirst()
                .orElse(cost.stream()
                        .filter(resource -> !"money".equalsIgnoreCase(resource.type()))
                        .map(CommercialTradeView.ResourceEntry::itemId)
                        .findFirst()
                        .orElse(""));
        return new CommercialTradeView.OfferEntry(id, cost, result, stockItem, Math.max(0, currentStock), maxStock, 0L, 0, cityId);
    }

    /**
     * validateDraft: 校验上架草稿。出售/收购各一件物品加价格，交换最多两件换一件。
     */
    public static String validateDraft(CommercialTradeOfferTab tab,
                                       CommercialTradeView.ResourceEntry costA,
                                       CommercialTradeView.ResourceEntry costB,
                                       CommercialTradeView.ResourceEntry result,
                                       double price) {
        if (tab == null) {
            return "message.simukraft.global_market.invalid_listing";
        }
        return switch (tab) {
            case SELL -> validateSell(result, price);
            case BUY -> validateBuy(costA, price);
            case BARTER -> validateBarter(costA, costB, result);
        };
    }

    /**
     * fromDraft: 把槽位草稿收成可落库的报价。
     */
    public static GlobalMarketListing fromDraft(String id,
                                                UUID cityId,
                                                String cityName,
                                                CommercialTradeOfferTab tab,
                                                CommercialTradeView.ResourceEntry costA,
                                                CommercialTradeView.ResourceEntry costB,
                                                CommercialTradeView.ResourceEntry result,
                                                double price) {
        int quantity = tab == CommercialTradeOfferTab.BUY
                ? (costA == null ? 1 : Math.max(1, costA.count()))
                : (result == null ? 1 : Math.max(1, result.count()));
        return fromDraft(id, cityId, cityName, tab, costA, costB, result, price, quantity);
    }

    /**
     * fromDraft: 出售/收购/交换用 quantity 作为总量上限。
     */
    public static GlobalMarketListing fromDraft(String id,
                                                UUID cityId,
                                                String cityName,
                                                CommercialTradeOfferTab tab,
                                                CommercialTradeView.ResourceEntry costA,
                                                CommercialTradeView.ResourceEntry costB,
                                                CommercialTradeView.ResourceEntry result,
                                                double price,
                                                int quantity) {
        return fromDraft(id, cityId, cityName, tab, costA, costB, result, price, quantity, null);
    }

    /**
     * fromDraft: 出售/收购/交换用 quantity 作为总量上限，并记下仓库所属建筑。
     */
    public static GlobalMarketListing fromDraft(String id,
                                                UUID cityId,
                                                String cityName,
                                                CommercialTradeOfferTab tab,
                                                CommercialTradeView.ResourceEntry costA,
                                                CommercialTradeView.ResourceEntry costB,
                                                CommercialTradeView.ResourceEntry result,
                                                double price,
                                                int quantity,
                                                UUID buildingId) {
        String error = validateDraft(tab, costA, costB, result, price);
        if (error != null) {
            throw new IllegalArgumentException(error);
        }
        List<CommercialTradeView.ResourceEntry> cost = new ArrayList<>();
        List<CommercialTradeView.ResourceEntry> produced = new ArrayList<>();
        int stock;
        int maxStock;
        String mode;
        switch (tab) {
            case BUY -> {
                mode = "buy";
                cost.add(unitItem(costA));
                produced.add(money(price));
                maxStock = Math.max(1, quantity);
                stock = 0;
            }
            case BARTER -> {
                mode = "barter";
                cost.add(item(costA));
                if (isItem(costB)) {
                    cost.add(item(costB));
                }
                produced.add(item(result));
                maxStock = Math.max(1, quantity);
                stock = maxStock;
            }
            default -> {
                mode = "sell";
                cost.add(money(price));
                produced.add(unitItem(result));
                maxStock = Math.max(1, quantity);
                stock = maxStock;
            }
        }
        return new GlobalMarketListing(id, cityId, cityName, mode, cost, produced, stock, maxStock, buildingId);
    }

    private static String validateSell(CommercialTradeView.ResourceEntry result, double price) {
        if (!isItem(result) || !validUnitPrice(price)) {
            return "message.simukraft.global_market.need_item_and_price";
        }
        return null;
    }

    private static String validateBuy(CommercialTradeView.ResourceEntry costA, double price) {
        if (!isItem(costA) || !validUnitPrice(price)) {
            return "message.simukraft.global_market.need_item_and_price";
        }
        return null;
    }

    private static boolean validUnitPrice(double price) {
        return EconomyService.normalizeAmount(price) >= 0.01D;
    }

    private static String validateBarter(CommercialTradeView.ResourceEntry costA,
                                         CommercialTradeView.ResourceEntry costB,
                                         CommercialTradeView.ResourceEntry result) {
        if (!isItem(costA) || !isItem(result)) {
            return "message.simukraft.global_market.need_barter_items";
        }
        if (costB != null && !costB.itemId().isBlank() && !isItem(costB)) {
            return "message.simukraft.global_market.barter_two_items";
        }
        return null;
    }

    private static boolean isItem(CommercialTradeView.ResourceEntry resource) {
        return resource != null
                && !"money".equalsIgnoreCase(resource.type())
                && resource.itemId() != null
                && !resource.itemId().isBlank()
                && resource.count() > 0;
    }

    private static CommercialTradeView.ResourceEntry item(CommercialTradeView.ResourceEntry resource) {
        return new CommercialTradeView.ResourceEntry("item", resource.itemId(), Math.max(1, resource.count()), 0.0D, resource.stackNbt());
    }

    private static CommercialTradeView.ResourceEntry unitItem(CommercialTradeView.ResourceEntry resource) {
        return new CommercialTradeView.ResourceEntry("item", resource.itemId(), 1, 0.0D, resource.stackNbt());
    }

    private static CommercialTradeView.ResourceEntry money(double amount) {
        return new CommercialTradeView.ResourceEntry("money", "", 0, amount);
    }
}
