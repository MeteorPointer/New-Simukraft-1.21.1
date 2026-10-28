package common.cn.nsimu.simukraft.path;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathSampleModelTest {
    @Test
    void tierKeepsHysteresis() {
        assertEquals(PathSampleModel.Tier.NARROW, PathSampleModel.advanceTier(PathSampleModel.Tier.STANDARD, 49));
        assertEquals(PathSampleModel.Tier.NARROW, PathSampleModel.advanceTier(PathSampleModel.Tier.NARROW, 40));
        assertEquals(PathSampleModel.Tier.STANDARD, PathSampleModel.advanceTier(PathSampleModel.Tier.NARROW, 32));
        assertEquals(PathSampleModel.Tier.STANDARD, PathSampleModel.advanceTier(PathSampleModel.Tier.WIDE, 17));
        assertEquals(PathSampleModel.Tier.WIDE, PathSampleModel.advanceTier(PathSampleModel.Tier.STANDARD, 12));
    }

    @Test
    void complexityRaisesPaddingWithoutPassingSixteen() {
        assertEquals(8, PathSampleModel.paddingForScore(0, PathSampleModel.Tier.NARROW));
        assertEquals(12, PathSampleModel.paddingForScore(1, PathSampleModel.Tier.NARROW));
        assertEquals(16, PathSampleModel.paddingForScore(3, PathSampleModel.Tier.NARROW));
        assertEquals(16, PathSampleModel.raisePadding(12));
        assertEquals(16, PathSampleModel.raisePadding(16));
    }

    @Test
    void nearbyDoorsShareACorridorAndOppositeStreetsDoNot() {
        long east = PathSampleModel.corridorKey(2, 2, 8, 70);
        long shiftedDoor = PathSampleModel.corridorKey(5, 3, 9, 72);
        long otherStreet = PathSampleModel.corridorKey(40, 2, 48, 70);
        assertEquals(east, shiftedDoor);
        assertNotEquals(east, otherStreet);
    }

    @Test
    void middleBuildingCountsAndOwnHousesDoNot() {
        PathSampleModel.BuildingSpan home = new PathSampleModel.BuildingSpan(1L, 0, 64, 0, 8, 70, 8);
        PathSampleModel.BuildingSpan shop = new PathSampleModel.BuildingSpan(2L, 0, 64, 40, 8, 70, 48);
        PathSampleModel.BuildingSpan work = new PathSampleModel.BuildingSpan(3L, 0, 64, 80, 8, 70, 88);
        int score = PathSampleModel.complexity(2, 65, 2, 4, 65, 84, new PathSampleModel.BuildingSpan[]{home, shop, work}, null);
        assertEquals(1, score);
        int flat = PathSampleModel.complexity(2, 65, 2, 4, 65, 84, new PathSampleModel.BuildingSpan[]{home, work}, null);
        assertEquals(0, flat);
        assertTrue(PathSampleModel.withinBlocks(1, 65, 2, 3, 66, 4, 2));
    }
}
