package common.cn.nsimu.simukraft.path.report;

import common.cn.nsimu.simukraft.path.PathReportSink;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 按路径累计寻路耗时、次数和瞬时量。只在服务端线程写入。
 * 折线在 time("tick") 时拍下这一拍，子项和计数要写在它前面。
 */
public final class PathReportSession implements PathReportSink.Listener {
    private final long startedAt = System.nanoTime();
    private final Map<String, Node> nodes = new LinkedHashMap<>();
    private final Map<String, Long> tickTimes = new LinkedHashMap<>();
    private final Map<String, Long> tickCounts = new LinkedHashMap<>();
    private final Map<String, String> tickLabels = new LinkedHashMap<>();
    private final List<Sample> samples = new ArrayList<>();
    private long ticks;

    @Override
    public void time(String path, long nanos) {
        node(path).addTime(nanos);
        tickTimes.merge(path, nanos, Long::sum);
        if ("tick".equals(path)) {
            ticks++;
            samples.add(snapshot(System.nanoTime() - startedAt));
            tickTimes.clear();
            tickCounts.clear();
            tickLabels.clear();
        }
    }

    @Override
    public void label(String path, String value) {
        if (value != null) {
            tickLabels.put(path, value);
        }
    }

    @Override
    public void count(String path, long amount) {
        node(path).addCount(amount);
        tickCounts.merge(path, amount, Long::sum);
    }

    @Override
    public void gauge(String path, long value) {
        node(path).observeGauge(value);
    }

    public long ticks() {
        return ticks;
    }

    public long elapsedNanos() {
        return System.nanoTime() - startedAt;
    }

    public List<Sample> samples() {
        return List.copyOf(samples);
    }

    private Sample snapshot(long offsetNanos) {
        return new Sample(offsetNanos / 1_000_000L,
                millis(tickTimes.getOrDefault("tick", 0L)),
                millis(tickTimes.getOrDefault("tick/queue/sample/capture", 0L)),
                millis(tickTimes.getOrDefault("tick/queue/sample", 0L)),
                millis(tickTimes.getOrDefault("tick/queue", 0L)),
                millis(tickTimes.getOrDefault("async/search", 0L)),
                millis(tickTimes.getOrDefault("tick/activePaths", 0L)),
                millis(tickTimes.getOrDefault("tick/doors", 0L)),
                millis(tickTimes.getOrDefault("tick/field", 0L)),
                millis(tickTimes.getOrDefault("tick/applyCompleted", 0L)),
                gaugeLast("state/queue"),
                gaugeLast("state/active"),
                gaugeLast("state/pending"),
                gaugeLast("state/padding"),
                tickCounts.getOrDefault("flow/freshSections", 0L),
                tickCounts.getOrDefault("flow/cacheHits", 0L),
                tickCounts.getOrDefault("flow/examined", 0L),
                tickCounts.getOrDefault("flow/waiting", 0L),
                tickCounts.getOrDefault("flow/failures", 0L),
                tickCounts.getOrDefault("flow/started", 0L),
                tickCounts.getOrDefault("flow/sampleMiss", 0L),
                tickCounts.getOrDefault("flow/budgetHold", 0L),
                tickCounts.getOrDefault("flow/blocked", 0L),
                tickCounts.getOrDefault("flow/completed", 0L),
                tickCounts.getOrDefault("flow/rejected", 0L),
                tickCounts.getOrDefault("flow/notTicking", 0L),
                tickLabels.getOrDefault("dimension", ""));
    }

    private static double millis(long nanos) {
        return nanos / 1_000_000.0D;
    }

    private long gaugeLast(String path) {
        Node node = nodes.get(path);
        return node == null ? 0L : node.gaugeLast;
    }

