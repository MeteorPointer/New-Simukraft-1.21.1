package common.cn.nsimu.simukraft.citizen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PregnancyStageTest {
    @Test
    void threeDayPregnancyUsesEarlyMiddleLateStages() {
        assertEquals(PregnancyStage.EARLY, PregnancyStage.resolve(0L, 3));
        assertEquals(PregnancyStage.MIDDLE, PregnancyStage.resolve(1L, 3));
        assertEquals(PregnancyStage.LATE, PregnancyStage.resolve(2L, 3));
        assertEquals(PregnancyStage.LATE, PregnancyStage.resolve(3L, 3));
    }

    @Test
    void durationIsClampedToThreeDays() {
        assertEquals(PregnancyStage.MIDDLE, PregnancyStage.resolve(1L, 8));
        assertEquals(PregnancyStage.LATE, PregnancyStage.resolve(2L, 8));
        assertEquals(PregnancyStage.NONE, PregnancyStage.resolve(-1L, 3));
    }
}
