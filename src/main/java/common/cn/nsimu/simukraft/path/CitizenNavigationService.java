package common.cn.nsimu.simukraft.path;

import common.cn.nsimu.simukraft.SimuKraft;
import common.cn.nsimu.simukraft.citizen.CitizenService;
import common.cn.nsimu.simukraft.citizen.CitizenTeleportService;
import common.cn.nsimu.simukraft.config.ServerConfig;
import common.cn.nsimu.simukraft.entity.CitizenEntity;
import common.cn.nsimu.simukraft.network.path.NpcPathDebugSyncPacket;
import common.cn.nsimu.simukraft.network.toast.InfoToastService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;


public final class CitizenNavigationService {
    private static final ConcurrentMap<String, LevelRuntime> RUNTIMES = new ConcurrentHashMap<>();
    private static final AtomicInteger THREAD_ID = new AtomicInteger();
    private static final int STALLED_TELEPORT_TICKS = 300;
    private static final int DEBUG_SYNC_PATH_LIMIT = 96;
    private static final double DEBUG_SYNC_RADIUS = 192.0D;
    private static volatile ExecutorService pathExecutor;
    private static volatile int executorSize;

    private CitizenNavigationService() {
    }

    public static boolean requestMove(ServerLevel level, UUID citizenId, Vec3 target, MovementIntent intent) {
        return requestMove(level, citizenId, target, intent, false);
    }

    public static boolean requestTestMove(ServerLevel level, UUID citizenId, Vec3 target, MovementIntent intent) {
        return requestMove(level, citizenId, target, intent, true);
    }

    private static boolean requestMove(ServerLevel level, UUID citizenId, Vec3 target, MovementIntent intent, boolean bypassAdmissionLimits) {
        if (level == null || citizenId == null || target == null) {
            return false;
        }
        MovementIntent normalizedIntent = intent != null ? intent : MovementIntent.WALK;
        CitizenEntity citizen = CitizenTeleportService.findCitizenEntity(level, citizenId);
        if (citizen == null) {
            return false;
        }
        if (normalizedIntent != MovementIntent.FLEE && (citizen.isStayInPlace() || citizen.isPanicking())) {
            return false;
        }
        if (citizen.getFollowPlayerId() != null
                && normalizedIntent != MovementIntent.FOLLOW
                && normalizedIntent != MovementIntent.FLEE) {
            return false;
        }
        if (citizen.isSleeping()) {
            return false;
        }
        Vec3 current = citizen.position();
        double distanceSqr = current.distanceToSqr(target);
        double farDistance = localPathDistanceLimit();
        if (distanceSqr >= farDistance * farDistance || !hasLoadedChunk(level, BlockPos.containing(target.x, target.y, target.z))) {
            if (!normalizedIntent.allowsTeleportFallback()) {
                return false;
            }
            return CitizenTeleportService.teleportCitizen(level, citizenId, target);
        }

        LevelRuntime runtime = runtime(level);
        if (normalizedIntent == MovementIntent.FLEE) {
            runtime.cooldowns.remove(citizenId);
            runtime.blockedSince.remove(citizenId);
            clearNonMatchingNavigation(level, runtime, citizenId, citizen, MovementIntent.FLEE);
        } else if (hasIntent(runtime, citizenId, MovementIntent.FLEE)) {
            return false;
        } else if (normalizedIntent == MovementIntent.SELF_FEEDING) {
            clearLowerPriorityNavigation(level, runtime, citizenId, citizen);
        } else if (normalizedIntent == MovementIntent.WORK && hasSelfFeedingNavigation(runtime, citizenId)) {
            return false;
        }
        ActiveNavigation active = runtime.active.get(citizenId);
        if (active != null && active.sameTarget(target)) {
            return true;
        }
        RunningRequest running = runtime.pending.get(citizenId);
        if (running != null) {
            BlockPos requestedTarget = BlockPos.containing(target.x, target.y, target.z);
            if (running.cacheKey().targetPos().equals(requestedTarget)
                    && running.cacheKey().intent() == normalizedIntent) {
                return true;
            }
            runtime.pending.remove(citizenId, running);
            running.future().cancel(true);
        }
        PathRequest queued = runtime.latestRequests.get(citizenId);
        if (queued != null
                && queued.intent() == normalizedIntent
                && queued.target().distanceToSqr(target) <= 4.0D) {
            return true;
        }
        Long cooldownUntil = runtime.cooldowns.get(citizenId);
        if (cooldownUntil != null && cooldownUntil > level.getGameTime() && normalizedIntent != MovementIntent.FLEE) {
            return false;
        }
        if (!bypassAdmissionLimits && normalizedIntent != MovementIntent.FLEE) {
            if (!runtime.active.containsKey(citizenId) && runtime.active.size() >= ServerConfig.pathMaxActiveCitizens()) {
                return false;
            }
            if (countLoadedCitizens(level, runtime) > ServerConfig.pathMaxLoadedCitizenEntities()) {
                return false;
            }
        }

        PathRequest request = new PathRequest(citizenId, level.dimension().location(), citizen.blockPosition(), target, normalizedIntent, level.getGameTime());
        runtime.latestRequests.put(citizenId, request);
        if (runtime.queuedCitizenIds.add(citizenId)) {
            runtime.queue.offer(citizenId);
        }
        return true;
    }

