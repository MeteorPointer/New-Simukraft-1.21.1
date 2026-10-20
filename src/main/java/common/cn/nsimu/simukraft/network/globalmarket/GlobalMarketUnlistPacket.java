package common.cn.nsimu.simukraft.network.globalmarket;

import common.cn.nsimu.simukraft.SimuKraft;
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
 * GlobalMarketUnlistPacket: 下架本城在全球市场的报价。
 */
public record GlobalMarketUnlistPacket(BlockPos pos, String listingId) implements CustomPacketPayload {
    public static final Type<GlobalMarketUnlistPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "global_market_unlist"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GlobalMarketUnlistPacket> STREAM_CODEC = StreamCodec.of(GlobalMarketUnlistPacket::encode, GlobalMarketUnlistPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * encode: 写入下架请求。
     */
    public static void encode(RegistryFriendlyByteBuf buffer, GlobalMarketUnlistPacket packet) {
        buffer.writeBlockPos(packet.pos());
        buffer.writeUtf(packet.listingId(), 64);
    }

    /**
     * decode: 读取下架请求。
     */
    public static GlobalMarketUnlistPacket decode(RegistryFriendlyByteBuf buffer) {
        return new GlobalMarketUnlistPacket(buffer.readBlockPos(), buffer.readUtf(64));
    }

    /**
     * handle: 校验权限后下架并刷新界面。
     */
    public static void handle(GlobalMarketUnlistPacket packet, IPayloadContext context) {
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
        java.util.List<ItemStack> before = CommercialTradePacket.snapshotInventory(player);
        String error = GlobalMarketService.unlistOffer(level, player, packet.pos(), packet.listingId());
        if (error != null) {
            InfoToastService.warning(player, Component.translatable(error));
            return;
        }
        CommercialTradePacket.syncChangedSlots(player, before);
        InfoToastService.success(player, Component.translatable("message.simukraft.global_market.unlisted"));
        PacketDistributor.sendToPlayer(player, CommercialTradeOpenResponsePacket.from(GlobalMarketService.buildView(level, player, packet.pos()), player.registryAccess()));
    }
}
