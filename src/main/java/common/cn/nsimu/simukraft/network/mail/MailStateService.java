package common.cn.nsimu.simukraft.network.mail;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 管理服务端邮件未读状态，并把结果同步给对应玩家。 */
public final class MailStateService {
    private static final ConcurrentHashMap<UUID, Integer> UNREAD_COUNTS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, SenderLocation> LAST_SENDER_LOCATIONS = new ConcurrentHashMap<>();

    private MailStateService() {
    }

    /** 登录时同步服务端缓存的邮件状态。 */
    public static void syncToPlayer(ServerPlayer player) {
        if (player != null) {
            send(player, count(player));
        }
    }

    /** 调试来信：增加一封未读邮件并立即同步。 */
    public static int receiveTest(ServerPlayer player) {
        if (player == null) {
            return 0;
        }
        int count = UNREAD_COUNTS.merge(player.getUUID(), 1, Integer::sum);
        LAST_SENDER_LOCATIONS.put(player.getUUID(), new SenderLocation(
                player.level().dimension().location(), player.blockPosition().getX(), player.blockPosition().getZ()));
        send(player, count);
        return count;
    }

    /** 打开邮件界面后由服务端确认已读。 */
    public static void markRead(ServerPlayer player) {
        if (player == null) {
            return;
        }
        UNREAD_COUNTS.remove(player.getUUID());
        send(player, 0);
    }

    /** 玩家离线时释放内存状态，避免跨存档残留。 */
    public static void clearPlayer(ServerPlayer player) {
        if (player != null) {
            UNREAD_COUNTS.remove(player.getUUID());
            LAST_SENDER_LOCATIONS.remove(player.getUUID());
        }
    }

    /** 服务器关闭时清理全部在线状态。 */
    public static void clearAll() {
        UNREAD_COUNTS.clear();
        LAST_SENDER_LOCATIONS.clear();
    }

    private static int count(ServerPlayer player) {
        return Math.max(0, UNREAD_COUNTS.getOrDefault(player.getUUID(), 0));
    }

    private static void send(ServerPlayer player, int count) {
        SenderLocation location = LAST_SENDER_LOCATIONS.get(player.getUUID());
        PacketDistributor.sendToPlayer(player, location == null
                ? new MailStatePacket(count, null, 0, 0)
                : new MailStatePacket(count, location.dimension(), location.x(), location.z()));
    }

    private record SenderLocation(ResourceLocation dimension, int x, int z) {
    }
}
