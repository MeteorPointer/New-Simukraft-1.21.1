package common.cn.kafei.simukraft.mixin;

import common.cn.kafei.simukraft.item.CoinItems;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NeoForge 物品槽默认用这个常量当上限，不改的话模组容器仍会把钱币截成 99。 */
@Mixin(Item.class)
public abstract class MixinItemAbsoluteStackSize {
    @Shadow
    @Final
    @Mutable
    public static int ABSOLUTE_MAX_STACK_SIZE;

    /** simukraft$raiseAbsoluteMaxStackSize：把绝对堆叠上限提高到钱币的一组数量。 */
    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void simukraft$raiseAbsoluteMaxStackSize(CallbackInfo callback) {
        ABSOLUTE_MAX_STACK_SIZE = Math.max(ABSOLUTE_MAX_STACK_SIZE, CoinItems.MAX_STACK);
    }
}
