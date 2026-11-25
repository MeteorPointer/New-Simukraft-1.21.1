package common.cn.nsimu.simukraft.network.mail;

import common.cn.nsimu.simukraft.storage.SimuSqliteStorage;
import net.minecraft.server.MinecraftServer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 邮件调度游标（周报已发周数、市民诉求冷却日），按服务器缓存并写回 SQLite。仅本包使用。 */
final class MailScheduleStore {
    private static final ConcurrentHashMap<MinecraftServer, Map<String, Long>> VALUES = new ConcurrentHashMap<>();

    private MailScheduleStore() {
    }

    static Long get(MinecraftServer server, String key) {
        return values(server).get(key);
    }

    static void put(MinecraftServer server, String key, long value) {
        values(server).put(key, value);
        SimuSqliteStorage.saveMailSchedule(server, key, value);
    }

    static void clearServerCaches(MinecraftServer server) {
        if (server != null) VALUES.remove(server);
    }

    private static Map<String, Long> values(MinecraftServer server) {
        return VALUES.computeIfAbsent(server, key -> {
            Map<String, Long> loaded = SimuSqliteStorage.loadMailSchedule(key);
            return loaded == null ? new ConcurrentHashMap<>() : new ConcurrentHashMap<>(loaded);
        });
    }
}
