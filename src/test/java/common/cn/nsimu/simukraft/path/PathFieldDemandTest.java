package common.cn.nsimu.simukraft.path;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathFieldDemandTest {
    private static final PathSnapshotBuilder.SnapshotBounds BOUNDS =
            new PathSnapshotBuilder.SnapshotBounds(0, 32, 0, 32, 60, 72);

    @Test
    void requiresThreeDistinctCitizensWhoseStartsFitTheSharedSnapshot() {
        PathFieldDemand demand = new PathFieldDemand();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        demand.observe(first, new BlockPos(2, 64, 2), 10L);
        demand.observe(first, new BlockPos(3, 64, 3), 11L);
        demand.observe(second, new BlockPos(4, 64, 4), 12L);
        assertFalse(demand.canBuild(BOUNDS));

        demand.observe(UUID.randomUUID(), new BlockPos(5, 64, 5), 13L);
        assertTrue(demand.canBuild(BOUNDS));
        assertFalse(demand.canBuild(new PathSnapshotBuilder.SnapshotBounds(0, 4, 0, 4, 60, 72)));
    }

    @Test
    void expiresOldDemandBeforeCountingNewRequests() {
        PathFieldDemand demand = new PathFieldDemand();
        demand.observe(UUID.randomUUID(), new BlockPos(2, 64, 2), 10L);
        demand.observe(UUID.randomUUID(), new BlockPos(3, 64, 3), 11L);
        demand.observe(UUID.randomUUID(), new BlockPos(4, 64, 4), 112L);

        assertFalse(demand.canBuild(BOUNDS));
        assertFalse(demand.expired(112L));
        assertTrue(demand.expired(113L + PathFieldDemand.WINDOW_TICKS));
    }

    @Test
    void boundsStoredCitizenStarts() {
        PathFieldDemand demand = new PathFieldDemand();

        for (int index = 0; index < 17; index++) {
            UUID citizenId = UUID.randomUUID();
            demand.observe(citizenId, new BlockPos(2, 64, 2), index);
        }

        assertEquals(16, demand.requesterCount());
        assertTrue(demand.canBuild(BOUNDS));
    }
}
