package common.cn.kafei.simukraft.building;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConstructionBillingTest {
    @Test
    void perBlockCostSplitsJsonPriceAcrossNbtBlocks() {
        assertEquals(2.5D, ConstructionBilling.perBlockCost(10.0D, 4));
        assertEquals(10.0D, ConstructionBilling.costThrough(10.0D, 4, 4));
        assertEquals(7.5D, ConstructionBilling.costThrough(10.0D, 4, 3));
    }

    @Test
    void centsThrough_keepsSubCentBlocksUntilTheyAddUp() {
        assertEquals(0L, ConstructionBilling.centsThrough(10.0D, 50_000, 1));
        assertEquals(1L, ConstructionBilling.centsThrough(10.0D, 50_000, 50));
        assertEquals(1000L, ConstructionBilling.centsThrough(10.0D, 50_000, 50_000));
        long paid = 0L;
        int billed = 0;
        for (int processed = 1; processed <= 50_000; processed++) {
            paid += ConstructionBilling.centsThrough(10.0D, 50_000, processed)
                    - ConstructionBilling.centsThrough(10.0D, 50_000, billed);
            billed = processed;
        }
        assertEquals(1000L, paid);
    }

    @Test
    void chargeIntervalFollowsBuilderLevel() {
        assertTrue(ConstructionBilling.chargePerPlacedBlock(1));
        assertTrue(ConstructionBilling.chargePerPlacedBlock(4));
        assertFalse(ConstructionBilling.chargePerPlacedBlock(5));
        assertFalse(ConstructionBilling.chargePerPlacedBlock(20));
        assertEquals(20, ConstructionBilling.chargeIntervalTicks(5));
        assertEquals(20, ConstructionBilling.chargeIntervalTicks(20));
    }
}
