package common.cn.nsimu.simukraft.citizen;

import java.util.Locale;

/**
 * 妊娠阶段，按妊娠持续日数的三段规则计算。
 */
public enum PregnancyStage {
    NONE,
    EARLY,
    MIDDLE,
    LATE;

    /**
     * resolve：按已怀孕天数和总天数计算阶段。
     */
    public static PregnancyStage resolve(long elapsedDays, int durationDays) {
        if (elapsedDays < 0L) {
            return NONE;
        }
        int duration = Math.max(1, Math.min(3, durationDays));
        long earlyEnd = Math.max(1L, (duration + 2L) / 3L);
        long lateStart = Math.max(0L, duration - Math.max(1L, (duration + 2L) / 3L));
        if (elapsedDays < earlyEnd && elapsedDays < lateStart) {
            return EARLY;
        }
        if (elapsedDays < lateStart) {
            return MIDDLE;
        }
        return LATE;
    }

    /**
     * translationKey：返回阶段翻译键。
     */
    public String translationKey() {
        return "pregnancy." + name().toLowerCase(Locale.ROOT);
    }
}
