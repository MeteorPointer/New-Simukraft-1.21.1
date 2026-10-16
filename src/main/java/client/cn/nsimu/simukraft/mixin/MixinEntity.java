package client.cn.nsimu.simukraft.mixin;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import client.cn.nsimu.simukraft.client.freecamera.FreeCameraManager;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
@OnlyIn(Dist.CLIENT)
public abstract class MixinEntity {
    @Inject(method = "getEyePosition(F)Lnet/minecraft/world/phys/Vec3;", at = @At("HEAD"), cancellable = true)
    private void simukraft$getEyePosition(float partialTick, CallbackInfoReturnable<Vec3> callbackInfo) {
        if (FreeCameraManager.isActive() && (Object) this instanceof LocalPlayer) {
            callbackInfo.setReturnValue(FreeCameraManager.getPosition());
        }
    }
}
