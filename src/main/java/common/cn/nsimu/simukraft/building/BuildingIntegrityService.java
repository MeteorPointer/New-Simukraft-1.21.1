package common.cn.nsimu.simukraft.building;

import common.cn.nsimu.simukraft.SimuKraft;
import common.cn.nsimu.simukraft.city.CityService;
import common.cn.nsimu.simukraft.city.FinanceTransactionData;
import common.cn.nsimu.simukraft.config.ServerConfig;
import common.cn.nsimu.simukraft.economy.EconomyService;
import common.cn.nsimu.simukraft.economy.FinanceLedgerService;
import common.cn.nsimu.simukraft.material.WorkMaterialPolicy;
import common.cn.nsimu.simukraft.util.SaveScopedCacheKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;


public final class BuildingIntegrityService {
    // 预览缓存有效期：界面连续刷新（含容器菜单每 tick 重建视图）复用同一次扫描，最多滞后 1 秒。
    private static final long PREVIEW_CACHE_TICKS = 20L;
    // 缓存条目超出上限时，只丢掉写入已超过这个墙钟时长的条目。
    private static final long PREVIEW_CACHE_STALE_MILLIS = 60_000L;
    // 完整性预览缓存，键含存档、维度和建筑 id，只服务界面展示。
    private static final ConcurrentMap<String, PreviewEntry> PREVIEWS = new ConcurrentHashMap<>();
    // 预览缓存条目上限，超限时清掉过期条目，避免长期建造拆除把已消失的建筑 id 一直留着。
    private static final int PREVIEW_CACHE_LIMIT = 4096;
    // 自动拆除扫描步长：每 20 tick 推进一片建筑，避免一次全量扫描挤掉同 tick 的界面请求。
    private static final int SCAN_STRIDE_TICKS = 20;
    // 自动拆除扫描游标，按存档维度持有。
    private static final ConcurrentMap<String, AtomicInteger> SCAN_CURSORS = new ConcurrentHashMap<>();

    private record PreviewEntry(long gameTime, long writtenAtMillis, IntegrityPreview preview) {
    }

    private BuildingIntegrityService() {
    }

    /**
     * tick：按配置间隔分片扫描已完成建筑，完整性低于阈值时自动拆除。
     */
    public static void tick(ServerLevel level) {
        if (level == null || level.isClientSide()) {
            return;
        }
        int threshold = ServerConfig.buildingIntegrityAutoDemolishThresholdPercent();
        if (threshold <= 0 || level.getGameTime() % SCAN_STRIDE_TICKS != 0L) {
            return;
        }
        List<PlacedBuildingRecord> buildings = PlacedBuildingService.getBuildings(level);
        if (buildings.isEmpty()) {
            return;
        }
        int interval = Math.max(20, ServerConfig.buildingIntegrityCheckIntervalTicks());
        // 一轮扫描仍覆盖全部建筑并耗时约一个检查间隔，只是均分成多个 SCAN_STRIDE_TICKS 的小片。
        int budget = Math.max(1, (int) ((buildings.size() * (long) SCAN_STRIDE_TICKS + interval - 1) / interval));
        AtomicInteger cursor = SCAN_CURSORS.computeIfAbsent(SaveScopedCacheKey.levelKey(level), ignored -> new AtomicInteger());
        int start = Math.floorMod(cursor.get(), buildings.size());
        for (int offset = 0; offset < budget; offset++) {
            PlacedBuildingRecord building = buildings.get((start + offset) % buildings.size());
            IntegrityScan scan = scan(level, building, true, false);
            if (!scan.available() || scan.totalBlocks() <= 0) {
                continue;
            }
            cachePreview(level, building, scan);
            double percent = scan.percent();
            if (percent < threshold) {
                autoDemolish(level, building, percent, threshold);
            }
        }
        cursor.set((start + budget) % buildings.size());
    }

    /**
     * snapshot：计算建筑当前完整性；建筑未加载完整时返回 unavailable，避免误拆未加载区块。
     */
    public static IntegritySnapshot snapshot(ServerLevel level, PlacedBuildingRecord building) {
        IntegrityScan scan = scan(level, building, false, false);
        return new IntegritySnapshot(scan.available(), scan.totalBlocks(), scan.intactBlocks());
    }

