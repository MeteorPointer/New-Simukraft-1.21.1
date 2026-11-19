package common.cn.nsimu.simukraft.network.mail;

import common.cn.nsimu.simukraft.SimuKraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 客户端请求服务端将当前玩家邮件标记为已读。 */
public record MailMarkReadPacket() implements CustomPacketPayload {
    public static final Type<MailMarkReadPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "mail_mark_read"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MailMarkReadPacket> STREAM_CODEC =
            StreamCodec.unit(new MailMarkReadPacket());

    @Override
    public Type<MailMarkReadPacket> type() {
        return TYPE;
    }

    public static void handle(MailMarkReadPacket packet, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            context.enqueueWork(() -> MailStateService.markRead(player));
        }
    }
}
