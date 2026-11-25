package common.cn.nsimu.simukraft.network.mail;

import common.cn.nsimu.simukraft.SimuKraft;
import common.cn.nsimu.simukraft.config.ServerConfig;
import common.cn.nsimu.simukraft.storage.MailSqliteRepository;
import common.cn.nsimu.simukraft.storage.SimuSqliteStorage;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 邮件门面：投递、已读、回复、教程状态和客户端同步。
 *
 * <p>内存里的邮箱是本次运行的权威副本，SQLite 只经顺序写队列追加和标记已读。
 * 离线玩家的邮件先挂在 pending 里，登录读库时与库里的行按 id 合并，避免读到尚未落库的写入。
 */
public final class MailStateService {
    /** 单条回复的最大字符数。 */
    public static final int MAX_REPLY_LENGTH = 160;
    static final int MAX_BROADCASTS = 32;
    private static final long REPLY_COOLDOWN_TICKS = 20L;
    private static final ConcurrentHashMap<MinecraftServer, ServerMail> SERVERS = new ConcurrentHashMap<>();

    private MailStateService() {
    }

    /** 登录时读档并同步邮件状态。 */
    public static void syncToPlayer(ServerPlayer player) {
        if (player != null) send(player);
    }

    /** 向玩家投递一封系统邮件；必须在服务端主线程调用。 */
    public static int addSystemMail(ServerPlayer player, Component sender, Component subject, Component body) {
        if (player == null) return 0;
        deliver(player.server, player.getUUID(), MailCategory.SYSTEM, MailCategory.SYSTEM_KEY, sender, subject, body, true);
        return unreadCount(player.server, player.getUUID());
    }

    /** 调试来信：增加一封系统邮件并立即同步。 */
    public static int receiveTest(ServerPlayer player) {
        return addSystemMail(player, Component.translatable("mail.simukraft.sender.information_system"),
                Component.translatable("mail.simukraft.test.subject"), Component.translatable("mail.simukraft.test.body"));
    }

    /**
     * 向任意玩家投递邮件，离线玩家登录后收到。必须在服务端主线程调用。
     */
    public static void deliver(MinecraftServer server, UUID recipientId, MailCategory category, String senderKey,
                               Component sender, Component subject, Component body, boolean unread) {
        if (server == null || recipientId == null) return;
        ServerMail state = state(server);
        MailMessageData message = new MailMessageData(nextId(server, state), category, senderKey, sender, subject, body,
                server.overworld().getGameTime(), unread, false, false);
        store(server, state, recipientId, message);
        ServerPlayer online = server.getPlayerList().getPlayer(recipientId);
        if (online != null) send(online);
    }

