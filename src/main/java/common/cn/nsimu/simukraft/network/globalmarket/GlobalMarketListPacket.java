package common.cn.nsimu.simukraft.network.globalmarket;

import common.cn.nsimu.simukraft.SimuKraft;
import common.cn.nsimu.simukraft.commercial.CommercialTradeOfferTab;
import common.cn.nsimu.simukraft.globalmarket.GlobalMarketService;
import common.cn.nsimu.simukraft.network.commercial.CommercialTradeOpenResponsePacket;
import common.cn.nsimu.simukraft.network.commercial.CommercialTradePacket;
import common.cn.nsimu.simukraft.network.toast.InfoToastService;
import common.cn.nsimu.simukraft.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * GlobalMarketListPacket: 市长或官员把选中物品批量上架到全球市场。
 */
public record GlobalMarketListPacket(BlockPos pos, String tab, java.util.List<ItemStack> items,
                                     double price, int quantity) implements CustomPacketPayload {
    public static final int MAX_BATCH = 36;
    public static final Type<GlobalMarketListPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "global_market_list"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GlobalMarketListPacket> STREAM_CODEC = StreamCodec.of(GlobalMarketListPacket::encode, GlobalMarketListPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * encode: 写入批量上架请求。
     */
    public static void encode(RegistryFriendlyByteBuf buffer, GlobalMarketListPacket packet) {
        buffer.writeBlockPos(packet.pos());
        buffer.writeUtf(packet.tab(), 16);
        java.util.List<ItemStack> items = packet.items() == null ? java.util.List.of() : packet.items();
        buffer.writeVarInt(Math.min(MAX_BATCH, items.size()));
        int written = 0;
        for (ItemStack item : items) {
            if (written >= MAX_BATCH) {
                break;
            }
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, item == null ? ItemStack.EMPTY : item);
            written++;
        }
        buffer.writeDouble(packet.price());
        buffer.writeVarInt(Math.max(0, packet.quantity()));
    }

    /**
     * decode: 读取批量上架请求。
     */
    public static GlobalMarketListPacket decode(RegistryFriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        String tab = buffer.readUtf(16);
        int count = Math.clamp(buffer.readVarInt(), 0, MAX_BATCH);
        java.util.List<ItemStack> items = new java.util.ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            items.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer));
        }
        return new GlobalMarketListPacket(pos, tab, java.util.List.copyOf(items), buffer.readDouble(), buffer.readVarInt());
    }

    /**
     * handle: 校验权限后批量上架并刷新界面。
     */
    public static void handle(GlobalMarketListPacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (!player.blockPosition().closerThan(packet.pos(), 16.0D)) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.commercial_control_box.too_far"));
            return;
        }
        if (!level.getBlockState(packet.pos()).is(ModBlocks.GLOBAL_MARKET_CONTROL_BOX.get())
                || !GlobalMarketService.isGlobalMarketBox(level, packet.pos())) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.global_market.no_building"));
            return;
        }
        CommercialTradeOfferTab tab;
        try {
            tab = CommercialTradeOfferTab.valueOf(packet.tab());
        } catch (IllegalArgumentException exception) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.global_market.invalid_listing"));
            return;
        }
        java.util.List<ItemStack> before = CommercialTradePacket.snapshotInventory(player);
        String error = GlobalMarketService.listSelected(level, player, packet.pos(), tab, packet.items(), packet.price(), packet.quantity());
        if (error != null) {
            InfoToastService.warning(player, Component.translatable(error));
            return;
        }
        CommercialTradePacket.syncChangedSlots(player, before);
        InfoToastService.success(player, Component.translatable("message.simukraft.global_market.listed"));
        PacketDistributor.sendToPlayer(player, CommercialTradeOpenResponsePacket.from(GlobalMarketService.buildView(level, player, packet.pos()), player.registryAccess()));
    }
}
