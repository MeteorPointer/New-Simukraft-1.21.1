package common.cn.nsimu.simukraft.path;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bounded LRU cache of successful path results, keyed by request signature.
 */
final class PathResultCache {
    private static final int MAX_ENTRIES = 512;
    private final LinkedHashMap<PathCacheKey, CacheEntry> entries =
            new LinkedHashMap<>(16, 0.75f, true);

    /**
     * findNear：起点和终点都差不过 2 格时复用折线。
     */
    synchronized PathResult findNear(ResourceLocation dimensionId, BlockPos start, BlockPos target, MovementIntent intent,
                                     long gameTime, PathSectionEpochs epochs) {
        if (dimensionId == null || start == null || target == null || intent == null) {
            return null;
        }
        for (Map.Entry<PathCacheKey, CacheEntry> entry : entries.entrySet()) {
            PathCacheKey key = entry.getKey();
            CacheEntry cached = entry.getValue();
            if (key.intent() != intent || !dimensionId.equals(key.dimensionId()) || cached.expiresAt < gameTime
                    || epochs == null || !epochs.matches(cached.stamp)) {
                continue;
            }
            if (PathSampleModel.withinBlocks(start.getX(), start.getY(), start.getZ(),
                    key.startPos().getX(), key.startPos().getY(), key.startPos().getZ(), PathSampleModel.PATH_REUSE_BLOCKS)
                    && PathSampleModel.withinBlocks(target.getX(), target.getY(), target.getZ(),
                    key.targetPos().getX(), key.targetPos().getY(), key.targetPos().getZ(), PathSampleModel.PATH_REUSE_BLOCKS)) {
                return cached.result;
            }
        }
        return null;
    }

    synchronized PathResult get(PathCacheKey key, long gameTime, PathSectionEpochs epochs) {
        CacheEntry entry = entries.get(key);
        if (entry == null || entry.expiresAt < gameTime || epochs == null || !epochs.matches(entry.stamp)) {
            if (entry != null) {
                entries.remove(key);
            }
            return null;
        }
        return entry.result;
    }

    synchronized void put(PathCacheKey key, PathResult result, long gameTime, int ttlTicks, PathSectionStamp stamp) {
        if (key == null || result == null || !result.success() || ttlTicks <= 0 || stamp == null) {
            return;
        }
        if (entries.size() >= MAX_ENTRIES) {
            trim(gameTime);
        }
        entries.put(key, new CacheEntry(result, gameTime + ttlTicks, stamp));
    }

    /**
     * dropStale: 区段版本变了的路线从缓存里拿走，避免占着名额。
     */
    synchronized void dropStale(long gameTime, PathSectionEpochs epochs) {
        entries.entrySet().removeIf(entry -> entry.getValue().expiresAt < gameTime
                || epochs == null
                || !epochs.matches(entry.getValue().stamp));
    }

    synchronized void clear() {
        entries.clear();
    }

    synchronized void cleanup(long gameTime) {
        entries.entrySet().removeIf(entry -> entry.getValue().expiresAt < gameTime);
    }

    // entries iterates in access order (least-recently-used first), so evicting from the
    // front discards the coldest entries rather than an arbitrary hash-order slice.
    private void trim(long gameTime) {
        cleanup(gameTime);
        for (Iterator<Map.Entry<PathCacheKey, CacheEntry>> iterator = entries.entrySet().iterator();
             iterator.hasNext() && entries.size() >= MAX_ENTRIES; ) {
            iterator.next();
            iterator.remove();
        }
    }

    private record CacheEntry(PathResult result, long expiresAt, PathSectionStamp stamp) {
    }
}
