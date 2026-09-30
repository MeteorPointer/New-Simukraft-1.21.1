package common.cn.kafei.simukraft.time;

import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.medical.DiseaseType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CitizenCalendarTest {
    @Test
    void shiftingDaysKeepsPregnancyAndPostpartumRelativeToTheNewCalendar() {
        CitizenData citizen = new CitizenData(UUID.randomUUID());
        citizen.setBornDay(12L);
        citizen.setPregnant(true);
        citizen.setPregnantSince(38L);
        citizen.setLastAgeGrowthDay(40L);
        citizen.medical().setPostpartumUntilDay(45L);
        citizen.medical().setLastHospitalMealDay(40L);
        citizen.setDisease(DiseaseType.COLD, 39L);
        citizen.medical().addDiseaseTreatmentTicks(80L);

        assertTrue(CitizenCalendar.shiftDays(citizen, 40L));

        assertEquals(-28L, citizen.bornDay());
        assertEquals(-2L, citizen.pregnantSince());
        assertEquals(0L, citizen.lastAgeGrowthDay());
        assertEquals(5L, citizen.medical().postpartumUntilDay());
        assertEquals(0L, citizen.medical().lastHospitalMealDay());
        assertEquals(-1L, citizen.medical().diseaseSinceDay());
        assertEquals(80L, citizen.medical().diseaseTreatmentTicks());
    }

    @Test
    void dayZeroPregnancyKeepsElapsedDays() {
        CitizenData citizen = new CitizenData(UUID.randomUUID());
        citizen.setPregnant(true);
        citizen.setPregnantSince(0L);
        citizen.setLastAgeGrowthDay(0L);
        citizen.medical().setLastHospitalMealDay(0L);

        assertTrue(CitizenCalendar.shiftDays(citizen, 40L));

        assertEquals(-40L, citizen.pregnantSince());
        assertEquals(-40L, citizen.lastAgeGrowthDay());
        assertEquals(-40L, citizen.medical().lastHospitalMealDay());
    }

    @Test
    void unsetMealDayStaysUnset() {
        CitizenData citizen = new CitizenData(UUID.randomUUID());

        CitizenCalendar.shiftDays(citizen, 3L);

        assertEquals(-1L, citizen.medical().lastHospitalMealDay());
    }
}
