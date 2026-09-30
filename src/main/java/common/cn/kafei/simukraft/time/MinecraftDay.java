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
     * shiftReal：真实日期减去回退天数。
     * 结果如果正好落到未设置哨兵上，再往前一天，避免把“第 0 天之后”收成“从未发生”。
     */
    public static long shiftReal(long recordedDay, long deltaDays, long unset) {
        if (deltaDays <= 0L || recordedDay == unset) {
            return recordedDay;
        }
        long shifted = recordedDay - deltaDays;
        return shifted == unset ? unset - 1L : shifted;
    }
}