    /**
     * preview：界面展示用的完整性和修复预估，未过期时直接复用上一次扫描结果。
     */
    public static IntegrityPreview preview(ServerLevel level, PlacedBuildingRecord building) {
        if (level == null || building == null || building.buildingId() == null) {
            return scan(level, building, true, false).preview();
        }
        IntegrityPreview cached = cachedPreview(previewKey(level, building.buildingId()), level.getGameTime());
        if (cached != null) {
            return cached;
        }
        IntegrityScan scan = scan(level, building, true, false);
        cachePreview(level, building, scan);
        return scan.preview();
    }

    /**
     * clearServerCaches：关服和切档时释放该存档的预览缓存和扫描游标。
     */
    public static void clearServerCaches(MinecraftServer server) {
        String serverKey = SaveScopedCacheKey.serverKey(server);
        PREVIEWS.keySet().removeIf(key -> key.startsWith(serverKey + "|"));
        SCAN_CURSORS.keySet().removeIf(key -> key.startsWith(serverKey + "|"));
    }

    private static IntegrityPreview cachedPreview(String key, long gameTime) {
        PreviewEntry entry = PREVIEWS.get(key);
        return entry == null || gameTime - entry.gameTime() > PREVIEW_CACHE_TICKS ? null : entry.preview();
    }

