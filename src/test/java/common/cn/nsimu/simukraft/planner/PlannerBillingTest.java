package common.cn.nsimu.simukraft.planner;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlannerBillingTest {
    @Test
    void cost_chargesEachTargetBlockAtTheConfiguredRate() {
        assertEquals(0.36D, PlannerBilling.cost(18, 0.02D));
        assertEquals(0.36D, PlannerBilling.cost(9, 0.04D));
        assertEquals(2000.0D, PlannerBilling.cost(100_000, 0.02D));
    }

    @Test
    void cost_roundsHalfUpToCents() {
        assertEquals(0.03D, PlannerBilling.cost(1, 0.025D));
        assertEquals(0.02D, PlannerBilling.cost(1, 0.02D));
    }

    @Test
    void cost_isZeroWhenNothingIsBillable() {
        assertEquals(0.0D, PlannerBilling.cost(0, 0.02D));
        assertEquals(0.0D, PlannerBilling.cost(-4, 0.02D));
        assertEquals(0.0D, PlannerBilling.cost(18, 0.0D));
        assertEquals(0.0D, PlannerBilling.cost(18, -1.0D));
        assertEquals(0.0D, PlannerBilling.cost(18, Double.NaN));
    }
}
