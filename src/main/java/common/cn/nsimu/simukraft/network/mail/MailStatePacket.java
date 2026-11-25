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

/**
 * 同步玩家邮件快照：未读数、是否待看教程、邮件列表和发件方名片。
 *
 * <p>ownContactKey 是玩家所属城市的名片 key，没有城市时为空串。
 */
public record MailStatePacket(int unreadCount, boolean tutorialPending, String ownContactKey,
                              List<MailMessageData> messages, List<MailContactData> contacts) implements CustomPacketPayload {
    private static final int MAX_MESSAGES = 320;
    private static final int MAX_CONTACTS = 96;

    public static final Type<MailStatePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "mail_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MailStatePacket> STREAM_CODEC =
            StreamCodec.of(MailStatePacket::encode, MailStatePacket::decode);

    public MailStatePacket {
        unreadCount = Math.max(0, unreadCount);
        ownContactKey = ownContactKey == null ? "" : ownContactKey;
        messages = messages == null ? List.of() : List.copyOf(messages.subList(0, Math.min(MAX_MESSAGES, messages.size())));
        contacts = contacts == null ? List.of() : List.copyOf(contacts.subList(0, Math.min(MAX_CONTACTS, contacts.size())));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buffer, MailStatePacket packet) {
        buffer.writeVarInt(packet.unreadCount());
        buffer.writeBoolean(packet.tutorialPending());
        buffer.writeUtf(packet.ownContactKey(), 64);
        buffer.writeVarInt(packet.messages().size());
        packet.messages().forEach(message -> MailMessageData.encode(buffer, message));
        buffer.writeVarInt(packet.contacts().size());
        packet.contacts().forEach(contact -> MailContactData.encode(buffer, contact));
    }

    private static MailStatePacket decode(RegistryFriendlyByteBuf buffer) {
        int unreadCount = buffer.readVarInt();
        boolean tutorialPending = buffer.readBoolean();
        String ownContactKey = buffer.readUtf(64);
        int messageCount = Math.min(MAX_MESSAGES, buffer.readVarInt());
        List<MailMessageData> messages = new ArrayList<>(messageCount);
        for (int i = 0; i < messageCount; i++) {
            messages.add(MailMessageData.decode(buffer));
        }
        int contactCount = Math.min(MAX_CONTACTS, buffer.readVarInt());
        List<MailContactData> contacts = new ArrayList<>(contactCount);
        for (int i = 0; i < contactCount; i++) {
            contacts.add(MailContactData.decode(buffer));
        }
        return new MailStatePacket(unreadCount, tutorialPending, ownContactKey, messages, contacts);
    }

    public static void handle(MailStatePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientboundNetworkBridge.handleMailState(packet));
    }
}
