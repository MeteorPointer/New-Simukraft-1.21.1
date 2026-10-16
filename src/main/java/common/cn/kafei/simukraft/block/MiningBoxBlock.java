package common.cn.kafei.simukraft.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** 挖矿盒：已注册的空方块，没有交互逻辑。 */
public final class MiningBoxBlock extends Block {
    @SuppressWarnings("deprecation")
    public MiningBoxBlock() {
        super(BlockBehaviour.Properties.ofLegacyCopy(Blocks.STONE));
    }
}
