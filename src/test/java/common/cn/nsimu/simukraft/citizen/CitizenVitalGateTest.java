package common.cn.nsimu.simukraft.citizen;

import common.cn.nsimu.simukraft.medical.DiseaseType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CitizenVitalGateTest {
    @Test
    void unloadedDaysDoNotAgeSickenTreatOrKill() {
        CitizenData citizen = new CitizenData(UUID.randomUUID());
        citizen.setAge(79);
        citizen.setLifespan(80);
        citizen.setLastAgeGrowthDay(10L);
        citizen.setDisease(DiseaseType.FLU, 10L);
        citizen.medical().addDiseaseTreatmentTicks(120L);
        citizen.setHealth(14.0D);
        citizen.setPregnant(true);
        citizen.setPregnantSince(8L);
        citizen.medical().setPostpartumUntilDay(12L);
        citizen.medical().setLastHospitalProgressDayTime(50_000L);

        assertTrue(CitizenVitalGate.holdUnloadedDays(citizen, 15L));

        assertEquals(79, citizen.age());
        assertEquals(80, citizen.lifespan());
        assertFalse(citizen.dead());
        assertEquals(DiseaseType.FLU, citizen.disease());
        assertEquals(120L, citizen.medical().diseaseTreatmentTicks());
        assertEquals(14.0D, citizen.health());
        assertEquals(25L, citizen.lastAgeGrowthDay());
        assertEquals(25L, citizen.medical().diseaseSinceDay());
        assertEquals(23L, citizen.pregnantSince());
        assertEquals(27L, citizen.medical().postpartumUntilDay());
        assertEquals(0L, citizen.medical().lastHospitalProgressDayTime());
        assertEquals(0L, NpcGrowthService.completedYears(citizen.lastAgeGrowthDay(), 25L, 4L));
    }

    @Test
    void unloadedCitizenCannotContractDisease() {
        assertFalse(CitizenVitalGate.mayContractDisease(false, false, false));
        assertFalse(CitizenVitalGate.mayContractDisease(true, true, false));
        assertFalse(CitizenVitalGate.mayContractDisease(true, false, true));
        assertTrue(CitizenVitalGate.mayContractDisease(true, false, false));
    }

    @Test
    void healthyCitizenWithoutAnchorsIsLeftUntouched() {
        CitizenData citizen = new CitizenData(UUID.randomUUID());

        assertFalse(CitizenVitalGate.holdUnloadedDays(citizen, 3L));
        assertEquals(-1L, citizen.lastAgeGrowthDay());
        assertEquals(DiseaseType.NONE, citizen.disease());
        assertFalse(citizen.dead());
    }
}
