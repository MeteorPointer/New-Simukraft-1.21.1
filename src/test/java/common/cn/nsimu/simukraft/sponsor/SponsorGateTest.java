package common.cn.nsimu.simukraft.sponsor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SponsorGateTest {
    @AfterEach
    void reset() {
        SponsorGate.resetForTests();
    }

    @Test
    void defaultsToDenyAndUsesInstalledResolver() {
        assertFalse(SponsorGate.allowed("sponsor"));
        assertThrows(SponsorAccessDeniedException.class, () -> SponsorGate.require("sponsor"));

        SponsorGate.installResolver("sponsor"::equals);
        assertTrue(SponsorGate.allowed(" SPONSOR "));
        SponsorGate.require("sponsor");
        assertFalse(SponsorGate.allowed("experimental"));
    }
}
