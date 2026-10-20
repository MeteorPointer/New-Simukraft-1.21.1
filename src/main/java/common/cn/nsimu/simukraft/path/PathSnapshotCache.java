package common.cn.nsimu.simukraft.path;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;

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
    private static final int MAX_FRESH_SECTIONS_PER_TICK = 8;
    private static final long REUSE_TTL_TICKS = 40L;
    private final Long2ObjectOpenHashMap<Entry> entries = new Long2ObjectOpenHashMap<>();
    private final PathSectionEpochs epochs = new PathSectionEpochs();
    private int freshThisTick;

    /**
     * Acquisition: 区段采样和它当时的版本。ready 为 false 表示这一 tick 的新区段预算用完，盒子还没采齐。
     */
    record Acquisition(PathSnapshotBuilder.ChunkDataCapture capture, PathSectionStamp stamp, boolean ready) {
    }

    /**
     * beginTick: 每个服务器 tick 重置新区段预算，避免一整盒采样堆在同一 tick。
     */
    void beginTick() {
        freshThisTick = 0;
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
        BlockPos buildStart = new BlockPos(
                Math.floorDiv(start.getX(), 16) * 16 + 8,
                start.getY(),
                Math.floorDiv(start.getZ(), 16) * 16 + 8);
        int buildRadius = radius + 16;
        PathSnapshotBuilder.SnapshotBounds bounds = PathSnapshotBuilder.bounds(level, buildStart, target, buildRadius);
        return acquire(level, bounds);
    }

    /**
     * acquire: 按已经算好的包围盒采样。多人同目的地时盒子是几个人起点的并集。
     */
    Acquisition acquire(ServerLevel level, PathSnapshotBuilder.SnapshotBounds bounds) {
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
        capture:
        for (int sectionX = minSectionX; sectionX <= maxSectionX; sectionX++) {
            for (int sectionZ = minSectionZ; sectionZ <= maxSectionZ; sectionZ++) {
                if (!level.hasChunk(sectionX, sectionZ)) {
                    complete = false;
                    continue;
                }
                for (int sectionY = minSectionY; sectionY <= maxSectionY; sectionY++) {
                    long key = SectionPos.asLong(sectionX, sectionY, sectionZ);
                    Entry entry = entries.get(key);
                    if (entry == null || now - entry.createdAt() > REUSE_TTL_TICKS) {
                        if (freshThisTick >= MAX_FRESH_SECTIONS_PER_TICK) {
                            ready = false;
                            break capture;
                        }
                        entry = new Entry(PathSnapshotBuilder.captureSection(level, sectionX, sectionY, sectionZ), now);
                        entries.put(key, entry);
                        freshThisTick++;
                    }
                    sections.put(key, entry.capture());
                }
            }
        }
        trim(now);
        if (!ready) {
            return new Acquisition(null, null, false);
        }
        long[] covered = PathSectionEpochs.sectionsIn(bounds.minX(), bounds.maxX(), bounds.minY(), bounds.maxY(),
                bounds.minZ(), bounds.maxZ());
        return new Acquisition(PathSnapshotBuilder.composeCapture(level, bounds, sections, complete), epochs.capture(covered), true);
    }

    /**
     * invalidate: 丢掉变化点周围区段的采样，并抬升版本。其余区段留着。
     */
    void invalidate(BlockPos pos) {
        epochs.bumpAround(pos);
        if (pos == null) {
            return;
        }
        int sectionX = SectionPos.blockToSectionCoord(pos.getX());
        int sectionY = SectionPos.blockToSectionCoord(pos.getY());
        int sectionZ = SectionPos.blockToSectionCoord(pos.getZ());
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    entries.remove(SectionPos.asLong(sectionX + dx, sectionY + dy, sectionZ + dz));
                }
            }
        }
    }

    void clear() {
        entries.clear();
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
        entries.long2ObjectEntrySet().removeIf(entry -> now - entry.getValue().createdAt() > REUSE_TTL_TICKS);
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

    private record Entry(PathSnapshotBuilder.SectionDataCapture capture, long createdAt) {
    }
}
