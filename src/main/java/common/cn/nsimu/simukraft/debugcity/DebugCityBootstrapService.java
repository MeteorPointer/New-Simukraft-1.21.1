package common.cn.nsimu.simukraft.debugcity;

import common.cn.nsimu.simukraft.SimuKraft;
import common.cn.nsimu.simukraft.bank.BankControlBoxService;
import common.cn.nsimu.simukraft.citizen.CitizenData;
import common.cn.nsimu.simukraft.citizen.CitizenManager;
import common.cn.nsimu.simukraft.citizen.CitizenService;
import common.cn.nsimu.simukraft.citizen.family.FamilyManager;
import common.cn.nsimu.simukraft.city.CityChunkManager;
import common.cn.nsimu.simukraft.city.CityData;
import common.cn.nsimu.simukraft.city.CityService;
import common.cn.nsimu.simukraft.city.group.CityGroupMessageService;
import common.cn.nsimu.simukraft.city.poi.CityPoiData;
import common.cn.nsimu.simukraft.city.poi.CityPoiService;
import common.cn.nsimu.simukraft.city.poi.CityPoiType;
import common.cn.nsimu.simukraft.commercial.CommercialBoxManager;
import common.cn.nsimu.simukraft.commercial.CommercialConstants;
import common.cn.nsimu.simukraft.config.ServerConfig;
import common.cn.nsimu.simukraft.economy.EconomyService;
import common.cn.nsimu.simukraft.exchange.ExchangeControlBoxService;
import common.cn.nsimu.simukraft.globalmarket.GlobalMarketControlBoxService;
import common.cn.nsimu.simukraft.industrial.IndustrialBoxManager;
import common.cn.nsimu.simukraft.industrial.IndustrialConstants;
import common.cn.nsimu.simukraft.job.CitizenEmploymentService;
import common.cn.nsimu.simukraft.medical.MedicalControlBoxService;
import common.cn.nsimu.simukraft.network.city.chunk.CityChunkSyncService;
import common.cn.nsimu.simukraft.network.hud.HudSyncService;
import common.cn.nsimu.simukraft.network.toast.InfoToastService;
import common.cn.nsimu.simukraft.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.chunk.ChunkGenerator;

import java.util.UUID;

/**
 * 在调试城市世界里分批建立一座可运行的网格城市。
 */
public final class DebugCityBootstrapService {
    private static final String CITY_NAME = "调试城市";
    private static final int BLOCK_FLAG = 2;

    private DebugCityBootstrapService() {
    }

    /**
     * tick：只处理使用调试城市生成器的主世界，每拍限量铺领地和居民。
     */
    public static void tick(MinecraftServer server) {
        if (server == null) {
            return;
        }
        ServerLevel level = server.overworld();
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        if (!(generator instanceof DebugCityChunkGenerator debugCity)) {
            return;
        }
        DebugCityProgress progress = DebugCityProgress.get(level);
        if (progress.done()) {
            return;
        }
        if (!progress.configured()) {
            progress.configure(debugCity.citizenCount(), debugCity.cityScale());
        }
        ServerPlayer player = firstPlayer(level);
        if (player == null) {
            return;
        }
        if (progress.waitingForPlayer()) {
            startCity(level, player, progress);
            return;
        }
        if (progress.cityId() == null || CityService.findCity(level, progress.cityId()).isEmpty()) {
            SimuKraft.LOGGER.warn("Simukraft: debug city {} disappeared before bootstrap finished", progress.cityId());
            progress.finish();
            return;
        }
        int budget = Math.max(1, ServerConfig.debugCityPlotsPerTick());
        if (progress.claiming()) {
            claimBudget(level, progress, budget * 16);
            return;
        }
        if (progress.populating()) {
            for (int i = 0; i < budget && progress.populating() && !progress.done(); i++) {
                placeNextCitizen(level, progress);
            }
            if (progress.done()) {
                finish(level, player, progress);
            }
        }
    }

