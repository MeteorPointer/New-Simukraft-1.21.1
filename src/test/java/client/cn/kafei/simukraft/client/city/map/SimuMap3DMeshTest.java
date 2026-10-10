package client.cn.kafei.simukraft.client.city.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SimuMap3DMeshTest {
    @Test
    void lodStep_getsFinerWhenZoomingIn() {
        assertEquals(1, SimuMap3DMesh.lodStep(4.0D));
        assertEquals(2, SimuMap3DMesh.lodStep(2.2D));
        assertEquals(4, SimuMap3DMesh.lodStep(1.2D));
        assertEquals(8, SimuMap3DMesh.lodStep(0.6D));
    }

    @Test
    void lodStep_holdsUntilZoomClearlyCrossesThreshold() {
        assertEquals(2, SimuMap3DMesh.lodStepWithHysteresis(3.3D, 2));
        assertEquals(1, SimuMap3DMesh.lodStepWithHysteresis(3.5D, 2));
        assertEquals(1, SimuMap3DMesh.lodStepWithHysteresis(3.1D, 1));
        assertEquals(2, SimuMap3DMesh.lodStepWithHysteresis(2.9D, 1));
    }

    @Test
    void snapCoord_alignsToStepIncludingNegatives() {
        assertEquals(10, SimuMap3DMesh.snapCoord(13, 5));
        assertEquals(-10, SimuMap3DMesh.snapCoord(-6, 5));
        assertEquals(0, SimuMap3DMesh.snapCoord(3, 8));
    }

    @Test
    void pickCellHeight_usesTallestBlockInTheStepWindow() {
        SimuMap3DMesh.Source source = new FlatSource() {
            @Override
            public int height(int worldX, int worldZ) {
                return worldX == 2 && worldZ == 1 ? 80 : 64;
            }
        };
        assertEquals(80, SimuMap3DMesh.pickCellHeight(source, 0, 0, 4));
        assertEquals(64, SimuMap3DMesh.pickCellHeight(source, 4, 0, 4));
    }

    @Test
    void rebuild_keepsBuildingTallerAndDifferentColorFromGrass() {
        SimuMap3DMesh mesh = new SimuMap3DMesh();
        mesh.rebuild(new BuildingSource(), 20, 20, 1, 0L);

        int buildingIx = (10 - mesh.worldXOf(0)) / 1;
        int buildingIz = (10 - mesh.worldZOf(0)) / 1;
        int grassIx = (16 - mesh.worldXOf(0)) / 1;
        int grassIz = (16 - mesh.worldZOf(0)) / 1;

        assertEquals(80, mesh.heightAt(buildingIx, buildingIz));
        assertEquals(64, mesh.heightAt(grassIx, grassIz));
        assertTrue(((mesh.colorAt(buildingIx, buildingIz) >> 16) & 0xFF)
                > ((mesh.colorAt(grassIx, grassIz) >> 16) & 0xFF));
        assertTrue(mesh.faceCount() < SimuMap3DMesh.MAX_CELLS * SimuMap3DMesh.MAX_CELLS);
        assertTrue(mesh.faceCount() > 4);
    }

    @Test
    void rebuild_mergesFlatGrassIntoFewFaces() {
        SimuMap3DMesh mesh = new SimuMap3DMesh();
        mesh.rebuild(new FlatSource(), 0, 0, 1, 0L);
        assertTrue(mesh.faceCount() < 500, "flat grass should greedy-merge, faces=" + mesh.faceCount());
        assertTrue(mesh.faceCount() > 80, "greedy span is capped so the field is not one clipped quad, faces=" + mesh.faceCount());
    }

    @Test
    void needsRebuild_ignoresSmallPansAndZoomScale() {
        SimuMap3DMesh mesh = new SimuMap3DMesh();
        mesh.rebuild(new FlatSource(), 20, 20, 1, 0L);

        assertFalse(mesh.needsRebuild(20, 20, 1, 0L));
        assertFalse(mesh.needsRebuild(28, 20, 1, 0L));
        assertTrue(mesh.needsRebuild(29, 20, 1, 0L));
        assertTrue(mesh.needsRebuild(20, 20, 2, 0L));
        assertTrue(mesh.needsRebuild(20, 20, 1, SimuMap3DMesh.CACHE_TICKS + 1));
    }

    private static class FlatSource implements SimuMap3DMesh.Source {
        @Override
        public int height(int worldX, int worldZ) {
            return 64;
        }

        @Override
        public int terrainColor(int worldX, int worldZ) {
            return 0xFF5A7A3A;
        }

        @Override
        public int overlay(int worldX, int worldZ) {
            return 0;
        }

        @Override
        public boolean core(int worldX, int worldZ) {
            return false;
        }
    }

    private static final class BuildingSource extends FlatSource {
        @Override
        public int height(int worldX, int worldZ) {
            if (worldX >= 10 && worldX <= 12 && worldZ >= 10 && worldZ <= 12) {
                return 80;
            }
            return 64;
        }

        @Override
        public int terrainColor(int worldX, int worldZ) {
            if (worldX >= 10 && worldX <= 12 && worldZ >= 10 && worldZ <= 12) {
                return 0xFF8A8A8A;
            }
            return 0xFF5A7A3A;
        }
    }
}
