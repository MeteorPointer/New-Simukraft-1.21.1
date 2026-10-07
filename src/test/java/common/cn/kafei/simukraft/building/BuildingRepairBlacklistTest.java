package common.cn.kafei.simukraft.building;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

class BuildingRepairBlacklistTest {
    @Test
    void oresAndRawMetalBlocksNeedItemsFromTheInventory() {
        assertTrue(BuildingRepairBlacklist.requiresCarriedBlock(Blocks.IRON_ORE.defaultBlockState()));
        assertTrue(BuildingRepairBlacklist.requiresCarriedBlock(Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState()));
        assertTrue(BuildingRepairBlacklist.requiresCarriedBlock(Blocks.NETHER_QUARTZ_ORE.defaultBlockState()));
        assertTrue(BuildingRepairBlacklist.requiresCarriedBlock(Blocks.RAW_IRON_BLOCK.defaultBlockState()));
        assertTrue(BuildingRepairBlacklist.requiresCarriedBlock(Blocks.RAW_GOLD_BLOCK.defaultBlockState()));
        assertTrue(BuildingRepairBlacklist.requiresCarriedBlock(Blocks.RAW_COPPER_BLOCK.defaultBlockState()));
        assertFalse(BuildingRepairBlacklist.requiresCarriedBlock(Blocks.STONE.defaultBlockState()));
        assertFalse(BuildingRepairBlacklist.requiresCarriedBlock(Blocks.IRON_BLOCK.defaultBlockState()));
    }
}
