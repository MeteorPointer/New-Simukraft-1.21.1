package client.cn.nsimu.simukraft.client.mail;

import common.cn.nsimu.simukraft.network.mail.MailMarkReadPacket;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;

/** 保存客户端邮件缓存，并提供打开邮件界面的入口。 */
public final class MailClientState {
    private static volatile int unreadCount;
    private static volatile long shakeStartedAt = -1L;

    private MailClientState() {
    }

    /** 接收服务端状态；未读状态每次同步都重新触发一秒提示动画。 */
    public static void apply(int count) {
        int safeCount = Math.max(0, count);
        unreadCount = safeCount;
        shakeStartedAt = safeCount > 0 ? System.currentTimeMillis() : -1L;
    }

    public static int unreadCount() {
        return unreadCount;
    }

    public static boolean hasUnread() {
        return unreadCount > 0;
    }

    /** 返回基于客户端毫秒时钟的平滑左右位移，动画最多持续一秒。 */
    public static float shakeOffset(long now) {
        long started = shakeStartedAt;
        if (unreadCount <= 0 || started < 0L) {
            return 0.0F;
        }
        long elapsed = now - started;
        if (elapsed < 0L || elapsed >= 1000L) {
            return 0.0F;
        }
        double progress = elapsed / 1000.0D;
        double envelope = 1.0D - progress;
        return (float) (Math.sin(elapsed * (Math.PI * 2.0D / 650.0D)) * 2.5D * envelope);
    }

    /** 打开邮件界面并立即请求服务端标记已读。 */
    public static void open() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        unreadCount = 0;
        shakeStartedAt = -1L;
        PacketDistributor.sendToServer(new MailMarkReadPacket());
        minecraft.setScreen(new MailScreen());
    }

    public static void reset() {
        unreadCount = 0;
        shakeStartedAt = -1L;
    }
}
