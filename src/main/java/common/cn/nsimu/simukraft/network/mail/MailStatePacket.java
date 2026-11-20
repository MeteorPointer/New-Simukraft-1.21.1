package common.cn.nsimu.simukraft.network.mail;

import common.cn.nsimu.simukraft.SimuKraft;
import common.cn.nsimu.simukraft.network.clientbound.ClientboundNetworkBridge;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 同步玩家当前邮件未读数量到客户端。 */
public record MailStatePacket(int unreadCount) implements CustomPacketPayload {
    public static final Type<MailStatePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "mail_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MailStatePacket> STREAM_CODEC =
            StreamCodec.of(MailStatePacket::encode, MailStatePacket::decode);

    public MailStatePacket {
        unreadCount = Math.max(0, unreadCount);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buffer, MailStatePacket packet) {
        buffer.writeVarInt(packet.unreadCount());
    }

    private static MailStatePacket decode(RegistryFriendlyByteBuf buffer) {
        return new MailStatePacket(buffer.readVarInt());
    }

    public static void handle(MailStatePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientboundNetworkBridge.handleMailState(packet));
    }
}
