package common.cn.nsimu.simukraft.path;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathRequestPriorityQueueTest {
    @Test
    void choosesNearestAcrossWholeQueueInsteadOfInsertionOrder() {
        PathRequestPriorityQueue queue = new PathRequestPriorityQueue();
        UUID far = UUID.randomUUID();
        UUID near = UUID.randomUUID();
        UUID middle = UUID.randomUUID();

        queue.offer(far, 400.0D);
        queue.offer(near, 4.0D);
        queue.offer(middle, 100.0D);

        assertEquals(near, queue.poll());
        assertEquals(middle, queue.poll());
        assertEquals(far, queue.poll());
        assertNull(queue.poll());
    }

    @Test
    void selectsNearestCitizensFromTwoThousandQueuedRequests() {
        PathRequestPriorityQueue queue = new PathRequestPriorityQueue();
        List<UUID> citizensByDistance = new ArrayList<>(2000);
        for (int distance = 1999; distance >= 0; distance--) {
            UUID citizenId = UUID.randomUUID();
            citizensByDistance.add(citizenId);
            queue.offer(citizenId, (double) distance * distance);
        }

        for (int distance = 0; distance < 24; distance++) {
            assertEquals(citizensByDistance.get(1999 - distance), queue.poll());
        }
        assertEquals(1976, queue.size());
    }

    @Test
    void updatesPriorityAndKeepsOnlyOneEntryPerCitizen() {
        PathRequestPriorityQueue queue = new PathRequestPriorityQueue();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        queue.offer(first, 100.0D);
        queue.offer(second, 50.0D);
        queue.offer(first, 1.0D);

        assertEquals(2, queue.size());
        assertEquals(first, queue.poll());
        assertEquals(second, queue.poll());
    }

    @Test
    void updatesPriorityWhenCitizenMovesFartherFromThePlayer() {
        PathRequestPriorityQueue queue = new PathRequestPriorityQueue();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        queue.offer(first, 1.0D);
        queue.offer(second, 2.0D);
        queue.offer(first, 100.0D);

        assertEquals(second, queue.poll());
        assertEquals(first, queue.poll());
    }

    @Test
    void refreshedNearbyCitizenCanCutAheadOfOlderRequests() {
        PathRequestPriorityQueue queue = new PathRequestPriorityQueue();
        UUID movingNear = UUID.randomUUID();
        UUID alreadyNear = UUID.randomUUID();

        queue.offer(movingNear, 900.0D);
        queue.offer(alreadyNear, 100.0D);

        assertEquals(movingNear, queue.nextForPriorityRefresh());
        queue.offer(movingNear, 1.0D);

        assertEquals(movingNear, queue.poll());
        assertEquals(alreadyNear, queue.poll());
    }

    @Test
    void removedCitizenIsSkippedByThePriorityRefreshCursor() {
        PathRequestPriorityQueue queue = new PathRequestPriorityQueue();
        UUID removed = UUID.randomUUID();
        UUID remaining = UUID.randomUUID();

        queue.offer(removed, 1.0D);
        queue.offer(remaining, 2.0D);
        assertTrue(queue.remove(removed));

        assertEquals(remaining, queue.nextForPriorityRefresh());
        assertEquals(remaining, queue.nextForPriorityRefresh());
    }

    @Test
    void removingAnEarlierRefreshEntryDoesNotRepeatTheCurrentEntry() {
        PathRequestPriorityQueue queue = new PathRequestPriorityQueue();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();

        queue.offer(first, 1.0D);
        queue.offer(second, 2.0D);
        queue.offer(third, 3.0D);
        assertEquals(first, queue.nextForPriorityRefresh());
        assertEquals(second, queue.nextForPriorityRefresh());
        assertTrue(queue.remove(first));

        assertEquals(third, queue.nextForPriorityRefresh());
    }

    @Test
    void removingAnEntryPreservesHeapOrder() {
        PathRequestPriorityQueue queue = new PathRequestPriorityQueue();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        UUID fourth = UUID.randomUUID();
        UUID fifth = UUID.randomUUID();
        UUID sixth = UUID.randomUUID();
        UUID seventh = UUID.randomUUID();

        queue.offer(first, 1.0D);
        queue.offer(second, 2.0D);
        queue.offer(third, 3.0D);
        queue.offer(fourth, 4.0D);
        queue.offer(fifth, 5.0D);
        queue.offer(sixth, 6.0D);
        queue.offer(seventh, 7.0D);

        assertTrue(queue.remove(second));
        assertEquals(first, queue.poll());
        assertEquals(third, queue.poll());
        assertEquals(fourth, queue.poll());
        assertEquals(fifth, queue.poll());
        assertEquals(sixth, queue.poll());
        assertEquals(seventh, queue.poll());
        assertEquals(0, queue.size());
    }
}
