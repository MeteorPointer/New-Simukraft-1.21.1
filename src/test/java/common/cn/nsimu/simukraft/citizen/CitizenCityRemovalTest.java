package common.cn.nsimu.simukraft.citizen;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A city deletion removes the database row immediately. An entity still stored in an unloaded
 * chunk must stay rejected when that chunk loads again, instead of being recreated as a ghost.
 */
class CitizenCityRemovalTest {
    @Test
    void removedCitizenStaysRejected() {
        CitizenManager manager = new CitizenManager();
        UUID citizenId = UUID.randomUUID();
        manager.rememberRemovedForTest(citizenId);

        assertTrue(manager.wasRemoved(citizenId));
        assertFalse(manager.wasRemoved(UUID.randomUUID()));
    }
}
