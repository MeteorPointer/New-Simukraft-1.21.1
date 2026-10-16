package common.cn.nsimu.simukraft.mixin;

import common.cn.nsimu.simukraft.fluid.MilkBucketPlacementService;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MilkBucketItem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MilkBucketItem.class)
public abstract class MixinMilkBucketItem {
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void simukraft$pourMilk(Level level,
                                    Player player,
                                    InteractionHand hand,
                                    CallbackInfoReturnable<InteractionResultHolder<ItemStack>> callbackInfo) {
        InteractionResultHolder<ItemStack> result = MilkBucketPlacementService.tryPourMilk(level, player, hand);
        if (result != null) {
            callbackInfo.setReturnValue(result);
        }
    }
}
