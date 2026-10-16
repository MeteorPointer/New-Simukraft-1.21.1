package common.cn.kafei.simukraft.citizen;

import common.cn.kafei.simukraft.entity.CitizenEntity;
import net.minecraft.server.level.ServerLevel;

/**
 * 区块没在 tick 这个市民时，疾病、治疗、传染、死亡和倒计时都停住。
 * 只平移已经写在档案里的日期锚点，不新发病，不改年龄，不改生死。
 */
public final class CitizenVitalGate {
    private CitizenVitalGate() {
    }

    /** entityIsTicking：实体在场，并且所在位置正在做实体 tick。没加载的区块返回 false。 */
    public static boolean entityIsTicking(ServerLevel level, CitizenData citizen) {
        if (level == null || citizen == null || citizen.dead()) {
            return false;
        }
        if (!level.dimension().location().toString().equals(citizen.dimensionId())) {
            return false;
        }
        CitizenEntity entity = CitizenTeleportService.findCitizenEntity(level, citizen.uuid());
        return entity != null
                && entity.isAlive()
                && !entity.isRemoved()
                && level.isPositionEntityTicking(entity.blockPosition());
    }

    /** mayContractDisease：只有正在 tick 的健康市民才会进入当日患病抽取。 */
    public static boolean mayContractDisease(boolean entityTicking, boolean dead, boolean alreadySick) {
        return entityTicking && !dead && !alreadySick;
    }

    /**
     * holdUnloadedDays：把年龄、妊娠、发病和产后的日期锚点整体后移。
     * 治疗进度和病情种类不动，住院时间锚点清掉，避免重新加载后把卸载区间一次性结算。
     */
    public static boolean holdUnloadedDays(CitizenData citizen, long days) {
        if (citizen == null || citizen.dead() || days <= 0L) {
            return false;
        }
        boolean changed = false;
        if (citizen.lastAgeGrowthDay() != -1L) {
            citizen.setLastAgeGrowthDay(citizen.lastAgeGrowthDay() + days);
            changed = true;
        }
        if (citizen.pregnant()) {
            citizen.setPregnantSince(citizen.pregnantSince() + days);
            changed = true;
        }
        if (citizen.childGrowthDueDay() > 0L) {
            citizen.setChildGrowthDueDay(citizen.childGrowthDueDay() + days);
            changed = true;
        }
        changed |= citizen.medical().postponeDiseaseAnchor(days);
        if (citizen.medical().postpartumUntilDay() > 0L) {
            citizen.medical().setPostpartumUntilDay(citizen.medical().postpartumUntilDay() + days);
            changed = true;
        }
        changed |= pauseTreatmentClock(citizen);
        return changed;
    }

    /** pauseTreatmentClock：清掉住院治疗的世界时间锚点，卸载期间不加治疗 tick、不回血。 */
    public static boolean pauseTreatmentClock(CitizenData citizen) {
        if (citizen == null || citizen.medical().lastHospitalProgressDayTime() == 0L) {
            return false;
        }
        citizen.medical().setLastHospitalProgressDayTime(0L);
        return true;
    }
}
