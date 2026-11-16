package client.cn.nsimu.simukraft.mixin;

import client.cn.nsimu.simukraft.client.ui.SimuKraftGlassTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 界面关闭后释放毛玻璃底图，避免下一帧把界面自己拍进去。
 */
@Mixin(Minecraft.class)
public abstract class MixinMinecraftGlassBackdrop {
    /**
     * simukraft$releaseGlassBackdrop: 离开界面时清掉已截取的底图。
     */
    @Inject(method = "setScreen", at = @At("HEAD"))
    private void simukraft$releaseGlassBackdrop(Screen screen, CallbackInfo callbackInfo) {
        Minecraft minecraft = (Minecraft) (Object) this;
        if (screen == null || screen != minecraft.screen) {
            SimuKraftGlassTextures.releaseBackdrop();
        }
    }
}
