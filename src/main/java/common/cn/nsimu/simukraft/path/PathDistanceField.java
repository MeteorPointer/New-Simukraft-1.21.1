package common.cn.nsimu.simukraft.path;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * PathDistanceField: 从目标沿现有邻居规则反向铺开的距离场。每个人顺着父指针走回自己的路点。
 */
final class PathDistanceField {
    private static final int MAX_ITERATIONS = 20000;
    private final long goalKey;
    private final PathSectionStamp stamp;
    private final LongOpenHashSet reached;
    private final Long2LongOpenHashMap parents;
    private final Long2ByteOpenHashMap leaveModes;

    private PathDistanceField(long goalKey, PathSectionStamp stamp, LongOpenHashSet reached,
                               Long2LongOpenHashMap parents, Long2ByteOpenHashMap leaveModes) {
        this.goalKey = goalKey;
        this.stamp = stamp;
        this.reached = reached;
        this.parents = parents;
        this.leaveModes = leaveModes;
    }

    PathSectionStamp stamp() {
        return stamp;
    }

    /**
     * build: 用和单人 A* 相同的边，从目标往外记下每个格子走向目标的父格和移动模式。
     */
    /**
     * build: 把步进跑完。测试和需要整场结果的调用走这里。
     */
    static PathDistanceField build(PathRequest request, PathSnapshot snapshot, PathSectionStamp stamp) {
        Build build = Build.start(request, snapshot, stamp);
        if (build == null) {
            return null;
        }
        while (!build.finished()) {
            build.step(MAX_ITERATIONS);
        }
        return build.toField();
    }

    /**
     * Build: 可暂停的距离场。每步只扩展有限个格子，避免一次占满工人线程。
     */
    static final class Build {
        private final PathRequest request;
        private final PathSnapshot snapshot;
        private final PathSectionStamp stamp;
        private final PathCell goal;
        private final LongOpenHashSet reached = new LongOpenHashSet();
        private final Long2DoubleOpenHashMap best = new Long2DoubleOpenHashMap();
        private final Long2LongOpenHashMap parents = new Long2LongOpenHashMap();
        private final Long2ByteOpenHashMap leaveModes = new Long2ByteOpenHashMap();
        private final PriorityQueue<FieldNode> open = new PriorityQueue<>(Comparator.comparingDouble(node -> node.cost));
        private int iterations;

        private Build(PathRequest request, PathSnapshot snapshot, PathSectionStamp stamp, PathCell goal) {
            this.request = request;
            this.snapshot = snapshot;
            this.stamp = stamp;
            this.goal = goal;
            reached.add(goal.key());
            best.put(goal.key(), 0.0D);
            open.add(new FieldNode(goal.key(), goal, 0.0D));
        }

        /**
         * start: 目标附近没有可走格时返回 null。
         */
        static Build start(PathRequest request, PathSnapshot snapshot, PathSectionStamp stamp) {
            if (request == null || snapshot == null || stamp == null) {
                return null;
            }
            PathCell goal = HybridPathfinder.nearestCell(snapshot, request.targetBlockPos(), HybridPathfinder.NEAREST_RANGE);
            if (goal == null) {
                return null;
            }
            return new Build(request, snapshot, stamp, goal);
        }

        /**
         * step: 最多扩展 budget 个节点。
         */
        void step(int budget) {
            int used = 0;
            while (!open.isEmpty() && used < budget && iterations < MAX_ITERATIONS) {
                used++;
                iterations++;
                FieldNode current = open.poll();
                if (current.cost > best.get(current.key)) {
                    continue;
                }
                relaxPredecessors(request, snapshot, goal, current, reached, best, parents, leaveModes, open);
            }
        }

        /**
         * finished: 队列空了，或达到扩展上限。
         */
        boolean finished() {
            return open.isEmpty() || iterations >= MAX_ITERATIONS;
        }

        /**
         * toField: 把当前已扩展的父指针收成距离场。没跑完也可以用，接不上的人会退回单人寻路。
         */
        PathDistanceField toField() {
            return new PathDistanceField(goal.key(), stamp, reached, parents, leaveModes);
        }
    }

