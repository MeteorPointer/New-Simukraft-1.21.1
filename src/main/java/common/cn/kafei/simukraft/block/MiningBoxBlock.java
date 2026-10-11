package common.cn.kafei.simukraft.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** 挖矿盒：硬度、正确工具掉落和音效与石头一致。 */
public final class MiningBoxBlock extends Block {
    @SuppressWarnings("deprecation")
    public MiningBoxBlock() {
        super(BlockBehaviour.Properties.ofLegacyCopy(Blocks.STONE));
    }
}
