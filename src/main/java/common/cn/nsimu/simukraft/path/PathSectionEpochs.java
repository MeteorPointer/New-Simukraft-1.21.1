package common.cn.nsimu.simukraft.path;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;

/**
 * PathSectionEpochs: 按 16³ 区段记录地形版本。方块变化只抬升周围一圈区段。
 */
final class PathSectionEpochs {
    private final Long2LongOpenHashMap epochs = new Long2LongOpenHashMap();

    /**
     * bumpAround: 变化方块所在区段及其相邻区段版本加一。门和围栏的碰撞会读到邻格。
     */
    void bumpAround(BlockPos pos) {
        if (pos == null) {
            return;
        }
        int sectionX = SectionPos.blockToSectionCoord(pos.getX());
        int sectionY = SectionPos.blockToSectionCoord(pos.getY());
        int sectionZ = SectionPos.blockToSectionCoord(pos.getZ());
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    long key = SectionPos.asLong(sectionX + dx, sectionY + dy, sectionZ + dz);
                    epochs.put(key, epochs.get(key) + 1L);
                }
            }
        }
    }

    /**
     * capture: 记下这批区段当前的版本，供在途结果回来时比对。
     */
    PathSectionStamp capture(long[] sections) {
        long[] seen = new long[sections.length];
        for (int index = 0; index < sections.length; index++) {
            seen[index] = epochs.get(sections[index]);
        }
        return new PathSectionStamp(sections, seen);
    }

    /**
     * matches: 采样之后这些区段都没有再变化。
     */
    boolean matches(PathSectionStamp stamp) {
        if (stamp == null || stamp.sections() == null || stamp.epochs() == null
                || stamp.sections().length != stamp.epochs().length) {
            return false;
        }
        long[] sections = stamp.sections();
        long[] seen = stamp.epochs();
        for (int index = 0; index < sections.length; index++) {
            if (epochs.get(sections[index]) != seen[index]) {
                return false;
            }
        }
        return true;
    }

    /**
     * sectionsIn: 包围盒加上一格垂直边，覆盖门和活板门会写到邻格的阻挡位。
     */
    static long[] sectionsIn(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        int minSectionX = SectionPos.blockToSectionCoord(minX);
        int maxSectionX = SectionPos.blockToSectionCoord(maxX);
        int minSectionY = SectionPos.blockToSectionCoord(minY - 1);
        int maxSectionY = SectionPos.blockToSectionCoord(maxY + 1);
        int minSectionZ = SectionPos.blockToSectionCoord(minZ);
        int maxSectionZ = SectionPos.blockToSectionCoord(maxZ);
        int count = (maxSectionX - minSectionX + 1)
                * (maxSectionY - minSectionY + 1)
                * (maxSectionZ - minSectionZ + 1);
        long[] sections = new long[Math.max(0, count)];
        int index = 0;
        for (int sectionX = minSectionX; sectionX <= maxSectionX; sectionX++) {
            for (int sectionY = minSectionY; sectionY <= maxSectionY; sectionY++) {
                for (int sectionZ = minSectionZ; sectionZ <= maxSectionZ; sectionZ++) {
                    sections[index++] = SectionPos.asLong(sectionX, sectionY, sectionZ);
                }
            }
        }
        return sections;
    }
}
