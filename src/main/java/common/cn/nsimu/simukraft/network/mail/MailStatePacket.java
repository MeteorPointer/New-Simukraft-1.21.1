package common.cn.nsimu.simukraft.network.mail;

import common.cn.nsimu.simukraft.SimuKraft;
import common.cn.nsimu.simukraft.network.clientbound.ClientboundNetworkBridge;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/** 同步玩家当前邮件未读数量到客户端。 */
public record MailStatePacket(int unreadCount, ResourceLocation senderDimension, int senderX, int senderZ,
                              List<MailMessageData> messages) implements CustomPacketPayload {
    public static final Type<MailStatePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "mail_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MailStatePacket> STREAM_CODEC =
            StreamCodec.of(MailStatePacket::encode, MailStatePacket::decode);

    public MailStatePacket {
        unreadCount = Math.max(0, unreadCount);
        messages = messages == null ? List.of() : List.copyOf(messages);
    }

    public MailStatePacket(int unreadCount, ResourceLocation senderDimension, int senderX, int senderZ) {
        this(unreadCount, senderDimension, senderX, senderZ, List.of());
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
        buffer.writeVarInt(Math.min(64, packet.messages().size()));
        packet.messages().stream().limit(64).forEach(message -> {
            buffer.writeLong(message.id());
            buffer.writeUtf(message.category(), 16);
            buffer.writeUtf(message.sender(), 128);
            buffer.writeUtf(message.subject(), 256);
            buffer.writeUtf(message.body(), 4096);
            buffer.writeLong(message.timestamp());
            buffer.writeBoolean(message.unread());
        });
    }

    private static MailStatePacket decode(RegistryFriendlyByteBuf buffer) {
        int unreadCount = buffer.readVarInt();
        if (!buffer.readBoolean()) {
            return decodeMessages(unreadCount, null, 0, 0, buffer);
        }
        return decodeMessages(unreadCount, buffer.readResourceLocation(), buffer.readInt(), buffer.readInt(), buffer);
    }

    private static MailStatePacket decodeMessages(int unreadCount, ResourceLocation dimension, int x, int z,
                                                  RegistryFriendlyByteBuf buffer) {
        int size = Math.min(64, buffer.readVarInt());
        List<MailMessageData> messages = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            messages.add(new MailMessageData(buffer.readLong(), buffer.readUtf(16), buffer.readUtf(128),
                    buffer.readUtf(256), buffer.readUtf(4096), buffer.readLong(), buffer.readBoolean()));
        }
        return new MailStatePacket(unreadCount, dimension, x, z, messages);
    }

    public static void handle(MailStatePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientboundNetworkBridge.handleMailState(packet));
    }
}
