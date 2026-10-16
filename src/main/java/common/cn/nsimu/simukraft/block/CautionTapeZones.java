package common.cn.nsimu.simukraft.block;

import common.cn.nsimu.simukraft.citizen.CitizenService;
import common.cn.nsimu.simukraft.city.CityData;
import common.cn.nsimu.simukraft.city.CityService;
import common.cn.nsimu.simukraft.path.PathExtraCosts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import javax.annotation.Nullable;
import java.util.*;

/**
 * CautionTapeZones: 闭合警戒线内部。只给放置者所属城市的 NPC 加寻路代价。
 */
public final class CautionTapeZones {
    public static final double EXTRA_COST = 20.0D;

    private static volatile List<Zone> zones = List.of();

    private CautionTapeZones() {
    }

    public static void registerPathCost() {
        PathExtraCosts.register(CautionTapeZones::extra);
    }

    public static void rebuild(BlockPos ignored) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            zones = List.of();
            return;
        }
        List<Zone> next = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            Map<BlockPos, UUID> entries = CautionTapePlacers.get(level).entries();
            Map<UUID, List<BlockPos>> byPlacer = new HashMap<>();
            entries.forEach((pos, placer) -> byPlacer.computeIfAbsent(placer, key -> new ArrayList<>()).add(pos));
            for (Map.Entry<UUID, List<BlockPos>> entry : byPlacer.entrySet()) {
                UUID cityId = CityService.findPlayerCity(level, entry.getKey()).map(CityData::cityId).orElse(null);
                if (cityId == null) {
                    continue;
                }
                next.addAll(enclosed(entry.getValue(), cityId, level.dimension().location()));
            }
        }
        zones = List.copyOf(next);
    }

    public static double extra(UUID citizenId, ResourceLocation dimension, BlockPos pos) {
        if (citizenId == null || dimension == null || pos == null || zones.isEmpty()) {
            return 0.0D;
        }
        UUID citizenCity = citizenCity(dimension, citizenId);
        if (citizenCity == null) {
            return 0.0D;
        }
        for (Zone zone : zones) {
            if (!dimension.equals(zone.dimension()) || !citizenCity.equals(zone.cityId())) {
                continue;
            }
            if (pos.getX() > zone.minX() && pos.getX() < zone.maxX()
                    && pos.getZ() > zone.minZ() && pos.getZ() < zone.maxZ()) {
                return EXTRA_COST;
            }
        }
        return 0.0D;
    }

    private static List<Zone> enclosed(List<BlockPos> positions, UUID cityId, ResourceLocation dimension) {
        List<Zone> result = new ArrayList<>();
        Set<BlockPos> remaining = new HashSet<>(positions);
        while (!remaining.isEmpty()) {
            BlockPos seed = remaining.iterator().next();
            Set<BlockPos> component = new HashSet<>();
            List<BlockPos> queue = new ArrayList<>();
            queue.add(seed);
            remaining.remove(seed);
            for (int index = 0; index < queue.size(); index++) {
                BlockPos current = queue.get(index);
                component.add(current);
                for (BlockPos next : List.of(current.north(), current.south(), current.east(), current.west())) {
                    if (remaining.remove(next)) {
                        queue.add(next);
                    }
                }
            }
            int minX = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxZ = Integer.MIN_VALUE;
            for (BlockPos pos : component) {
                minX = Math.min(minX, pos.getX());
                maxX = Math.max(maxX, pos.getX());
                minZ = Math.min(minZ, pos.getZ());
                maxZ = Math.max(maxZ, pos.getZ());
            }
            int width = maxX - minX + 1;
            int depth = maxZ - minZ + 1;
            if (width < 3 || depth < 3) {
                continue;
            }
            int border = 0;
            for (BlockPos pos : component) {
                if (pos.getX() == minX || pos.getX() == maxX || pos.getZ() == minZ || pos.getZ() == maxZ) {
                    border++;
                }
            }
            if (border >= 2 * (width + depth) - 4) {
                result.add(new Zone(dimension, cityId, minX, maxX, minZ, maxZ));
            }
        }
        return result;
    }

    @Nullable
    private static UUID citizenCity(ResourceLocation dimension, UUID citizenId) {
        ServerLevel level = level(dimension);
        if (level == null) {
            return null;
        }
        return CitizenService.findCitizen(level, citizenId).map(citizen -> citizen.cityId()).orElse(null);
    }

    @Nullable
    private static ServerLevel level(ResourceLocation dimension) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || dimension == null) {
            return null;
        }
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
    }

    private record Zone(ResourceLocation dimension, UUID cityId, int minX, int maxX, int minZ, int maxZ) {
    }
}