    /**
     * hasSelfFeedingNavigation: 判断买饭导航是否正在占用该 NPC，避免普通工作移动抢占。
     */
    private static boolean hasSelfFeedingNavigation(LevelRuntime runtime, UUID citizenId) {
        ActiveNavigation active = runtime.active.get(citizenId);
        if (active != null && active.intent == MovementIntent.SELF_FEEDING) {
            return true;
        }
        RunningRequest running = runtime.pending.get(citizenId);
        if (running != null && running.cacheKey().intent() == MovementIntent.SELF_FEEDING) {
            return true;
        }
        PathRequest queued = runtime.latestRequests.get(citizenId);
        return queued != null && queued.intent() == MovementIntent.SELF_FEEDING;
    }

    /**
     * hasIntent: 判断该市民是否已有指定意图的排队、计算中或执行中导航。
     */
    public static boolean hasIntent(ServerLevel level, UUID citizenId, MovementIntent intent) {
        if (level == null || citizenId == null || intent == null) {
            return false;
        }
        return hasIntent(runtime(level), citizenId, intent);
    }

    private static boolean hasIntent(LevelRuntime runtime, UUID citizenId, MovementIntent intent) {
        ActiveNavigation active = runtime.active.get(citizenId);
        if (active != null && active.intent == intent) {
            return true;
        }
        RunningRequest running = runtime.pending.get(citizenId);
        if (running != null && running.cacheKey().intent() == intent) {
            return true;
        }
        PathRequest queued = runtime.latestRequests.get(citizenId);
        return queued != null && queued.intent() == intent;
    }

    /**
     * clearNonMatchingNavigation: 高优先级意图开始时清掉其它意图，避免两套路径抢控制。
     */
    private static void clearNonMatchingNavigation(ServerLevel level, LevelRuntime runtime, UUID citizenId,
                                                   CitizenEntity citizen, MovementIntent keep) {
        ActiveNavigation active = runtime.active.get(citizenId);
        if (active != null && active.intent != keep) {
            runtime.active.remove(citizenId);
            citizen.getNavigation().stop();
            PathCrowdCoordinator.clear(level, citizenId);
        }
        RunningRequest running = runtime.pending.get(citizenId);
        if (running != null && running.cacheKey().intent() != keep) {
            runtime.pending.remove(citizenId);
            running.future().cancel(true);
        }
        PathRequest queued = runtime.latestRequests.get(citizenId);
        if (queued != null && queued.intent() != keep) {
            runtime.latestRequests.remove(citizenId);
            runtime.queuedCitizenIds.remove(citizenId);
        }
    }

    /**
     * clearLowerPriorityNavigation: 买饭开始时清掉旧的普通工作导航，防止两套状态轮流改目标。
     */
    private static void clearLowerPriorityNavigation(ServerLevel level, LevelRuntime runtime, UUID citizenId, CitizenEntity citizen) {
        ActiveNavigation active = runtime.active.get(citizenId);
        if (active != null && active.intent != MovementIntent.SELF_FEEDING) {
            runtime.active.remove(citizenId);
            citizen.getNavigation().stop();
            PathCrowdCoordinator.clear(level, citizenId);
        }
        RunningRequest running = runtime.pending.get(citizenId);
        if (running != null && running.cacheKey().intent() != MovementIntent.SELF_FEEDING) {
            runtime.pending.remove(citizenId);
        }
        PathRequest queued = runtime.latestRequests.get(citizenId);
        if (queued != null && queued.intent() != MovementIntent.SELF_FEEDING) {
            runtime.latestRequests.remove(citizenId);
            runtime.queuedCitizenIds.remove(citizenId);
        }
    }

    public static void stop(ServerLevel level, UUID citizenId) {
        stopInternal(level, citizenId, false);
    }

    /**
     * stopForced：死亡、卡墙救援或玩家强制调遣时连逃跑路径一起取消。
     */
    public static void stopForced(ServerLevel level, UUID citizenId) {
        stopInternal(level, citizenId, true);
    }

    private static void stopInternal(ServerLevel level, UUID citizenId, boolean includeFlee) {
        if (level == null || citizenId == null) {
            return;
        }
        LevelRuntime runtime = runtime(level);
        if (!includeFlee && hasIntent(runtime, citizenId, MovementIntent.FLEE)) {
            return;
        }
        runtime.latestRequests.remove(citizenId);
        runtime.blockedSince.remove(citizenId);
        runtime.pending.remove(citizenId);
        runtime.queuedCitizenIds.remove(citizenId);
        runtime.active.remove(citizenId);
        CitizenEntity citizen = CitizenTeleportService.findCitizenEntity(level, citizenId);
        if (citizen != null) {
            citizen.getNavigation().stop();
            citizen.getMoveControl().setWantedPosition(citizen.getX(), citizen.getY(), citizen.getZ(), 0.0D);
        }
        PathCrowdCoordinator.clear(level, citizenId);
    }

