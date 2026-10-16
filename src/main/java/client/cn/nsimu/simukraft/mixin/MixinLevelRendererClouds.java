package client.cn.nsimu.simukraft.mixin;

import client.cn.nsimu.simukraft.client.freecamera.FreeCameraManager;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * RTS 关闭原版云渲染。Sodium 会把这条路径转到自己的云渲染器，那边也有同样的拦截。
 */
@Mixin(LevelRenderer.class)
public class MixinLevelRendererClouds {
    /**
     * simukraft$skipRtsClouds: RTS 激活时跳过原版云提交。
     */
    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
    private void simukraft$skipRtsClouds(
            PoseStack poseStack,
            Matrix4f frustumMatrix,
            Matrix4f projectionMatrix,
            float partialTick,
            double camX,
            double camY,
            double camZ,
            CallbackInfo callbackInfo
    ) {
        if (FreeCameraManager.isRtsActive()) {
            callbackInfo.cancel();
        }
    }
}
