package common.cn.nsimu.simukraft.network.globalmarket;

import common.cn.nsimu.simukraft.SimuKraft;
import common.cn.nsimu.simukraft.globalmarket.GlobalMarketControlBoxService;
import common.cn.nsimu.simukraft.network.rts.RtsRemoteMenuAccess;
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
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * GlobalMarketControlBoxOpenRequestPacket: 打开全球市场控制箱管理界面。
 */
public record GlobalMarketControlBoxOpenRequestPacket(BlockPos pos) implements CustomPacketPayload {
    public static final Type<GlobalMarketControlBoxOpenRequestPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "global_market_control_box_open_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GlobalMarketControlBoxOpenRequestPacket> STREAM_CODEC = StreamCodec.of(GlobalMarketControlBoxOpenRequestPacket::encode, GlobalMarketControlBoxOpenRequestPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, GlobalMarketControlBoxOpenRequestPacket packet) {
        buffer.writeBlockPos(packet.pos());
    }

    public static GlobalMarketControlBoxOpenRequestPacket decode(RegistryFriendlyByteBuf buffer) {
        return new GlobalMarketControlBoxOpenRequestPacket(buffer.readBlockPos());
    }

    public static void handle(GlobalMarketControlBoxOpenRequestPacket packet, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            openFor(level, player, packet.pos());
        }
    }

    /**
     * openFor: 校验距离和方块后发送控制箱视图。
     */
    public static void openFor(ServerLevel level, ServerPlayer player, BlockPos pos) {
        if (!player.blockPosition().closerThan(pos, 16.0D) && !RtsRemoteMenuAccess.hasAccess(player, pos)) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.global_market.too_far"));
            return;
        }
        if (!level.getBlockState(pos).is(ModBlocks.GLOBAL_MARKET_CONTROL_BOX.get())) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.global_market.not_found"));
            return;
        }
        PacketDistributor.sendToPlayer(player, GlobalMarketControlBoxOpenResponsePacket.from(GlobalMarketControlBoxService.buildView(level, pos)));
    }
}
