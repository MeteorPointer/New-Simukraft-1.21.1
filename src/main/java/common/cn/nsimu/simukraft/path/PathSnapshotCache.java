package common.cn.nsimu.simukraft.path;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Small bounded cache of recently captured section data.
 *
 * <p>When several citizens path through overlapping areas, each 16x16x16 section is sampled at
 * most once during a short burst. Requests compose their own immutable capture from those section
 * entries before handing it to the worker thread.
 *
 * <p>Only accessed from the server thread, so it needs no synchronization.
 */
final class PathSnapshotCache {
    private static final int MAX_SECTION_ENTRIES = 512;
    // 主线程现在只复制调色板，不再逐格读 4096 次。48 份复制仍轻于以前 8 段全量抄方块，一条平坦街区才能在一拍内采齐。
    private static final int MAX_FRESH_SECTIONS_PER_TICK = 48;
    // 玩家周围的路要留给下一位市民。方块变化仍会立刻作废对应区段。
    private static final long REUSE_TTL_TICKS = PathSampleModel.TTL_NORMAL;
    private final Long2ObjectOpenHashMap<Entry> entries = new Long2ObjectOpenHashMap<>();
    private final PathSectionEpochs epochs = new PathSectionEpochs();
    private final PathHierarchy hierarchy = new PathHierarchy();
    private int freshThisTick;

    /**
     * Acquisition: 区段采样和它当时的版本。ready 为 false 表示这一 tick 的新区段预算用完，盒子还没采齐。
     */
    record Acquisition(PathSnapshotBuilder.ChunkDataCapture capture, PathSectionStamp stamp, boolean ready,
                       int freshSections) {
    }

    /**
     * beginTick: 每个服务器 tick 重置新区段预算，避免一整盒采样堆在同一 tick。
     */
    void beginTick(long now) {
        freshThisTick = 0;
        trim(now);
        if (PathReportSink.active()) {
            PathReportSink.gauge("state/freshBudget", MAX_FRESH_SECTIONS_PER_TICK);
        }
    }

    /**
     * epochs: 供路径缓存和距离场判断区段是否还新鲜。
     */
    PathSectionEpochs epochs() {
        return epochs;
    }

    /**
     * Returns a {@link PathSnapshotBuilder.ChunkDataCapture} covering the request. Fresh section
     * captures are reused even when whole request boxes differ.
     */
    Acquisition acquire(ServerLevel level, BlockPos start, BlockPos target, int radius) {
        // 只采起点到目标的走廊。按整段寻路半径扩成街区，会让几百人的采样永远做不完。
        return acquire(level, PathSnapshotBuilder.bounds(level, start, target, radius));
    }

    /**
     * acquireCorridor：按分级余量采样。allowFresh 为 false 时缺区块就停，不占用新区段。
     */
    Acquisition acquireCorridor(ServerLevel level, BlockPos start, BlockPos target, int horizontalPadding, long reuseTtlTicks, boolean allowFresh) {
        return acquire(level, PathSnapshotBuilder.corridorBounds(level, start, target, horizontalPadding), reuseTtlTicks, allowFresh);
    }

    /**
     * acquireFlatBlocks：平坦街区按整块区块柱采样。采过的柱直接复用，不按每人一条加宽走廊重抄。
     */
    Acquisition acquireFlatBlocks(ServerLevel level, BlockPos start, BlockPos target, long reuseTtlTicks, boolean allowFresh) {
        boolean reporting = PathReportSink.active();
        long started = reporting ? System.nanoTime() : 0L;
        try {
            return acquireFlatMeasured(level, start, target, reuseTtlTicks, allowFresh);
        } finally {
            if (reporting) {
                PathReportSink.time("tick/queue/sample", System.nanoTime() - started);
            }
        }
    }

    /**
     * acquire: 按已经算好的包围盒采样。多人同目的地时盒子是几个人起点的并集。
     */
    Acquisition acquire(ServerLevel level, PathSnapshotBuilder.SnapshotBounds bounds) {
        return acquire(level, bounds, REUSE_TTL_TICKS, true);
    }

    private Acquisition acquire(ServerLevel level, PathSnapshotBuilder.SnapshotBounds bounds, long reuseTtlTicks, boolean allowFresh) {
        boolean reporting = PathReportSink.active();
        long started = reporting ? System.nanoTime() : 0L;
        try {
            return acquireMeasured(level, bounds, reuseTtlTicks, allowFresh);
        } finally {
            if (reporting) {
                PathReportSink.time("tick/queue/sample", System.nanoTime() - started);
            }
        }
    }

