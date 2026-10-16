package common.cn.nsimu.simukraft.citizen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HabitationRelocationTest {
    @Test
    void extraVacantBedsDoNotOutrankAnExactFit() {
        assertEquals(1.0D, match(2, 2));
        assertEquals(1.0D, match(6, 2));
        assertEquals(0.5D, match(1, 2));
    }

    private static double match(int vacant, int expected) {
        if (vacant < expected) {
            return expected > 0 ? (double) vacant / expected : 0.0D;
        }
        return 1.0D;
    }
}
