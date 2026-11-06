package common.cn.nsimu.simukraft.mineraldrilling;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MineralDrillingWorkServiceTest {
    /**
     * consumesAtSelectedDrillDepth: 防止用控制箱高度校验地下矿脉，导致误报矿脉枯竭。
     */
    @Test
    void consumesAtSelectedDrillDepth() {
        BlockPos controlBox = new BlockPos(7113, 131, -1206);

        assertEquals(new BlockPos(7113, -64, -1206),
                MineralDrillingWorkService.drillTargetPos(controlBox, -64));
    }

    /**
     * splitsProductionAcrossOverlappingVeins: 同一 Y 层命中两个矿脉时每条矿脉仅取得一半产量。
     */
    @Test
    void splitsProductionAcrossOverlappingVeins() {
        assertEquals(10, MineralDrillingWorkService.productionAmountPerVein(10, 1));
        assertEquals(5, MineralDrillingWorkService.productionAmountPerVein(10, 2));
        assertEquals(4, MineralDrillingWorkService.productionAmountPerVein(9, 2));
        assertEquals(1, MineralDrillingWorkService.productionAmountPerVein(1, 2));
    }

    /**
     * fillerAmountStaysWithinMinuteRange: 周期为 1 分钟时填充物件数落在 40~100。
     */
    @Test
    void fillerAmountStaysWithinMinuteRange() {
        for (long seed = 0; seed < 40; seed++) {
            int amount = MineralDrillingWorkService.fillerAmount(RandomSource.create(seed), 1200);
            assertTrue(amount >= MineralDrillingWorkService.FILLER_MIN_PER_MINUTE, "seed " + seed);
            assertTrue(amount <= MineralDrillingWorkService.FILLER_MAX_PER_MINUTE, "seed " + seed);
        }
    }

    /**
     * fillerPoolUsesDirtAtOrAboveZeroAndCobbledDeepslateBelow: Y 小于 0 出深板岩圆石，0 及以上出泥土。
     */
    @Test
    void fillerPoolUsesDirtAtOrAboveZeroAndCobbledDeepslateBelow() {
        ResourceLocation dirt = ResourceLocation.withDefaultNamespace("dirt");
        ResourceLocation cobbledDeepslate = ResourceLocation.withDefaultNamespace("cobbled_deepslate");
        ResourceLocation cobblestone = ResourceLocation.withDefaultNamespace("cobblestone");
        List<ResourceLocation> shallow = MineralDrillingWorkService.fillerItemIds(0);
        List<ResourceLocation> deep = MineralDrillingWorkService.fillerItemIds(-1);
        assertTrue(shallow.contains(dirt));
        assertFalse(shallow.contains(cobbledDeepslate));
        assertTrue(shallow.contains(cobblestone));
        assertTrue(deep.contains(cobbledDeepslate));
        assertFalse(deep.contains(dirt));
        assertTrue(deep.contains(cobblestone));
    }
}