    /**
     * rows：按 Spark 的树形表展开。子项耗时从父项里扣掉，得到自身占用。
     */
    public List<Row> rows() {
        Map<String, Row> rows = new LinkedHashMap<>();
        for (Map.Entry<String, Node> entry : nodes.entrySet()) {
            rows.put(entry.getKey(), Row.from(entry.getKey(), entry.getValue()));
        }
        List<String> linked = new ArrayList<>();
        for (Row row : rows.values()) {
            int slash = row.name().lastIndexOf('/');
            if (slash > 0) {
                Row parent = rows.get(row.name().substring(0, slash));
                if (parent != null) {
                    parent.children.add(row);
                    parent.childNanos += row.totalNanos;
                    linked.add(row.name());
                }
            }
        }
        long tickNanos = rows.containsKey("tick") ? rows.get("tick").totalNanos : 0L;
        List<Row> roots = new ArrayList<>();
        for (Row row : rows.values()) {
            if (!linked.contains(row.name())) {
                roots.add(row);
            }
        }
        roots.sort(Comparator.comparingInt(row -> rank(row.name())));
        List<Row> flat = new ArrayList<>();
        for (Row root : roots) {
            flatten(root, 0, tickNanos, flat);
        }
        return flat;
    }

    private static int rank(String name) {
        if ("tick".equals(name) || name.startsWith("tick/")) {
            return 0;
        }
        if (name.startsWith("async")) {
            return 1;
        }
        if (name.startsWith("state")) {
            return 2;
        }
        if (name.startsWith("flow")) {
            return 3;
        }
        return 4;
    }

    private static void flatten(Row row, int depth, long tickNanos, List<Row> out) {
        row.depth = depth;
        row.percent = tickNanos <= 0L || !row.name().startsWith("tick") ? 0.0D : row.totalNanos * 100.0D / tickNanos;
        out.add(row);
        for (Row child : row.children) {
            flatten(child, depth + 1, tickNanos, out);
        }
    }

    private Node node(String path) {
        return nodes.computeIfAbsent(path, key -> new Node());
    }

    private static final class Node {
        private long totalNanos;
        private long timeCount;
        private long counter;
        private long gaugeLast;
        private long gaugeSum;
        private long gaugeCount;

        private void addTime(long nanos) {
            totalNanos += nanos;
            timeCount++;
        }

        private void addCount(long amount) {
            counter += amount;
        }

        private void observeGauge(long value) {
            gaugeLast = value;
            gaugeSum += value;
            gaugeCount++;
        }
    }

    /**
     * 一行报告。total 含子项，self 是扣掉子项后的自身占用。
     */
    public static final class Row {
        private final String name;
        private final long totalNanos;
        private final long timeCount;
        private final long counter;
        private final long gaugeLast;
        private final long gaugeSum;
        private final long gaugeCount;
        private final List<Row> children = new ArrayList<>();
        private long childNanos;
        private int depth;
        private double percent;

        private Row(String name, long totalNanos, long timeCount, long counter, long gaugeLast, long gaugeSum, long gaugeCount) {
            this.name = name;
            this.totalNanos = totalNanos;
            this.timeCount = timeCount;
            this.counter = counter;
            this.gaugeLast = gaugeLast;
            this.gaugeSum = gaugeSum;
            this.gaugeCount = gaugeCount;
        }

        private static Row from(String name, Node node) {
            return new Row(name, node.totalNanos, node.timeCount, node.counter, node.gaugeLast, node.gaugeSum, node.gaugeCount);
        }

        public String name() {
            return name;
        }

        public int depth() {
            return depth;
        }

        public long totalNanos() {
            return totalNanos;
        }

        public long selfNanos() {
            return Math.max(0L, totalNanos - childNanos);
        }

        public long count() {
            return timeCount > 0L ? timeCount : counter > 0L ? counter : gaugeCount;
        }

        public double percent() {
            return percent;
        }

        public long gaugeLast() {
            return gaugeLast;
        }

        public double gaugeAverage() {
            return gaugeCount == 0L ? 0.0D : gaugeSum / (double) gaugeCount;
        }
    }

    /**
     * 一拍的瓶颈读数，供折线图按时间段筛选。
     */
    public record Sample(long offsetMs, double tickMs, double captureMs, double sampleMs, double queueMs, double searchMs,
                         double activeMs, double doorsMs, double fieldMs, double completedMs,
                         long queueSize, long active, long pending, long padding,
                         long freshSections, long cacheHits, long examined, long waiting, long failures,
                         long started, long sampleMiss, long budgetHold, long blocked, long completedSearches,
                         long rejected, long notTicking, String dimension) {
    }
}
