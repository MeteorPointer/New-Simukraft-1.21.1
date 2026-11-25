package client.cn.nsimu.simukraft.client.mail;

import common.cn.nsimu.simukraft.network.mail.MailContactData;
import common.cn.nsimu.simukraft.network.mail.MailMarkReadPacket;
import common.cn.nsimu.simukraft.network.mail.MailMessageData;
import common.cn.nsimu.simukraft.network.mail.MailStatePacket;
import common.cn.nsimu.simukraft.network.mail.MailTutorialDonePacket;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 保存服务端下发的邮件快照，并提供打开邮件界面和教程的入口。客户端只读，不改权威数据。 */
public final class MailClientState {
    private static volatile int unreadCount;
    private static volatile long shakeStartedAt = -1L;
    private static volatile boolean tutorialPending;
    private static volatile boolean tutorialRequested;
    private static volatile boolean tutorialShownThisSession;
    private static volatile String ownContactKey = "";
    private static volatile List<MailMessageData> messages = List.of();
    private static volatile Map<String, MailContactData> contacts = Map.of();
    private static volatile int version;

    private MailClientState() {
    }

    /**
     * 接收服务端快照；未读数增加时重新触发晃动。
     *
     * <p>教程是否待看以服务端为准：服务端从“已看完”变成“待看”（例如执行了重置指令）时清掉本次会话的弹出记录，
     * 这样重置后不用重进世界也会再次弹出。
     */
    public static void apply(MailStatePacket packet) {
        int previousUnread = unreadCount;
        boolean wasPending = tutorialPending;
        unreadCount = packet.unreadCount();
        tutorialPending = packet.tutorialPending();
        if (tutorialPending && !wasPending) tutorialShownThisSession = false;
        ownContactKey = packet.ownContactKey();
        messages = packet.messages();
        Map<String, MailContactData> byKey = new HashMap<>();
        for (MailContactData contact : packet.contacts()) byKey.put(contact.key(), contact);
        contacts = Map.copyOf(byKey);
        version++;
        if (unreadCount > previousUnread) shakeStartedAt = System.currentTimeMillis();
        if (unreadCount == 0) shakeStartedAt = -1L;
        if (tutorialPending && !messages.isEmpty() && !tutorialShownThisSession) tutorialRequested = true;
    }

    /** 客户端 tick：没有其他界面时弹出教程。 */
    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!tutorialRequested || minecraft.player == null || minecraft.screen != null) return;
        tutorialRequested = false;
        tutorialShownThisSession = true;
        minecraft.setScreen(new MailTutorialScreen());
    }

    /** 教程看完或跳过：通知服务端写入存档。 */
    public static void finishTutorial() {
        tutorialPending = false;
        tutorialRequested = false;
        tutorialShownThisSession = true;
        PacketDistributor.sendToServer(new MailTutorialDonePacket());
    }

    public static List<MailMessageData> messages() {
        return messages;
    }

    public static MailContactData contact(String key) {
        return key == null ? null : contacts.get(key);
    }

    public static String ownContactKey() {
        return ownContactKey;
    }

    public static int version() {
        return version;
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

    /** 打开邮件界面并请求服务端标记已读；教程还没看完时在邮件界面里继续引导。 */
    public static void open() {
        boolean withTutorial = tutorialPending;
        if (withTutorial) {
            tutorialRequested = false;
            tutorialShownThisSession = true;
        }
        open(withTutorial);
    }

    public static boolean tutorialPending() {
        return tutorialPending;
    }

    /** 打开邮件界面；withTutorial 为 true 时继续邮件内的教程步骤。 */
    public static void open(boolean withTutorial) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        requestMarkRead();
        minecraft.setScreen(new MailScreen(withTutorial));
    }

    /** 界面打开期间收到新邮件时再次确认已读，未读数由服务端回传。 */
    static void requestMarkRead() {
        if (unreadCount > 0) {
            unreadCount = 0;
            shakeStartedAt = -1L;
        }
        PacketDistributor.sendToServer(new MailMarkReadPacket());
    }

    public static void reset() {
        unreadCount = 0;
        shakeStartedAt = -1L;
        tutorialPending = false;
        tutorialRequested = false;
        tutorialShownThisSession = false;
        ownContactKey = "";
        messages = List.of();
        contacts = Map.of();
        version++;
    }
}
