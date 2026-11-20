package common.cn.nsimu.simukraft.network.mail;

import common.cn.nsimu.simukraft.SimuKraft;
import common.cn.nsimu.simukraft.network.clientbound.ClientboundNetworkBridge;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 同步玩家当前邮件未读数量到客户端。 */
public record MailStatePacket(int unreadCount, ResourceLocation senderDimension, int senderX, int senderZ) implements CustomPacketPayload {
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
        buffer.writeBoolean(packet.senderDimension() != null);
        if (packet.senderDimension() != null) {
            buffer.writeResourceLocation(packet.senderDimension());
            buffer.writeInt(packet.senderX());
            buffer.writeInt(packet.senderZ());
        }
    }

    private static MailStatePacket decode(RegistryFriendlyByteBuf buffer) {
        int unreadCount = buffer.readVarInt();
        if (!buffer.readBoolean()) {
            return new MailStatePacket(unreadCount, null, 0, 0);
        }
        return new MailStatePacket(unreadCount, buffer.readResourceLocation(), buffer.readInt(), buffer.readInt());
    }

    public static void handle(MailStatePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientboundNetworkBridge.handleMailState(packet));
    }
}