    /**
     * reconstruct: 从这次请求的起点沿着父指针走到目标，再做和单人路径一样的平滑。
     */
    PathResult reconstruct(PathRequest request, PathSnapshot snapshot) {
        PathCell start = HybridPathfinder.nearestCell(snapshot, request.startPos(), HybridPathfinder.NEAREST_RANGE);
        PathCell goal = HybridPathfinder.nearestCell(snapshot, request.targetBlockPos(), HybridPathfinder.NEAREST_RANGE);
        if (start == null || goal == null || goal.key() != goalKey || !reached.contains(start.key())) {
            return PathResult.failed(request, "field_miss");
        }
        if (start.key() == goalKey) {
            return PathResult.success(request, List.of(PathWaypoint.of(start, start.defaultMode(request.intent()))));
        }
        List<PathWaypoint> raw = new ArrayList<>();
        raw.add(PathWaypoint.of(start, start.defaultMode(request.intent())));
        long cursor = start.key();
        int guard = 0;
        while (cursor != goalKey) {
            if (!parents.containsKey(cursor) || !leaveModes.containsKey(cursor) || ++guard > 4096) {
                return PathResult.failed(request, "field_miss");
            }
            MovementMode mode = MovementMode.values()[leaveModes.get(cursor)];
            cursor = parents.get(cursor);
            PathCell next = snapshot.cell(BlockPos.getX(cursor), BlockPos.getY(cursor), BlockPos.getZ(cursor));
            if (next == null) {
                return PathResult.failed(request, "field_miss");
            }
            raw.add(PathWaypoint.of(next, mode));
        }
        return PathResult.success(request, HybridPathfinder.smooth(snapshot, raw));
    }

    private static void relaxPredecessors(PathRequest request, PathSnapshot snapshot, PathCell goal, FieldNode current,
                                           LongOpenHashSet reached, Long2DoubleOpenHashMap best,
                                           Long2LongOpenHashMap parents, Long2ByteOpenHashMap leaveModes,
                                           PriorityQueue<FieldNode> open) {
        for (int dy = -1; dy <= 3; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    PathCell source = resolveSource(snapshot, goal, request.intent(),
                            current.cell.x() + dx, current.cell.y() + dy, current.cell.z() + dz);
                    if (source == null || source.key() == current.key) {
                        continue;
                    }
                    HybridPathfinder.Neighbor edge = edgeInto(snapshot, source, goal, request.intent(), current.key);
                    if (edge == null) {
                        continue;
                    }
                    double nextCost = current.cost + edge.cost()
                            + PathExtraCosts.total(request.citizenId(), request.dimensionId(), current.cell.pos());
                    if (reached.contains(source.key()) && best.get(source.key()) <= nextCost) {
                        continue;
                    }
                    reached.add(source.key());
                    best.put(source.key(), nextCost);
                    parents.put(source.key(), current.key);
                    leaveModes.put(source.key(), (byte) edge.mode().ordinal());
                    open.add(new FieldNode(source.key(), source, nextCost));
                }
            }
        }
    }

    /**
     * edgeInto: 源格正向邻居里指向当前格的那条边。邻居列表是线程临时缓冲，这里先复制。
     */
    private static HybridPathfinder.Neighbor edgeInto(PathSnapshot snapshot, PathCell source, PathCell goal,
                                                       MovementIntent intent, long currentKey) {
        HybridPathfinder.Neighbor found = null;
        for (HybridPathfinder.Neighbor neighbor : copyNeighbors(snapshot, source, goal, intent)) {
            if (neighbor.cell().key() != currentKey) {
                continue;
            }
            if (found == null || neighbor.cost() < found.cost()) {
                found = neighbor;
            }
        }
        return found;
    }

    private static List<HybridPathfinder.Neighbor> copyNeighbors(PathSnapshot snapshot, PathCell source, PathCell goal,
                                                                  MovementIntent intent) {
        return new ArrayList<>(HybridPathfinder.neighbors(snapshot, source, goal, intent));
    }

    /**
     * resolveSource: 普通格子直接取。梯子顶上的虚拟落点不在格子表里，从下一格的邻居里找回来。
     */
    private static PathCell resolveSource(PathSnapshot snapshot, PathCell goal, MovementIntent intent, int x, int y, int z) {
        PathCell direct = snapshot.cell(x, y, z);
        if (direct != null) {
            return direct;
        }
        PathCell below = snapshot.cell(x, y - 1, z);
        if (below == null || !below.climbable()) {
            return null;
        }
        for (HybridPathfinder.Neighbor neighbor : copyNeighbors(snapshot, below, goal, intent)) {
            if (neighbor.cell().x() == x && neighbor.cell().y() == y && neighbor.cell().z() == z) {
                return neighbor.cell();
            }
        }
        return null;
    }

    private static final class FieldNode {
        private final long key;
        private final PathCell cell;
        private final double cost;

        private FieldNode(long key, PathCell cell, double cost) {
            this.key = key;
            this.cell = cell;
            this.cost = cost;
        }
    }
}
