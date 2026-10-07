package common.cn.kafei.simukraft.building;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;

/**
 * 修复黑名单：矿石块和粗铁、粗金、粗铜块不走城市资金补全。
 * 玩家背包里有对应方块时才消耗并补上。
 */
public final class BuildingRepairBlacklist {
    private static final Set<Block> CARRIED_BLOCKS = Set.of(
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
    );

    private BuildingRepairBlacklist() {
    }

    public static boolean requiresCarriedBlock(BlockState state) {
        return state != null && !state.isAir() && CARRIED_BLOCKS.contains(state.getBlock());
    }

    public static Item carriedItem(BlockState state) {
        if (!requiresCarriedBlock(state)) {
            return Items.AIR;
        }
        Item item = state.getBlock().asItem();
        return item == null ? Items.AIR : item;
    }
}
