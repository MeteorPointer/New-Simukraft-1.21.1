package common.cn.nsimu.simukraft.util;

import common.cn.nsimu.simukraft.citizen.CitizenData;
import common.cn.nsimu.simukraft.citizen.CitizenManager;
import common.cn.nsimu.simukraft.city.poi.CityPoiData;
import common.cn.nsimu.simukraft.city.poi.CityPoiManager;
import common.cn.nsimu.simukraft.config.ServerConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 调试用的全员强加载。打开后为每个居民的区块保持实体 tick，关掉后按拍释放。
 */
public final class NpcForceLoadService {
    private static final int TICKET_DISTANCE = 2;
    private static final int CHANGES_PER_TICK = 32;
    private static final TicketType<ChunkPos> TICKET = TicketType.create(
            "simukraft_npc_force_load", Comparator.comparingLong(ChunkPos::toLong));
    private static final Map<String, Set<Long>> HELD = new HashMap<>();

    private NpcForceLoadService() {
    }

    /**
     * tick：只在服务端线程调整这一维的强加载票。
     */
    public static void tick(ServerLevel level) {
        if (level == null || level.isClientSide()) {
            return;
        }
        String levelKey = SaveScopedCacheKey.levelKey(level);
        Set<Long> held = HELD.computeIfAbsent(levelKey, ignored -> new HashSet<>());
        if (!ServerConfig.forceLoadAllNpcs()) {
            releaseSome(level, held, CHANGES_PER_TICK);
            if (held.isEmpty()) {
                HELD.remove(levelKey);
            }
            return;
        }
        Set<Long> desired = desiredChunks(level);
        int budget = CHANGES_PER_TICK;
        for (long chunk : desired) {
            if (budget <= 0) {
                break;
            }
            if (held.add(chunk)) {
                ChunkPos chunkPos = new ChunkPos(chunk);
                level.getChunkSource().addRegionTicket(TICKET, chunkPos, TICKET_DISTANCE, chunkPos);
                budget--;
            }
        }
        var iterator = held.iterator();
        while (budget > 0 && iterator.hasNext()) {
            long chunk = iterator.next();
            if (desired.contains(chunk)) {
                continue;
            }
            iterator.remove();
            ChunkPos chunkPos = new ChunkPos(chunk);
            level.getChunkSource().removeRegionTicket(TICKET, chunkPos, TICKET_DISTANCE, chunkPos);
            budget--;
        }
    }

    /**
     * clear：服务器停止时卸掉还没释放的强加载票。
     */
    public static void clear(MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            Set<Long> held = HELD.remove(SaveScopedCacheKey.levelKey(level));
            if (held != null) {
                releaseSome(level, held, held.size());
            }
        }
    }

    private static Set<Long> desiredChunks(ServerLevel level) {
        Set<Long> chunks = new HashSet<>();
        String dimension = level.dimension().location().toString();
        for (CitizenData citizen : CitizenManager.get(level.getServer().overworld()).allCitizens()) {
            if (citizen == null || citizen.dead() || !dimension.equals(citizen.dimensionId())) {
                continue;
            }
            if (citizen.lastKnownChunk().isPresent()) {
                chunks.add(citizen.lastKnownChunk().get().toLong());
                continue;
            }
            if (citizen.homeId() == null) {
                continue;
            }
            CityPoiData home = CityPoiManager.get(level).getPoi(citizen.homeId());
            if (home != null) {
                chunks.add(new ChunkPos(home.pos()).toLong());
            }
        }
        return chunks;
    }

    private static void releaseSome(ServerLevel level, Set<Long> held, int budget) {
        var iterator = held.iterator();
        int remaining = budget;
        while (remaining > 0 && iterator.hasNext()) {
            long chunk = iterator.next();
            iterator.remove();
            ChunkPos chunkPos = new ChunkPos(chunk);
            level.getChunkSource().removeRegionTicket(TICKET, chunkPos, TICKET_DISTANCE, chunkPos);
            remaining--;
        }
    }
}
