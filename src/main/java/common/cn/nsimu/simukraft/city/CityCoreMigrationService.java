package common.cn.nsimu.simukraft.city;

import common.cn.nsimu.simukraft.economy.EconomyService;
import common.cn.nsimu.simukraft.network.city.chunk.CityChunkSyncService;
import common.cn.nsimu.simukraft.network.city.core.CityCoreOpenRequestPacket;
import common.cn.nsimu.simukraft.network.hud.HudSyncService;
import common.cn.nsimu.simukraft.network.toast.InfoToastService;
import common.cn.nsimu.simukraft.registry.ModBlocks;
import common.cn.nsimu.simukraft.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.Optional;
import java.util.UUID;


/**
 * CityCoreMigrationService: 把城市核心迁到领地内未认领的核心方块。
 */
public final class CityCoreMigrationService {
    public static final double COST = 200.0D;
    private static final String REASON = "city_core_migrate";
    private static final String REFUND_REASON = "city_core_migrate_refund";

    private CityCoreMigrationService() {
    }

    /**
     * migrateTarget: 当前未认领核心是否可被该玩家迁入。
     */
    public static Optional<CityData> migrateTarget(ServerLevel level, BlockPos pos, UUID playerId) {
        if (level == null || pos == null || playerId == null || CityService.hasCityAtCorePos(level, pos)) {
            return Optional.empty();
        }
        boolean districtCore = DistrictManager.get(level).all().stream()
                .anyMatch(district -> district.cores().contains(pos.asLong()));
        if (districtCore) {
            return Optional.empty();
        }
        UUID ownerId = CityChunkManager.get(level).getChunkOwner(new ChunkPos(pos).toLong());
        if (ownerId == null) {
            return Optional.empty();
        }
        return CityService.findCity(level, ownerId)
                .filter(city -> city.hasPermission(playerId, CityPermissionLevel.MAYOR))
                .filter(city -> !pos.equals(city.cityCorePos()));
    }

    /**
     * migrate: 校验名称、领地和资金后迁移城市核心。
     */
    public static boolean migrate(ServerLevel level, ServerPlayer player, BlockPos newPos, String confirmation) {
        if (level == null || player == null || newPos == null) {
            return false;
        }
        if (!level.getBlockState(newPos).is(ModBlocks.CITY_CORE.get())) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.city_core.not_found"));
            return false;
        }
        Optional<CityData> target = migrateTarget(level, newPos, player.getUUID());
        if (target.isEmpty()) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.city_core.migrate_outside_city"));
            CityCoreOpenRequestPacket.openFor(level, player, newPos);
            return false;
        }
        CityData city = target.get();
        if (!city.cityName().equals(confirmation)) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.city_core.migrate_confirm_failed"));
            CityCoreOpenRequestPacket.openFor(level, player, newPos);
            return false;
        }
        if (!EconomyService.canAfford(level, city.cityId(), COST)) {
            InfoToastService.warning(player, Component.translatable(
                    "message.simukraft.city_core.migrate_not_enough_funds",
                    String.format(java.util.Locale.ROOT, "%.2f", COST)));
            CityCoreOpenRequestPacket.openFor(level, player, newPos);
            return false;
        }
        BlockPos oldPos = city.cityCorePos().immutable();
        if (!EconomyService.withdrawCityFunds(level, city.cityId(), player, COST, REASON)) {
            InfoToastService.warning(player, Component.translatable(
                    "message.simukraft.city_core.migrate_not_enough_funds",
                    String.format(java.util.Locale.ROOT, "%.2f", COST)));
            CityCoreOpenRequestPacket.openFor(level, player, newPos);
            return false;
        }
        if (!CityService.moveCityCore(level, city.cityId(), player.getUUID(), newPos)) {
            EconomyService.depositCityFunds(level, city.cityId(), player, COST, REFUND_REASON);
            InfoToastService.warning(player, Component.translatable("message.simukraft.city_core.migrate_failed"));
            CityCoreOpenRequestPacket.openFor(level, player, newPos);
            return false;
        }
        unbindDistrictCore(level, oldPos);
        unbindDistrictCore(level, newPos);
        if (!oldPos.equals(newPos) && level.getBlockState(oldPos).is(ModBlocks.CITY_CORE.get())) {
            level.setBlock(oldPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            Block.popResource(level, oldPos, new ItemStack(ModItems.PORTABLE_CITY_CORE.get()));
        }
        InfoToastService.success(player, Component.translatable(
                "message.simukraft.city_core.migrated",
                String.format(java.util.Locale.ROOT, "%.2f", COST)));
        CityChunkSyncService.syncToAll(level);
        HudSyncService.syncToCityGroup(level, city.cityId(), true);
        CityCoreOpenRequestPacket.openFor(level, player, newPos);
        return true;
    }

    private static void unbindDistrictCore(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) {
            return;
        }
        long core = pos.asLong();
        DistrictManager.get(level).all().stream()
                .filter(district -> district.cores().contains(core))
                .findFirst()
                .ifPresent(district -> DistrictManager.get(level).unbindCore(district.districtId(), core));
    }
}