    private Acquisition acquireMeasured(ServerLevel level, PathSnapshotBuilder.SnapshotBounds bounds, long reuseTtlTicks, boolean allowFresh) {
        long now = level.getGameTime();
        Long2ObjectOpenHashMap<PathSnapshotBuilder.SectionDataCapture> sections = new Long2ObjectOpenHashMap<>();
        boolean complete = true;
        boolean ready = true;
        int minSectionX = Math.floorDiv(bounds.minX(), 16);
        int maxSectionX = Math.floorDiv(bounds.maxX(), 16);
        int minSectionY = Math.floorDiv(Math.max(level.getMinBuildHeight(), bounds.minY() - 1), 16);
        int maxSectionY = Math.floorDiv(Math.min(level.getMaxBuildHeight() - 1, bounds.maxY() + 1), 16);
        int minSectionZ = Math.floorDiv(bounds.minZ(), 16);
        int maxSectionZ = Math.floorDiv(bounds.maxZ(), 16);
        int freshSections = 0;
        capture:
        for (int sectionX = minSectionX; sectionX <= maxSectionX; sectionX++) {
            for (int sectionZ = minSectionZ; sectionZ <= maxSectionZ; sectionZ++) {
                if (!level.hasChunk(sectionX, sectionZ)) {
                    complete = false;
                    continue;
                }
                for (int sectionY = minSectionY; sectionY <= maxSectionY; sectionY++) {
                    int captured = captureSection(level, sections, sectionX, sectionY, sectionZ, now, reuseTtlTicks, allowFresh);
                    if (captured < 0) {
                        ready = false;
                        break capture;
                    }
                    freshSections += captured;
                }
            }
        }
        if (!ready) {
            return new Acquisition(null, null, false, freshSections);
        }
        long[] covered = PathSectionEpochs.sectionsIn(bounds.minX(), bounds.maxX(), bounds.minY(), bounds.maxY(),
                bounds.minZ(), bounds.maxZ());
        return new Acquisition(PathSnapshotBuilder.composeCapture(level, bounds, sections, complete),
                epochs.capture(covered), true, freshSections);
    }