    /**
     * stopReturnHome：仅取消指定居民已排队、计算中或执行中的回家导航。
     */
    public static void stopReturnHome(ServerLevel level, UUID citizenId) {
        if (level == null || citizenId == null) {
            return;
        }
        LevelRuntime runtime = runtime(level);
        boolean stopped = false;
        ActiveNavigation active = runtime.active.get(citizenId);
        if (active != null && active.intent == MovementIntent.RETURN_HOME) {
            runtime.active.remove(citizenId, active);
            stopped = true;
        }
        RunningRequest running = runtime.pending.get(citizenId);
        if (running != null && running.cacheKey().intent() == MovementIntent.RETURN_HOME) {
            runtime.pending.remove(citizenId, running);
            running.future().cancel(true);
            stopped = true;
        }
        PathRequest queued = runtime.latestRequests.get(citizenId);
        if (queued != null && queued.intent() == MovementIntent.RETURN_HOME) {
            runtime.latestRequests.remove(citizenId, queued);
            runtime.queuedCitizenIds.remove(citizenId);
            stopped = true;
        }
        if (!stopped) {
            return;
        }
        runtime.blockedSince.remove(citizenId);
        runtime.cooldowns.remove(citizenId);
        CitizenEntity citizen = CitizenTeleportService.findCitizenEntity(level, citizenId);
        if (citizen != null) {
            citizen.getNavigation().stop();
            citizen.getMoveControl().setWantedPosition(citizen.getX(), citizen.getY(), citizen.getZ(), 0.0D);
        }
        PathCrowdCoordinator.clear(level, citizenId);
    }

    public static boolean isNavigating(ServerLevel level, UUID citizenId) {
        if (level == null || citizenId == null) {
            return false;
        }
        LevelRuntime runtime = runtime(level);
        return runtime.active.containsKey(citizenId) || runtime.pending.containsKey(citizenId) || runtime.latestRequests.containsKey(citizenId);
    }

