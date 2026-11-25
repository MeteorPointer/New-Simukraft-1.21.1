package common.cn.nsimu.simukraft.network.mail;

import common.cn.nsimu.simukraft.SimuKraft;
import common.cn.nsimu.simukraft.city.CityData;
import common.cn.nsimu.simukraft.city.CityMemberData;
import common.cn.nsimu.simukraft.city.CityPermissionLevel;
import common.cn.nsimu.simukraft.city.CityService;
import common.cn.nsimu.simukraft.city.FinanceTransactionData;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 每周财务报告。周数变化时给每座城市的市长发上一整周的报表，离线市长登录后收到。
 *
 * <p>已发周数按城市存进 mail_schedule，重启或跨周停服后只补发最近一周，不重复发放。
 */
public final class MailWeeklyReportService {
    private static final long TICKS_PER_WEEK = 7L * 24000L;
    private static final String KEY_PREFIX = "report:";
    private static final String[] BUCKETS = {"building", "industrial", "logistics", "residential", "trade", "other"};
    private static final ConcurrentHashMap<MinecraftServer, Long> CHECKED_WEEK = new ConcurrentHashMap<>();

    private MailWeeklyReportService() {
    }

    /** 每个服务端 tick 调用；只有周数变化时才遍历城市。 */
    public static void tick(MinecraftServer server) {
        if (server == null || server.overworld() == null) return;
        long week = server.overworld().getGameTime() / TICKS_PER_WEEK;
        Long checked = CHECKED_WEEK.put(server, week);
        if (checked != null && checked == week) return;
        if (week < 1L) return;
        long reportWeek = week - 1L;
        for (ServerLevel level : server.getAllLevels()) {
            for (CityData city : CityService.allCities(level)) {
                try {
                    String key = KEY_PREFIX + city.cityId();
                    Long last = MailScheduleStore.get(server, key);
                    if (last != null && last >= reportWeek) continue;
                    MailScheduleStore.put(server, key, reportWeek);
                    sendReport(level, city, reportWeek);
                } catch (RuntimeException exception) {
                    SimuKraft.LOGGER.error("Failed to send weekly report for city {}", city.cityId(), exception);
                }
            }
        }
    }

    /** 立即给执行者发送本周到目前为止的结算测试邮件，不改变周报去重状态。 */
    public static boolean sendTestReport(ServerPlayer player) {
        if (player == null) return false;
        ServerLevel level = player.serverLevel();
        CityData city = CityService.findPlayerCity(level, player.getUUID()).orElse(null);
        if (city == null) return false;
        long week = level.getGameTime() / TICKS_PER_WEEK;
        Component body = buildBody(city, player.getGameProfile().getName(), week);
        MailStateService.deliver(player.server, player.getUUID(), MailCategory.SYSTEM, MailCategory.SYSTEM_KEY,
                Component.translatable("mail.simukraft.sender.city_system"),
                Component.translatable("mail.simukraft.report.test_subject", city.cityName()), body, true);
        return true;
    }

    static void clearServerCaches(MinecraftServer server) {
        if (server != null) CHECKED_WEEK.remove(server);
    }

    private static void sendReport(ServerLevel level, CityData city, long week) {
        CityMemberData mayor = city.members().stream()
                .filter(member -> member.permissionLevel() == CityPermissionLevel.MAYOR)
                .findFirst().orElse(null);
        if (mayor == null) return;
        MailStateService.deliver(level.getServer(), mayor.playerId(), MailCategory.SYSTEM, MailCategory.SYSTEM_KEY,
                Component.translatable("mail.simukraft.sender.city_system"),
                Component.translatable("mail.simukraft.report.subject", city.cityName(), week + 1L),
                buildBody(city, mayor.playerName(), week), true);
    }

    private static Component buildBody(CityData city, String recipientName, long week) {
        double[] expenses = new double[BUCKETS.length];
        double income = 0.0D;
        long start = week * TICKS_PER_WEEK;
        long end = start + TICKS_PER_WEEK;
        for (FinanceTransactionData transaction : city.financeTransactions()) {
            if (transaction.time() < start || transaction.time() >= end) continue;
            if (transaction.type() == FinanceTransactionData.Type.INCOME) {
                income += Math.max(0.0D, transaction.amount());
            } else if (transaction.type() == FinanceTransactionData.Type.EXPENSE) {
                expenses[expenseBucket(transaction.reason())] += Math.max(0.0D, transaction.amount());
            }
        }
        double expenseTotal = 0.0D;
        MutableComponent body = Component.translatable("mail.simukraft.report.greeting", recipientName, week + 1L);
        for (int i = 0; i < BUCKETS.length; i++) {
            expenseTotal += expenses[i];
            line(body, "mail.simukraft.report.expense." + BUCKETS[i], expenses[i]);
        }
        body.append("\n").append(Component.translatable("mail.simukraft.report.separator"));
        line(body, "mail.simukraft.report.income_total", income);
        line(body, "mail.simukraft.report.expense_total", expenseTotal);
        line(body, "mail.simukraft.report.net", income - expenseTotal);
        line(body, "mail.simukraft.report.balance", city.funds());
        return body;
    }

    private static void line(MutableComponent body, String key, double amount) {
        body.append("\n").append(Component.translatable(key, String.format(Locale.ROOT, "%.2f", amount)));
    }

    /** 按流水原因归类支出；原因是注册在代码里的英文 id，不是显示名。 */
    private static int expenseBucket(String reason) {
        String normalized = reason == null ? "" : reason.toLowerCase(Locale.ROOT);
        if (normalized.contains("construction") || normalized.contains("building") || normalized.contains("planner")
                || normalized.contains("repair") || normalized.contains("claim_chunk")) return 0;
        if (normalized.contains("industrial") || normalized.contains("drill")) return 1;
        if (normalized.contains("logistic")) return 2;
        if (normalized.contains("rent") || normalized.contains("residential") || normalized.contains("citizen")) return 3;
        if (normalized.contains("trade") || normalized.contains("market") || normalized.contains("commercial")
                || normalized.contains("exchange")) return 4;
        return 5;
    }
}
