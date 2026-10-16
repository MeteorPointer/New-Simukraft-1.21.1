package common.cn.nsimu.simukraft.medical;

import common.cn.nsimu.simukraft.citizen.CitizenData;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MedicalMealServiceTest {
    @Test
    void mealWindowStartsAtNoonAndEndsBeforeAfternoon() {
        assertFalse(MedicalMealService.isMealTime(5_999L));
        assertTrue(MedicalMealService.isMealTime(6_000L));
        assertTrue(MedicalMealService.isMealTime(7_999L));
        assertFalse(MedicalMealService.isMealTime(8_000L));
        assertTrue(MedicalMealService.isMealTime(30_000L));
    }

    @Test
    void admittedPatientReceivesAtMostOneHospitalMealPerDay() {
        CitizenData patient = new CitizenData(UUID.randomUUID());
        patient.medical().setMedicalBedPoiId(UUID.randomUUID());

        assertTrue(MedicalMealService.needsMeal(patient, 0L));
        patient.medical().setLastHospitalMealDay(0L);
        assertFalse(MedicalMealService.needsMeal(patient, 0L));
        assertTrue(MedicalMealService.needsMeal(patient, 1L));
        patient.medical().setMedicalBedPoiId(null);
        assertFalse(MedicalMealService.needsMeal(patient, 1L));
    }
}
