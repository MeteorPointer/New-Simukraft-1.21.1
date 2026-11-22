package common.cn.nsimu.simukraft.network.mail;

import common.cn.nsimu.simukraft.city.CityData;
import common.cn.nsimu.simukraft.city.CityMemberData;
import common.cn.nsimu.simukraft.city.CityPermissionLevel;
import common.cn.nsimu.simukraft.city.CityService;
import common.cn.nsimu.simukraft.city.FinanceTransactionData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** 管理邮件、周报生成和服务端到客户端的邮件快照同步。 */
public final class MailStateService {
    private static final long TICKS_PER_WEEK = 7L * 24000L;
    private static final int MAX_MESSAGES = 64;
    private static final ConcurrentHashMap<UUID, Integer> UNREAD_COUNTS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, List<MailMessageData>> MESSAGES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, SenderLocation> LAST_SENDER_LOCATIONS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<MinecraftServer, Long> LAST_REPORT_WEEK = new ConcurrentHashMap<>();
    private static final AtomicLong NEXT_ID = new AtomicLong(1L);
    private static final DecimalFormat MONEY = new DecimalFormat("0.00");

    private MailStateService() {
    }

    /** 登录时同步服务端缓存的邮件状态。 */
    public static void syncToPlayer(ServerPlayer player) {
        if (player != null) send(player);
    }

    /** 调试来信：增加一封系统邮件并立即同步。 */
    public static int receiveTest(ServerPlayer player) {
        if (player == null) return 0;
        return addSystemMail(player, "信息系统", "测试来信",
                "这是一封测试邮件。邮件系统已连接，未读状态和客户端同步正常。", player.level().getGameTime());
    }

    /** 立即给执行者发送当前城市的结算测试邮件，不改变周报去重状态。 */
    public static boolean sendTestWeeklyReport(ServerPlayer player) {
        if (player == null) return false;
        ServerLevel level = player.serverLevel();
        CityData city = CityService.findPlayerCity(level, player.getUUID()).orElse(null);
        if (city == null) return false;
        long week = Math.max(0L, level.getGameTime() / TICKS_PER_WEEK);
        String body = buildFinancialReportBody(level, city, player.getGameProfile().getName(), week);
        addSystemMail(player, "城市管理系统", city.cityName() + "结算测试报告", body, level.getGameTime());
        return true;
    }

    /** 向玩家投递系统邮件；调用方必须位于服务端主线程。 */
    public static int addSystemMail(ServerPlayer player, String sender, String subject, String body, long timestamp) {
        if (player == null) return 0;
        MailMessageData message = new MailMessageData(NEXT_ID.getAndIncrement(), "SYSTEM", sender, subject, body, timestamp, true);
        MESSAGES.compute(player.getUUID(), (id, current) -> {
            List<MailMessageData> next = new ArrayList<>();
            if (current != null) next.addAll(current);
            next.add(0, message);
            if (next.size() > MAX_MESSAGES) next.subList(MAX_MESSAGES, next.size()).clear();
            return List.copyOf(next);
        });
        int count = UNREAD_COUNTS.merge(player.getUUID(), 1, Integer::sum);
        LAST_SENDER_LOCATIONS.put(player.getUUID(), new SenderLocation(
                player.level().dimension().location(), player.blockPosition().getX(), player.blockPosition().getZ()));
        send(player, count);
        return count;
    }

    /** 打开邮件界面后由服务端确认已读。 */
    public static void markRead(ServerPlayer player) {
        if (player == null) return;
        UNREAD_COUNTS.remove(player.getUUID());
        MESSAGES.computeIfPresent(player.getUUID(), (id, current) -> current.stream().map(MailMessageData::markRead).toList());
        send(player, 0);
    }

