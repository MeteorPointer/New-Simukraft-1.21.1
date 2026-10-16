package common.cn.nsimu.simukraft.network.guidebook;

import common.cn.nsimu.simukraft.network.clientbound.ClientboundNetworkBridge;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 通知客户端重新读取指南书页面。
 */
public record ReloadGuideBookPacket() implements CustomPacketPayload {
    public static final Type<ReloadGuideBookPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("simukraft", "reload_guide_book"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ReloadGuideBookPacket> STREAM_CODEC = StreamCodec.unit(new ReloadGuideBookPacket());

    @Override
    public Type<ReloadGuideBookPacket> type() {
        return TYPE;
    }

    public static void handle(ReloadGuideBookPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientboundNetworkBridge.handleReloadGuideBook(packet));
    }
}
