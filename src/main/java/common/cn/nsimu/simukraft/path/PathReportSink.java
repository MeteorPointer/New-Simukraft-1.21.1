package common.cn.nsimu.simukraft.path;

/**
 * 寻路耗时的窄出口。报告模块没打开时，调用只读一次引用。
 */
public final class PathReportSink {
    private static final Listener NONE = new Listener() {
    };
    private static volatile Listener listener = NONE;

    private PathReportSink() {
    }

    public static void setListener(Listener next) {
        listener = next == null ? NONE : next;
    }

    public static boolean active() {
        return listener != NONE;
    }

    public static void time(String path, long nanos) {
        Listener current = listener;
        if (current != NONE && nanos > 0L) {
            current.time(path, nanos);
        }
    }

    public static void count(String path, long amount) {
        Listener current = listener;
        if (current != NONE && amount != 0L) {
            current.count(path, amount);
        }
    }

    public static void gauge(String path, long value) {
        Listener current = listener;
        if (current != NONE) {
            current.gauge(path, value);
        }
    }

    public static void label(String path, String value) {
        Listener current = listener;
        if (current != NONE && value != null) {
            current.label(path, value);
        }
    }

    /**
     * 报告模块实现这个接口。寻路包不依赖报告的存储和导出。
     */
    public interface Listener {
        default void time(String path, long nanos) {
        }

        default void count(String path, long amount) {
        }

        default void gauge(String path, long value) {
        }

        default void label(String path, String value) {
        }
    }
}
