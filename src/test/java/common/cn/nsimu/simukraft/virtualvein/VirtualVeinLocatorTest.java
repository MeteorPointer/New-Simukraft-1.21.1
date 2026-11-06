package common.cn.nsimu.simukraft.virtualvein;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VirtualVeinLocatorTest {
    /**
     * matchesVeinIdAndShortName: 完整 id、短名和显示名都能命中同一条矿脉。
     */
    @Test
    void matchesVeinIdAndShortName() {
        VirtualVeinSlot slot = slot("hematite_vein", "赤铁矿脉");
        assertTrue(VirtualVeinLocator.matches(slot, "hematite_vein"));
        assertTrue(VirtualVeinLocator.matches(slot, "hematite"));
        assertTrue(VirtualVeinLocator.matches(slot, "赤铁矿脉"));
        assertTrue(VirtualVeinLocator.matches(slot, "HEMATITE"));
        assertFalse(VirtualVeinLocator.matches(slot, "diamond"));
        assertFalse(VirtualVeinLocator.matches(slot, ""));
    }

    /**
     * nearestAmongPicksCloserField: 多个矿区时选水平距离更近的那个。
     */
    @Test
    void nearestAmongPicksCloserField() {
        VirtualVeinFieldProfile far = field(4, 0, 4 * 256 + 128, 128, slot("gold_vein", "金矿脉"));
        VirtualVeinFieldProfile near = field(1, 0, 256 + 128, 128, slot("gold_vein", "金矿脉"));
        Optional<VirtualVeinLocator.NearestVein> found = VirtualVeinLocator.nearestAmong(
                new BlockPos(0, 64, 0), "gold", List.of(far, near));
        assertTrue(found.isPresent());
        assertEquals(256 + 128, found.get().key().centerX());
        assertEquals(405, found.get().distanceBlocks());
    }

    /**
     * nearestAmongIgnoresDepletedSlots: 枯竭矿脉不参与最近查找。
     */
    @Test
    void nearestAmongIgnoresDepletedSlots() {
        VirtualVeinSlot depleted = new VirtualVeinSlot("gold_vein", "金矿脉",
                ResourceLocation.withDefaultNamespace("raw_gold"), -56, -16,
                1, 20, 100, 0, VirtualVeinSlotState.DEPLETED);
        VirtualVeinFieldProfile field = field(0, 0, 128, 128, depleted);
        assertTrue(VirtualVeinLocator.nearestAmong(new BlockPos(0, 64, 0), "gold", List.of(field)).isEmpty());
    }

    private static VirtualVeinSlot slot(String id, String name) {
        return new VirtualVeinSlot(id, name, ResourceLocation.withDefaultNamespace("raw_iron"),
                16, 80, 1, 20, 100, 100, VirtualVeinSlotState.ACTIVE);
    }

    private static VirtualVeinFieldProfile field(int cellX, int cellZ, int centerX, int centerZ, VirtualVeinSlot slot) {
        return new VirtualVeinFieldProfile(
                "minecraft:overworld",
                new VirtualVeinFieldKey(cellX, cellZ, centerX, centerZ, "minecraft:plains"),
                "minecraft:plains",
                0L,
                List.of(slot));
    }
}
