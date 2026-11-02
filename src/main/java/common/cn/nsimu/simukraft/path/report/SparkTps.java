package common.cn.nsimu.simukraft.path.report;

import java.util.ArrayDeque;

/**
 * Spark 的 5 秒 TPS。两次 tick 结束的间隔里，一拍记 1，再按间隔加权，最后不超过 20。
 * 窗口一开始按 5 秒满速填充，和 Spark 的滚动平均一样。
 */
final class SparkTps {
    static final long NANOS_PER_SECOND = 1_000_000_000L;
    static final double WINDOW_NANOS = 5.0D * NANOS_PER_SECOND;
    private final ArrayDeque<double[]> spans = new ArrayDeque<>();
    private double padNanos = WINDOW_NANOS;
    private double padCredit = 20.0D * WINDOW_NANOS;
    private double realNanos;
    private double realCredit;

    /**
     * instant：这一拍如果持续下去的每秒 tick 数，不封顶。满速 50ms 是 20，100ms 是 10。
     */
    static double instant(long nanos) {
        if (nanos <= 0L) {
            return 20.0D;
        }
        return (double) NANOS_PER_SECOND / nanos;
    }

    /**
     * add：记入一次 tick 间隔，返回当前 5 秒 TPS。
     */
    double add(long nanos) {
        double credit = (double) NANOS_PER_SECOND;
        spans.add(new double[]{nanos, credit});
        realNanos += nanos;
        realCredit += credit;
        double overflow = realNanos + padNanos - WINDOW_NANOS;
        if (overflow > 0.0D && padNanos > 0.0D) {
            double cut = Math.min(padNanos, overflow);
            padNanos -= cut;
            padCredit -= 20.0D * cut;
        }
        while (realNanos > WINDOW_NANOS && spans.size() > 1) {
            double[] old = spans.removeFirst();
            realNanos -= old[0];
            realCredit -= old[1];
        }
        double totalNanos = realNanos + Math.max(0.0D, padNanos);
        if (totalNanos <= 0.0D) {
            return 20.0D;
        }
        return Math.min(20.0D, (realCredit + Math.max(0.0D, padCredit)) / totalNanos);
    }

    double average() {
        double totalNanos = realNanos + Math.max(0.0D, padNanos);
        if (totalNanos <= 0.0D) {
            return 20.0D;
        }
        return Math.min(20.0D, (realCredit + Math.max(0.0D, padCredit)) / totalNanos);
    }
}
