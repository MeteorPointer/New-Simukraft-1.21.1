package common.cn.nsimu.simukraft.network.mail;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * 邮件右侧信息栏展示的发件方名片，由服务端按当前存档生成。
 *
 * <p>dimension 为 null 时没有可显示的位置，地图退回玩家当前位置。
 */
public record MailContactData(String key, Component title, List<Component> lines, String skinPath,
                              ResourceLocation dimension, int x, int z) {
    private static final int MAX_LINES = 12;

    public MailContactData {
        key = key == null ? "" : key;
        title = title == null ? Component.empty() : title;
        lines = lines == null ? List.of() : List.copyOf(lines.subList(0, Math.min(MAX_LINES, lines.size())));
        skinPath = skinPath == null ? "" : skinPath;
    }

    static void encode(RegistryFriendlyByteBuf buffer, MailContactData contact) {
        buffer.writeUtf(contact.key(), 64);
        ComponentSerialization.STREAM_CODEC.encode(buffer, contact.title());
        buffer.writeVarInt(contact.lines().size());
        for (Component line : contact.lines()) {
            ComponentSerialization.STREAM_CODEC.encode(buffer, line);
        }
        buffer.writeUtf(contact.skinPath(), 256);
        buffer.writeBoolean(contact.dimension() != null);
        if (contact.dimension() != null) {
            buffer.writeResourceLocation(contact.dimension());
            buffer.writeInt(contact.x());
            buffer.writeInt(contact.z());
        }
    }

    static MailContactData decode(RegistryFriendlyByteBuf buffer) {
        String key = buffer.readUtf(64);
        Component title = ComponentSerialization.STREAM_CODEC.decode(buffer);
        int size = Math.min(MAX_LINES, buffer.readVarInt());
        List<Component> lines = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            lines.add(ComponentSerialization.STREAM_CODEC.decode(buffer));
        }
        String skinPath = buffer.readUtf(256);
        if (!buffer.readBoolean()) {
            return new MailContactData(key, title, lines, skinPath, null, 0, 0);
        }
        return new MailContactData(key, title, lines, skinPath, buffer.readResourceLocation(), buffer.readInt(), buffer.readInt());
    }
}
