package common.cn.nsimu.simukraft;

import com.mojang.logging.LogUtils;
import common.cn.nsimu.simukraft.building.*;
import common.cn.nsimu.simukraft.citizen.*;
import common.cn.nsimu.simukraft.city.*;
import common.cn.nsimu.simukraft.city.poi.CityPoiManager;
import common.cn.nsimu.simukraft.command.SimuKraftCommand;
import common.cn.nsimu.simukraft.commercial.*;
import common.cn.nsimu.simukraft.config.ClientConfig;
import common.cn.nsimu.simukraft.config.ServerConfig;
import common.cn.nsimu.simukraft.economy.ResidentialRentService;
import common.cn.nsimu.simukraft.event.CityPlacementRestrictionHandler;
import common.cn.nsimu.simukraft.event.PlayerWelcomeService;
import common.cn.nsimu.simukraft.exchange.ExchangeMarketService;
import common.cn.nsimu.simukraft.farmland.FarmlandBoxManager;
import common.cn.nsimu.simukraft.farmland.FarmlandFarmingService;
import common.cn.nsimu.simukraft.globalmarket.GlobalMarketService;
import common.cn.nsimu.simukraft.globalmarket.GlobalMarketWarehouseService;
import common.cn.nsimu.simukraft.guard.NtIntegrityGuard;
import common.cn.nsimu.simukraft.industrial.IndustrialBoxManager;
import common.cn.nsimu.simukraft.industrial.IndustrialDefinitionLoader;
import common.cn.nsimu.simukraft.industrial.IndustrialWorkService;
import common.cn.nsimu.simukraft.job.CityJobAssignmentService;
import common.cn.nsimu.simukraft.logistics.LogisticsAutoClientService;
import common.cn.nsimu.simukraft.logistics.LogisticsManager;
import common.cn.nsimu.simukraft.logistics.LogisticsWorkService;
import common.cn.nsimu.simukraft.material.WorkMaterialPolicy;
import common.cn.nsimu.simukraft.medical.MedicalDefinitionLoader;
import common.cn.nsimu.simukraft.medical.MedicalMealService;
import common.cn.nsimu.simukraft.medical.MedicalService;
import common.cn.nsimu.simukraft.mineraldrilling.MineralDrillingBoxManager;
import common.cn.nsimu.simukraft.mineraldrilling.MineralDrillingDefinitionLoader;
import common.cn.nsimu.simukraft.mineraldrilling.MineralDrillingWorkService;
import common.cn.nsimu.simukraft.network.ModNetwork;
import common.cn.nsimu.simukraft.network.city.chunk.CityChunkSyncService;
import common.cn.nsimu.simukraft.network.hud.HudSyncService;
import common.cn.nsimu.simukraft.network.rts.RtsChunkViewService;
import common.cn.nsimu.simukraft.network.rts.RtsRemoteCitizenAccess;
import common.cn.nsimu.simukraft.network.rts.RtsRemoteMenuAccess;
import common.cn.nsimu.simukraft.path.CitizenNavigationService;
import common.cn.nsimu.simukraft.path.CitizenWanderService;
import common.cn.nsimu.simukraft.planner.PlannerWorkService;
import common.cn.nsimu.simukraft.protection.NpcBlockProtectionPolicy;
import common.cn.nsimu.simukraft.registry.*;
import common.cn.nsimu.simukraft.storage.SimuSqliteStorage;
import common.cn.nsimu.simukraft.util.NpcWorkChunkLoadService;
import common.cn.nsimu.simukraft.virtualvein.VirtualVeinService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.level.PistonEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;


