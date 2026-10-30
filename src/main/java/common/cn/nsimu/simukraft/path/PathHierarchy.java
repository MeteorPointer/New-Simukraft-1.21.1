package common.cn.nsimu.simukraft.path;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.Arrays;

/**
 * GDC 式空间分级。第 0 级是 16³ 区段，第 1 级是整块区块柱，第 2 级是地面高度差不超过 1 格的连通平坦块。
 * 不读世界。方块变化后由缓存丢掉对应区块柱。
 */
final class PathHierarchy {
    static final int BLOCK = 16;
    static final int EDGE = 8;
    static final int CONNECT_Y = 1;

    enum Level {
        SECTION,
        BLOCK,
        REGION
    }

    private final Long2LongOpenHashMap parent = new Long2LongOpenHashMap();
    private final Long2IntOpenHashMap surface = new Long2IntOpenHashMap();

    /**
     * columns：直线穿过的区块柱。离区块边不足 8 格时带上外侧那一柱，左右余量仍落在采样里。
     */
    static long[] columns(int startX, int startZ, int targetX, int targetZ) {
        LongOpenHashSet found = new LongOpenHashSet();
        int steps = Math.max(Math.abs(targetX - startX), Math.abs(targetZ - startZ));
        int lastX = Integer.MIN_VALUE;
        int lastZ = Integer.MIN_VALUE;
        for (int i = 0; i <= steps; i++) {
            int x = steps == 0 ? startX : startX + (targetX - startX) * i / steps;
            int z = steps == 0 ? startZ : startZ + (targetZ - startZ) * i / steps;
            int sectionX = Math.floorDiv(x, BLOCK);
            int sectionZ = Math.floorDiv(z, BLOCK);
            if (sectionX != lastX || sectionZ != lastZ) {
                found.add(column(sectionX, sectionZ));
                lastX = sectionX;
                lastZ = sectionZ;
            }
            addEdge(found, x, z);
        }
        long[] columns = found.toLongArray();
        Arrays.sort(columns);
        return columns;
    }

    static boolean connects(int surfaceY, int neighborY) {
        return Math.abs(surfaceY - neighborY) <= CONNECT_Y;
    }

    static long column(int sectionX, int sectionZ) {
        return (((long) sectionX) << 32) ^ (sectionZ & 0xFFFFFFFFL);
    }

    static int sectionX(long column) {
        return (int) (column >> 32);
    }

    static int sectionZ(long column) {
        return (int) column;
    }

    /**
     * note：记下这块平坦街区的地面高度，并和高度差不超过 1 格的邻居并成同一级。
     */
    void note(long column, int surfaceY) {
        parent.putIfAbsent(column, column);
        surface.put(column, surfaceY);
        link(column, sectionX(column) - 1, sectionZ(column), surfaceY);
        link(column, sectionX(column) + 1, sectionZ(column), surfaceY);
        link(column, sectionX(column), sectionZ(column) - 1, surfaceY);
        link(column, sectionX(column), sectionZ(column) + 1, surfaceY);
    }

    /**
     * forget：方块变化后拆掉这一柱，避免旧的连通关系继续复用。
     */
    void forget(int sectionX, int sectionZ) {
        long column = column(sectionX, sectionZ);
        parent.remove(column);
        surface.remove(column);
        long[] keys = parent.keySet().toLongArray();
        for (long key : keys) {
            if (parent.get(key) == column) {
                parent.put(key, key);
            }
        }
    }

    void clear() {
        parent.clear();
        surface.clear();
    }

    long region(long column) {
        return parent.containsKey(column) ? find(column) : column;
    }

    Level level(long column) {
        if (!surface.containsKey(column)) {
            return Level.SECTION;
        }
        long region = find(column);
        int sectionX = sectionX(column);
        int sectionZ = sectionZ(column);
        if (same(region, sectionX - 1, sectionZ) || same(region, sectionX + 1, sectionZ)
                || same(region, sectionX, sectionZ - 1) || same(region, sectionX, sectionZ + 1)) {
            return Level.REGION;
        }
        return Level.BLOCK;
    }

    private boolean same(long region, int sectionX, int sectionZ) {
        long neighbor = column(sectionX, sectionZ);
        return surface.containsKey(neighbor) && find(neighbor) == region;
    }

    private void link(long column, int sectionX, int sectionZ, int surfaceY) {
        long neighbor = column(sectionX, sectionZ);
        if (!surface.containsKey(neighbor) || !connects(surfaceY, surface.get(neighbor))) {
            return;
        }
        long left = find(column);
        long right = find(neighbor);
        if (left != right) {
            parent.put(right, left);
        }
    }

    private long find(long column) {
        long root = column;
        while (parent.containsKey(root) && parent.get(root) != root) {
            root = parent.get(root);
        }
        long cursor = column;
        while (cursor != root) {
            long next = parent.get(cursor);
            parent.put(cursor, root);
            cursor = next;
        }
        return root;
    }

    private static void addEdge(LongOpenHashSet found, int x, int z) {
        int sectionX = Math.floorDiv(x, BLOCK);
        int sectionZ = Math.floorDiv(z, BLOCK);
        int localX = Math.floorMod(x, BLOCK);
        int localZ = Math.floorMod(z, BLOCK);
        found.add(column(sectionX + (localX < EDGE ? -1 : 1), sectionZ));
        found.add(column(sectionX, sectionZ + (localZ < EDGE ? -1 : 1)));
    }
}
