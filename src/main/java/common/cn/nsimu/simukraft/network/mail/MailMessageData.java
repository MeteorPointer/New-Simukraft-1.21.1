package common.cn.nsimu.simukraft.network.mail;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

/**
 * 客户端邮件列表使用的不可变消息快照。
 *
 * <p>senderKey 标识发件方或回复对象：{@code citizen:<uuid>}、{@code city:<uuid>}、{@code system}。
 * outgoing 为 true 时是玩家自己发出的回复，senderKey 指向回复对象。
 */
public record MailMessageData(long id, MailCategory category, String senderKey, Component sender,
                              Component subject, Component body, long timestamp,
                              boolean unread, boolean outgoing, boolean broadcast) {
    public MailMessageData {
        category = category == null ? MailCategory.SYSTEM : category;
        senderKey = senderKey == null ? MailCategory.SYSTEM_KEY : senderKey;
        sender = sender == null ? Component.empty() : sender;
        subject = subject == null ? Component.empty() : subject;
        body = body == null ? Component.empty() : body;
    }

    public MailMessageData markRead() {
        return unread ? new MailMessageData(id, category, senderKey, sender, subject, body, timestamp, false, outgoing, broadcast) : this;
    }

    /** 是否计入未读数：玩家自己的回复和全服广播不计入。 */
    public boolean countsAsUnread() {
        return unread && !outgoing && !broadcast;
    }

    static void encode(RegistryFriendlyByteBuf buffer, MailMessageData message) {
        buffer.writeLong(message.id());
        buffer.writeEnum(message.category());
        buffer.writeUtf(message.senderKey(), 64);
        ComponentSerialization.STREAM_CODEC.encode(buffer, message.sender());
        ComponentSerialization.STREAM_CODEC.encode(buffer, message.subject());
        ComponentSerialization.STREAM_CODEC.encode(buffer, message.body());
        buffer.writeLong(message.timestamp());
        buffer.writeBoolean(message.unread());
        buffer.writeBoolean(message.outgoing());
        buffer.writeBoolean(message.broadcast());
    }

    static MailMessageData decode(RegistryFriendlyByteBuf buffer) {
        return new MailMessageData(buffer.readLong(), buffer.readEnum(MailCategory.class), buffer.readUtf(64),
                ComponentSerialization.STREAM_CODEC.decode(buffer), ComponentSerialization.STREAM_CODEC.decode(buffer),
                ComponentSerialization.STREAM_CODEC.decode(buffer), buffer.readLong(), buffer.readBoolean(),
                buffer.readBoolean(), buffer.readBoolean());
    }
}