    /** 每周生成一次各城市财务报告，在线市长立即收件，离线市长登录时同步。 */
    public static void tickWeeklyReports(MinecraftServer server) {
        if (server == null || server.overworld() == null) return;
        long week = Math.max(0L, server.overworld().getGameTime() / TICKS_PER_WEEK);
        Long previous = LAST_REPORT_WEEK.putIfAbsent(server, week);
        if (previous != null && previous == week) return;
        if (previous != null && previous > week) {
            LAST_REPORT_WEEK.put(server, week);
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            for (CityData city : CityService.allCities(level)) {
                CityMemberData recipient = city.members().stream()
                        .filter(member -> member.permissionLevel() == CityPermissionLevel.MAYOR)
                        .findFirst().orElse(null);
                if (recipient == null) continue;
                ServerPlayer player = server.getPlayerList().getPlayer(recipient.playerId());
                if (player == null) continue;
                String body = buildFinancialReportBody(level, city, recipient.playerName(), week);
                addSystemMail(player, "城市管理系统", city.cityName() + "每周财务报告", body, level.getGameTime());
            }
        }
    }

    private static String buildFinancialReportBody(ServerLevel level, CityData city, String recipientName, long week) {
        List<FinanceTransactionData> transactions = city.financeTransactions().stream()
                .filter(transaction -> transaction.time() >= week * TICKS_PER_WEEK
                        && transaction.time() < (week + 1L) * TICKS_PER_WEEK)
                .toList();
        double[] expenses = new double[6];
        double income = 0.0D;
        for (FinanceTransactionData transaction : transactions) {
            if (transaction.type() == FinanceTransactionData.Type.INCOME) {
                income += Math.max(0.0D, transaction.amount());
            } else if (transaction.type() == FinanceTransactionData.Type.EXPENSE) {
                expenses[expenseBucket(transaction.reason())] += Math.max(0.0D, transaction.amount());
            }
        }
        return recipientName + " 城市管理员您好! 这周的财务报表已经发送到您的账户请注意已查收!\n"
                + "建筑花费:" + MONEY.format(expenses[0]) + "$\n"
                + "工业花费:" + MONEY.format(expenses[1]) + "$\n"
                + "物流花费:" + MONEY.format(expenses[2]) + "$\n"
                + "居民花费:" + MONEY.format(expenses[3]) + "$\n"
                + "交易花费:" + MONEY.format(expenses[4]) + "$\n"
                + "其他花费:" + MONEY.format(expenses[5]) + "$\n"
                + "------------\n共计收入:" + MONEY.format(income) + "$\n时间戳:" + level.getGameTime();
    }

    private static int expenseBucket(String reason) {
        String normalized = reason == null ? "" : reason.toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("building") || normalized.contains("planner") || normalized.contains("repair")) return 0;
        if (normalized.contains("industrial") || normalized.contains("drill")) return 1;
        if (normalized.contains("logistic")) return 2;
        if (normalized.contains("rent") || normalized.contains("residential") || normalized.contains("citizen")) return 3;
        if (normalized.contains("trade") || normalized.contains("market") || normalized.contains("commercial")) return 4;
        return 5;
    }

    /** 玩家离线时保留邮件，服务器关闭时清理本次运行状态。 */
    public static void clearPlayer(ServerPlayer player) {
        if (player != null) LAST_SENDER_LOCATIONS.remove(player.getUUID());
    }

    public static void clearAll() {
        UNREAD_COUNTS.clear();
        MESSAGES.clear();
        LAST_SENDER_LOCATIONS.clear();
        LAST_REPORT_WEEK.clear();
    }

    public static List<MailMessageData> messages(UUID playerId) {
        return playerId == null ? List.of() : MESSAGES.getOrDefault(playerId, List.of());
    }

    private static int count(ServerPlayer player) {
        return Math.max(0, UNREAD_COUNTS.getOrDefault(player.getUUID(), 0));
    }

    private static void send(ServerPlayer player) {
        send(player, count(player));
    }

    private static void send(ServerPlayer player, int count) {
        SenderLocation location = LAST_SENDER_LOCATIONS.get(player.getUUID());
        List<MailMessageData> messages = messages(player.getUUID());
        PacketDistributor.sendToPlayer(player, location == null
                ? new MailStatePacket(count, null, 0, 0, messages)
                : new MailStatePacket(count, location.dimension(), location.x(), location.z(), messages));
    }

    private record SenderLocation(ResourceLocation dimension, int x, int z) {
    }
}
