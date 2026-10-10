package common.cn.kafei.simukraft.util;

/** TypewriterReveal: 按时间把整句裁成逐字显示。 */
public final class TypewriterReveal {
    private TypewriterReveal() {
    }

    public static String reveal(String full, long elapsedMillis, int millisPerChar) {
        if (full == null || full.isEmpty()) {
            return "";
        }
        int step = Math.max(1, millisPerChar);
        int count = Math.min(full.length(), 1 + (int) Math.max(0L, elapsedMillis) / step);
        return full.substring(0, count);
    }

    public static int millisPerChar(int length, int totalMillis) {
        return Math.max(30, totalMillis / Math.max(1, length));
    }
}
