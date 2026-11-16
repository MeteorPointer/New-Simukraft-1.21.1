package client.cn.nsimu.simukraft.mixin;

import client.cn.nsimu.simukraft.client.ClientFontSelection;
import net.minecraft.client.gui.font.FontManager;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/** 将 Minecraft 的默认字体请求切换到客户端配置选择的字体资源。 */
@Mixin(FontManager.class)
public abstract class MixinFontManager {
    @Shadow @Final private Map<ResourceLocation, FontSet> fontSets;

    /** simukraft$selectConfiguredFont: 替换默认字体资源，其他自定义字体保持原样。 */
    @Inject(method = "getFontSetRaw", at = @At("HEAD"), cancellable = true)
    private void simukraft$selectConfiguredFont(ResourceLocation id, CallbackInfoReturnable<FontSet> cir) {
        if (id.equals(ResourceLocation.withDefaultNamespace("default"))) {
            FontSet selected = fontSets.get(ClientFontSelection.effectiveFontId());
            if (selected != null) cir.setReturnValue(selected);
        }
    }
}
