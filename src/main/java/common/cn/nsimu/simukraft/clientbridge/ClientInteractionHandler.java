package common.cn.nsimu.simukraft.clientbridge;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * ClientInteractionHandler: 客户端交互动作抽象，避免方块和物品 common 逻辑直接打开客户端界面。
 */
public interface ClientInteractionHandler {
    ClientInteractionHandler NOOP = new ClientInteractionHandler() {
    };

    /**
     * openBuildBox: 打开建造箱客户端界面。
     */
    default void openBuildBox(BlockPos pos) {
    }

    /**
     * openManifest: 打开材料清单客户端界面。
     */
    default void openManifest(ItemStack stack, InteractionHand hand) {
    }

    /**
     * openGuideBook: 打开指南书客户端界面。
     */
    default void openGuideBook(ItemStack stack) {
    }
}