    private static void startCity(ServerLevel level, ServerPlayer player, DebugCityProgress progress) {
        if (CityService.findPlayerCity(level, player.getUUID()).isPresent()) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.debug_city.player_has_city"));
            progress.finish();
            return;
        }
        BlockPos corePos = corePos(progress.side());
        ChunkPos coreChunk = new ChunkPos(corePos);
        CityChunkManager chunks = CityChunkManager.get(level);
        if (!chunks.isAreaAvailable(coreChunk)) {
            SimuKraft.LOGGER.warn("Simukraft: debug city core chunks are already claimed");
            progress.finish();
            return;
        }
        level.getChunk(coreChunk.x, coreChunk.z);
        placeCore(level, corePos);
        CityData city = CityService.createCity(level, CITY_NAME, player.getUUID(), player.getGameProfile().getName(), corePos);
        if (city == null || !chunks.assignInitialArea(city.cityId(), coreChunk)) {
            SimuKraft.LOGGER.warn("Simukraft: failed to create the debug city");
            progress.finish();
            return;
        }
        double funds = EconomyService.normalizeAmount(Math.max(100_000.0D, progress.citizenCount() * 500.0D));
        EconomyService.depositCityFunds(level, city.cityId(), player, funds, "debug_city_seed");
        progress.beginClaiming(city.cityId());
        if (!progress.announced()) {
            progress.markAnnounced();
            InfoToastService.success(player, Component.translatable(
                    "message.simukraft.debug_city.building", progress.citizenCount(), progress.side()));
        }
        SimuKraft.LOGGER.info("Simukraft: building debug city {} with {} citizens on a {} chunk grid",
                city.cityId(), progress.citizenCount(), progress.side());
    }

    private static void claimBudget(ServerLevel level, DebugCityProgress progress, int budget) {
        CityChunkManager chunks = CityChunkManager.get(level);
        int total = progress.side() * progress.side();
        int origin = DebugCityLayout.originChunk(progress.side());
        for (int i = 0; i < budget && progress.claimIndex() < total; i++) {
            int index = progress.claimIndex();
            int chunkX = origin + index % progress.side();
            int chunkZ = origin + index / progress.side();
            chunks.claimChunk(progress.cityId(), ChunkPos.asLong(chunkX, chunkZ));
            progress.advanceClaim();
        }
    }

    private static void placeNextCitizen(ServerLevel level, DebugCityProgress progress) {
        var cells = progress.occupiedCells();
        int index = progress.citizenIndex();
        if (index >= cells.size()) {
            progress.finish();
            return;
        }
        DebugCityLayout.Cell cell = cells.get(index);
        DebugCityLayout.Cell workCell = DebugCityLayout.workCell(cell, progress.side());
        DebugCityLayout.WorkplaceKind kind = DebugCityLayout.workplaceKind(index);
        level.getChunk(cell.chunkX(), cell.chunkZ());
        level.getChunk(workCell.chunkX(), workCell.chunkZ());
        BlockPos houseFloor = new BlockPos(
                DebugCityLayout.worldX(cell, DebugCityLayout.HOUSE_LOCAL_X),
                DebugCityLayout.SURFACE_Y,
                DebugCityLayout.worldZ(cell, DebugCityLayout.HOUSE_LOCAL_Z));
        BlockPos workFloor = new BlockPos(
                DebugCityLayout.worldX(workCell, DebugCityLayout.WORK_LOCAL_X),
                DebugCityLayout.SURFACE_Y,
                DebugCityLayout.worldZ(workCell, DebugCityLayout.WORK_LOCAL_Z));
        placeShell(level, houseFloor, Blocks.OAK_PLANKS.defaultBlockState(), Blocks.OAK_LOG.defaultBlockState(),
                DebugCityLayout.HOUSE_DOOR_DX, DebugCityLayout.HOUSE_DOOR_DZ);
        placeBed(level, houseFloor);
        level.setBlock(houseFloor.offset(3, 1, 1), ModBlocks.RESIDENTIAL_CONTROL_BOX.get().defaultBlockState(), BLOCK_FLAG);
        placeShell(level, workFloor, Blocks.STONE_BRICKS.defaultBlockState(), wallFor(kind),
                DebugCityLayout.WORK_DOOR_DX, DebugCityLayout.WORK_DOOR_DZ);
        BlockPos workBox = workFloor.offset(2, 1, 2);
        level.setBlock(workBox, controlBox(kind), BLOCK_FLAG);
        BlockPos bedPos = houseFloor.offset(1, 1, 1);
        CityPoiData home = CityPoiService.registerPoi(level, progress.cityId(), bedPos, CityPoiType.RESIDENTIAL, 1);
        CitizenData existing = CitizenEmploymentService.findAssigned(level, sourceType(kind), role(kind), workBox).orElse(null);
        if (existing == null) {
            existing = citizenWithHome(level, home.poiId());
        }
        if (existing == null) {
            BlockPos spawnFloor = new BlockPos(
                    DebugCityLayout.worldX(cell, DebugCityLayout.HOUSE_LOCAL_X + DebugCityLayout.HOUSE_DOOR_DX + 1),
                    DebugCityLayout.SURFACE_Y,
                    DebugCityLayout.worldZ(cell, DebugCityLayout.HOUSE_LOCAL_Z + DebugCityLayout.HOUSE_DOOR_DZ));
            var spawned = CitizenService.spawnCitizen(level, spawnFloor, progress.cityId(), true);
            if (spawned.isEmpty()) {
                SimuKraft.LOGGER.warn("Simukraft: skipped debug citizen {} because the chunk did not accept the entity", index);
                progress.advanceCitizen();
                return;
            }
            existing = CitizenService.ensureCitizen(level, spawned.get());
        }
        if (existing != null) {
            CitizenService.setHome(level, existing.uuid(), home.poiId());
            if (existing.familyId() == null) {
                var family = FamilyManager.get(level).createSingle(level, progress.cityId(), existing.uuid(), existing.gender());
                existing.setFamilyId(family.familyId());
                CitizenService.save(level, existing.uuid());
            }
            prepareWorkplace(level, kind, workBox);
            CitizenEmploymentService.hireForSource(level, existing.uuid(), sourceType(kind), role(kind), workBox, "");
        }
        progress.advanceCitizen();
    }

    private static void finish(ServerLevel level, ServerPlayer player, DebugCityProgress progress) {
        CityChunkSyncService.syncToAll(level);
        HudSyncService.syncToCityGroup(level, progress.cityId(), true);
        Component ready = Component.translatable("message.simukraft.debug_city.ready", progress.citizenCount());
        CityGroupMessageService.successToCity(level, progress.cityId(), ready);
        InfoToastService.success(player, ready);
        SimuKraft.LOGGER.info("Simukraft: debug city {} is ready with {} citizens", progress.cityId(), progress.citizenCount());
    }

    private static CitizenData citizenWithHome(ServerLevel level, UUID homeId) {
        if (homeId == null) {
            return null;
        }
        for (CitizenData citizen : CitizenManager.get(level).allCitizens()) {
            if (!citizen.dead() && homeId.equals(citizen.homeId())) {
                return citizen;
            }
        }
        return null;
    }

    private static void prepareWorkplace(ServerLevel level, DebugCityLayout.WorkplaceKind kind, BlockPos workBox) {
        if (kind == DebugCityLayout.WorkplaceKind.INDUSTRIAL) {
            IndustrialBoxManager.get(level).getOrCreate(workBox);
        } else if (kind == DebugCityLayout.WorkplaceKind.COMMERCIAL) {
            CommercialBoxManager.get(level).getOrCreate(workBox);
        }
    }

    private static void placeCore(ServerLevel level, BlockPos corePos) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos floor = corePos.offset(dx, -1, dz);
                level.setBlock(floor, Blocks.STONE_BRICKS.defaultBlockState(), BLOCK_FLAG);
                for (int dy = 0; dy <= 2; dy++) {
                    BlockPos above = corePos.offset(dx, dy, dz);
                    if (dx == 0 && dz == 0 && dy == 0) {
                        continue;
                    }
                    level.setBlock(above, Blocks.AIR.defaultBlockState(), BLOCK_FLAG);
                }
            }
        }
        level.setBlock(corePos, ModBlocks.CITY_CORE.get().defaultBlockState(), BLOCK_FLAG);
    }

    private static void placeShell(ServerLevel level, BlockPos floorOrigin, BlockState floor, BlockState wall, int doorDx, int doorDz) {
        int size = DebugCityLayout.PLOT_SIZE;
        for (int dx = 0; dx < size; dx++) {
            for (int dz = 0; dz < size; dz++) {
                boolean edge = dx == 0 || dz == 0 || dx == size - 1 || dz == size - 1;
                boolean door = dx == doorDx && dz == doorDz;
                level.setBlock(floorOrigin.offset(dx, 0, dz), floor, BLOCK_FLAG);
                for (int dy = 1; dy <= 3; dy++) {
                    BlockPos pos = floorOrigin.offset(dx, dy, dz);
                    BlockState state = shellState(edge, door, dx, dz, dy, wall);
                    level.setBlock(pos, state, BLOCK_FLAG);
                }
            }
        }
    }

    private static BlockState shellState(boolean edge, boolean door, int dx, int dz, int dy, BlockState wall) {
        if (dx == 2 && dz == 2 && dy == 3) {
            return Blocks.GLOWSTONE.defaultBlockState();
        }
        if (door && dy <= 2) {
            return Blocks.AIR.defaultBlockState();
        }
        if (!edge && dy < 3) {
            return Blocks.AIR.defaultBlockState();
        }
        return wall;
    }

    private static void placeBed(ServerLevel level, BlockPos floorOrigin) {
        BlockState foot = Blocks.RED_BED.defaultBlockState()
                .setValue(BedBlock.FACING, net.minecraft.core.Direction.SOUTH)
                .setValue(BedBlock.PART, BedPart.FOOT);
        level.setBlock(floorOrigin.offset(1, 1, 2), foot.setValue(BedBlock.PART, BedPart.HEAD), BLOCK_FLAG);
        level.setBlock(floorOrigin.offset(1, 1, 1), foot, BLOCK_FLAG);
    }

    private static BlockState wallFor(DebugCityLayout.WorkplaceKind kind) {
        return switch (kind) {
            case INDUSTRIAL -> ModBlocks.INDUSTRIAL_HOUSING.get().defaultBlockState();
            case COMMERCIAL -> Blocks.CYAN_TERRACOTTA.defaultBlockState();
            case MEDICAL -> Blocks.WHITE_CONCRETE.defaultBlockState();
            case BANK -> Blocks.YELLOW_TERRACOTTA.defaultBlockState();
            case EXCHANGE -> Blocks.BLUE_TERRACOTTA.defaultBlockState();
            case GLOBAL_MARKET -> Blocks.LIME_TERRACOTTA.defaultBlockState();
        };
    }

    private static BlockState controlBox(DebugCityLayout.WorkplaceKind kind) {
        return switch (kind) {
            case INDUSTRIAL -> ModBlocks.INDUSTRIAL_CONTROL_BOX.get().defaultBlockState();
            case COMMERCIAL -> ModBlocks.COMMERCIAL_CONTROL_BOX.get().defaultBlockState();
            case MEDICAL -> ModBlocks.MEDICAL_CONTROL_BOX.get().defaultBlockState();
            case BANK -> ModBlocks.BANK_CONTROL_BOX.get().defaultBlockState();
            case EXCHANGE -> ModBlocks.EXCHANGE_CONTROL_BOX.get().defaultBlockState();
            case GLOBAL_MARKET -> ModBlocks.GLOBAL_MARKET_CONTROL_BOX.get().defaultBlockState();
        };
    }

    private static String sourceType(DebugCityLayout.WorkplaceKind kind) {
        return switch (kind) {
            case INDUSTRIAL -> IndustrialConstants.HIRE_SOURCE_TYPE;
            case COMMERCIAL -> CommercialConstants.HIRE_SOURCE_TYPE;
            case MEDICAL -> MedicalControlBoxService.HIRE_SOURCE_TYPE;
            case BANK -> BankControlBoxService.HIRE_SOURCE_TYPE;
            case EXCHANGE -> ExchangeControlBoxService.HIRE_SOURCE_TYPE;
            case GLOBAL_MARKET -> GlobalMarketControlBoxService.HIRE_SOURCE_TYPE;
        };
    }

    private static String role(DebugCityLayout.WorkplaceKind kind) {
        return switch (kind) {
            case INDUSTRIAL -> IndustrialConstants.HIRE_ROLE;
            case COMMERCIAL -> CommercialConstants.HIRE_ROLE;
            case MEDICAL -> MedicalControlBoxService.HIRE_ROLE;
            case BANK -> BankControlBoxService.HIRE_ROLE;
            case EXCHANGE -> ExchangeControlBoxService.HIRE_ROLE;
            case GLOBAL_MARKET -> GlobalMarketControlBoxService.HIRE_ROLE;
        };
    }

    private static BlockPos corePos(int side) {
        int center = DebugCityLayout.centerChunk(side);
        return new BlockPos(center * DebugCityLayout.CELL_SIZE + 8, DebugCityLayout.SURFACE_Y + 1, center * DebugCityLayout.CELL_SIZE + 8);
    }

    private static ServerPlayer firstPlayer(ServerLevel level) {
        return level.players().isEmpty() ? null : level.players().getFirst();
    }
}
