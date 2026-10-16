package client.cn.nsimu.simukraft.mixin.sodium;

import client.cn.nsimu.simukraft.client.freecamera.FreeCameraManager;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sodium RTS 兼容：正交俯视关闭云渲染。
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.immediate.CloudRenderer", remap = false)
public class MixinSodiumCloudRenderer {
    /**
     * simukraft$skipRtsClouds: RTS 激活时不提交云网格。Iris 的云也走这个入口。
     */
    @Inject(method = "render", at = @At("HEAD"), cancellable = true, remap = false)
    private void simukraft$skipRtsClouds(
            Camera camera,
            ClientLevel level,
            Matrix4f projectionMatrix,
            PoseStack poseStack,
            float ticks,
            float tickDelta,
            CallbackInfo callbackInfo
    ) {
        if (FreeCameraManager.isRtsActive()) {
            callbackInfo.cancel();
        }
    }
}
