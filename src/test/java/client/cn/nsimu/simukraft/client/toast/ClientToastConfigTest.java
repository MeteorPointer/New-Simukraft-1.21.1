package client.cn.nsimu.simukraft.client.toast;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientToastConfigTest {
    /**
     * Verifies all six toast anchor positions.
     */
    @Test
    void calculatesPositionsForAllAnchors() {
        assertPosition(ClientToastConfig.Anchor.TOP_LEFT, 7, 11);
        assertPosition(ClientToastConfig.Anchor.TOP_RIGHT, 207, 11);
        assertPosition(ClientToastConfig.Anchor.BOTTOM_LEFT, 7, 171);
        assertPosition(ClientToastConfig.Anchor.BOTTOM_RIGHT, 207, 171);
        assertPosition(ClientToastConfig.Anchor.TOP_CENTER, 107, 11);
        assertPosition(ClientToastConfig.Anchor.BOTTOM_CENTER, 107, 171);
    }

    /**
     * fromNameDefaultsToWidth: 未知扩展模式回退到宽度优先。
     */
    @Test
    void fromNameDefaultsToWidth() {
        assertEquals(ClientToastConfig.FitMode.WIDTH, ClientToastConfig.FitMode.fromName("WIDTH"));
        assertEquals(ClientToastConfig.FitMode.HEIGHT, ClientToastConfig.FitMode.fromName("height"));
        assertEquals(ClientToastConfig.FitMode.HEIGHT, ClientToastConfig.FitMode.fromName("nope"));
    }

    /**
     * growPrefersWidthThenHeight: 宽度优先时先加宽到能放下，再不够才加高。
     */
    @Test
    void growPrefersWidthThenHeight() {
        int[] size = ClientToastFit.resolve(100, 40, 400, 200, 0.5F, ClientToastConfig.FitMode.WIDTH,
                logicalWidth -> 40_000 / Math.max(1, logicalWidth) + 20);
        assertTrue(size[0] > 100);
        assertTrue(ClientToastFit.fits(size[0], size[1], 0.5F, logicalWidth -> 40_000 / Math.max(1, logicalWidth) + 20));
    }

    /**
     * growPrefersHeightThenWidth: 高度优先时先加高。
     */
    @Test
    void growPrefersHeightThenWidth() {
        int[] widthFirst = ClientToastFit.resolve(100, 40, 400, 200, 0.5F, ClientToastConfig.FitMode.WIDTH,
                logicalWidth -> 40_000 / Math.max(1, logicalWidth) + 20);
        int[] heightFirst = ClientToastFit.resolve(100, 40, 400, 200, 0.5F, ClientToastConfig.FitMode.HEIGHT,
                logicalWidth -> 40_000 / Math.max(1, logicalWidth) + 20);
        assertTrue(heightFirst[1] >= widthFirst[1]);
        assertTrue(heightFirst[0] <= widthFirst[0]);
    }

    private static void assertPosition(ClientToastConfig.Anchor anchor, int expectedX, int expectedY) {
        assertArrayEquals(
                new int[]{expectedX, expectedY},
                ClientToastConfig.calculatePosition(anchor, 7, 11, 300, 200, 100, 40));
    }
}
