package common.cn.nsimu.simukraft.path;

import net.minecraft.core.BlockPos;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** 按不同市民的同目标请求判断是否值得构建共享距离场。 */
final class PathFieldDemand {
    static final int MIN_CITIZENS = 3;
    static final long WINDOW_TICKS = 100L;
    private static final int MAX_CITIZENS = 16;

    private final LinkedHashMap<UUID, BlockPos> starts = new LinkedHashMap<>();
    private long lastRequestTick = Long.MIN_VALUE;

    void observe(UUID citizenId, BlockPos start, long now) {
        if (citizenId == null || start == null) {
            return;
        }
        if (lastRequestTick == Long.MIN_VALUE || now < lastRequestTick || now - lastRequestTick > WINDOW_TICKS) {
            starts.clear();
        }
        lastRequestTick = now;
        starts.remove(citizenId);
        starts.put(citizenId, start.immutable());
        while (starts.size() > MAX_CITIZENS) {
            starts.remove(starts.keySet().iterator().next());
        }
    }

    boolean canBuild(PathSnapshotBuilder.SnapshotBounds bounds) {
        if (bounds == null || starts.size() < MIN_CITIZENS) {
            return false;
        }
        for (BlockPos start : starts.values()) {
            if (start.getX() < bounds.minX() || start.getX() > bounds.maxX()
                    || start.getY() < bounds.minY() || start.getY() > bounds.maxY()
                    || start.getZ() < bounds.minZ() || start.getZ() > bounds.maxZ()) {
                return false;
            }
        }
        return true;
    }

    boolean expired(long now) {
        return lastRequestTick == Long.MIN_VALUE || now < lastRequestTick || now - lastRequestTick > WINDOW_TICKS;
    }

    int requesterCount() {
        return starts.size();
    }
}