    /** 全服广播一条城市动态；广播不计未读，发给所有在线玩家。 */
    public static void broadcast(MinecraftServer server, MailCategory category, String senderKey,
                                 Component sender, Component subject, Component body) {
        if (server == null) return;
        ServerMail state = state(server);
        List<MailMessageData> broadcasts = broadcasts(server, state);
        MailMessageData message = new MailMessageData(nextId(server, state), category, senderKey, sender, subject, body,
                server.overworld().getGameTime(), false, false, true);
        List<MailMessageData> next = new ArrayList<>(broadcasts.size() + 1);
        next.add(message);
        next.addAll(broadcasts);
        state.broadcasts = trim(next, MAX_BROADCASTS);
        SimuSqliteStorage.saveMail(server, toRow(server, MailSqliteRepository.BROADCAST_RECIPIENT, message), MAX_BROADCASTS);
        MailContactCards.Cache cache = new MailContactCards.Cache();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            send(player, cache);
        }
    }

    /** 玩家在邮件界面查看某个标签页后，由服务端把该分类标为已读；其他分类保持未读。 */
    public static void markRead(ServerPlayer player, MailCategory category) {
        if (player == null || category == null) return;
        Mailbox box = mailbox(player.server, player.getUUID());
        boolean changed = box.messages.stream().anyMatch(message -> message.category() == category && message.unread());
        if (!changed) return;
        long maxId = box.messages.stream().filter(message -> message.category() == category)
                .mapToLong(MailMessageData::id).max().orElse(0L);
        box.messages = box.messages.stream()
                .map(message -> message.category() == category ? message.markRead() : message).toList();
        SimuSqliteStorage.markMailRead(player.server, player.getUUID().toString(), category.name(), maxId);
        send(player);
    }

    /** 处理玩家回复：服务端重新校验分类、对象、长度和频率，客户端只提交请求。 */
    public static void reply(ServerPlayer player, MailCategory category, String targetKey, String rawText) {
        if (player == null || category == null || !category.allowsReply()) return;
        String text = sanitize(rawText);
        if (text.isEmpty()) return;
        ServerMail state = state(player.server);
        long now = player.server.overworld().getGameTime();
        Long last = state.lastReplyTick.get(player.getUUID());
        if (last != null && now - last < REPLY_COOLDOWN_TICKS) return;
        MailContactCards.ReplyTarget target = MailContactCards.resolveReplyTarget(player, category, targetKey, text);
        if (target == null) {
            common.cn.nsimu.simukraft.network.toast.InfoToastService.warning(player,
                    Component.translatable("message.simukraft.mail.reply.invalid_target"));
            return;
        }
        state.lastReplyTick.put(player.getUUID(), now);
        MailMessageData outgoing = new MailMessageData(nextId(player.server, state), category, target.key(),
                Component.literal(player.getGameProfile().getName()), Component.empty(), Component.literal(text),
                now, false, true, false);
        store(player.server, state, player.getUUID(), outgoing);
        for (MailContactCards.Delivery delivery : target.deliveries()) {
            deliver(player.server, delivery.recipientId(), category, delivery.senderKey(), delivery.sender(),
                    delivery.subject(), delivery.body(), delivery.unread());
        }
        send(player);
    }

    /** 记录玩家已看完邮件教程。 */
    public static void completeTutorial(ServerPlayer player) {
        if (player == null) return;
        Mailbox box = mailbox(player.server, player.getUUID());
        if (box.tutorialDone) return;
        box.tutorialDone = true;
        SimuSqliteStorage.saveMailTutorialDone(player.server, player.getUUID().toString(), true);
        send(player);
    }

    /** 调试用：让玩家下次收到邮件时重新看教程。 */
    public static void resetTutorial(ServerPlayer player) {
        if (player == null) return;
        mailbox(player.server, player.getUUID()).tutorialDone = false;
        SimuSqliteStorage.saveMailTutorialDone(player.server, player.getUUID().toString(), false);
        send(player);
    }

    /** 玩家下线时清理回复频率记录；邮箱缓存保留到关服，保证离线投递与读库一致。 */
    public static void clearPlayer(ServerPlayer player) {
        if (player != null) {
            ServerMail state = SERVERS.get(player.server);
            if (state != null) state.lastReplyTick.remove(player.getUUID());
        }
    }

    /** 关服或切档时丢掉本服全部邮件缓存。 */
    public static void clearServerCaches(MinecraftServer server) {
        if (server != null) SERVERS.remove(server);
        MailScheduleStore.clearServerCaches(server);
        MailCitizenRequestService.clearServerCaches(server);
        MailWeeklyReportService.clearServerCaches(server);
    }

    static int unreadCount(MinecraftServer server, UUID playerId) {
        return (int) mailbox(server, playerId).messages.stream().filter(MailMessageData::countsAsUnread).count();
    }

    private static void send(ServerPlayer player) {
        send(player, new MailContactCards.Cache());
    }

    private static void send(ServerPlayer player, MailContactCards.Cache cache) {
        MinecraftServer server = player.server;
        Mailbox box = mailbox(server, player.getUUID());
        String ownCityKey = MailContactCards.ownCityKey(player);
        List<MailMessageData> visible = new ArrayList<>(box.messages);
        for (MailMessageData message : broadcasts(server, state(server))) {
            if (!message.senderKey().equals(ownCityKey)) visible.add(message);
        }
        visible.sort(Comparator.comparingLong(MailMessageData::id).reversed());
        int unread = (int) box.messages.stream().filter(MailMessageData::countsAsUnread).count();
        PacketDistributor.sendToPlayer(player, new MailStatePacket(unread, !box.tutorialDone, ownCityKey, visible,
                MailContactCards.build(player, visible, ownCityKey, cache)));
    }

    private static void store(MinecraftServer server, ServerMail state, UUID recipientId, MailMessageData message) {
        int limit = ServerConfig.mailMaxMessagesPerPlayer();
        Mailbox box = state.boxes.get(recipientId);
        if (box != null) {
            List<MailMessageData> next = new ArrayList<>(box.messages.size() + 1);
            next.add(message);
            next.addAll(box.messages);
            box.messages = trim(next, limit);
        } else {
            List<MailMessageData> pending = state.pending.computeIfAbsent(recipientId, id -> new CopyOnWriteArrayList<>());
            pending.add(message);
            while (pending.size() > limit) pending.remove(0);
        }
        SimuSqliteStorage.saveMail(server, toRow(server, recipientId.toString(), message), limit);
    }

    private static Mailbox mailbox(MinecraftServer server, UUID playerId) {
        ServerMail state = state(server);
        return state.boxes.computeIfAbsent(playerId, id -> {
            Mailbox box = new Mailbox();
            List<MailMessageData> merged = new ArrayList<>(load(server, id.toString()));
            List<MailMessageData> pending = state.pending.remove(id);
            if (pending != null) {
                for (MailMessageData message : pending) {
                    if (merged.stream().noneMatch(existing -> existing.id() == message.id())) merged.add(message);
                }
            }
            merged.sort(Comparator.comparingLong(MailMessageData::id).reversed());
            box.messages = trim(merged, ServerConfig.mailMaxMessagesPerPlayer());
            box.tutorialDone = SimuSqliteStorage.isMailTutorialDone(server, id.toString());
            return box;
        });
    }

    private static List<MailMessageData> broadcasts(MinecraftServer server, ServerMail state) {
        List<MailMessageData> current = state.broadcasts;
        if (current == null) {
            current = trim(load(server, MailSqliteRepository.BROADCAST_RECIPIENT), MAX_BROADCASTS);
            state.broadcasts = current;
        }
        return current;
    }

    private static List<MailMessageData> load(MinecraftServer server, String recipientId) {
        int limit = MailSqliteRepository.BROADCAST_RECIPIENT.equals(recipientId) ? MAX_BROADCASTS : ServerConfig.mailMaxMessagesPerPlayer();
        List<MailSqliteRepository.MailRow> rows = SimuSqliteStorage.loadMail(server, recipientId, limit);
        if (rows == null) return List.of();
        List<MailMessageData> messages = new ArrayList<>(rows.size());
        for (MailSqliteRepository.MailRow row : rows) {
            messages.add(fromRow(server.registryAccess(), row));
        }
        return messages;
    }

    private static long nextId(MinecraftServer server, ServerMail state) {
        synchronized (state) {
            if (state.nextId.get() <= 0L) {
                state.nextId.set(Math.max(0L, SimuSqliteStorage.maxMailId(server)) + 1L);
            }
            return state.nextId.getAndIncrement();
        }
    }

    private static ServerMail state(MinecraftServer server) {
        return SERVERS.computeIfAbsent(server, key -> new ServerMail());
    }

    private static List<MailMessageData> trim(List<MailMessageData> messages, int limit) {
        return List.copyOf(messages.size() > limit ? messages.subList(0, limit) : messages);
    }

    private static String sanitize(String raw) {
        if (raw == null) return "";
        StringBuilder builder = new StringBuilder();
        raw.codePoints().filter(codePoint -> !Character.isISOControl(codePoint) && codePoint != '§')
                .limit(MAX_REPLY_LENGTH).forEach(builder::appendCodePoint);
        return builder.toString().strip();
    }

    private static MailSqliteRepository.MailRow toRow(MinecraftServer server, String recipientId, MailMessageData message) {
        HolderLookup.Provider registries = server.registryAccess();
        return new MailSqliteRepository.MailRow(message.id(), recipientId, message.category().name(), message.senderKey(),
                Component.Serializer.toJson(message.sender(), registries),
                Component.Serializer.toJson(message.subject(), registries),
                Component.Serializer.toJson(message.body(), registries),
                message.timestamp(), message.unread(), message.outgoing());
    }

    private static MailMessageData fromRow(HolderLookup.Provider registries, MailSqliteRepository.MailRow row) {
        return new MailMessageData(row.id(), MailCategory.fromName(row.category()), row.senderKey(),
                parse(registries, row.senderJson()), parse(registries, row.subjectJson()), parse(registries, row.bodyJson()),
                row.gameTime(), row.unread(), row.outgoing(), MailSqliteRepository.BROADCAST_RECIPIENT.equals(row.recipientId()));
    }

    private static Component parse(HolderLookup.Provider registries, String json) {
        if (json == null || json.isBlank()) return Component.empty();
        try {
            Component component = Component.Serializer.fromJson(json, registries);
            return component == null ? Component.empty() : component;
        } catch (RuntimeException exception) {
            SimuKraft.LOGGER.warn("Skipping unreadable mail text: {}", exception.getMessage());
            return Component.literal(json);
        }
    }

    private static final class ServerMail {
        final Map<UUID, Mailbox> boxes = new ConcurrentHashMap<>();
        final Map<UUID, List<MailMessageData>> pending = new ConcurrentHashMap<>();
        final Map<UUID, Long> lastReplyTick = new ConcurrentHashMap<>();
        final AtomicLong nextId = new AtomicLong();
        volatile List<MailMessageData> broadcasts;
    }

    private static final class Mailbox {
        volatile List<MailMessageData> messages = List.of();
        volatile boolean tutorialDone;
    }
}
