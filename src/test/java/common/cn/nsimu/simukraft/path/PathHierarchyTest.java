package common.cn.nsimu.simukraft.path;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathHierarchyTest {
    @Test
    void sameStreetSharesBlockColumnsAndAParallelStreetDoesNot() {
        long[] door = PathHierarchy.columns(2, 64, 8, 80);
        long[] shifted = PathHierarchy.columns(5, 66, 9, 82);
        long[] other = PathHierarchy.columns(40, 64, 48, 80);
        assertTrue(shares(door, shifted));
        assertTrue(!shares(door, other));
    }

    @Test
    void flatColumnsStaySmallerThanAPaddedRectangle() {
        long[] columns = PathHierarchy.columns(0, 0, 20, 0);
        assertTrue(columns.length <= 6);
        assertTrue(contains(columns, 0, 0));
        assertTrue(contains(columns, 1, 0));
    }

    @Test
    void connectedFlatBlocksMergeAndACliffStaysApart() {
        PathHierarchy hierarchy = new PathHierarchy();
        long street = PathHierarchy.column(0, 0);
        long next = PathHierarchy.column(1, 0);
        long cliff = PathHierarchy.column(0, 1);
        hierarchy.note(street, 64);
        hierarchy.note(next, 65);
        hierarchy.note(cliff, 70);
        assertEquals(hierarchy.region(street), hierarchy.region(next));
        assertEquals(PathHierarchy.Level.REGION, hierarchy.level(street));
        assertNotEquals(hierarchy.region(street), hierarchy.region(cliff));
        assertEquals(PathHierarchy.Level.BLOCK, hierarchy.level(cliff));
        hierarchy.forget(0, 0);
        assertEquals(PathHierarchy.Level.SECTION, hierarchy.level(street));
    }

    private static boolean shares(long[] left, long[] right) {
        for (long column : left) {
            for (long other : right) {
                if (column == other) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean contains(long[] columns, int sectionX, int sectionZ) {
        long column = PathHierarchy.column(sectionX, sectionZ);
        for (long candidate : columns) {
            if (candidate == column) {
                return true;
            }
        }
        return false;
    }
}