    public static boolean debugPathTo(ServerPlayer player, Vec3 target) {
        if (player == null || target == null) {
            return false;
        }
        CitizenEntity citizen = CitizenPathDebugService.findNearestLoadedCitizen(player.serverLevel(), player.position(), ServerConfig.pathLocalRadiusBlocks());
        if (citizen == null) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.path_debug.no_citizen"));
            PacketDistributor.sendToPlayer(player, NpcPathDebugSyncPacket.clear());
            return false;
        }
        return debugPathTo(player, citizen, target);
    }

    public static boolean debugPathTo(ServerPlayer player, CitizenEntity citizen, Vec3 target) {
        if (player == null || citizen == null || target == null || !(citizen.level() instanceof ServerLevel level)) {
            return false;
        }
        if (level != player.serverLevel()) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.path_debug.failed", "citizen_dimension_mismatch"));
            return false;
        }
        BlockPos targetPos = BlockPos.containing(target.x, target.y, target.z);
        if (!hasLoadedChunk(level, targetPos)) {
            CitizenPathDebugService.sendDebugFailure(player, citizen.getUUID(), "target_chunk_not_loaded");
            return false;
        }
        double radius = ServerConfig.pathLocalRadiusBlocks();
        if (citizen.position().distanceToSqr(target) > radius * radius) {
            CitizenPathDebugService.sendDebugFailure(player, citizen.getUUID(), "target_outside_local_radius");
            return false;
        }

        PathRequest request = new PathRequest(citizen.getUUID(), level.dimension().location(), citizen.blockPosition(), target, MovementIntent.RUN, level.getGameTime());
        PathSnapshot snapshot = PathSnapshotBuilder.build(level, request.startPos(), request.targetBlockPos(), ServerConfig.pathLocalRadiusBlocks());
        InfoToastService.send(player, Component.translatable("message.simukraft.path_debug.started", citizen.getName().getString(), CitizenPathDebugService.formatTarget(target)));
        CompletableFuture<PathResult> future = CompletableFuture.supplyAsync(() -> HybridPathfinder.find(request, snapshot), executor());
        future.whenComplete((result, throwable) -> level.getServer().execute(() -> applyDebugPath(level, player, citizen.getUUID(), result, throwable)));
        return true;
    }

    public static void clearDebugPath(ServerPlayer player) {
        if (player != null) {
            PacketDistributor.sendToPlayer(player, NpcPathDebugSyncPacket.clear());
            InfoToastService.send(player, Component.translatable("message.simukraft.path_debug.cleared"));
        }
    }

    public static boolean sendStatus(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        LevelRuntime runtime = runtime(level);
        runtime.cooldowns.entrySet().removeIf(entry -> entry.getValue() <= level.getGameTime());
        InfoToastService.send(player, Component.translatable(
                "message.simukraft.path_status.summary",
                runtime.queuedCitizenIds.size(),
                runtime.pending.size(),
                runtime.active.size(),
                runtime.cooldowns.size()));
        CitizenPathDebugService.PathRuntimeIssue issue = CitizenPathDebugService.nearestIssue(level, runtime.active, player.position());
        if (issue != null) {
            InfoToastService.warning(player, Component.translatable(
                    "message.simukraft.path_status.issue",
                    CitizenPathDebugService.shortId(issue.citizenId()),
                    issue.status(),
                    String.format(Locale.ROOT, "%.1f", Math.sqrt(issue.distanceToTargetSqr())),
                    issue.waypointIndex(),
                    issue.waypointCount()));
        } else {
            InfoToastService.success(player, Component.translatable("message.simukraft.path_status.no_issue"));
        }
        syncDebugPaths(level, player);
        return true;
    }

    public static void syncDebugPaths(ServerLevel level, ServerPlayer player) {
        if (level == null || player == null) {
            return;
        }
        LevelRuntime runtime = runtime(level);
        PacketDistributor.sendToPlayer(player, NpcPathDebugSyncPacket.clear());
        double maxDistanceSqr = DEBUG_SYNC_RADIUS * DEBUG_SYNC_RADIUS;
        List<CitizenPathDebugService.DebugPathEntry> entries = new ArrayList<>();
        for (Map.Entry<UUID, ActiveNavigation> entry : runtime.active.entrySet()) {
            CitizenEntity citizen = CitizenTeleportService.findCitizenEntity(level, entry.getKey());
            if (citizen == null) {
                continue;
            }
            double distanceSqr = citizen.position().distanceToSqr(player.position());
            if (distanceSqr <= maxDistanceSqr) {
                entries.add(new CitizenPathDebugService.DebugPathEntry(entry.getKey(), entry.getValue(), distanceSqr));
            }
        }
        entries.sort(Comparator.comparingDouble(CitizenPathDebugService.DebugPathEntry::distanceSqr));
        int sent = 0;
        for (CitizenPathDebugService.DebugPathEntry entry : entries) {
            if (sent >= DEBUG_SYNC_PATH_LIMIT) {
                break;
            }
            PacketDistributor.sendToPlayer(player, NpcPathDebugSyncPacket.fromWaypoints(entry.citizenId(), entry.navigation().waypoints, entry.navigation().debugStatus()));
            sent++;
        }
        if (sent > 0) {
            InfoToastService.send(player, Component.translatable("message.simukraft.path_debug.synced", sent, runtime.active.size()));
        }
    }

    public static void tick(ServerLevel level) {
        if (level == null || level.isClientSide()) {
            return;
        }
        LevelRuntime runtime = runtime(level);
        runtime.snapshotCache.beginTick();
        applyCompletedPaths(level, runtime);
        tickActivePaths(level, runtime);
        CitizenDoorService.processOpenedDoors(level, runtime.openedDoors, runtime.active.keySet());
        processQueuedRequests(level, runtime);
        pumpFieldBuild(level, runtime);
        if (level.getGameTime() % 200L == 0L) {
            runtime.pathCache.cleanup(level.getGameTime());
            runtime.snapshotCache.cleanup(level.getGameTime());
            runtime.cooldowns.entrySet().removeIf(entry -> entry.getValue() <= level.getGameTime());
            PathCrowdCoordinator.cleanup(level);
        }
    }

    public static void invalidate(ServerLevel level, BlockPos changedPos) {
        if (level == null || changedPos == null) {
            return;
        }
        invalidateAll(level, List.of(changedPos));
    }

    /**
     * invalidateAll: 一批方块只抬升区段版本，路径缓存只扫一次。
     */
    public static void invalidateAll(ServerLevel level, Iterable<BlockPos> positions) {
        if (level == null || positions == null) {
            return;
        }
        LevelRuntime runtime = runtime(level);
        boolean changed = false;
        for (BlockPos pos : positions) {
            if (pos == null) {
                continue;
            }
            runtime.snapshotCache.invalidate(pos);
            changed = true;
        }
        if (changed) {
            runtime.pathCache.dropStale(level.getGameTime(), runtime.snapshotCache.epochs());
        }
    }

    public static void clearServerCaches(MinecraftServer server) {
        if (server != null) {
            String prefix = SaveKey.serverKey(server) + "|";
            RUNTIMES.keySet().removeIf(key -> key.startsWith(prefix));
        } else {
            RUNTIMES.clear();
        }
        PathCrowdCoordinator.clearServerCaches(server);
        ExecutorService executor = pathExecutor;
        if (executor != null) {
            executor.shutdownNow();
        }
        pathExecutor = null;
        executorSize = 0;
    }

    private static void processQueuedRequests(ServerLevel level, LevelRuntime runtime) {
        int processed = 0;
        int budget = Math.max(0, ServerConfig.pathMaxNewRequestsPerTick());
        while (processed < budget) {
            if (runtime.active.size() + runtime.pending.size() >= ServerConfig.pathMaxActiveCitizens()) {
                return;
            }
            UUID citizenId = runtime.queue.poll();
            if (citizenId == null) {
                return;
            }
            runtime.queuedCitizenIds.remove(citizenId);
            PathRequest request = runtime.latestRequests.remove(citizenId);
            if (request == null || runtime.pending.containsKey(citizenId)) {
                continue;
            }
            CitizenEntity citizen = CitizenTeleportService.findCitizenEntity(level, citizenId);
            if (citizen == null) {
                continue;
            }
            if (citizen.isSleeping()) {
                clearRuntimeNavigation(level, runtime, citizenId, citizen, true);
                continue;
            }
            if (!level.isPositionEntityTicking(citizen.blockPosition())) {
                runtime.latestRequests.put(citizenId, request);
                if (runtime.queuedCitizenIds.add(citizenId)) {
                    runtime.queue.offer(citizenId);
                }
                return;
            }
            PathRequest currentRequest = new PathRequest(citizenId, request.dimensionId(), citizen.blockPosition(), request.target(), request.intent(), level.getGameTime());
            PathCacheKey cacheKey = new PathCacheKey(currentRequest.dimensionId(), currentRequest.startPos(), currentRequest.targetBlockPos(), currentRequest.intent());
            PathResult cached = runtime.blockedSince.containsKey(citizenId)
                    ? null
                    : runtime.pathCache.get(cacheKey, level.getGameTime(), runtime.snapshotCache.epochs());
            if (cached != null) {
                activate(level, runtime, cached.forRequest(currentRequest));
                processed++;
                continue;
            }
            UUID cityId = cityOf(level, citizenId);
            PathDistanceFieldCache.PathFieldKey fieldKey = new PathDistanceFieldCache.PathFieldKey(
                    currentRequest.dimensionId(), cityId, currentRequest.targetBlockPos(), currentRequest.intent());
            PathDistanceField existingField = runtime.distanceFields.get(fieldKey, runtime.snapshotCache.epochs());
            List<BlockPos> sharedPoints = sharedFieldPoints(level, runtime, cityId, currentRequest);
            int radius = ServerConfig.pathLocalRadiusBlocks();
            if (existingField == null && sharedPoints.size() >= 3 && runtime.fieldPump == null) {
                runtime.fieldPump = FieldPump.start(fieldKey, currentRequest, cityId,
                        PathSnapshotBuilder.unionBounds(level, sharedPoints, currentRequest.targetBlockPos(), radius));
            }
            PathSnapshotCache.Acquisition acquired = runtime.snapshotCache.acquire(level, currentRequest.startPos(), currentRequest.targetBlockPos(), radius);
            if (!acquired.ready()) {
                requeue(runtime, citizenId, currentRequest);
                return;
            }
            CompletableFuture<PathJob> future;
            try {
                future = CompletableFuture.supplyAsync(
                        () -> PathExtraCosts.callWithCity(cityId, () -> search(currentRequest, acquired, existingField)),
                        executor());
            } catch (RejectedExecutionException rejected) {
                requeue(runtime, citizenId, currentRequest);
                return;
            }
            runtime.pending.put(citizenId, new RunningRequest(future, cacheKey, acquired.stamp()));
            processed++;
        }
    }

    /**
     * search: 能接上已有距离场就回溯，否则单人 A*。建场放到后台分步做。
     */
    private static PathJob search(PathRequest request, PathSnapshotCache.Acquisition acquired, PathDistanceField existingField) {
        PathSnapshot snapshot = PathSnapshotBuilder.buildFromCapture(acquired.capture(), request.startPos(), request.targetBlockPos());
        if (existingField != null) {
            PathResult reconstructed = existingField.reconstruct(request, snapshot);
            if (reconstructed.success()) {
                return new PathJob(reconstructed);
            }
        }
        return new PathJob(HybridPathfinder.find(request, snapshot));
    }

    /**
     * pumpFieldBuild: 每 tick 最多推进一步距离场。采样没完成就只吃本 tick 剩余的区段预算。
     */
    private static void pumpFieldBuild(ServerLevel level, LevelRuntime runtime) {
        FieldPump pump = runtime.fieldPump;
        if (pump == null) {
            return;
        }
        if (pump.step != null) {
            if (!pump.step.isDone()) {
                return;
            }
            Boolean continued = null;
            try {
                continued = pump.step.get();
            } catch (Exception ignored) {
                runtime.fieldPump = null;
                return;
            }
            pump.step = null;
            if (!Boolean.TRUE.equals(continued) || pump.acquired == null
                    || !runtime.snapshotCache.epochs().matches(pump.acquired.stamp())) {
                runtime.fieldPump = null;
                return;
            }
            if (pump.build != null && pump.build.finished()) {
                runtime.distanceFields.put(pump.key, pump.build.toField());
                runtime.fieldPump = null;
                return;
            }
        }
        if (pump.acquired == null) {
            PathSnapshotCache.Acquisition acquired = runtime.snapshotCache.acquire(level, pump.bounds);
            if (!acquired.ready()) {
                return;
            }
            pump.acquired = acquired;
        }
        try {
            pump.step = CompletableFuture.supplyAsync(
                    () -> PathExtraCosts.callWithCity(pump.cityId, () -> pump.advance()),
                    executor());
        } catch (RejectedExecutionException rejected) {
            pump.step = null;
        }
    }

    /**
     * requeue: 这一 tick 做不完就放回队尾，下个 tick 继续。
     */
    private static void requeue(LevelRuntime runtime, UUID citizenId, PathRequest request) {
        runtime.latestRequests.put(citizenId, request);
        if (runtime.queuedCitizenIds.add(citizenId)) {
            runtime.queue.offer(citizenId);
        }
    }

    /**
     * sharedFieldPoints: 收集同一城市、同一目标、同一意图的排队起点。只有自己时不建距离场。
     */
    private static List<BlockPos> sharedFieldPoints(ServerLevel level, LevelRuntime runtime, UUID cityId, PathRequest current) {
        List<BlockPos> points = new ArrayList<>();
        points.add(current.startPos());
        points.add(current.targetBlockPos());
        BlockPos target = current.targetBlockPos();
        for (PathRequest queued : runtime.latestRequests.values()) {
            if (queued.intent() != current.intent() || !queued.targetBlockPos().equals(target)) {
                continue;
            }
            if (!java.util.Objects.equals(cityId, cityOf(level, queued.citizenId()))) {
                continue;
            }
            points.add(queued.startPos());
            if (points.size() >= 18) {
                break;
            }
        }
        return points;
    }

    /**
     * cityOf: 在主线程解析市民所属城市，搜索线程不再逐边查找。
     */
    private static UUID cityOf(ServerLevel level, UUID citizenId) {
        return CitizenService.findCitizen(level, citizenId).map(citizen -> citizen.cityId()).orElse(null);
    }

    private static void applyCompletedPaths(ServerLevel level, LevelRuntime runtime) {
        for (Iterator<Map.Entry<UUID, RunningRequest>> iterator = runtime.pending.entrySet().iterator(); iterator.hasNext(); ) {
            Map.Entry<UUID, RunningRequest> entry = iterator.next();
            RunningRequest running = entry.getValue();
            if (!running.future().isDone()) {
                continue;
            }
            iterator.remove();
            PathJob job;
            try {
                job = running.future().get();
            } catch (Exception e) {
                job = null;
            }
            PathResult result = job == null ? null : job.result();
            boolean fresh = runtime.snapshotCache.epochs().matches(running.stamp());
            if (result != null && result.success()) {
                if (fresh) {
                    runtime.pathCache.put(running.cacheKey(), result, level.getGameTime(), ServerConfig.pathCacheTtlTicks(), running.stamp());
                }
                activate(level, runtime, result);
            } else {
                UUID citizenId = entry.getKey();
                runtime.cooldowns.remove(citizenId);
                runtime.blockedSince.remove(citizenId);
                if (result != null) {
                    if (running.cacheKey().intent().allowsTeleportFallback()) {
                        CitizenTeleportService.teleportCitizen(level, citizenId, result.target());
                        if (ServerConfig.pathDebugEnabled()) {
                            SimuKraft.LOGGER.info("Simukraft: NPC path failed for {}, teleporting to target: {}", citizenId, result.reason());
                        }
                    }
                }
            }
        }
    }

    private static void activate(ServerLevel level, LevelRuntime runtime, PathResult result) {
        if (!result.success() || result.waypoints().isEmpty()) {
            return;
        }
        CitizenEntity citizen = CitizenTeleportService.findCitizenEntity(level, result.citizenId());
        if (citizen == null) {
            return;
        }
        if (citizen.isSleeping()) {
            clearRuntimeNavigation(level, runtime, result.citizenId(), citizen, true);
            return;
        }
        runtime.active.put(result.citizenId(), new ActiveNavigation(result));
    }

    private static void tickActivePaths(ServerLevel level, LevelRuntime runtime) {
        for (Iterator<Map.Entry<UUID, ActiveNavigation>> iterator = runtime.active.entrySet().iterator(); iterator.hasNext(); ) {
            Map.Entry<UUID, ActiveNavigation> entry = iterator.next();
            CitizenEntity citizen = CitizenTeleportService.findCitizenEntity(level, entry.getKey());
            if (citizen == null) {
                iterator.remove();
                continue;
            }
            if (citizen.isSleeping()) {
                clearRuntimeNavigation(level, runtime, entry.getKey(), citizen, false);
                iterator.remove();
                continue;
            }
            if (!level.isPositionEntityTicking(citizen.blockPosition())) {
                continue;
            }
            ActiveTickResult result = entry.getValue().tick(level, citizen, runtime.openedDoors);
            if (result == ActiveTickResult.RUNNING) {
                continue;
            }
            citizen.getNavigation().stop();
            citizen.getMoveControl().setWantedPosition(citizen.getX(), citizen.getY(), citizen.getZ(), 0.0);
            PathCrowdCoordinator.clear(level, entry.getKey());
            iterator.remove();
            if (result == ActiveTickResult.REPATH) {
                ActiveNavigation active = entry.getValue();
                long blockedSince = runtime.blockedSince.computeIfAbsent(entry.getKey(), id -> level.getGameTime());
                if (level.getGameTime() - blockedSince >= STALLED_TELEPORT_TICKS) {
                    if (active.intent.allowsTeleportFallback()) {
                        CitizenTeleportService.teleportCitizen(level, entry.getKey(), active.target);
                    }
                    runtime.latestRequests.remove(entry.getKey());
                    runtime.cooldowns.remove(entry.getKey());
                    runtime.blockedSince.remove(entry.getKey());
                    continue;
                }
                runtime.cooldowns.put(entry.getKey(), level.getGameTime() + 20L);
                PathRequest request = new PathRequest(entry.getKey(), level.dimension().location(), citizen.blockPosition(), active.target, active.intent, level.getGameTime());
                runtime.latestRequests.put(entry.getKey(), request);
                if (runtime.queuedCitizenIds.add(entry.getKey())) {
                    runtime.queue.offer(entry.getKey());
                }
            } else {
                runtime.blockedSince.remove(entry.getKey());
            }
        }
    }

    private static void clearRuntimeNavigation(ServerLevel level, LevelRuntime runtime, UUID citizenId, CitizenEntity citizen, boolean removeActive) {
        if (removeActive) {
            runtime.active.remove(citizenId);
        }
        runtime.latestRequests.remove(citizenId);
        runtime.pending.remove(citizenId);
        runtime.queuedCitizenIds.remove(citizenId);
        runtime.blockedSince.remove(citizenId);
        runtime.cooldowns.remove(citizenId);
        if (citizen != null) {
            citizen.getNavigation().stop();
            citizen.getMoveControl().setWantedPosition(citizen.getX(), citizen.getY(), citizen.getZ(), 0.0D);
        }
        PathCrowdCoordinator.clear(level, citizenId);
    }

    private static LevelRuntime runtime(ServerLevel level) {
        return RUNTIMES.computeIfAbsent(runtimeKey(level), key -> new LevelRuntime());
    }

    private static String runtimeKey(ServerLevel level) {
        return SaveKey.serverKey(level.getServer()) + "|" + level.dimension().location();
    }

    /**
     * Counts the citizen entities loaded in the level, memoizing the result for the current tick so
     * the many move requests issued per tick share a single full entity scan.
     */
    private static int countLoadedCitizens(ServerLevel level, LevelRuntime runtime) {
        long gameTime = level.getGameTime();
        if (runtime.loadedCitizenCountTick == gameTime) {
            return runtime.loadedCitizenCount;
        }
        int count = 0;
        for (net.minecraft.world.entity.Entity entity : level.getAllEntities()) {
            if (entity instanceof CitizenEntity) {
                count++;
            }
        }
        runtime.loadedCitizenCountTick = gameTime;
        runtime.loadedCitizenCount = count;
        return count;
    }

    private static boolean hasLoadedChunk(ServerLevel level, BlockPos pos) {
        return level != null
                && pos != null
                && level.hasChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
    }

    private static void applyDebugPath(ServerLevel level, ServerPlayer player, UUID citizenId, PathResult result, Throwable throwable) {
        if (throwable != null) {
            SimuKraft.LOGGER.warn("Simukraft: NPC debug path calculation failed for {}", citizenId, throwable);
            CitizenPathDebugService.sendDebugFailure(player, citizenId, "path_calculation_failed");
            return;
        }
        if (result == null) {
            CitizenPathDebugService.sendDebugFailure(player, citizenId, "path_result_missing");
            return;
        }
        PacketDistributor.sendToPlayer(player, NpcPathDebugSyncPacket.fromResult(result));
        if (result.success()) {
            LevelRuntime runtime = runtime(level);
            runtime.latestRequests.remove(result.citizenId());
            runtime.pending.remove(result.citizenId());
            runtime.cooldowns.remove(result.citizenId());
            activate(level, runtime, result);
            InfoToastService.success(player, Component.translatable("message.simukraft.path_debug.success", result.waypoints().size()));
        } else {
            InfoToastService.warning(player, Component.translatable("message.simukraft.path_debug.failed", result.reason()));
        }
    }

    private static int localPathDistanceLimit() {
        // 只用传送阈值配置，不再被 localPathRadiusBlocks 静默截断
        return ServerConfig.pathFarMovementTeleportDistance();
    }

    private static ExecutorService executor() {
        int requestedSize = Math.max(1, ServerConfig.pathWorkerThreads());
        ExecutorService existing = pathExecutor;
        if (existing != null && !existing.isShutdown() && executorSize == requestedSize) {
            return existing;
        }
        synchronized (CitizenNavigationService.class) {
            existing = pathExecutor;
            if (existing != null && !existing.isShutdown() && executorSize == requestedSize) {
                return existing;
            }
            if (existing != null) {
                // Let already-submitted path futures finish when the live worker count changes.
                // shutdownNow would remove queued AsyncSupply tasks without completing their futures.
                existing.shutdown();
            }
            executorSize = requestedSize;
            // Use a bounded elastic pool: core=0 so idle threads are reaped after 60 s,
            // avoiding permanent thread overhead when no pathfinding work is pending.
            ThreadPoolExecutor pool = new ThreadPoolExecutor(
                    0, requestedSize, 60L, TimeUnit.SECONDS,
                    new java.util.concurrent.SynchronousQueue<>(),
                    new PathThreadFactory());
            pool.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
            pathExecutor = pool;
            return pathExecutor;
        }
    }

    private static final class LevelRuntime {
        private final ConcurrentLinkedQueue<UUID> queue = new ConcurrentLinkedQueue<>();
        private final java.util.Set<UUID> queuedCitizenIds = ConcurrentHashMap.newKeySet();
        private final Map<UUID, PathRequest> latestRequests = new java.util.HashMap<>();
        private final Map<UUID, RunningRequest> pending = new java.util.HashMap<>();
        private final Map<UUID, ActiveNavigation> active = new java.util.HashMap<>();
        private final Map<UUID, Long> cooldowns = new java.util.HashMap<>();
        private final Map<UUID, Long> blockedSince = new java.util.HashMap<>();
        private final Map<Long, CitizenDoorService.OpenedDoor> openedDoors = new java.util.HashMap<>();
        private final PathResultCache pathCache = new PathResultCache();
        private final PathSnapshotCache snapshotCache = new PathSnapshotCache();
        private final PathDistanceFieldCache distanceFields = new PathDistanceFieldCache();
        private FieldPump fieldPump;
        private long loadedCitizenCountTick = Long.MIN_VALUE;
        private int loadedCitizenCount;
    }

    private record RunningRequest(CompletableFuture<PathJob> future, PathCacheKey cacheKey, PathSectionStamp stamp) {
    }

    private record PathJob(PathResult result) {
    }

    /**
     * FieldPump: 后台距离场。同一时间只跑一份，每步扩展有限个格子。
     */
    private static final class FieldPump {
        private static final int STEP_ITERATIONS = 400;
        private final PathDistanceFieldCache.PathFieldKey key;
        private final PathRequest request;
        private final UUID cityId;
        private final PathSnapshotBuilder.SnapshotBounds bounds;
        private PathSnapshotCache.Acquisition acquired;
        private PathDistanceField.Build build;
        private CompletableFuture<Boolean> step;

        private FieldPump(PathDistanceFieldCache.PathFieldKey key, PathRequest request, UUID cityId,
                          PathSnapshotBuilder.SnapshotBounds bounds) {
            this.key = key;
            this.request = request;
            this.cityId = cityId;
            this.bounds = bounds;
        }

        /**
         * start: 记下要覆盖的盒子，采样留到后续 tick。
         */
        static FieldPump start(PathDistanceFieldCache.PathFieldKey key, PathRequest request, UUID cityId,
                               PathSnapshotBuilder.SnapshotBounds bounds) {
            return new FieldPump(key, request, cityId, bounds);
        }

        /**
         * advance: 在工人线程上创建或推进距离场。目标不可达时返回 false。
         */
        boolean advance() {
            if (build == null) {
                PathSnapshot snapshot = PathSnapshotBuilder.buildFromCapture(acquired.capture(), request.startPos(), request.targetBlockPos());
                build = PathDistanceField.Build.start(request, snapshot, acquired.stamp());
                if (build == null) {
                    return false;
                }
            }
            build.step(STEP_ITERATIONS);
            return true;
        }
    }

    private static final class PathThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "simukraft-path-worker-" + THREAD_ID.incrementAndGet());
            thread.setDaemon(true);
            thread.setPriority(Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 1));
            return thread;
        }
    }

    private static final class SaveKey {
        private static final java.util.WeakHashMap<net.minecraft.server.MinecraftServer, String> CACHE = new java.util.WeakHashMap<>();

        private static String serverKey(net.minecraft.server.MinecraftServer server) {
            if (server == null) return "unknown";
            return CACHE.computeIfAbsent(server, s ->
                    s.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT));
        }
    }
}
