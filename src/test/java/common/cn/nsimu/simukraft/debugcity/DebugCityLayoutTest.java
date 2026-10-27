package common.cn.nsimu.simukraft.debugcity;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DebugCityLayoutTest {
    @Test
    void growsTheGridUntilEveryCitizenHasAPlot() {
        assertTrue(DebugCityLayout.sideLength(64, 8) >= 8);
        assertTrue(DebugCityLayout.sideLength(10, 32) >= 32);
        assertEquals(64, DebugCityLayout.occupiedCells(64, 8).size());
    }

    @Test
    void givesEachCitizenAUniquePlotAwayFromTheCore() {
        List<DebugCityLayout.Cell> cells = DebugCityLayout.occupiedCells(64, 8);
        int center = DebugCityLayout.centerChunk(DebugCityLayout.sideLength(64, 8));
        Set<Long> seen = new HashSet<>();
        assertEquals(64, cells.size());
        for (DebugCityLayout.Cell cell : cells) {
            assertTrue(seen.add((((long) cell.chunkX()) << 32) ^ (cell.chunkZ() & 0xFFFFFFFFL)));
            assertFalse(cell.chunkX() == center && cell.chunkZ() == center);
        }
    }

    @Test
    void rotatesIndustrialCommercialAndPublicJobs() {
        assertEquals(DebugCityLayout.WorkplaceKind.INDUSTRIAL, DebugCityLayout.workplaceKind(0));
        assertEquals(DebugCityLayout.WorkplaceKind.COMMERCIAL, DebugCityLayout.workplaceKind(1));
        assertEquals(DebugCityLayout.WorkplaceKind.MEDICAL, DebugCityLayout.workplaceKind(2));
        assertEquals(DebugCityLayout.WorkplaceKind.BANK, DebugCityLayout.workplaceKind(5));
        assertEquals(DebugCityLayout.WorkplaceKind.EXCHANGE, DebugCityLayout.workplaceKind(8));
        assertEquals(DebugCityLayout.WorkplaceKind.GLOBAL_MARKET, DebugCityLayout.workplaceKind(11));
    }

    @Test
    void putsWorkOnAnotherBlockWithinOneHundred() {
        int side = DebugCityLayout.sideLength(64, 8);
        List<DebugCityLayout.Cell> homes = DebugCityLayout.occupiedCells(64, 8);
        Set<Long> homeIds = new HashSet<>();
        Set<Long> workIds = new HashSet<>();
        boolean east = false;
        boolean west = false;
        for (DebugCityLayout.Cell home : homes) {
            DebugCityLayout.Cell work = DebugCityLayout.workCell(home, side);
            assertTrue(homeIds.add(cellId(home)));
            assertTrue(workIds.add(cellId(work)));
            assertEquals(home.chunkZ(), work.chunkZ());
            assertEquals(1, Math.abs(work.chunkX() - home.chunkX()));
            if (work.chunkX() > home.chunkX()) {
                east = true;
            } else {
                west = true;
            }
            int dx = DebugCityLayout.worldX(work, DebugCityLayout.WORK_LOCAL_X)
                    - DebugCityLayout.worldX(home, DebugCityLayout.HOUSE_LOCAL_X);
            int dz = DebugCityLayout.worldZ(work, DebugCityLayout.WORK_LOCAL_Z)
                    - DebugCityLayout.worldZ(home, DebugCityLayout.HOUSE_LOCAL_Z);
            assertTrue(Math.hypot(dx, dz) < 100.0D);
        }
        assertTrue(east && west);
        homeIds.retainAll(workIds);
        assertTrue(homeIds.isEmpty());
    }

    @Test
    void keepsHouseAndWorkplaceOffTheRoad() {
        DebugCityLayout.Cell cell = new DebugCityLayout.Cell(3, -2);
        assertFootprintClear(cell, DebugCityLayout.HOUSE_LOCAL_X, DebugCityLayout.HOUSE_LOCAL_Z);
        assertFootprintClear(cell, DebugCityLayout.WORK_LOCAL_X, DebugCityLayout.WORK_LOCAL_Z);
        assertTrue(DebugCityLayout.HOUSE_LOCAL_X + DebugCityLayout.PLOT_SIZE <= DebugCityLayout.WORK_LOCAL_X);
    }

    private static long cellId(DebugCityLayout.Cell cell) {
        return (((long) cell.chunkX()) << 32) ^ (cell.chunkZ() & 0xFFFFFFFFL);
    }

    private static void assertFootprintClear(DebugCityLayout.Cell cell, int localX, int localZ) {
        for (int dx = 0; dx < DebugCityLayout.PLOT_SIZE; dx++) {
            for (int dz = 0; dz < DebugCityLayout.PLOT_SIZE; dz++) {
                int worldX = DebugCityLayout.worldX(cell, localX + dx);
                int worldZ = DebugCityLayout.worldZ(cell, localZ + dz);
                assertFalse(DebugCityLayout.isRoad(worldX, worldZ));
            }
        }
    }
}
