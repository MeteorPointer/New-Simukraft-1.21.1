package common.cn.kafei.simukraft.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import common.cn.kafei.simukraft.item.CoinItems;
import net.neoforged.neoforge.items.ComponentItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.EntityEquipmentInvWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** 这些类把 99 编译进了 getSlotLimit，改 Item.ABSOLUTE_MAX_STACK_SIZE 不会生效。 */
@Mixin({ItemStackHandler.class, ComponentItemHandler.class, EntityEquipmentInvWrapper.class})
public abstract class MixinCoinSlotLimit {
    /** simukraft$raiseCoinSlotLimit：1 格的装备槽保持 1，其余槽允许钱币堆到 1000。物品自身上限仍然生效。 */
    @ModifyReturnValue(method = "getSlotLimit(I)I", at = @At("RETURN"))
    private int simukraft$raiseCoinSlotLimit(int original) {
        if (original <= 1) {
            return original;
        }
        return Math.max(original, CoinItems.MAX_STACK);
    }
}