    private Acquisition acquireFlatMeasured(ServerLevel level, BlockPos start, BlockPos target, long reuseTtlTicks, boolean allowFresh) {
        long now = level.getGameTime();
        long[] columns = PathHierarchy.columns(start.getX(), start.getZ(), target.getX(), target.getZ());
        PathReportSink.count("flow/flatColumns", columns.length);
        int minY = Math.max(level.getMinBuildHeight(), Math.min(start.getY(), target.getY()) - PathSnapshotBuilder.verticalPadding());
        int maxY = Math.min(level.getMaxBuildHeight() - 2, Math.max(start.getY(), target.getY()) + PathSnapshotBuilder.verticalPadding());
        int minSectionY = Math.floorDiv(Math.max(level.getMinBuildHeight(), minY - 1), 16);
        int maxSectionY = Math.floorDiv(Math.min(level.getMaxBuildHeight() - 1, maxY + 1), 16);
        Long2ObjectOpenHashMap<PathSnapshotBuilder.SectionDataCapture> sections = new Long2ObjectOpenHashMap<>();
        boolean complete = true;
        boolean ready = true;
        int freshSections = 0;
        int minBlockX = Integer.MAX_VALUE;
        int maxBlockX = Integer.MIN_VALUE;
        int minBlockZ = Integer.MAX_VALUE;
        int maxBlockZ = Integer.MIN_VALUE;
        capture:
        for (long column : columns) {
            int sectionX = PathHierarchy.sectionX(column);
            int sectionZ = PathHierarchy.sectionZ(column);
            minBlockX = Math.min(minBlockX, sectionX * 16);
            maxBlockX = Math.max(maxBlockX, sectionX * 16 + 15);
            minBlockZ = Math.min(minBlockZ, sectionZ * 16);
            maxBlockZ = Math.max(maxBlockZ, sectionZ * 16 + 15);
            if (!level.hasChunk(sectionX, sectionZ)) {
                complete = false;
                continue;
            }
            for (int sectionY = minSectionY; sectionY <= maxSectionY; sectionY++) {
                int captured = captureSection(level, sections, sectionX, sectionY, sectionZ, now, reuseTtlTicks, allowFresh);
                if (captured < 0) {
                    ready = false;
                    break capture;
                }
                freshSections += captured;
            }
            if (ready) {
                int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sectionX * 16 + 8, sectionZ * 16 + 8);
                hierarchy.note(column, surface);
            }
        }
        if (!ready || columns.length == 0) {
            return new Acquisition(null, null, false, freshSections);
        }
        PathSnapshotBuilder.SnapshotBounds bounds = new PathSnapshotBuilder.SnapshotBounds(
                minBlockX, maxBlockX, minBlockZ, maxBlockZ, minY, maxY);
        long[] covered = sections.keySet().toLongArray();
        return new Acquisition(PathSnapshotBuilder.composeCapture(level, bounds, sections, complete),
                epochs.capture(covered), true, freshSections);
    }

    /**
     * captureSection：缺的区段才新抄。返回 1 表示新抄，0 表示复用，-1 表示这一拍的名额用完。
     */
    private int captureSection(ServerLevel level, Long2ObjectOpenHashMap<PathSnapshotBuilder.SectionDataCapture> sections,
                               int sectionX, int sectionY, int sectionZ, long now, long reuseTtlTicks, boolean allowFresh) {
        long key = SectionPos.asLong(sectionX, sectionY, sectionZ);
        Entry entry = entries.get(key);
        if (entry == null || now - entry.createdAt() > entry.ttlTicks()) {
            if (!allowFresh || freshThisTick >= MAX_FRESH_SECTIONS_PER_TICK) {
                return -1;
            }
            boolean timing = PathReportSink.active();
            long captureStart = timing ? System.nanoTime() : 0L;
            entry = new Entry(PathSnapshotBuilder.captureSection(level, sectionX, sectionY, sectionZ), now, reuseTtlTicks);
            if (timing) {
                PathReportSink.time("tick/queue/sample/capture", System.nanoTime() - captureStart);
            }
            entries.put(key, entry);
            freshThisTick++;
            PathReportSink.count("flow/freshSections", 1L);
            sections.put(key, entry.capture());
            return 1;
        }
        if (entry.createdAt() != now) {
            entry = new Entry(entry.capture(), now, Math.max(entry.ttlTicks(), reuseTtlTicks));
            entries.put(key, entry);
        }
        sections.put(key, entry.capture());
        return 0;
    }

    /**
     * invalidate: 丢掉变化点周围区段的采样，并抬升版本。其余区段留着。
     */
    void invalidate(BlockPos pos, long gameTime) {
        epochs.bumpAround(pos, gameTime);
        if (pos == null) {
            return;
        }
        int sectionX = SectionPos.blockToSectionCoord(pos.getX());
        int sectionY = SectionPos.blockToSectionCoord(pos.getY());
        int sectionZ = SectionPos.blockToSectionCoord(pos.getZ());
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                hierarchy.forget(sectionX + dx, sectionZ + dz);
                for (int dy = -1; dy <= 1; dy++) {
                    entries.remove(SectionPos.asLong(sectionX + dx, sectionY + dy, sectionZ + dz));
                }
            }
        }
    }

    void clear() {
        entries.clear();
        hierarchy.clear();
    }

    /**
     * cleanup: 在空闲时释放超出复用窗口的区段快照。
     */
    void cleanup(long now) {
        trim(now);
    }

    /**
     * trim: 限制短期区段缓存的内存上界并移除过期项。
     */
    private void trim(long now) {
        entries.long2ObjectEntrySet().removeIf(entry -> now - entry.getValue().createdAt() > entry.getValue().ttlTicks());
        while (entries.size() > MAX_SECTION_ENTRIES) {
            long oldestKey = 0L;
            long oldestTick = Long.MAX_VALUE;
            for (var entry : entries.long2ObjectEntrySet()) {
                if (entry.getValue().createdAt() < oldestTick) {
                    oldestTick = entry.getValue().createdAt();
                    oldestKey = entry.getLongKey();
                }
            }
            entries.remove(oldestKey);
        }
    }

    private record Entry(PathSnapshotBuilder.SectionDataCapture capture, long createdAt, long ttlTicks) {
    }
}
