package client.cn.kafei.simukraft.client.city.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SimuMapColorMathTest {
    @Test
    void multiplyTint_turnsGrayscaleGrassTextureIntoOlive() {
        int grassTexture = 0xFF9A9A9A;
        int plainsTint = 0x91BD59;
        int color = SimuBlockColors.multiplyTint(grassTexture, plainsTint);

        int expected = 0xFF000000
                | ((0x9A * 0x91 / 255) << 16)
                | ((0x9A * 0xBD / 255) << 8)
                | (0x9A * 0x59 / 255);
        assertEquals(expected, color);
        assertTrue(((color >> 16) & 0xFF) < 0x70);
        assertTrue(((color >> 8) & 0xFF) > 0x60);
    }

    @Test
    void multiplyTint_ignoresWhiteTint() {
        assertEquals(0xFF336699, SimuBlockColors.multiplyTint(0xFF336699, 0xFFFFFF));
    }

    @Test
    void slopeBrightness_raisesNorthwestRidgesAndDarkensValleys() {
        float ridge = SimuBlockColors.slopeBrightness(70, 64, 64, false);
        float valley = SimuBlockColors.slopeBrightness(64, 70, 70, false);
        float flat = SimuBlockColors.slopeBrightness(64, 64, 64, false);

        assertTrue(ridge > 0.3f);
        assertTrue(valley < -0.3f);
        assertEquals(0.0f, flat, 0.0001f);
    }

    @Test
    void slopeBrightness_isWeakerOnWater() {
        float land = SimuBlockColors.slopeBrightness(66, 64, 64, false);
        float water = SimuBlockColors.slopeBrightness(66, 64, 64, true);
        assertTrue(Math.abs(water) < Math.abs(land));
    }

    @Test
    void adjustBrightness_lightensAndDarkensWithoutClippingAlpha() {
        int lighter = SimuBlockColors.adjustBrightness(0xFF408040, 0.25f);
        int darker = SimuBlockColors.adjustBrightness(0xFF408040, -0.25f);

        assertEquals(0xFF, (lighter >>> 24) & 0xFF);
        assertTrue(((lighter >>> 8) & 0xFF) > 0x80);
        assertTrue((darker & 0xFF) < 0x40);
    }

    @Test
    void opaqueTerrainColor_treatsTransparentAsFallback() {
        assertEquals(SimuBlockColors.FALLBACK_TERRAIN_COLOR, SimuBlockColors.opaqueTerrainColor(0));
        assertEquals(0xFF112233, SimuBlockColors.opaqueTerrainColor(0xFF112233));
    }

    @Test
    void composeMapColumnColor_keepsTerrainUnderTerritoryFill() {
        int grass = 0xFF5A7A3A;
        int fill = 0x5500DD00;
        int composed = SimuBlockColors.composeMapColumnColor(grass, fill, false, 64, 64, 64, false);
        int orAlpha = SimuBlockColors.blendColors(grass, fill | 0x66000000);

        int grassGreen = (grass >> 8) & 0xFF;
        int composedGreen = (composed >> 8) & 0xFF;
        int orGreen = (orAlpha >> 8) & 0xFF;
        assertTrue(Math.abs(composedGreen - grassGreen) < Math.abs(orGreen - grassGreen));
        assertEquals(0x77, ((fill | 0x66000000) >>> 24) & 0xFF);
    }

    @Test
    void composeMapColumnColor_doesNotReplaceCoreColumnWithSolidBlue() {
        int grass = 0xFF5A7A3A;
        int composed = SimuBlockColors.composeMapColumnColor(grass, 0, true, 64, 64, 64, false);

        assertNotEquals(0xFF4080FF, composed);
        assertTrue(((composed >> 8) & 0xFF) > 0x20);
        assertTrue((composed & 0xFF) > (grass & 0xFF));
    }

    @Test
    void composeMapColumnColor_usesWeakerWaterSlope() {
        int waterBlue = 0xFF3F76E4;
        int land = SimuBlockColors.composeMapColumnColor(waterBlue, 0, false, 66, 64, 64, false);
        int water = SimuBlockColors.composeMapColumnColor(waterBlue, 0, false, 66, 64, 64, true);

        int baseBlue = waterBlue & 0xFF;
        assertTrue(Math.abs((water & 0xFF) - baseBlue) < Math.abs((land & 0xFF) - baseBlue));
    }

    @Test
    void composeThreeDColumnColor_keepsInteriorGrassWithoutTerritoryFill() {
        int grass = 0xFF5A7A3A;
        int interior = SimuBlockColors.composeThreeDColumnColor(
                grass, 0, false, 64, 64, 64, 64, 64, false, true);
        int neonFill = SimuBlockColors.composeThreeDColumnColor(
                grass, 0x5500DD00, false, 64, 64, 64, 64, 64, false, true);

        assertEquals(grass, interior);
        assertTrue(((neonFill >> 8) & 0xFF) > ((interior >> 8) & 0xFF));
    }

    @Test
    void heightOcclusion_darkensAgainstTallerNeighbors() {
        assertEquals(0.0F, SimuBlockColors.heightOcclusion(64, 64, 64, 64, 64), 0.0001F);
        assertTrue(SimuBlockColors.heightOcclusion(64, 70, 64, 64, 64) < 0.0F);
    }

    @Test
    void nativeColor_roundTripsArgb() {
        int argb = 0xFF4080C0;
        assertEquals(argb, SimuBlockColors.fromNativeColor(SimuBlockColors.toNativeColor(argb)));
    }

    @Test
    void lambertShade_nwFacingSlopeIsBrighterThanSeFacingSlope() {
        float northwest = SimuBlockColors.lambertShade(64, 70, 64, 70, 1.0F);
        float southeast = SimuBlockColors.lambertShade(70, 64, 70, 64, 1.0F);
        float flat = SimuBlockColors.lambertShade(64, 64, 64, 64, 1.0F);

        assertTrue(northwest > southeast);
        assertTrue(flat > 0.7F);
        assertTrue(flat < 1.05F);
    }

    @Test
    void scaleColor_multipliesRgbAndKeepsAlpha() {
        int darker = SimuBlockColors.scaleColor(0xFF80A0C0, 0.5F);
        assertEquals(0xFF, (darker >>> 24) & 0xFF);
        assertEquals(0x40, (darker >> 16) & 0xFF);
        assertEquals(0x50, (darker >> 8) & 0xFF);
        assertEquals(0x60, darker & 0xFF);
    }
}
