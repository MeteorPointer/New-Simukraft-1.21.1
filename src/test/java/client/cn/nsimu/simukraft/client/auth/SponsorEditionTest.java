package client.cn.nsimu.simukraft.client.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SponsorEditionTest {
    @Test
    void treatsEitherSponsorSignalAsSponsorEdition() {
        assertFalse(SponsorEdition.decide(false, false));
        assertTrue(SponsorEdition.decide(true, false));
        assertTrue(SponsorEdition.decide(false, true));
        assertTrue(SponsorEdition.decide(true, true));
    }
}
