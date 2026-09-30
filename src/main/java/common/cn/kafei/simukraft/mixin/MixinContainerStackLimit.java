package common.cn.kafei.simukraft.mixin;

import common.cn.kafei.simukraft.item.CoinItems;
import net.minecraft.world.Container;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

/** 容器默认上限是 99。只抬高这个默认值，酿造台等自己返回 1 的格子不受影响。 */
@Mixin(Container.class)
public interface MixinContainerStackLimit {
    /** simukraft$raiseDefaultContainerLimit：让玩家背包和箱子能装下一组 1000 的钱币。 */
    @ModifyReturnValue(method = "getMaxStackSize()I", at = @At("RETURN"))
    private int simukraft$raiseDefaultContainerLimit(int original) {
        return Math.max(original, CoinItems.MAX_STACK);
    }
}