    // cachePreview：只缓存可用结果，区块没加载完的扫描会提前返回，代价本来就低，留到下次重算。
    private static void cachePreview(ServerLevel level, PlacedBuildingRecord building, IntegrityScan scan) {
        if (building == null || building.buildingId() == null || !scan.available()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (PREVIEWS.size() > PREVIEW_CACHE_LIMIT) {
            PREVIEWS.entrySet().removeIf(entry -> now - entry.getValue().writtenAtMillis() > PREVIEW_CACHE_STALE_MILLIS);
        }
        PREVIEWS.put(previewKey(level, building.buildingId()), new PreviewEntry(level.getGameTime(), now, scan.preview()));
    }

    private static void invalidatePreview(ServerLevel level, UUID buildingId) {
        if (level != null && buildingId != null) {
            PREVIEWS.remove(previewKey(level, buildingId));
        }
    }

    private static String previewKey(ServerLevel level, UUID buildingId) {
        return SaveScopedCacheKey.levelKey(level) + "|building=" + buildingId;
    }

    /**
     * scan：一次遍历体素同时得到完整性和修复预估；collectTargets 时才收集修复目标，供真正修复使用。
     */
    private static IntegrityScan scan(ServerLevel level, PlacedBuildingRecord building, boolean classifyDamaged, boolean collectTargets) {
        if (level == null || building == null || building.blocks() == null || building.blocks().isEmpty()) {
            return IntegrityScan.unavailable();
        }
        int total = 0;
        int intact = 0;
        int repairable = 0;
        int manualRepair = 0;
        int carriedRepair = 0;
        List<RepairTarget> paidTargets = collectTargets ? new ArrayList<>() : List.of();
        List<RepairTarget> carriedTargets = collectTargets ? new ArrayList<>() : List.of();
        for (BuildingBlockData block : building.blocks()) {
            if (block == null || block.state() == null || block.state().isAir()) {
                continue;
            }
            BlockPos worldPos = resolveWorldPos(building, block.relativePos());
            if (!level.isLoaded(worldPos)) {
                return IntegrityScan.unavailable();
            }
            total++;
            if (matchesStructureBlock(level.getBlockState(worldPos), block.state())) {
                intact++;
                continue;
            }
            if (!classifyDamaged
                    || PlacedBuildingService.isOccupiedByOtherBuilding(level, building.buildingId(), worldPos)) {
                continue;
            }
            if (BuildingRepairBlacklist.requiresCarriedBlock(block.state())) {
                carriedRepair++;
                if (collectTargets) {
                    carriedTargets.add(new RepairTarget(worldPos, block.state()));
                }
                continue;
            }
            if (WorkMaterialPolicy.requiresMaterial(block.state())) {
                manualRepair++;
                continue;
            }
            repairable++;
            if (collectTargets) {
                paidTargets.add(new RepairTarget(worldPos, block.state()));
            }
        }
        return new IntegrityScan(true, total, intact, repairable, manualRepair, carriedRepair, List.copyOf(paidTargets), List.copyOf(carriedTargets));
    }

    public static double repairCost(int repairableBlocks) {
        return EconomyService.normalizeAmount(Math.max(0, repairableBlocks) * ServerConfig.buildingIntegrityRepairMoneyPerBlock());
    }

    public static RepairResult repair(ServerLevel level, ServerPlayer player, PlacedBuildingRecord building) {
        if (level == null || building == null || building.cityId() == null) {
            return new RepairResult(RepairStatus.NO_BUILDING, 0, 0, 0.0D);
        }
        IntegrityScan scan = scan(level, building, true, true);
        if (!scan.available()) {
            return new RepairResult(RepairStatus.UNAVAILABLE, 0, 0, 0.0D);
        }
        List<RepairTarget> paidTargets = scan.paidTargets();
        List<RepairTarget> supplied = carriedBlocksToPlace(player, scan.carriedTargets());
        int manualRepairBlocks = scan.manualRepairBlocks() + scan.carriedTargets().size() - supplied.size();
        if (paidTargets.isEmpty() && supplied.isEmpty()) {
            RepairStatus status = manualRepairBlocks > 0 ? RepairStatus.MATERIALS_REQUIRED : RepairStatus.NO_REPAIR_NEEDED;
            return new RepairResult(status, 0, manualRepairBlocks, 0.0D);
        }
        double cost = repairCost(paidTargets.size());
        if (cost > 0.0D) {
            if (!EconomyService.canAfford(level, building.cityId(), cost) || !CityService.withdrawFunds(level, building.cityId(), cost)) {
                return new RepairResult(RepairStatus.NOT_ENOUGH_FUNDS, paidTargets.size(), manualRepairBlocks, cost);
            }
            FinanceLedgerService.record(level, building.cityId(), player, -cost, EconomyService.getCityBalance(level, building.cityId()), FinanceTransactionData.Type.EXPENSE, "building_repair");
        }
        int repairedBlocks = 0;
        for (RepairTarget target : paidTargets) {
            placeRepairedBlock(level, target);
            repairedBlocks++;
        }
        for (RepairTarget target : supplied) {
            Item item = BuildingRepairBlacklist.carriedItem(target.state());
            if (!consumeOne(player, item)) {
                manualRepairBlocks++;
                continue;
            }
            placeRepairedBlock(level, target);
            repairedBlocks++;
        }
        // 修复改写了方块，作废预览缓存，界面下一拍就能看到最新完整性。
        invalidatePreview(level, building.buildingId());
        return new RepairResult(RepairStatus.SUCCESS, repairedBlocks, manualRepairBlocks, cost);
    }

    /**
     * 按背包存量决定哪些黑名单方块这次能补，先不消耗。
     */
    private static List<RepairTarget> carriedBlocksToPlace(ServerPlayer player, List<RepairTarget> carriedTargets) {
        if (player == null || carriedTargets.isEmpty()) {
            return List.of();
        }
        Map<Item, Integer> stock = new HashMap<>();
        List<RepairTarget> supplied = new ArrayList<>();
        for (RepairTarget target : carriedTargets) {
            Item item = BuildingRepairBlacklist.carriedItem(target.state());
            if (item == Items.AIR) {
                continue;
            }
            int have = stock.computeIfAbsent(item, key -> countCarried(player, key));
            if (have <= 0) {
                continue;
            }
            stock.put(item, have - 1);
            supplied.add(target);
        }
        return supplied;
    }

    private static int countCarried(ServerPlayer player, Item item) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static boolean consumeOne(ServerPlayer player, Item item) {
        if (player == null || item == null || item == Items.AIR) {
            return false;
        }
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty() || !stack.is(item)) {
                continue;
            }
            stack.shrink(1);
            return true;
        }
        return false;
    }

    private static void placeRepairedBlock(ServerLevel level, RepairTarget target) {
        level.setBlock(target.pos(), BuildingBlockPlacementService.refreshedPlacementState(level, target.pos(), target.state()), 3);
    }

    /**
     * autoDemolish：统一走现有拆除服务，保证 POI、居民和控制箱状态一起清理。
     */
    private static void autoDemolish(ServerLevel level, PlacedBuildingRecord building, double percent, int threshold) {
        if (PlacedBuildingDemolitionService.demolish(level, building)) {
            SimuKraft.LOGGER.info("Simukraft: Auto demolished building {} because integrity {:.1f}% is below {}%",
                    building.displayName(), percent, threshold);
        }
    }

    /**
     * matchesStructureBlock：完整性只比较方块类型，避免门开关、箱子开合等状态变化被当作损坏。
     */
    private static boolean matchesStructureBlock(BlockState current, BlockState expected) {
        return current != null && expected != null && current.is(expected.getBlock());
    }

    /**
     * resolveWorldPos：兼容旧记录中保存相对坐标或世界坐标两种形式。
     */
    private static BlockPos resolveWorldPos(PlacedBuildingRecord building, BlockPos storedPos) {
        if (isInside(storedPos, building.minPos(), building.maxPos())) {
            return storedPos;
        }
        return building.worldOrigin().offset(storedPos);
    }

    private static boolean isInside(BlockPos pos, BlockPos min, BlockPos max) {
        return pos != null
                && pos.getX() >= Math.min(min.getX(), max.getX()) && pos.getX() <= Math.max(min.getX(), max.getX())
                && pos.getY() >= Math.min(min.getY(), max.getY()) && pos.getY() <= Math.max(min.getY(), max.getY())
                && pos.getZ() >= Math.min(min.getZ(), max.getZ()) && pos.getZ() <= Math.max(min.getZ(), max.getZ());
    }

    public record IntegritySnapshot(boolean available, int totalBlocks, int intactBlocks) {
        public double percent() {
            return totalBlocks <= 0 ? 100.0D : intactBlocks * 100.0D / totalBlocks;
        }
    }

    public record IntegrityPreview(boolean available, int totalBlocks, int intactBlocks, int repairableBlocks,
                                   int manualRepairBlocks, double repairCost) {
        public double percent() {
            return totalBlocks <= 0 ? 100.0D : intactBlocks * 100.0D / totalBlocks;
        }
    }

    public record RepairResult(RepairStatus status, int repairedBlocks, int manualRepairBlocks, double cost) {
        public boolean success() {
            return status == RepairStatus.SUCCESS || status == RepairStatus.NO_REPAIR_NEEDED;
        }
    }

    public enum RepairStatus {
        SUCCESS,
        NO_BUILDING,
        UNAVAILABLE,
        NO_REPAIR_NEEDED,
        NOT_ENOUGH_FUNDS,
        MATERIALS_REQUIRED
    }

    /**
     * IntegrityScan：一次体素遍历的完整结果；未收集目标时 repair 计数仍与界面预估一致。
     */
    private record IntegrityScan(boolean available, int totalBlocks, int intactBlocks, int repairableBlocks,
                                 int manualRepairBlocks, int carriedRepairBlocks, List<RepairTarget> paidTargets,
                                 List<RepairTarget> carriedTargets) {
        private static final IntegrityScan UNAVAILABLE = new IntegrityScan(false, 0, 0, 0, 0, 0, List.of(), List.of());

        private static IntegrityScan unavailable() {
            return UNAVAILABLE;
        }

        private double percent() {
            return totalBlocks <= 0 ? 100.0D : intactBlocks * 100.0D / totalBlocks;
        }

        private IntegrityPreview preview() {
            if (!available) {
                return new IntegrityPreview(false, 0, 0, 0, 0, 0.0D);
            }
            int manual = manualRepairBlocks + carriedRepairBlocks;
            return new IntegrityPreview(true, totalBlocks, intactBlocks, repairableBlocks, manual, repairCost(repairableBlocks));
        }
    }

    private record RepairTarget(BlockPos pos, BlockState state) {
    }
}
