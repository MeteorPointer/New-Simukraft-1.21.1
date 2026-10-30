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
    private static final int MAX_FIELD_DEMAND_KEYS = 32;
    private static final int MAX_QUEUE_PRIORITY_REFRESH_PER_TICK = 64;
    // 同小区上班、逃跑这类开阔短距不进全局队列，避免几百人把采样名额耗光。
    private static final double DIRECT_MOVE_DISTANCE = 8.0D;
    private static final double DIRECT_MOVE_DISTANCE_SQR = DIRECT_MOVE_DISTANCE * DIRECT_MOVE_DISTANCE;
    private static final int DEBUG_SYNC_PATH_LIMIT = 96;
    // 每拍只检查离玩家最近的这么多人。报告用同一个数判断是不是检查上限。
    private static final int EXAMINE_PER_TICK = 24;
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
            runtime.queue.offer(citizenId, nearestPlayerDistanceSqr(level, citizen.position()));
            return true;
        }
        Long cooldownUntil = runtime.cooldowns.get(citizenId);
        if (cooldownUntil != null && cooldownUntil > level.getGameTime() && normalizedIntent != MovementIntent.FLEE) {
            return false;
        }
        if (distanceSqr <= DIRECT_MOVE_DISTANCE_SQR && canWalkStraight(level, current, target)) {
            startDirectMove(level, runtime, citizen, citizenId, target, normalizedIntent);
            return true;
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
        runtime.queue.offer(citizenId, nearestPlayerDistanceSqr(level, citizen.position()));
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
            forgetQueued(runtime, citizenId);
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
            forgetQueued(runtime, citizenId);
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
        forgetQueued(runtime, citizenId);
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
            forgetQueued(runtime, citizenId);
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
                runtime.queue.size(),
                runtime.pending.size(),
                runtime.active.size(),
                runtime.cooldowns.size()));
        double averageSearchMillis = runtime.completedSearches == 0L ? 0.0D
                : runtime.searchNanos / (double) runtime.completedSearches / 1_000_000.0D;
        InfoToastService.send(player, Component.translatable(
                "message.simukraft.path_status.metrics",
                runtime.pathResultCacheHits,
                runtime.snapshotCacheHits,
                runtime.snapshotAcquisitions,
                runtime.freshSectionsCaptured,
                String.format(Locale.ROOT, "%.1f", averageSearchMillis),
                runtime.workerRejections,
                runtime.distanceFieldHits,
                runtime.distanceFieldBuilds,
                runtime.completedSearches));
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
        long tickStart = System.nanoTime();
        LevelRuntime runtime = runtime(level);
        runtime.snapshotCache.beginTick(level.getGameTime());
        runtime.upgradesThisTick = 0;
        long mark = System.nanoTime();
        applyCompletedPaths(level, runtime);
        PathReportSink.time("tick/applyCompleted", System.nanoTime() - mark);
        mark = System.nanoTime();
        tickActivePaths(level, runtime);
        PathReportSink.time("tick/activePaths", System.nanoTime() - mark);
        mark = System.nanoTime();
        CitizenDoorService.processOpenedDoors(level, runtime.openedDoors, runtime.active.keySet());
        PathReportSink.time("tick/doors", System.nanoTime() - mark);
        mark = System.nanoTime();
        processQueuedRequests(level, runtime);
        PathReportSink.time("tick/queue", System.nanoTime() - mark);
        mark = System.nanoTime();
        pumpFieldBuild(runtime);
        PathReportSink.time("tick/field", System.nanoTime() - mark);
        if (PathReportSink.active()) {
            PathReportSink.label("dimension", level.dimension().location().toString());
            PathReportSink.gauge("state/queue", runtime.queue.size());
            PathReportSink.gauge("state/active", runtime.active.size());
            PathReportSink.gauge("state/pending", runtime.pending.size());
            PathReportSink.time("tick", System.nanoTime() - tickStart);
        }
        if (level.getGameTime() % 200L == 0L) {
            runtime.pathCache.cleanup(level.getGameTime());
            runtime.snapshotCache.cleanup(level.getGameTime());
            runtime.fieldDemands.entrySet().removeIf(entry -> entry.getValue().expired(level.getGameTime()));
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
            runtime.snapshotCache.invalidate(pos, level.getGameTime());
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

    /**
     * processQueuedRequests：采样名额只给正在模拟、离玩家最近的人。某条路没采完时，热区里已经采齐的人仍在这一拍开工。
     */
    private static void processQueuedRequests(ServerLevel level, LevelRuntime runtime) {
        int budget = Math.max(0, ServerConfig.pathMaxNewRequestsPerTick());
        refreshQueuedPriorities(level, runtime);
        // 优先队列已覆盖全部待处理请求；每拍只取最近的一批，避免全量查询实体和缓存。
        int guard = Math.min(runtime.queue.size(), EXAMINE_PER_TICK);
        List<NearRequest> near = new ArrayList<>(Math.min(guard, 64));
        for (int examined = 0; examined < guard; examined++) {
            UUID citizenId = pollQueuedCitizen(runtime);
            if (citizenId == null) {
                break;
            }
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
            double distanceSqr = nearestPlayerDistanceSqr(level, citizen.position());
            if (!level.isPositionEntityTicking(citizen.blockPosition())) {
                // 不在模拟距离里的人不占采样名额。记下来，避免队列变长却看不到原因。
                PathReportSink.count("flow/notTicking", 1L);
                requeue(runtime, citizenId, request, distanceSqr);
                continue;
            }
            near.add(new NearRequest(citizenId, request, citizen, distanceSqr));
        }
        near.sort(Comparator.comparingDouble(NearRequest::distanceSqr));
        long now = level.getGameTime();
        List<SampleCandidate> waiting = new ArrayList<>();
        int processed = 0;
        for (NearRequest item : near) {
            PathRequest currentRequest = refreshedRequest(item, now);
            if (item.distanceSqr() == Double.MAX_VALUE) {
                requeue(runtime, item.citizenId(), currentRequest, item.distanceSqr());
                continue;
            }
            if (processed < budget && tryActivateCached(level, runtime, currentRequest)) {
                processed++;
                continue;
            }
            waiting.add(new SampleCandidate(item, currentRequest, corridorKey(currentRequest), 0, 0));
        }
        runtime.sampleTier = PathSampleModel.advanceTier(runtime.sampleTier, waiting.size());
        if (PathReportSink.active()) {
            PathReportSink.count("flow/examined", near.size());
            PathReportSink.count("flow/waiting", waiting.size());
            PathReportSink.gauge("state/padding", runtime.sampleTier.padding);
            PathReportSink.gauge("state/requestBudget", budget);
            PathReportSink.gauge("state/examineCap", EXAMINE_PER_TICK);
        }
        if (waiting.isEmpty()) {
            return;
        }
        Map<Long, List<SampleCandidate>> groups = new java.util.LinkedHashMap<>();
        for (SampleCandidate candidate : waiting) {
            groups.computeIfAbsent(candidate.partitionKey(), key -> new ArrayList<>()).add(candidate);
        }
        List<List<SampleCandidate>> partitions = new ArrayList<>(groups.values());
        partitions.sort(Comparator.comparingDouble(group -> representative(group).item().distanceSqr()));
        boolean allowFresh = true;
        PathSampleModel.BuildingSpan[] buildings = null;
        long recentSince = now - PathSampleModel.RECENT_CHANGE_TICKS;
        for (List<SampleCandidate> group : partitions) {
            SampleCandidate lead = representative(group);
            if (routeStillBlocked(runtime, lead.partitionKey())) {
                PathReportSink.count("flow/blocked", group.size());
                requeueGroup(runtime, group);
                continue;
            }
            if (processed >= budget || runtime.active.size() + runtime.pending.size() >= ServerConfig.pathMaxActiveCitizens()) {
                PathReportSink.count("flow/budgetHold", group.size());
                requeueGroup(runtime, group);
                continue;
            }
            if (buildings == null) {
                buildings = buildingSpans(level);
            }
            scoreGroup(level, runtime, group, buildings, recentSince);
            lead = representative(group);
            int padding = groupPadding(group);
            boolean flat = flatGroup(runtime, group);
            PathSnapshotCache.Acquisition acquired = flat
                    ? runtime.snapshotCache.acquireFlatBlocks(level, lead.request().startPos(), lead.request().targetBlockPos(),
                    runtime.sampleTier.ttlTicks, allowFresh)
                    : runtime.snapshotCache.acquireCorridor(
                    level, lead.request().startPos(), lead.request().targetBlockPos(), padding,
                    runtime.sampleTier.ttlTicks, allowFresh);
            if (!acquired.ready()) {
                recordSnapshotAcquisition(runtime, acquired);
                PathReportSink.count("flow/sampleMiss", group.size());
                requeueGroup(runtime, group);
                allowFresh = false;
                continue;
            }
            for (SampleCandidate member : group) {
                if (processed >= budget || runtime.active.size() + runtime.pending.size() >= ServerConfig.pathMaxActiveCitizens()) {
                    PathReportSink.count("flow/budgetHold", 1L);
                    requeue(runtime, member.item().citizenId(), member.request(), member.item().distanceSqr());
                    continue;
                }
                PathSnapshotCache.Acquisition memberCapture = member == lead
                        ? acquired
                        : flat
                        ? runtime.snapshotCache.acquireFlatBlocks(level, member.request().startPos(), member.request().targetBlockPos(),
                        runtime.sampleTier.ttlTicks, allowFresh)
                        : runtime.snapshotCache.acquireCorridor(level, member.request().startPos(), member.request().targetBlockPos(),
                        padding, runtime.sampleTier.ttlTicks, allowFresh);
                recordSnapshotAcquisition(runtime, memberCapture);
                if (!memberCapture.ready()) {
                    PathReportSink.count("flow/sampleMiss", 1L);
                    requeue(runtime, member.item().citizenId(), member.request(), member.item().distanceSqr());
                    continue;
                }
                if (submitSearch(level, runtime, member.request(), memberCapture, padding, member.partitionKey())) {
                    processed++;
                    PathReportSink.count("flow/started", 1L);
                } else {
                    requeue(runtime, member.item().citizenId(), member.request(), member.item().distanceSqr());
                }
            }
        }
    }

    /** 每 tick 轮转刷新一小批排队距离，跟上玩家和居民移动，同时保持主线程开销有界。 */
    private static void refreshQueuedPriorities(ServerLevel level, LevelRuntime runtime) {
        int refreshCount = Math.min(runtime.queue.size(), MAX_QUEUE_PRIORITY_REFRESH_PER_TICK);
        for (int refreshed = 0; refreshed < refreshCount; refreshed++) {
            UUID citizenId = runtime.queue.nextForPriorityRefresh();
            if (citizenId == null) {
                return;
            }
            PathRequest request = runtime.latestRequests.get(citizenId);
            if (request == null || runtime.pending.containsKey(citizenId)) {
                runtime.queue.remove(citizenId);
                continue;
            }
            CitizenEntity citizen = CitizenTeleportService.findCitizenEntity(level, citizenId);
            if (citizen == null) {
                runtime.latestRequests.remove(citizenId);
                runtime.queue.remove(citizenId);
                continue;
            }
            if (citizen.isSleeping()) {
                clearRuntimeNavigation(level, runtime, citizenId, citizen, true);
                continue;
            }
            runtime.queue.offer(citizenId, nearestPlayerDistanceSqr(level, citizen.position()));
        }
    }

    private static PathRequest refreshedRequest(NearRequest item, long now) {
        return new PathRequest(item.citizenId(), item.request().dimensionId(), item.citizen().blockPosition(),
                item.request().target(), item.request().intent(), now);
    }

    private static void recordSnapshotAcquisition(LevelRuntime runtime, PathSnapshotCache.Acquisition acquisition) {
        runtime.snapshotAcquisitions++;
        runtime.freshSectionsCaptured += acquisition.freshSections();
        if (acquisition.ready() && acquisition.freshSections() == 0) {
            runtime.snapshotCacheHits++;
        }
    }

    private static boolean tryActivateCached(ServerLevel level, LevelRuntime runtime, PathRequest request) {
        if (runtime.blockedSince.containsKey(request.citizenId())) {
            return false;
        }
        PathCacheKey cacheKey = new PathCacheKey(request.dimensionId(), request.startPos(), request.targetBlockPos(), request.intent());
        PathResult cached = runtime.pathCache.get(cacheKey, level.getGameTime(), runtime.snapshotCache.epochs());
        if (cached == null) {
            return false;
        }
        runtime.pathResultCacheHits++;
        activate(level, runtime, cached.forRequest(request));
        runtime.paddingFloor.remove(request.citizenId());
        PathReportSink.count("flow/cacheHits", 1L);
        return true;
    }

    private static long corridorKey(PathRequest request) {
        return PathSampleModel.corridorKey(request.startPos().getX(), request.startPos().getZ(),
                request.targetBlockPos().getX(), request.targetBlockPos().getZ());
    }

    private static SampleCandidate representative(List<SampleCandidate> group) {
        SampleCandidate best = group.get(0);
        for (int i = 1; i < group.size(); i++) {
            SampleCandidate candidate = group.get(i);
            int distance = Double.compare(candidate.item().distanceSqr(), best.item().distanceSqr());
            if (distance < 0) {
                best = candidate;
            }
        }
        return best;
    }

    /**
     * scoreGroup：只给这一拍真要采样的分区算复杂度，组内每个人都要算，才能取最大余量。
     */
    private static void scoreGroup(ServerLevel level, LevelRuntime runtime, List<SampleCandidate> group,
                                   PathSampleModel.BuildingSpan[] buildings, long recentSince) {
        for (int i = 0; i < group.size(); i++) {
            SampleCandidate candidate = group.get(i);
            int score = PathSampleModel.complexity(
                    candidate.request().startPos().getX(), candidate.request().startPos().getY(), candidate.request().startPos().getZ(),
                    candidate.request().targetBlockPos().getX(), candidate.request().targetBlockPos().getY(), candidate.request().targetBlockPos().getZ(),
                    buildings, (sectionX, sectionY, sectionZ) -> runtime.snapshotCache.epochs().changedSince(sectionX, sectionY, sectionZ, recentSince));
            int padding = Math.max(PathSampleModel.paddingForScore(score, runtime.sampleTier), runtime.paddingFloor.getOrDefault(candidate.item().citizenId(), 0));
            group.set(i, new SampleCandidate(candidate.item(), candidate.request(), candidate.partitionKey(), padding, score));
        }
    }

    private static boolean routeStillBlocked(LevelRuntime runtime, long partitionKey) {
        PathSectionStamp stamp = runtime.impassableUntilChange.get(partitionKey);
        if (stamp == null) {
            return false;
        }
        if (runtime.snapshotCache.epochs().matches(stamp)) {
            return true;
        }
        runtime.impassableUntilChange.remove(partitionKey);
        return false;
    }

    /**
     * flatGroup：复杂度为 0、且没有失败加宽过的分区走整块街区。加宽过的人回到走廊盒。
     */
    private static boolean flatGroup(LevelRuntime runtime, List<SampleCandidate> group) {
        for (SampleCandidate candidate : group) {
            if (candidate.score() != 0 || runtime.paddingFloor.getOrDefault(candidate.item().citizenId(), 0) > 0) {
                return false;
            }
        }
        return !group.isEmpty();
    }

    private static int groupPadding(List<SampleCandidate> group) {
        int padding = PathSampleModel.PAD_NARROW;
        for (SampleCandidate candidate : group) {
            padding = Math.max(padding, candidate.padding());
        }
        return padding;
    }

    private static void requeueGroup(LevelRuntime runtime, List<SampleCandidate> group) {
        for (SampleCandidate candidate : group) {
            requeue(runtime, candidate.item().citizenId(), candidate.request(), candidate.item().distanceSqr());
        }
    }

    private static boolean submitSearch(ServerLevel level, LevelRuntime runtime, PathRequest request,
                                        PathSnapshotCache.Acquisition acquired, int padding, long partitionKey) {
        UUID cityId = cityOf(level, request.citizenId());
        PathDistanceFieldCache.PathFieldKey fieldKey = new PathDistanceFieldCache.PathFieldKey(
                request.dimensionId(), cityId, request.targetBlockPos(), request.intent());
        PathDistanceField existingField = runtime.distanceFields.get(fieldKey, runtime.snapshotCache.epochs());
        CompletableFuture<PathJob> future;
        try {
            future = CompletableFuture.supplyAsync(
                    () -> PathExtraCosts.callWithCity(cityId, () -> search(request, acquired, existingField)),
                    executor());
        } catch (RejectedExecutionException rejected) {
            runtime.workerRejections++;
            PathReportSink.count("flow/rejected", 1L);
            return false;
        }
        if (existingField != null) {
            runtime.distanceFieldHits++;
        }
        PathCacheKey cacheKey = new PathCacheKey(request.dimensionId(), request.startPos(), request.targetBlockPos(), request.intent());
        runtime.pending.put(request.citizenId(), new RunningRequest(future, cacheKey, acquired.stamp(), padding, partitionKey));
        if (existingField == null && (runtime.fieldPump == null || !runtime.fieldPump.key.equals(fieldKey))) {
            observeFieldDemand(runtime, fieldKey, request, cityId, acquired, level.getGameTime());
        }
        return true;
    }

    /** 只有同目标请求确实重复时才启动共享距离场，并复用本次已采好的快照。 */
    private static void observeFieldDemand(LevelRuntime runtime, PathDistanceFieldCache.PathFieldKey key,
                                           PathRequest request, UUID cityId,
                                           PathSnapshotCache.Acquisition acquired, long now) {
        runtime.fieldDemands.entrySet().removeIf(entry -> entry.getValue().expired(now));
        PathFieldDemand demand = runtime.fieldDemands.get(key);
        if (demand == null) {
            if (runtime.fieldDemands.size() >= MAX_FIELD_DEMAND_KEYS) {
                PathDistanceFieldCache.PathFieldKey oldest = runtime.fieldDemands.keySet().iterator().next();
                runtime.fieldDemands.remove(oldest);
            }
            demand = new PathFieldDemand();
            runtime.fieldDemands.put(key, demand);
        }
        demand.observe(request.citizenId(), request.startPos(), now);
        if (runtime.fieldPump == null && acquired.capture().complete()
                && demand.canBuild(acquired.capture().bounds())) {
            runtime.fieldDemands.remove(key);
            runtime.fieldPump = new FieldPump(key, request, cityId, acquired);
        }
    }

    private static void noteSampleFailure(LevelRuntime runtime, UUID citizenId, RunningRequest running) {
        if (running.horizontalPadding() >= PathSampleModel.PAD_WIDE) {
            if (running.stamp() != null) {
                runtime.impassableUntilChange.put(running.partitionKey(), running.stamp());
            }
            PathReportSink.count("flow/failures", 1L);
            return;
        }
        if (runtime.upgradesThisTick >= PathSampleModel.MAX_NEW_UPGRADES) {
            return;
        }
        runtime.upgradesThisTick++;
        runtime.paddingFloor.put(citizenId, PathSampleModel.raisePadding(running.horizontalPadding()));
        PathReportSink.count("flow/failures", 1L);
    }

    private static PathSampleModel.BuildingSpan[] buildingSpans(ServerLevel level) {
        List<common.cn.nsimu.simukraft.building.PlacedBuildingRecord> records = common.cn.nsimu.simukraft.building.PlacedBuildingService.getBuildings(level);
        PathSampleModel.BuildingSpan[] spans = new PathSampleModel.BuildingSpan[records.size()];
        for (int i = 0; i < records.size(); i++) {
            common.cn.nsimu.simukraft.building.PlacedBuildingRecord record = records.get(i);
            if (record.minPos() == null || record.maxPos() == null) {
                continue;
            }
            spans[i] = new PathSampleModel.BuildingSpan(record.buildingId().getMostSignificantBits() ^ record.buildingId().getLeastSignificantBits(),
                    Math.min(record.minPos().getX(), record.maxPos().getX()),
                    Math.min(record.minPos().getY(), record.maxPos().getY()),
                    Math.min(record.minPos().getZ(), record.maxPos().getZ()),
                    Math.max(record.minPos().getX(), record.maxPos().getX()),
                    Math.max(record.minPos().getY(), record.maxPos().getY()),
                    Math.max(record.minPos().getZ(), record.maxPos().getZ()));
        }
        return spans;
    }

    /**
     * nearestPlayerDistanceSqr：没有玩家时返回最大值，避免没人看的时候还去采新区段。
     */
    private static double nearestPlayerDistanceSqr(ServerLevel level, Vec3 pos) {
        double best = Double.MAX_VALUE;
        for (ServerPlayer player : level.players()) {
            if (player == null || player.isSpectator()) {
                continue;
            }
            best = Math.min(best, pos.distanceToSqr(player.position()));
        }
        return best;
    }

    /**
     * search: 能接上已有距离场就回溯，否则单人 A*。建场放到后台分步做。
     */
    private static PathJob search(PathRequest request, PathSnapshotCache.Acquisition acquired, PathDistanceField existingField) {
        long startedAt = System.nanoTime();
        PathSnapshot snapshot = PathSnapshotBuilder.buildFromCapture(acquired.capture(), request.startPos(), request.targetBlockPos());
        if (existingField != null) {
            PathResult reconstructed = existingField.reconstruct(request, snapshot);
            if (reconstructed.success()) {
                return new PathJob(reconstructed, System.nanoTime() - startedAt);
            }
        }
        return new PathJob(HybridPathfinder.find(request, snapshot), System.nanoTime() - startedAt);
    }

    /**
     * pumpFieldBuild: 每 tick 最多推进一步距离场。采样没完成就只吃本 tick 剩余的区段预算。
     */
    private static void pumpFieldBuild(LevelRuntime runtime) {
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
                runtime.distanceFieldBuilds++;
                runtime.fieldPump = null;
                return;
            }
        }
        try {
            pump.step = CompletableFuture.supplyAsync(
                    () -> PathExtraCosts.callWithCity(pump.cityId, () -> pump.advance()),
                    executor());
        } catch (RejectedExecutionException rejected) {
            runtime.workerRejections++;
            pump.step = null;
        }
    }

    /**
     * pollQueuedCitizen：优先继续上拍没采完的那个人，避免新区段被撒到全城后过期。
     */
    private static UUID pollQueuedCitizen(LevelRuntime runtime) {
        return runtime.queue.poll();
    }

    /**
     * forgetQueued：离开队列时同时清除堆索引和距离刷新索引。
     */
    private static void forgetQueued(LevelRuntime runtime, UUID citizenId) {
        runtime.queue.remove(citizenId);
    }

    /**
     * startDirectMove：开阔的近距离直接走到目标，不占用全局寻路名额。
     */
    private static void startDirectMove(ServerLevel level, LevelRuntime runtime, CitizenEntity citizen, UUID citizenId,
                                        Vec3 target, MovementIntent intent) {
        runtime.latestRequests.remove(citizenId);
        forgetQueued(runtime, citizenId);
        RunningRequest running = runtime.pending.remove(citizenId);
        if (running != null) {
            running.future().cancel(true);
        }
        runtime.cooldowns.remove(citizenId);
        runtime.blockedSince.remove(citizenId);
        Vec3 stand = new Vec3(target.x, citizen.getY(), target.z);
        PathRequest request = new PathRequest(citizenId, level.dimension().location(), citizen.blockPosition(), stand, intent, level.getGameTime());
        activate(level, runtime, PathResult.success(request, java.util.List.of(
                new PathWaypoint(citizen.blockPosition(), citizen.position(), MovementMode.WALK),
                new PathWaypoint(BlockPos.containing(stand), stand, MovementMode.WALK))));
    }

    /**
     * canWalkStraight：近距离脚下和头顶都空、地面还在，才允许不寻路直走。
     */
    private static boolean canWalkStraight(ServerLevel level, Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal < 1.0E-3D) {
            return true;
        }
        if (Math.abs(to.y - from.y) > 1.0D) {
            return false;
        }
        int steps = Math.max(1, (int) Math.ceil(horizontal));
        for (int step = 1; step <= steps; step++) {
            double t = step / (double) steps;
            BlockPos feet = BlockPos.containing(from.x + dx * t, from.y, from.z + dz * t);
            if (!isOpenColumn(level, feet)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isOpenColumn(ServerLevel level, BlockPos feet) {
        BlockPos ground = feet.below();
        if (!isPassable(level, feet) || !isPassable(level, feet.above())) {
            return false;
        }
        return !level.getBlockState(ground).getCollisionShape(level, ground).isEmpty();
    }

    private static boolean isPassable(ServerLevel level, BlockPos pos) {
        net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
        return state.getFluidState().isEmpty() && state.getCollisionShape(level, pos).isEmpty();
    }

    /** 未完成的请求按最近玩家距离重新排队，避免大队列轮转时近处请求长期落在队尾。 */
    private static void requeue(LevelRuntime runtime, UUID citizenId, PathRequest request, double distanceSqr) {
        runtime.latestRequests.put(citizenId, request);
        runtime.queue.offer(citizenId, distanceSqr);
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
            if (job != null) {
                runtime.completedSearches++;
                runtime.searchNanos += job.elapsedNanos();
                PathReportSink.time("async/search", job.elapsedNanos());
                PathReportSink.count("flow/completed", 1L);
            }
            boolean fresh = runtime.snapshotCache.epochs().matches(running.stamp());
            if (result != null && result.success()) {
                if (fresh) {
                    runtime.pathCache.put(running.cacheKey(), result, level.getGameTime(), ServerConfig.pathCacheTtlTicks(), running.stamp());
                }
                runtime.paddingFloor.remove(entry.getKey());
                activate(level, runtime, result);
            } else {
                UUID citizenId = entry.getKey();
                runtime.cooldowns.remove(citizenId);
                runtime.blockedSince.remove(citizenId);
                if (result != null) {
                    noteSampleFailure(runtime, citizenId, running);
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
                runtime.queue.offer(entry.getKey(), nearestPlayerDistanceSqr(level, citizen.position()));
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
        forgetQueued(runtime, citizenId);
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
        private final PathRequestPriorityQueue queue = new PathRequestPriorityQueue();
        private final Map<UUID, PathRequest> latestRequests = new java.util.HashMap<>();
        private final Map<UUID, RunningRequest> pending = new java.util.HashMap<>();
        private final Map<UUID, ActiveNavigation> active = new java.util.HashMap<>();
        private final Map<UUID, Long> cooldowns = new java.util.HashMap<>();
        private final Map<UUID, Long> blockedSince = new java.util.HashMap<>();
        private final Map<Long, CitizenDoorService.OpenedDoor> openedDoors = new java.util.HashMap<>();
        private final PathResultCache pathCache = new PathResultCache();
        private final PathSnapshotCache snapshotCache = new PathSnapshotCache();
        private final PathDistanceFieldCache distanceFields = new PathDistanceFieldCache();
        private final LinkedHashMap<PathDistanceFieldCache.PathFieldKey, PathFieldDemand> fieldDemands =
                new LinkedHashMap<>(MAX_FIELD_DEMAND_KEYS, 0.75F, true);
        private FieldPump fieldPump;
        private PathSampleModel.Tier sampleTier = PathSampleModel.Tier.STANDARD;
        private int upgradesThisTick;
        private final Map<UUID, Integer> paddingFloor = new java.util.HashMap<>();
        private final Map<Long, PathSectionStamp> impassableUntilChange = new java.util.HashMap<>();
        private long pathResultCacheHits;
        private long snapshotCacheHits;
        private long snapshotAcquisitions;
        private long freshSectionsCaptured;
        private long completedSearches;
        private long searchNanos;
        private long workerRejections;
        private long distanceFieldHits;
        private long distanceFieldBuilds;
        private long loadedCitizenCountTick = Long.MIN_VALUE;
        private int loadedCitizenCount;
    }

    private record NearRequest(UUID citizenId, PathRequest request, CitizenEntity citizen, double distanceSqr) {
    }

    private record RunningRequest(CompletableFuture<PathJob> future, PathCacheKey cacheKey, PathSectionStamp stamp,
                                  int horizontalPadding, long partitionKey) {
    }

    private record SampleCandidate(NearRequest item, PathRequest request, long partitionKey, int padding, int score) {
    }

    private record PathJob(PathResult result, long elapsedNanos) {
    }

    /**
     * FieldPump: 后台距离场。同一时间只跑一份，每步扩展有限个格子。
     */
    private static final class FieldPump {
        private static final int STEP_ITERATIONS = 400;
        private final PathDistanceFieldCache.PathFieldKey key;
        private final PathRequest request;
        private final UUID cityId;
        private final PathSnapshotCache.Acquisition acquired;
        private PathDistanceField.Build build;
        private CompletableFuture<Boolean> step;

        private FieldPump(PathDistanceFieldCache.PathFieldKey key, PathRequest request, UUID cityId,
                          PathSnapshotCache.Acquisition acquired) {
            this.key = key;
            this.request = request;
            this.cityId = cityId;
            this.acquired = acquired;
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