@Mod(SimuKraft.MOD_ID)
public final class SimuKraft {
    public static final String MOD_ID = "simukraft";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SimuKraft(IEventBus modEventBus, ModContainer modContainer) {
        NtIntegrityGuard.verify(); // 内测版启动前校验 NT 保护层完整性，防止用户删除 JiJ 绕过
        common.cn.nsimu.simukraft.block.CautionTapeZones.registerPathCost();
        ModFluidTypes.register(modEventBus);
        ModFluids.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModItems.register(modEventBus);
        ModMenuTypes.register(modEventBus);
        ModRecipeSerializers.register(modEventBus);
        ModCreativeModeTabs.register(modEventBus);
        ModEntities.register(modEventBus);
        ModEntityAttributes.register(modEventBus);
        ModSoundEvents.register(modEventBus);
        ModStructures.register(modEventBus);
        modEventBus.addListener(BuildingStructureDataPack::onAddPackFinders);
        modContainer.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        modContainer.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
        modEventBus.register(ModNetwork.class);
        modEventBus.addListener(this::onConfigLoading);
        modEventBus.addListener(this::onConfigReloading);
        NeoForge.EVENT_BUS.register(CityPlacementRestrictionHandler.class);
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(this::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(this::onBlockBreak);
        NeoForge.EVENT_BUS.addListener(this::onBlockPlace);
        NeoForge.EVENT_BUS.addListener(this::onNeighborNotify);
        NeoForge.EVENT_BUS.addListener(this::onFluidPlaceBlock);
        NeoForge.EVENT_BUS.addListener(this::onPistonPre);
        NeoForge.EVENT_BUS.addListener(this::onPistonPost);
        NeoForge.EVENT_BUS.addListener(this::onExplosionDetonate);
        NeoForge.EVENT_BUS.addListener(this::onFarmlandTrample);
        NeoForge.EVENT_BUS.addListener(this::onServerAboutToStart);
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(this::onServerTick);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
        NeoForge.EVENT_BUS.addListener(this::onServerStopped);
        NeoForge.EVENT_BUS.addListener(this::onLevelSave);
        NeoForge.EVENT_BUS.addListener(this::onPlayerInteractBed);
        LOGGER.info("\nWelcome to\n========================================================================\n███████╗██╗███╗   ███╗██╗   ██╗██╗  ██╗██████╗  █████╗ ███████╗████████╗\n██╔════╝██║████╗ ████║██║   ██║██║ ██╔╝██╔══██╗██╔══██╗██╔════╝╚══██╔══╝\n███████╗██║██╔████╔██║██║   ██║█████╔╝ ██████╔╝███████║█████╗     ██║   \n╚════██║██║██║╚██╔╝██║██║   ██║██╔═██╗ ██╔══██╗██╔══██║██╔══╝     ██║   \n███████║██║██║ ╚═╝ ██║╚██████╔╝██║  ██╗██║  ██║██║  ██║██║        ██║   \n╚══════╝╚═╝╚═╝     ╚═╝ ╚═════╝ ╚═╝  ╚═╝╚═╝  ╚═╝╚═╝  ╚═╝╚═╝        ╚═╝  \n========================================================================\n");
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        SimuKraftCommand.register(event.getDispatcher());
    }

    private void onConfigLoading(ModConfigEvent.Loading event) {
        clearServerConfigCaches(event);
    }

    private void onConfigReloading(ModConfigEvent.Reloading event) {
        clearServerConfigCaches(event);
    }

    private void clearServerConfigCaches(ModConfigEvent event) {
        if (event.getConfig().getSpec() == ServerConfig.SPEC) {
            WorkMaterialPolicy.clearCache();
            NpcBlockProtectionPolicy.clearCache();
        }
    }

    private void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            PlayerWelcomeService.handleLogin(player);
            CityChunkSyncService.syncToPlayer(player);
        }
    }

    /**
     * onPlayerLoggedOut: 清理 RTS 远程菜单会话。
     */
    private void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            RtsRemoteMenuAccess.clear(player);
            RtsRemoteCitizenAccess.clear(player);
            RtsChunkViewService.clear(player);
        }
    }

    private void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof common.cn.nsimu.simukraft.entity.CitizenEntity citizenEntity
                && citizenEntity.level() instanceof net.minecraft.server.level.ServerLevel level) {
            CitizenDeathService.handleDeath(level, citizenEntity);
        }
    }

    private void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel level) {
            CitizenNavigationService.invalidate(level, event.getPos());
        }
    }

    private void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel level) {
            CitizenNavigationService.invalidate(level, event.getPos());
        }
    }

    private void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel level) {
            CitizenNavigationService.invalidate(level, event.getPos());
        }
    }

    private void onFluidPlaceBlock(BlockEvent.FluidPlaceBlockEvent event) {
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel level) {
            CitizenNavigationService.invalidate(level, event.getPos());
        }
    }

    /**
     * 活塞结构里只要带上城市核心就取消本次推动，避免原位恢复和被推走的方块同时存在。
     */
    private void onPistonPre(PistonEvent.Pre event) {
        PistonStructureResolver resolver = event.getStructureHelper();
        if (resolver == null || !resolver.resolve()) {
            return;
        }
        if (containsCityCore(event, resolver.getToPush()) || containsCityCore(event, resolver.getToDestroy())) {
            event.setCanceled(true);
        }
    }

    private static boolean containsCityCore(PistonEvent event, java.util.List<BlockPos> positions) {
        if (positions == null) {
            return false;
        }
        for (BlockPos pos : positions) {
            if (event.getLevel().getBlockState(pos).is(ModBlocks.CITY_CORE.get())) {
                return true;
            }
        }
        return false;
    }

    private void onPistonPost(PistonEvent.Post event) {
        if (!(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) {
            return;
        }
        BlockPos origin = event.getPos();
        Direction direction = event.getDirection();
        java.util.List<BlockPos> positions = new java.util.ArrayList<>();
        positions.add(origin);
        if (direction != null) {
            for (int step = 1; step <= 12; step++) {
                positions.add(origin.relative(direction, step));
            }
        }
        CitizenNavigationService.invalidateAll(level, positions);
    }

    private void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) {
            return;
        }
        java.util.List<BlockPos> positions = new java.util.ArrayList<>(event.getAffectedBlocks());
        Vec3 center = event.getExplosion().center();
        positions.add(BlockPos.containing(center.x, center.y, center.z));
        CitizenNavigationService.invalidateAll(level, positions);
    }

    private void onFarmlandTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (event.getEntity() instanceof common.cn.nsimu.simukraft.entity.CitizenEntity) {
            event.setCanceled(true);
        }
    }

    /**
     * onServerAboutToStart: 在任何 tick 之前打开存储并建表，把建库失败暴露在启动阶段。
     */
    private void onServerAboutToStart(ServerAboutToStartEvent event) {
        SimuSqliteStorage.bootstrap(event.getServer());
    }

    /**
     * onServerStarted: 服务端启动后立即准备官方建筑包目录。
     */
    private void onServerStarted(ServerStartedEvent event) {
        BuildingPackageCatalog.ensurePrepared();
        common.cn.nsimu.simukraft.block.CautionTapeZones.rebuild(null);
    }

    private void onPlayerInteractBed(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        net.minecraft.core.BlockPos clickedPos = event.getPos();
        net.minecraft.world.level.block.state.BlockState state = level.getBlockState(clickedPos);
        if (state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock
                || state.getBlock() instanceof net.minecraft.world.level.block.FenceGateBlock
                || state.getBlock() instanceof net.minecraft.world.level.block.TrapDoorBlock) {
            CitizenNavigationService.invalidate(level, clickedPos);
        }
        net.minecraft.core.BlockPos bedHeadPos = state.is(net.minecraft.world.level.block.Blocks.RED_BED)
                ? ResidentialBedPoiService.resolveBedHeadPos(clickedPos, state)
                : MedicalBedPoiService.resolveBedHeadPos(clickedPos, state);
        if (bedHeadPos == null) return;
        java.util.UUID occupant = CitizenBedSleepService.getOccupantUUID(level, bedHeadPos);
        if (occupant == null) return;
        common.cn.nsimu.simukraft.entity.CitizenEntity entity = CitizenTeleportService.findCitizenEntity(level, occupant);
        if (entity == null) {
            CitizenBedSleepService.release(level, occupant);
            return;
        }
        CitizenBedSleepService.wakeUp(level, entity, null);
        event.setCanceled(true);
    }

    private void onServerTick(ServerTickEvent.Post event) {
        // 先刷新城市运行状态，保证本 tick 的居民和医疗调度使用最新的激活结果。
        event.getServer().getAllLevels().forEach(CityRuntimeService::tick);
        event.getServer().getAllLevels().forEach(CityUpgradeService::tick);
        // CitizenManager 数据挂主世界，tick 只需对 overworld 执行一次，避免 N-1 次无效调用。
        ServerLevel overworld = event.getServer().overworld();
        CitizenManager.get(overworld).tick(overworld);
        event.getServer().getAllLevels().forEach(level -> {
            CitizenNavigationService.tick(level);
            CitizenWanderService.tick(level);
            PlacedBuildingService.ensureCityPoisRegistered(level);
            BuildingIntegrityService.tick(level);
            CitizenHomeRestService.tick(level);
            MedicalService.tick(level);
            ExchangeMarketService.tick(level);
            CitizenSelfFeedingService.tick(level);
            BuilderConstructionService.tick(level);
            PlannerWorkService.tick(level);
            IndustrialWorkService.tick(level);
            MineralDrillingWorkService.tick(level);
            CommercialWorkService.tick(level);
            LogisticsWorkService.tick(level);
            PopulationGrowthService.tick(level);
            ResidentialRentService.tick(level);
            FarmlandFarmingService.tick(level);
            HudSyncService.tick(level);
            CityPermissionInviteService.tick(level);
        });
        PlayerWelcomeService.tick(event.getServer());
    }

    /**
     * onLevelSave: 世界自动保存时顺带落股市，避免只在关服才写。
     */
    private void onLevelSave(LevelEvent.Save event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ExchangeMarketService.saveToSqlite(level);
        }
    }

    private void onServerStopping(ServerStoppingEvent event) {
        event.getServer().getAllLevels().forEach(level -> {
            BuilderConstructionService.flush(level);
            PlannerWorkService.flush(level);
            IndustrialWorkService.flush(level);
            CommercialWorkService.flush(level);
            saveDimensionSqlite(level);
        });
        saveGlobalSqlite(event.getServer());
        /*
         * 关键顺序：全量保存和各服务的 flush 只是把写入排进队列，必须在清理缓存之前把队列排空。
         * 旧实现依赖 daemon 线程，关服时队列里未执行的写入会被直接丢弃（重进存档数据回退的主因）。
         */
        SimuSqliteStorage.flush(event.getServer());
        BuilderConstructionService.clearServerCaches(event.getServer());
        PlannerWorkService.clearServerCaches(event.getServer());
        NpcWorkChunkLoadService.clearServerCaches(event.getServer());
        IndustrialWorkService.clearServerCaches(event.getServer());
        MineralDrillingWorkService.clearServerCaches(event.getServer());
        CommercialWorkService.clearServerCaches(event.getServer());
        LogisticsWorkService.clearServerCaches(event.getServer());
        LogisticsAutoClientService.clearServerCaches(event.getServer());
        FarmlandFarmingService.clearServerCaches(event.getServer());
        CityRuntimeService.clearServerCaches(event.getServer());
        PlacedBuildingService.clearServerCaches(event.getServer());
        ResidentialBedPoiService.clearServerCaches(event.getServer());
        MedicalBedPoiService.clearServerCaches(event.getServer());
        MedicalDefinitionLoader.clearCache();
        ExchangeMarketService.clearServerCaches(event.getServer());
        GlobalMarketService.clearCache();
        GlobalMarketWarehouseService.clearCache();
        MedicalMealService.clearServerCaches(event.getServer());
        CitizenHomeRestService.clearServerCaches(event.getServer());
        CitizenDroppedFoodService.clearServerCaches(event.getServer());
        CitizenSelfFeedingService.clearServerCaches(event.getServer());
        CommercialFoodMarketService.clearServerCaches(event.getServer());
        CityJobAssignmentService.clearServerCaches(event.getServer());
        CitizenNavigationService.clearServerCaches(event.getServer());
        CitizenWanderService.clearServerCaches(event.getServer());
        ResidentialRentService.clearServerCaches(event.getServer());
        HudSyncService.clearServerCaches(event.getServer());
        CommercialDefinitionLoader.clearCache();
        IndustrialDefinitionLoader.clearCache();
        MineralDrillingDefinitionLoader.clearCache();
        BuildingCatalog.clearCache();
        WorkMaterialPolicy.clearCache();
        NpcBlockProtectionPolicy.clearCache();
        PlayerWelcomeService.clearServerCaches(event.getServer());
        RtsChunkViewService.clearServer(event.getServer());
        // shutdown 会再排空一次队列、checkpoint WAL 并关闭连接，之后该服务器实例不会被残留任务重新注册。
        SimuSqliteStorage.shutdown(event.getServer());
        VirtualVeinService.clearServerCache(event.getServer());
        CityPoiManager.clearGlobalCache();
        common.cn.nsimu.simukraft.util.SaveScopedCacheKey.clearServerCache(event.getServer());
    }

    /**
     * onServerStopped: 服务器实例彻底退出后释放存储层对它的引用。
     */
    private void onServerStopped(ServerStoppedEvent event) {
        SimuSqliteStorage.forgetServer(event.getServer());
        common.cn.nsimu.simukraft.storage.BuildingStructureSqliteDatabase.forgetServer(event.getServer());
    }

    private void saveDimensionSqlite(ServerLevel level) {
        CityManager.get(level).saveToSqlite(level);
        CityChunkManager.get(level).saveToSqlite(level);
        CityPoiManager.get(level).saveToSqlite(level);
        FarmlandBoxManager.get(level).saveToSqlite(level);
        IndustrialBoxManager.get(level).saveToSqlite(level);
        MineralDrillingBoxManager.get(level).saveToSqlite(level);
        CommercialBoxManager.get(level).saveToSqlite(level);
        CommercialStockManager.get(level).saveToSqlite(level);
        LogisticsManager.get(level).saveToSqlite(level);
        ExchangeMarketService.saveToSqlite(level);
    }

    private void saveGlobalSqlite(MinecraftServer server) {
        ServerLevel storageLevel = server.overworld();
        CitizenManager.get(storageLevel).saveToSqlite(storageLevel);
    }
}
