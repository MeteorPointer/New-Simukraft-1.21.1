package common.cn.kafei.simukraft.mixin;

import com.mojang.serialization.Codec;
import common.cn.kafei.simukraft.item.CoinItems;
import net.minecraft.util.ExtraCodecs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 原版把物品数量和 max_stack_size 都限制在 1 到 99。
 * 钱币要存成 1000，这两个编码范围必须一起放宽，否则存档和组件会拒绝这组物品。
 */
@Mixin(ExtraCodecs.class)
public abstract class MixinExtraCodecsStackRange {
    /** simukraft$widenVanillaStackRange：只放宽原版 1–99 这一档，避免影响其它数值范围。 */
    @Inject(method = "intRange(II)Lcom/mojang/serialization/Codec;", at = @At("RETURN"), cancellable = true)
    private static void simukraft$widenVanillaStackRange(int min, int max, CallbackInfoReturnable<Codec<Integer>> callback) {
        if (min == 1 && max == 99) {
            callback.setReturnValue(ExtraCodecs.intRange(1, CoinItems.MAX_STACK));
        }
    }
}
