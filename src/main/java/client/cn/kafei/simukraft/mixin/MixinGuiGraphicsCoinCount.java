package client.cn.kafei.simukraft.mixin;

import common.cn.kafei.simukraft.item.CoinItems;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 钱币满 1000 时，角标写成 1k，并放到格子右侧。 */
@Mixin(GuiGraphics.class)
public abstract class MixinGuiGraphicsCoinCount {
    @Unique
    private boolean simukraft$fullCoinStack;

    /** simukraft$markFullCoinStack：这一帧的数量文字是否来自满组钱币。 */
    @Inject(
            method = "renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V",
            at = @At("HEAD")
    )
    private void simukraft$markFullCoinStack(Font font, ItemStack stack, int x, int y, String text, CallbackInfo callback) {
        simukraft$fullCoinStack = text == null && CoinItems.isCoin(stack) && stack.getCount() == CoinItems.MAX_STACK;
    }

    /** simukraft$compactCoinCount：物品装饰里只有这一次绘制数量。 */
    @ModifyArg(
            method = "renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)I"
            ),
            index = 1
    )
    private String simukraft$compactCoinCount(String original) {
        return simukraft$fullCoinStack && CoinItems.MAX_STACK_LABEL.equals(original) ? "1k" : original;
    }

    /** simukraft$shiftFullCoinCount：1k 从物品底部挪到格子右侧。 */
    @ModifyArg(
            method = "renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)I"
            ),
            index = 2
    )
    private int simukraft$shiftFullCoinCount(int original) {
        return simukraft$fullCoinStack ? original + CoinItems.FULL_STACK_LABEL_SHIFT_X : original;
    }
}
