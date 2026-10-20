package common.cn.nsimu.simukraft.commercial;

import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.UUID;

public record CommercialTradeView(BlockPos boxPos,
                                  UUID workerId,
                                  String shopName,
                                  String workerName,
                                  double cityBalance,
                                  boolean running,
                                  List<OfferEntry> offers,
                                  boolean globalMarket,
                                  boolean canList,
                                  UUID marketCityId,
                                  List<WarehouseEntry> warehouse) {
    public CommercialTradeView {
        offers = offers != null ? List.copyOf(offers) : List.of();
        warehouse = warehouse != null ? List.copyOf(warehouse) : List.of();
    }

    public CommercialTradeView(BlockPos boxPos,
                               UUID workerId,
                               String shopName,
                               String workerName,
                               double cityBalance,
                               boolean running,
                               List<OfferEntry> offers) {
        this(boxPos, workerId, shopName, workerName, cityBalance, running, offers, false, false, null, List.of());
    }

    public CommercialTradeView(BlockPos boxPos,
                               UUID workerId,
                               String shopName,
                               String workerName,
                               double cityBalance,
                               boolean running,
                               List<OfferEntry> offers,
                               boolean globalMarket,
                               boolean canList,
                               UUID marketCityId) {
        this(boxPos, workerId, shopName, workerName, cityBalance, running, offers, globalMarket, canList, marketCityId, List.of());
    }

    public record OfferEntry(String id,
                             List<ResourceEntry> cost,
                             List<ResourceEntry> result,
                             String stockItem,
                             int currentStock,
                             int maxStock,
                             long restockInterval,
                             int restockAmount,
                             UUID sellerCityId) {
        public OfferEntry(String id,
                          List<ResourceEntry> cost,
                          List<ResourceEntry> result,
                          String stockItem,
                          int currentStock,
                          int maxStock,
                          long restockInterval,
                          int restockAmount) {
            this(id, cost, result, stockItem, currentStock, maxStock, restockInterval, restockAmount, null);
        }
    }

    public record ResourceEntry(String type, String itemId, int count, double money, String stackNbt) {
        public ResourceEntry {
            type = type != null ? type : "";
            itemId = itemId != null ? itemId : "";
            stackNbt = stackNbt != null ? stackNbt : "";
        }

        public ResourceEntry(String type, String itemId, int count, double money) {
            this(type, itemId, count, money, "");
        }
    }

    public record WarehouseEntry(String itemId, int count, String stackNbt) {
        public WarehouseEntry {
            itemId = itemId != null ? itemId : "";
            count = Math.max(0, count);
            stackNbt = stackNbt != null ? stackNbt : "";
        }
    }
}
