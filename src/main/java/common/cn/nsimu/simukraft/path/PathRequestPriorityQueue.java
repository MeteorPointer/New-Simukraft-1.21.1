package common.cn.nsimu.simukraft.path;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 按最近玩家距离维护待寻路市民的优先级，避免只从队首抽样而漏掉近处请求。 */
final class PathRequestPriorityQueue {
    private static final Comparator<Entry> ORDER = Comparator
            .comparingDouble(Entry::distanceSqr)
            .thenComparingLong(Entry::sequence);

    private final List<Entry> heap = new ArrayList<>();
    private final Map<UUID, Integer> positions = new HashMap<>();
    private final List<UUID> refreshOrder = new ArrayList<>();
    private final Map<UUID, Integer> refreshPositions = new HashMap<>();
    private int refreshCursor;
    private int staleRefreshSlots;
    private long nextSequence;

    void offer(UUID citizenId, double distanceSqr) {
        if (citizenId == null) {
            return;
        }
        double priority = Double.isFinite(distanceSqr) && distanceSqr >= 0.0D
                ? distanceSqr
                : Double.MAX_VALUE;
        Integer existingIndex = positions.get(citizenId);
        if (existingIndex != null) {
            Entry previous = heap.get(existingIndex);
            double oldPriority = previous.distanceSqr;
            previous.distanceSqr = priority;
            if (priority < oldPriority) {
                siftUp(existingIndex);
            } else if (priority > oldPriority) {
                siftDown(existingIndex);
            }
            return;
        }
        Entry entry = new Entry(citizenId, priority, nextSequence++);
        positions.put(citizenId, heap.size());
        refreshPositions.put(citizenId, refreshOrder.size());
        refreshOrder.add(citizenId);
        heap.add(entry);
        siftUp(heap.size() - 1);
    }

    UUID poll() {
        if (heap.isEmpty()) {
            return null;
        }
        Entry entry = heap.get(0);
        positions.remove(entry.citizenId());
        untrackForRefresh(entry.citizenId());
        Entry last = heap.remove(heap.size() - 1);
        if (!heap.isEmpty()) {
            heap.set(0, last);
            positions.put(last.citizenId(), 0);
            siftDown(0);
        }
        return entry.citizenId();
    }

    boolean remove(UUID citizenId) {
        Integer index = positions.remove(citizenId);
        if (index == null) {
            return false;
        }
        untrackForRefresh(citizenId);
        Entry last = heap.remove(heap.size() - 1);
        if (index < heap.size()) {
            heap.set(index, last);
            positions.put(last.citizenId(), index);
            if (index > 0 && ORDER.compare(last, heap.get(parent(index))) < 0) {
                siftUp(index);
            } else {
                siftDown(index);
            }
        }
        return true;
    }

    int size() {
        return heap.size();
    }

    /** 轮转读取待刷新距离的市民，调用方每 tick 只需处理固定数量。 */
    UUID nextForPriorityRefresh() {
        int slots = refreshOrder.size();
        while (slots-- > 0 && !refreshOrder.isEmpty()) {
            if (refreshCursor >= refreshOrder.size()) {
                refreshCursor = 0;
            }
            UUID citizenId = refreshOrder.get(refreshCursor++);
            if (citizenId != null && refreshPositions.containsKey(citizenId)) {
                return citizenId;
            }
        }
        return null;
    }

    private void untrackForRefresh(UUID citizenId) {
        Integer index = refreshPositions.remove(citizenId);
        if (index == null) {
            return;
        }
        refreshOrder.set(index, null);
        staleRefreshSlots++;
        if (refreshCursor < 0 || refreshCursor >= refreshOrder.size()) {
            refreshCursor = 0;
        }
        if (staleRefreshSlots >= 64 && staleRefreshSlots * 2 >= refreshOrder.size()) {
            compactRefreshOrder();
        }
    }

    private void compactRefreshOrder() {
        List<UUID> compacted = new ArrayList<>(refreshPositions.size());
        refreshPositions.clear();
        for (UUID citizenId : refreshOrder) {
            if (citizenId != null && positions.containsKey(citizenId)) {
                refreshPositions.put(citizenId, compacted.size());
                compacted.add(citizenId);
            }
        }
        refreshOrder.clear();
        refreshOrder.addAll(compacted);
        refreshCursor = 0;
        staleRefreshSlots = 0;
    }

    private void siftUp(int index) {
        Entry entry = heap.get(index);
        while (index > 0) {
            int parent = parent(index);
            Entry parentEntry = heap.get(parent);
            if (ORDER.compare(entry, parentEntry) >= 0) {
                break;
            }
            heap.set(index, parentEntry);
            positions.put(parentEntry.citizenId(), index);
            index = parent;
        }
        heap.set(index, entry);
        positions.put(entry.citizenId(), index);
    }

    private void siftDown(int index) {
        Entry entry = heap.get(index);
        int size = heap.size();
        while (true) {
            int left = index * 2 + 1;
            if (left >= size) {
                break;
            }
            int right = left + 1;
            int smaller = right < size && ORDER.compare(heap.get(right), heap.get(left)) < 0
                    ? right
                    : left;
            if (ORDER.compare(entry, heap.get(smaller)) <= 0) {
                break;
            }
            Entry child = heap.get(smaller);
            heap.set(index, child);
            positions.put(child.citizenId(), index);
            index = smaller;
        }
        heap.set(index, entry);
        positions.put(entry.citizenId(), index);
    }

    private static int parent(int index) {
        return (index - 1) >>> 1;
    }

    private static final class Entry {
        private final UUID citizenId;
        private final long sequence;
        private double distanceSqr;

        private Entry(UUID citizenId, double distanceSqr, long sequence) {
            this.citizenId = citizenId;
            this.distanceSqr = distanceSqr;
            this.sequence = sequence;
        }

        UUID citizenId() {
            return citizenId;
        }

        double distanceSqr() {
            return distanceSqr;
        }

        long sequence() {
            return sequence;
        }
    }
}
