package common.cn.nsimu.simukraft.farmland;

import net.minecraft.core.BlockPos;

/**
 * 农田盒界面视图：服务端构建后下发给客户端渲染，客户端不自行推断状态。
 */
public record FarmlandBoxView(BlockPos boxPos,
                              boolean hasCity,
                              String cropId,
                              boolean hasPlot,
                              BlockPos plotMin,
                              BlockPos plotMax,
                              boolean hasChest,
                              BlockPos chestPos,
                              boolean running,
                              boolean hasFarmer,
                              String farmerName) {
    public static FarmlandBoxView empty(BlockPos boxPos) {
        return new FarmlandBoxView(boxPos.immutable(), false, "", false, BlockPos.ZERO, BlockPos.ZERO, false, BlockPos.ZERO, false, false, "");
    }
}
