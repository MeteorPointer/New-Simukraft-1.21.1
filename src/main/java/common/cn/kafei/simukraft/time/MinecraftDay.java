package common.cn.kafei.simukraft.time;

/** MinecraftDay：把原版 dayTime 换成游戏日，并在 /time set 回退时平移已记录的日期。 */
public final class MinecraftDay {
    public static final long TICKS_PER_DAY = 24_000L;

    private MinecraftDay() {
    }

    /** index：dayTime 对应的游戏日。/time set day 会把这个值直接打回 0。 */
    public static long index(long dayTime) {
        return Math.floorDiv(dayTime, TICKS_PER_DAY);
    }

    /**
     * shift：日号回退了 delta 天时，把档案里的日期减去同样的天数。
     * 小于 minimum 的哨兵值（例如未吃饭的 -1）保持不动。
     */
    public static long shift(long recordedDay, long deltaDays, long minimum) {
        if (deltaDays <= 0L || recordedDay < minimum) {
            return recordedDay;
        }
        return Math.max(minimum, recordedDay - deltaDays);
    }
}
