package common.cn.nsimu.simukraft.path;

import java.util.Arrays;

/**
 * 寻路采样的档位、分区键和复杂度。不读世界。
 */
final class PathSampleModel {
    static final int PAD_NARROW = 8;
    static final int PAD_STANDARD = 12;
    static final int PAD_WIDE = 16;
    static final long TTL_NORMAL = 200L;
    static final long TTL_NARROW = 400L;
    static final int MAX_NEW_UPGRADES = 2;
    static final int PATH_REUSE_BLOCKS = 2;
    static final int RECENT_CHANGE_TICKS = 200;

    private PathSampleModel() {
    }

    enum Tier {
        WIDE(PAD_WIDE, TTL_NORMAL),
        STANDARD(PAD_STANDARD, TTL_NORMAL),
        NARROW(PAD_NARROW, TTL_NARROW);

        final int padding;
        final long ttlTicks;

        Tier(int padding, long ttlTicks) {
            this.padding = padding;
            this.ttlTicks = ttlTicks;
        }
    }

    /**
     * advanceTier：按附近等待人数切换档位，窄档和宽档都留回差。
     */
    static Tier advanceTier(Tier current, int demand) {
        Tier tier = current == null ? Tier.STANDARD : current;
        if (demand >= 49) {
            return Tier.NARROW;
        }
        if (tier == Tier.NARROW) {
            return demand <= 32 ? Tier.STANDARD : Tier.NARROW;
        }
        if (demand <= 12) {
            return Tier.WIDE;
        }
        if (tier == Tier.WIDE && demand >= 17) {
            return Tier.STANDARD;
        }
        return tier;
    }

    /**
     * paddingForScore：复杂度盖过全局档位，最高 16 格。
     */
    static int paddingForScore(int score, Tier global) {
        int base = (global == null ? Tier.STANDARD : global).padding;
        if (score >= 3) {
            return PAD_WIDE;
        }
        if (score >= 1) {
            return Math.max(base, PAD_STANDARD);
        }
        return base;
    }

    /**
     * raisePadding：失败后升一档，到 16 格为止。
     */
    static int raisePadding(int padding) {
        if (padding < PAD_STANDARD) {
            return PAD_STANDARD;
        }
        return PAD_WIDE;
    }

    /**
     * corridorKey：直线穿过的区块柱，方向和余量不影响键。
     */
    static long corridorKey(int startX, int startZ, int targetX, int targetZ) {
        int steps = Math.max(Math.abs(targetX - startX), Math.abs(targetZ - startZ));
        long[] columns = new long[steps + 1];
        int count = 0;
        int last = Integer.MIN_VALUE;
        int lastZ = Integer.MIN_VALUE;
        for (int i = 0; i <= steps; i++) {
            int x = steps == 0 ? startX : startX + (targetX - startX) * i / steps;
            int z = steps == 0 ? startZ : startZ + (targetZ - startZ) * i / steps;
            int sectionX = Math.floorDiv(x, 16);
            int sectionZ = Math.floorDiv(z, 16);
            if (sectionX == last && sectionZ == lastZ) {
                continue;
            }
            last = sectionX;
            lastZ = sectionZ;
            columns[count++] = (((long) sectionX) << 32) ^ (sectionZ & 0xFFFFFFFFL);
        }
        Arrays.sort(columns, 0, count);
        long hash = 17L;
        long previous = Long.MIN_VALUE;
        for (int i = 0; i < count; i++) {
            if (columns[i] == previous) {
                continue;
            }
            previous = columns[i];
            hash = hash * 31L + columns[i];
        }
        return hash;
    }

    /**
     * complexity：高度差、中间建筑和最近改动。起点和终点自己的房子不算。
     */
    static int complexity(int startX, int startY, int startZ, int targetX, int targetY, int targetZ,
                          BuildingSpan[] buildings, SectionChange sectionChange) {
        int score = Math.abs(startY - targetY) > 4 ? 1 : 0;
        int crossed = otherBuildings(startX, startY, startZ, targetX, targetY, targetZ, buildings);
        if (crossed >= 2) {
            score += 2;
        } else if (crossed == 1) {
            score += 1;
        }
        if (recentlyChanged(startX, startY, startZ, targetX, targetY, targetZ, sectionChange)) {
            score += 1;
        }
        return score;
    }

    static boolean withinBlocks(int ax, int ay, int az, int bx, int by, int bz, int blocks) {
        return Math.abs(ax - bx) <= blocks && Math.abs(ay - by) <= blocks && Math.abs(az - bz) <= blocks;
    }

    private static int otherBuildings(int startX, int startY, int startZ, int targetX, int targetY, int targetZ,
                                      BuildingSpan[] buildings) {
        if (buildings == null || buildings.length == 0) {
            return 0;
        }
        long startId = buildingAt(startX, startY, startZ, buildings);
        long targetId = buildingAt(targetX, targetY, targetZ, buildings);
        int steps = Math.max(1, Math.max(Math.abs(targetX - startX), Math.abs(targetZ - startZ)));
        long first = Long.MIN_VALUE;
        long second = Long.MIN_VALUE;
        int found = 0;
        for (int i = 8; i < steps; i += 8) {
            int x = startX + (targetX - startX) * i / steps;
            int y = startY + (targetY - startY) * i / steps;
            int z = startZ + (targetZ - startZ) * i / steps;
            long id = buildingAt(x, y, z, buildings);
            if (id == Long.MIN_VALUE || id == startId || id == targetId) {
                continue;
            }
            if (found == 0) {
                first = id;
                found = 1;
            } else if (id != first) {
                second = id;
                found = 2;
                break;
            }
        }
        return second == Long.MIN_VALUE ? found : 2;
    }

    private static long buildingAt(int x, int y, int z, BuildingSpan[] buildings) {
        for (BuildingSpan building : buildings) {
            if (building != null && building.contains(x, y, z)) {
                return building.id();
            }
        }
        return Long.MIN_VALUE;
    }

    private static boolean recentlyChanged(int startX, int startY, int startZ, int targetX, int targetY, int targetZ,
                                           SectionChange sectionChange) {
        if (sectionChange == null) {
            return false;
        }
        int steps = Math.max(Math.abs(targetX - startX), Math.abs(targetZ - startZ));
        for (int i = 0; i <= steps; i += 8) {
            int x = steps == 0 ? startX : startX + (targetX - startX) * i / steps;
            int y = steps == 0 ? startY : startY + (targetY - startY) * i / steps;
            int z = steps == 0 ? startZ : startZ + (targetZ - startZ) * i / steps;
            if (sectionChange.changedRecently(Math.floorDiv(x, 16), Math.floorDiv(y, 16), Math.floorDiv(z, 16))) {
                return true;
            }
        }
        return sectionChange.changedRecently(Math.floorDiv(targetX, 16), Math.floorDiv(targetY, 16), Math.floorDiv(targetZ, 16));
    }

    record BuildingSpan(long id, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        boolean contains(int x, int y, int z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }
    }

    interface SectionChange {
        boolean changedRecently(int sectionX, int sectionY, int sectionZ);
    }
}
