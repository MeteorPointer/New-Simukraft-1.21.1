package client.cn.nsimu.simukraft.mixin;

import client.cn.nsimu.simukraft.client.ui.SimuKraftGlassTextures;
import com.lowdragmc.lowdraglib2.gui.ui.Style;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在本模组 LDLib 界面绘制前截取当前画面，并给面板换上圆角毛玻璃底色。
 */
@Mixin(value = ModularUI.ModularUIWidget.class, remap = false)
public abstract class MixinModularUiGlass {
    /**
     * simukraft$captureGlassBackdrop: 控件绘制前复制当前画面。
     */
    @Inject(method = "render", at = @At("HEAD"))
    private void simukraft$captureGlassBackdrop(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo callbackInfo) {
        if (SimuKraftGlassTextures.enabled()) {
            SimuKraftGlassTextures.captureBackdrop();
        }
    }

    /**
     * simukraft$applyGlassBackground: 绘制前替换本模组面板底色。
     */
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lcom/lowdragmc/lowdraglib2/gui/ui/UIElement;drawInBackground(Lcom/lowdragmc/lowdraglib2/gui/ui/rendering/GUIContext;)V"))
    private void simukraft$applyGlassBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo callbackInfo) {
        if (!SimuKraftGlassTextures.enabled()) {
            return;
        }
        ModularUI modularUi = ((ModularUI.ModularUIWidget) (Object) this).getModularUI();
        if (modularUi == null || modularUi.ui == null || modularUi.ui.rootElement == null) {
            return;
        }
        apply(modularUi.ui.rootElement);
    }

    private static boolean isDark(int color) {
        int red = (color >> 16) & 0xFF;
        int green = (color >> 8) & 0xFF;
        int blue = color & 0xFF;
        return Math.max(red, Math.max(green, blue)) < 0xC0;
    }

    private static void apply(UIElement element) {
        if (element instanceof Label label && label.getTextStyle() != null && isDark(label.getTextStyle().textColor())) {
            label.textStyle(style -> Style.importantPipeline(style, important -> important
                    .textColor(0xFFFFFFFF)
                    .textShadow(false)));
        }
        SimuKraftGlassTextures.applyElement(element);
        for (UIElement child : element.getChildren()) {
            apply(child);
        }
    }
}
