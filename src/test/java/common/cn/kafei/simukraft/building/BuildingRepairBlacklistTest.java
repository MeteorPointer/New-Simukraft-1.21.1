package common.cn.kafei.simukraft.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

class BuildingRepairBlacklistTest {
    private static final Block[] CARRIED_BLOCKS = {
            Blocks.COAL_ORE,
            Blocks.DEEPSLATE_COAL_ORE,
            Blocks.IRON_ORE,
            Blocks.DEEPSLATE_IRON_ORE,
            Blocks.COPPER_ORE,
            Blocks.DEEPSLATE_COPPER_ORE,
            Blocks.GOLD_ORE,
            Blocks.DEEPSLATE_GOLD_ORE,
            Blocks.NETHER_GOLD_ORE,
            Blocks.REDSTONE_ORE,
            Blocks.DEEPSLATE_REDSTONE_ORE,
            Blocks.EMERALD_ORE,
            Blocks.DEEPSLATE_EMERALD_ORE,
            Blocks.LAPIS_ORE,
            Blocks.DEEPSLATE_LAPIS_ORE,
            Blocks.DIAMOND_ORE,
            Blocks.DEEPSLATE_DIAMOND_ORE,
            Blocks.NETHER_QUARTZ_ORE,
            Blocks.RAW_IRON_BLOCK,
            Blocks.RAW_GOLD_BLOCK,
            Blocks.RAW_COPPER_BLOCK
    };

    @Test
    void oresAndRawMetalBlocksNeedItemsFromTheInventory() {
        for (Block block : CARRIED_BLOCKS) {
            assertTrue(BuildingRepairBlacklist.requiresCarriedBlock(block.defaultBlockState()), block.toString());
            assertEquals(block.asItem(), BuildingRepairBlacklist.carriedItem(block.defaultBlockState()), block.toString());
        }
        assertFalse(BuildingRepairBlacklist.requiresCarriedBlock(Blocks.STONE.defaultBlockState()));
        assertFalse(BuildingRepairBlacklist.requiresCarriedBlock(Blocks.IRON_BLOCK.defaultBlockState()));
        assertFalse(BuildingRepairBlacklist.requiresCarriedBlock(Blocks.COAL_BLOCK.defaultBlockState()));
        assertFalse(BuildingRepairBlacklist.requiresCarriedBlock(Blocks.DIAMOND_BLOCK.defaultBlockState()));
        assertFalse(BuildingRepairBlacklist.requiresCarriedBlock(Blocks.ANCIENT_DEBRIS.defaultBlockState()));
        assertFalse(BuildingRepairBlacklist.requiresCarriedBlock(Blocks.AIR.defaultBlockState()));
        assertFalse(BuildingRepairBlacklist.requiresCarriedBlock(null));
        assertEquals(Items.AIR, BuildingRepairBlacklist.carriedItem(Blocks.STONE.defaultBlockState()));
        assertEquals(Items.AIR, BuildingRepairBlacklist.carriedItem(null));
    }
}
