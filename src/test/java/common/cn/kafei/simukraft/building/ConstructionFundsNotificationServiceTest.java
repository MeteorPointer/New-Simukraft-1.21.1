package common.cn.kafei.simukraft.building;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConstructionFundsNotificationServiceTest {
    @Test
    void successfulChargeWarnsOnlyWhenWorkRemainsAndBalanceIsGone() {
        assertTrue(ConstructionFundsNotificationService.shouldWarnAfterSuccessfulCharge(true, 0.0D));
        assertFalse(ConstructionFundsNotificationService.shouldWarnAfterSuccessfulCharge(true, 0.01D));
        assertFalse(ConstructionFundsNotificationService.shouldWarnAfterSuccessfulCharge(false, 0.0D));
        assertFalse(ConstructionFundsNotificationService.shouldWarnAfterSuccessfulCharge(false, 12.5D));
    }

    @Test
    void cooldownMatchesMaterialWarningGate() {
        assertFalse(ConstructionFundsNotificationService.isCoolingDown(null, 100L));
        assertTrue(ConstructionFundsNotificationService.isCoolingDown(120L, 100L));
        assertFalse(ConstructionFundsNotificationService.isCoolingDown(100L, 100L));
        assertFalse(ConstructionFundsNotificationService.isCoolingDown(99L, 100L));
        assertEquals(120L, ConstructionFundsNotificationService.nextAllowedTick(100L, 20));
        assertEquals(120L, ConstructionFundsNotificationService.nextAllowedTick(100L, 1));
        assertEquals(500L, ConstructionFundsNotificationService.nextAllowedTick(100L, 400));
    }
}
