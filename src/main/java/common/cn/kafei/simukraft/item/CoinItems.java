package common.cn.kafei.simukraft.item;

import common.cn.kafei.simukraft.registry.ModItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** CoinItems：铜币、银币、金币的堆叠上限和角标。 */
public final class CoinItems {
    /** 一组钱币的最大数量。原版组件上限是 99，超过部分靠 mixin 放行。 */
    public static final int MAX_STACK = 1000;
    /** 满组时角标上的原始数字，渲染时换成 1k。 */
    public static final String MAX_STACK_LABEL = "1000";
    /** 满组角标相对原版右下角再向右移的像素，对齐格子右侧。 */
    public static final int FULL_STACK_LABEL_SHIFT_X = 11;

    private CoinItems() {
    }

    /** isCoin：是否为模组钱币。 */
    public static boolean isCoin(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        Item item = stack.getItem();
        return item == ModItems.COPPER_COIN.get()
                || item == ModItems.SILVER_COIN.get()
                || item == ModItems.GOLD_COIN.get();
    }

}
