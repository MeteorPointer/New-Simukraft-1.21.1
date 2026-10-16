package common.cn.nsimu.simukraft.citizen;

import common.cn.nsimu.simukraft.medical.DiseaseType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CitizenServiceTest {
    @Test
    void sickAndPregnantCitizensAreNotHireable() {
        CitizenData citizen = new CitizenData(UUID.randomUUID());
        assertTrue(CitizenService.isHireable(citizen));

        citizen.setDisease(DiseaseType.COLD, 1L);
        assertFalse(CitizenService.isHireable(citizen));

        citizen.clearDisease();
        citizen.setPregnant(true);
        assertFalse(CitizenService.isHireable(citizen));
    }
}
