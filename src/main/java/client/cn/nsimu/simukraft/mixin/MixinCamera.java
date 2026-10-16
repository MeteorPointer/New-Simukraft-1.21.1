package client.cn.nsimu.simukraft.mixin;

import client.cn.nsimu.simukraft.client.freecamera.FreeCameraManager;
import net.minecraft.client.Camera;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
@OnlyIn(Dist.CLIENT)
public abstract class MixinCamera {
    @Shadow
    protected abstract void setPosition(Vec3 position);

    // NeoForge added this overload; it is not in the official obfuscation map.
    @Shadow(remap = false)
    protected abstract void setRotation(float yRot, float xRot, float roll);

    /**
     * simukraft$setup: finish vanilla camera setup, then apply the RTS camera pose.
     */
    @Inject(method = "setup", at = @At("TAIL"))
    private void simukraft$setup(BlockGetter level, Entity entity, boolean detached, boolean mirrored, float partialTick, CallbackInfo callbackInfo) {
        if (FreeCameraManager.isActive() && entity instanceof LocalPlayer) {
            setPosition(FreeCameraManager.getPosition());
            setRotation(FreeCameraManager.getYaw(), FreeCameraManager.getPitch(), 0.0F);
        }
    }
}

