package common.cn.nsimu.simukraft.network.mail;

import common.cn.nsimu.simukraft.SimuKraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 客户端看完或跳过邮件教程后通知服务端，服务端记入存档，之后不再弹出。 */
public record MailTutorialDonePacket() implements CustomPacketPayload {
    public static final Type<MailTutorialDonePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "mail_tutorial_done"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MailTutorialDonePacket> STREAM_CODEC =
            StreamCodec.unit(new MailTutorialDonePacket());

    @Override
    public Type<MailTutorialDonePacket> type() {
        return TYPE;
    }

    public static void handle(MailTutorialDonePacket packet, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            context.enqueueWork(() -> MailStateService.completeTutorial(player));
        }
    }
}
