package common.cn.nsimu.simukraft.network.commercial;

import common.cn.nsimu.simukraft.SimuKraft;
import common.cn.nsimu.simukraft.commercial.CommercialTradeView;
import common.cn.nsimu.simukraft.globalmarket.GlobalMarketItemStacks;
import common.cn.nsimu.simukraft.network.clientbound.ClientboundNetworkBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


public record CommercialTradeOpenResponsePacket(BlockPos boxPos,
                                                UUID workerId,
                                                String shopName,
                                                String workerName,
                                                double cityBalance,
                                                boolean running,
                                                List<OfferEntry> offers,
                                                boolean globalMarket,
                                                boolean canList,
                                                UUID marketCityId,
                                                List<WarehouseSlot> warehouse) implements CustomPacketPayload {
    public CommercialTradeOpenResponsePacket {
        offers = offers != null ? List.copyOf(offers) : List.of();
        warehouse = warehouse != null ? List.copyOf(warehouse) : List.of();
    }

    public static final Type<CommercialTradeOpenResponsePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "commercial_trade_open_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CommercialTradeOpenResponsePacket> STREAM_CODEC = StreamCodec.of(CommercialTradeOpenResponsePacket::encode, CommercialTradeOpenResponsePacket::decode);

    public static CommercialTradeOpenResponsePacket from(CommercialTradeView view) {
        return from(view, null);
    }

    /**
     * from: 把交易快照转成客户端包，有注册表时还原完整物品栈。
     */
    public static CommercialTradeOpenResponsePacket from(CommercialTradeView view, HolderLookup.Provider registries) {
        return new CommercialTradeOpenResponsePacket(
                view.boxPos(),
                view.workerId(),
                view.shopName(),
                view.workerName(),
                view.cityBalance(),
                view.running(),
                view.offers().stream()
                        .map(offer -> new OfferEntry(
                                offer.id(),
                                resourceEntries(offer.cost(), registries),
                                resourceEntries(offer.result(), registries),
                                offer.stockItem(),
                                offer.currentStock(),
                                offer.maxStock(),
                                offer.restockInterval(),
                                offer.restockAmount(),
                                offer.sellerCityId()))
                        .toList(),
                view.globalMarket(),
                view.canList(),
                view.marketCityId(),
                warehouseSlots(view.warehouse(), registries)
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * encode: 写入 NPC 商业交易视图响应。
     */
    public static void encode(RegistryFriendlyByteBuf buffer, CommercialTradeOpenResponsePacket packet) {
        buffer.writeBlockPos(packet.boxPos());
        buffer.writeBoolean(packet.workerId() != null);
        if (packet.workerId() != null) {
            buffer.writeUUID(packet.workerId());
        }
        buffer.writeUtf(packet.shopName(), 256);
        buffer.writeUtf(packet.workerName(), 256);
        buffer.writeDouble(packet.cityBalance());
        buffer.writeBoolean(packet.running());
        buffer.writeVarInt(packet.offers().size());
        for (OfferEntry offer : packet.offers()) {
            offer.encode(buffer);
        }
        buffer.writeBoolean(packet.globalMarket());
        buffer.writeBoolean(packet.canList());
        buffer.writeBoolean(packet.marketCityId() != null);
        if (packet.marketCityId() != null) {
            buffer.writeUUID(packet.marketCityId());
        }
        buffer.writeVarInt(packet.warehouse().size());
        for (WarehouseSlot slot : packet.warehouse()) {
            slot.encode(buffer);
        }
    }

    /**
     * decode: 读取 NPC 商业交易视图响应。
     */
    public static CommercialTradeOpenResponsePacket decode(RegistryFriendlyByteBuf buffer) {
        BlockPos boxPos = buffer.readBlockPos();
        UUID workerId = buffer.readBoolean() ? buffer.readUUID() : null;
        String shopName = buffer.readUtf(256);
        String workerName = buffer.readUtf(256);
        double cityBalance = buffer.readDouble();
        boolean running = buffer.readBoolean();
        int offerCount = buffer.readVarInt();
        List<OfferEntry> offers = new ArrayList<>();
        for (int i = 0; i < offerCount; i++) {
            offers.add(OfferEntry.decode(buffer));
        }
        boolean globalMarket = buffer.readBoolean();
        boolean canList = buffer.readBoolean();
        UUID marketCityId = buffer.readBoolean() ? buffer.readUUID() : null;
        int warehouseCount = buffer.readVarInt();
        List<WarehouseSlot> warehouse = new ArrayList<>();
        for (int i = 0; i < warehouseCount; i++) {
            warehouse.add(WarehouseSlot.decode(buffer));
        }
        return new CommercialTradeOpenResponsePacket(boxPos, workerId, shopName, workerName, cityBalance, running, List.copyOf(offers), globalMarket, canList, marketCityId, List.copyOf(warehouse));
    }

    /**
     * handle: 分发 NPC 商业交易视图到客户端 UI。
     */
    public static void handle(CommercialTradeOpenResponsePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientboundNetworkBridge.handleCommercialTradeOpenResponse(packet));
    }

    private static List<WarehouseSlot> warehouseSlots(List<CommercialTradeView.WarehouseEntry> entries, HolderLookup.Provider registries) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        List<WarehouseSlot> slots = new ArrayList<>();
        for (CommercialTradeView.WarehouseEntry entry : entries) {
            ItemStack stack = GlobalMarketItemStacks.decode(registries, entry.stackNbt(), entry.itemId(), 1);
            if (stack.isEmpty()) {
                continue;
            }
            slots.add(new WarehouseSlot(stack, Math.max(1, entry.count())));
        }
        return List.copyOf(slots);
    }

    private static List<ResourceEntry> resourceEntries(List<CommercialTradeView.ResourceEntry> entries, HolderLookup.Provider registries) {
        return entries.stream()
                .map(entry -> new ResourceEntry(
                        entry.type(),
                        entry.itemId(),
                        entry.count(),
                        entry.money(),
                        GlobalMarketItemStacks.decode(registries, entry)))
                .toList();
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
        private void encode(RegistryFriendlyByteBuf buffer) {
            buffer.writeUtf(id, 256);
            buffer.writeVarInt(cost.size());
            for (ResourceEntry resource : cost) {
                resource.encode(buffer);
            }
            buffer.writeVarInt(result.size());
            for (ResourceEntry resource : result) {
                resource.encode(buffer);
            }
            buffer.writeUtf(stockItem, 256);
            buffer.writeVarInt(currentStock);
            buffer.writeVarInt(maxStock);
            buffer.writeVarLong(restockInterval);
            buffer.writeVarInt(restockAmount);
            buffer.writeBoolean(sellerCityId != null);
            if (sellerCityId != null) {
                buffer.writeUUID(sellerCityId);
            }
        }

        private static OfferEntry decode(RegistryFriendlyByteBuf buffer) {
            String id = buffer.readUtf(256);
            int costCount = buffer.readVarInt();
            List<ResourceEntry> cost = new ArrayList<>();
            for (int i = 0; i < costCount; i++) {
                cost.add(ResourceEntry.decode(buffer));
            }
            int resultCount = buffer.readVarInt();
            List<ResourceEntry> result = new ArrayList<>();
            for (int i = 0; i < resultCount; i++) {
                result.add(ResourceEntry.decode(buffer));
            }
            String stockItem = buffer.readUtf(256);
            int currentStock = buffer.readVarInt();
            int maxStock = buffer.readVarInt();
            long restockInterval = buffer.readVarLong();
            int restockAmount = buffer.readVarInt();
            UUID sellerCityId = buffer.readBoolean() ? buffer.readUUID() : null;
            return new OfferEntry(id, List.copyOf(cost), List.copyOf(result), stockItem, currentStock, maxStock, restockInterval, restockAmount, sellerCityId);
        }
    }

    public record ResourceEntry(String type, String itemId, int count, double money, ItemStack stack) {
        public ResourceEntry {
            stack = stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
        }

        public ResourceEntry(String type, String itemId, int count, double money) {
            this(type, itemId, count, money, ItemStack.EMPTY);
        }

        private void encode(RegistryFriendlyByteBuf buffer) {
            buffer.writeUtf(type, 16);
            buffer.writeUtf(itemId, 256);
            buffer.writeVarInt(count);
            buffer.writeDouble(money);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, stack == null ? ItemStack.EMPTY : stack);
        }

        private static ResourceEntry decode(RegistryFriendlyByteBuf buffer) {
            return new ResourceEntry(
                    buffer.readUtf(16),
                    buffer.readUtf(256),
                    buffer.readVarInt(),
                    buffer.readDouble(),
                    ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer));
        }
    }

    public record WarehouseSlot(ItemStack stack, int count) {
        public WarehouseSlot {
            stack = stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
            count = Math.max(0, count);
        }

        private void encode(RegistryFriendlyByteBuf buffer) {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, stack == null ? ItemStack.EMPTY : stack);
            buffer.writeVarInt(count);
        }

        private static WarehouseSlot decode(RegistryFriendlyByteBuf buffer) {
            return new WarehouseSlot(ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer), buffer.readVarInt());
        }
    }
}
