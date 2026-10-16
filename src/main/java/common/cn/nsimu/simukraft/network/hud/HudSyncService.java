package common.cn.nsimu.simukraft.network.hud;

import common.cn.nsimu.simukraft.city.CityData;
import common.cn.nsimu.simukraft.city.CityPermissionLevel;
import common.cn.nsimu.simukraft.city.CityPopulationStats;
import common.cn.nsimu.simukraft.city.CityService;
import common.cn.nsimu.simukraft.city.group.CityUserGroup;
import common.cn.nsimu.simukraft.city.group.CityUserGroupService;
import common.cn.nsimu.simukraft.citizen.CitizenManager;
import common.cn.nsimu.simukraft.util.SaveScopedCacheKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class HudSyncService {
    private static final long SYNC_INTERVAL_TICKS = 20L;
    private static final Map<String, HudState> LAST_SYNC_STATES = new ConcurrentHashMap<>();

    private HudSyncService() {
    }

    public static void tick(ServerLevel level) {
        if (level == null || level.getGameTime() % SYNC_INTERVAL_TICKS != 0L) {
            return;
        }
        java.util.List<ServerPlayer> players = level.players();
        if (players.isEmpty()) {
            return;
        }
        int worldPopulation = CitizenManager.get(level).getWorldPopulation();
        for (ServerPlayer player : players) {
            syncToPlayer(player, false, worldPopulation);
        }
    }

    public static void syncToPlayer(ServerPlayer player, boolean force) {
        syncToPlayer(player, force, CitizenManager.get(player.serverLevel()).getWorldPopulation());
    }

    private static void syncToPlayer(ServerPlayer player, boolean force, int worldPopulation) {
        if (player == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        Optional<CityData> city = CityService.findPlayerCity(level, player.getUUID());
        int currentDay = (int) Math.max(1L, level.getDayTime() / 24000L + 1L);
        boolean creativeMode = player.isCreative();
        HudState state = city.map(cityData -> {
            CityPermissionLevel permissionLevel = CityService.getPlayerPermission(cityData, player.getUUID());
            return new HudState(
                    currentDay,
                    worldPopulation,
                    cityData.cityName(),
                    cityData.funds(),
                    CityPopulationStats.population(level, cityData.cityId()),
                    permissionLevel,
                    creativeMode
            );
        }).orElseGet(() -> new HudState(currentDay, worldPopulation, "", 0.0D, 0, CityPermissionLevel.CITIZEN, creativeMode));
        HudState previous = LAST_SYNC_STATES.put(SaveScopedCacheKey.playerKey(player), state);
        if (!force && state.equals(previous)) {
            return;
        }
        PacketDistributor.sendToPlayer(player, new HudSyncPacket(
                state.currentDay(),
                state.worldPopulation(),
                state.cityName(),
                state.cityFunds(),
                state.cityPopulation(),
                state.permissionLevel(),
                state.creativeMode()
        ));
    }

    // syncToCityGroup: 立即同步城市用户组内所有在线玩家的 HUD。
    public static void syncToCityGroup(ServerLevel level, UUID cityId, boolean force) {
        CityUserGroupService.forEach(level, CityUserGroup.members(cityId), player -> syncToPlayer(player, force));
    }

    // syncResolvedGroup: 同步已经解析好的用户组快照，供成员移除等场景使用。
    public static void syncResolvedGroup(Collection<ServerPlayer> players, boolean force) {
        if (players == null || players.isEmpty()) {
            return;
        }
        players.forEach(player -> syncToPlayer(player, force));
    }

    public static void clearPlayer(UUID playerId) {
        if (playerId != null) {
            LAST_SYNC_STATES.keySet().removeIf(key -> key.endsWith("|player=" + playerId));
        }
    }

    // 清理指定存档的 HUD 同步快照，防止切换世界后首帧被旧状态跳过。
    public static void clearServerCaches(MinecraftServer server) {
        String serverKey = SaveScopedCacheKey.serverKey(server);
        LAST_SYNC_STATES.keySet().removeIf(key -> key.startsWith(serverKey + "|"));
    }

    private record HudState(int currentDay, int worldPopulation, String cityName, double cityFunds,
                            int cityPopulation, CityPermissionLevel permissionLevel, boolean creativeMode) {
    }
}
