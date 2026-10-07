package common.cn.kafei.simukraft.city;

import common.cn.kafei.simukraft.registry.ModBlocks;
import common.cn.kafei.simukraft.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
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

    /**
     * 城市官员仍可操作整座城市。分区官员只在已启用核心的本分区区块内获得同样的建造和拆除资格。
     */
    public static boolean canBuild(ServerLevel level, UUID cityId, UUID playerId, long chunkLong) {
        if (CityService.hasPermission(level, cityId, playerId, CityPermissionLevel.OFFICIAL)) return true;
        Optional<DistrictData> district = findByChunk(level, chunkLong);
        if (district.isEmpty() || cityId == null || !cityId.equals(district.get().parentCityId())) return false;
        return DistrictManager.get(level).isEnabled(district.get(), level)
                && district.get().hasPermission(playerId, DistrictRole.OFFICIAL);
    }

    /** releaseCity: 先卸下分区记录，再拆掉分区核心。主城核心方块不动。 */
    public static void releaseCity(ServerLevel level, UUID cityId) {
        if (level == null || cityId == null) return;
        List<BlockPos> cores = DistrictManager.get(level).removeCity(cityId);
        for (BlockPos pos : cores) {
            if (CityService.hasCityAtCorePos(level, pos)) continue;
            if (!level.getBlockState(pos).is(ModBlocks.CITY_CORE.get())) continue;
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            Block.popResource(level, pos, new ItemStack(ModItems.PORTABLE_CITY_CORE.get()));
        }
        DistrictOwnershipSync.retagCity(level, cityId);
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
