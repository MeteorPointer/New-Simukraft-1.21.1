package common.cn.kafei.simukraft.time;

import common.cn.kafei.simukraft.citizen.CitizenData;

/** CitizenCalendar：时间回退时平移居民身上的游戏日，避免怀孕、产后和住院吃饭停住。 */
public final class CitizenCalendar {
    private CitizenCalendar() {
    }

    /** shiftDays：返回值表示档案是否被改过。 */
    public static boolean shiftDays(CitizenData citizen, long deltaDays) {
        if (citizen == null || deltaDays <= 0L) {
            return false;
        }
        boolean changed = false;
        if (citizen.bornDay() > 0L) {
            citizen.setBornDay(MinecraftDay.shift(citizen.bornDay(), deltaDays, 0L));
            changed = true;
        }
        if (citizen.pregnantSince() > 0L) {
            citizen.setPregnantSince(MinecraftDay.shift(citizen.pregnantSince(), deltaDays, 0L));
            changed = true;
        }
        if (citizen.lastAgeGrowthDay() >= 0L) {
            citizen.setLastAgeGrowthDay(MinecraftDay.shift(citizen.lastAgeGrowthDay(), deltaDays, -1L));
            changed = true;
        }
        changed |= citizen.medical().shiftDays(deltaDays);
        return changed;
    }
}
