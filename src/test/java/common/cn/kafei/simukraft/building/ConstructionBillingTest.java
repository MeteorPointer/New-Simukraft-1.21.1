package common.cn.kafei.simukraft.building;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConstructionBillingTest {
    @Test
    void perBlockCostSplitsJsonPriceAcrossNbtBlocks() {
        assertEquals(2.5D, ConstructionBilling.perBlockCost(10.0D, 4));
        assertEquals(10.0D, ConstructionBilling.costThrough(10.0D, 4, 4));
        assertEquals(7.5D, ConstructionBilling.costThrough(10.0D, 4, 3));
    }

    @Test
    void chargeIntervalFollowsBuilderLevel() {
        assertEquals(20, ConstructionBilling.chargeIntervalTicks(1));
        assertEquals(20, ConstructionBilling.chargeIntervalTicks(4));
        assertEquals(20, ConstructionBilling.chargeIntervalTicks(9));
        assertEquals(100, ConstructionBilling.chargeIntervalTicks(10));
        assertEquals(400, ConstructionBilling.chargeIntervalTicks(15));
        assertEquals(1200, ConstructionBilling.chargeIntervalTicks(20));
    }
}
