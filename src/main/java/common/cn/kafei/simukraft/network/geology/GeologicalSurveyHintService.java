package common.cn.kafei.simukraft.network.geology;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** GeologicalSurveyHintService: 发送地质锤专用客户端提示。 */
public final class GeologicalSurveyHintService {
    private GeologicalSurveyHintService() {
    }

    /** send: 向指定玩家发送一条会覆盖旧内容的勘探提示。 */
    public static void send(ServerPlayer player, Component message) {
        send(player, message, false, 0);
    }

    /** sendTypewriter: 用地质锤文本框逐字打出较长台词。 */
    public static void sendTypewriter(ServerPlayer player, Component message, int lingerMillis) {
        send(player, message, true, lingerMillis);
    }

    private static void send(ServerPlayer player, Component message, boolean typewriter, int lingerMillis) {
        if (player == null || message == null) {
            return;
        }
        PacketDistributor.sendToPlayer(player, new GeologicalSurveyHintPacket(message, typewriter, lingerMillis));
    }
}
