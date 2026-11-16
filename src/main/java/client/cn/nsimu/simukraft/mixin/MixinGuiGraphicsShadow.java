package client.cn.nsimu.simukraft.mixin;

import client.cn.nsimu.simukraft.client.ClientFontSelection;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** 自定义字体关闭 GUI 文本阴影，避免 TTF 字形出现黑色外圈。 */
@Mixin(GuiGraphics.class)
public abstract class MixinGuiGraphicsShadow {
    /** simukraft$disableDropShadow: 修改 GUI 字符串绘制的阴影开关。 */
    @ModifyVariable(method = "drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)I",
            at = @At("HEAD"), argsOnly = true, index = 6)
    private boolean simukraft$disableDropShadow(boolean dropShadow) {
        return ClientFontSelection.isCustomFont() ? false : dropShadow;
    }
}
