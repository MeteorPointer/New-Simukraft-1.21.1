package common.cn.kafei.simukraft.city;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class DistrictService {
    private DistrictService() {}

    public static Optional<DistrictData> findByChunk(ServerLevel level, long chunkLong) {
        return level == null ? Optional.empty() : DistrictManager.get(level).byChunk(chunkLong);
    }

    public static Optional<DistrictData> findByChunk(ServerLevel level, ChunkPos chunk) {
        return chunk == null ? Optional.empty() : findByChunk(level, chunk.toLong());
    }

    public static boolean canManage(ServerLevel level, UUID cityId, UUID playerId, DistrictData district) {
        if (level == null || playerId == null || district == null) return false;
        if (CityService.hasPermission(level, cityId, playerId, CityPermissionLevel.MAYOR)) return true;
        return DistrictManager.get(level).isEnabled(district, level) && district.hasPermission(playerId, DistrictRole.OFFICIAL);
    }

    public static boolean canBuild(ServerLevel level, UUID cityId, UUID playerId, long chunkLong) {
        if (CityService.hasPermission(level, cityId, playerId, CityPermissionLevel.MAYOR)) return true;
        Optional<DistrictData> district = findByChunk(level, chunkLong);
        if (district.isPresent()) return DistrictManager.get(level).isEnabled(district.get(), level) && district.get().hasPermission(playerId, DistrictRole.OFFICIAL);
        return CityService.hasPermission(level, cityId, playerId, CityPermissionLevel.OFFICIAL);
    }

    public static boolean assign(ServerLevel level, UUID cityId, UUID playerId, UUID districtId, Set<Long> chunks) {
        if (level == null || cityId == null || playerId == null || !CityService.hasPermission(level, cityId, playerId, CityPermissionLevel.MAYOR)) return false;
        DistrictData district = DistrictManager.get(level).get(districtId).orElse(null);
        if (district == null || !district.parentCityId().equals(cityId) || chunks == null || chunks.isEmpty()) return false;
        CityChunkManager chunkManager = CityChunkManager.get(level);
        if (chunks.stream().anyMatch(chunk -> !cityId.equals(chunkManager.getChunkOwner(chunk)))) return false;
        return DistrictManager.get(level).assignChunks(districtId, chunks);
    }
}
