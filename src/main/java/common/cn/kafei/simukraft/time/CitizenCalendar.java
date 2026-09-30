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
        if (citizen.bornDay() != 0L) {
            long shifted = citizen.bornDay() - deltaDays;
            citizen.setBornDay(shifted == 0L ? -1L : shifted);
            changed = true;
        }
        if (citizen.pregnant()) {
            citizen.setPregnantSince(citizen.pregnantSince() - deltaDays);
            changed = true;
        }
        if (citizen.lastAgeGrowthDay() != -1L) {
            citizen.setLastAgeGrowthDay(MinecraftDay.shiftReal(citizen.lastAgeGrowthDay(), deltaDays, -1L));
            changed = true;
        }
        changed |= citizen.medical().shiftDays(deltaDays);
        return changed;
    }
}
