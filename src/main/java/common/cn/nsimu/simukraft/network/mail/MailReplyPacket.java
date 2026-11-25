package common.cn.nsimu.simukraft.network.mail;

import common.cn.nsimu.simukraft.SimuKraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 玩家在邮件界面回复市民或城市；服务端重新校验回复对象、分类、长度和频率。 */
public record MailReplyPacket(MailCategory category, String targetKey, String text) implements CustomPacketPayload {
    public static final Type<MailReplyPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "mail_reply"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MailReplyPacket> STREAM_CODEC =
            StreamCodec.of(MailReplyPacket::encode, MailReplyPacket::decode);

    @Override
    public Type<MailReplyPacket> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buffer, MailReplyPacket packet) {
        buffer.writeEnum(packet.category());
        buffer.writeUtf(packet.targetKey(), 64);
        buffer.writeUtf(packet.text(), MailStateService.MAX_REPLY_LENGTH * 4);
    }

    private static MailReplyPacket decode(RegistryFriendlyByteBuf buffer) {
        return new MailReplyPacket(buffer.readEnum(MailCategory.class), buffer.readUtf(64),
                buffer.readUtf(MailStateService.MAX_REPLY_LENGTH * 4));
    }

    public static void handle(MailReplyPacket packet, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            context.enqueueWork(() -> MailStateService.reply(player, packet.category(), packet.targetKey(), packet.text()));
        }
    }
}
