package common.cn.nsimu.simukraft.mixin;

import common.cn.nsimu.simukraft.network.rts.RtsRemoteCitizenAccess;
import common.cn.nsimu.simukraft.network.rts.RtsRemoteMenuAccess;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * RTS 远程容器兼容：只为当前 RTS 会话菜单绕过原版本体距离关闭。
 */

@Mixin(ServerPlayer.class)
public abstract class MixinServerPlayer {
    /**
     * simukraft$keepRtsRemoteMenuOpen: 跳过 tick 里因距离失败而关闭容器。
     * stillValid 本身会被别的模组 Redirect，不能再抢那一次调用。
     */
    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;closeContainer()V"))
    private void simukraft$keepRtsRemoteMenuOpen(ServerPlayer player) {
        if (!simukraft$rtsRemoteMenuShouldStayOpen(player)) {
            player.closeContainer();
        }
    }

    /**
     * simukraft$keepRtsRemoteMenuAssigned: 跳过紧随关闭的物品栏回写。
     * closeContainer 被跳过后，这次赋值仍会把远程菜单清掉。
     */
    @Redirect(method = "tick", at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD,
            target = "Lnet/minecraft/server/level/ServerPlayer;containerMenu:Lnet/minecraft/world/inventory/AbstractContainerMenu;"))
    private void simukraft$keepRtsRemoteMenuAssigned(ServerPlayer player, AbstractContainerMenu replacement) {
        if (!simukraft$rtsRemoteMenuShouldStayOpen(player)) {
            player.containerMenu = replacement;
        }
    }

    /**
     * simukraft$rtsRemoteMenuShouldStayOpen: 当前容器仍属于已授权的 RTS 会话。
     */
    @Unique
    private static boolean simukraft$rtsRemoteMenuShouldStayOpen(ServerPlayer player) {
        AbstractContainerMenu menu = player.containerMenu;
        return RtsRemoteMenuAccess.keepsMenuOpen(player, menu)
                || RtsRemoteCitizenAccess.keepsMenuOpen(player, menu);
    }

    /**
     * simukraft$finishRtsRemoteMenu: 关闭原版容器前结束 RTS 会话，恢复原版距离和开盖状态。
     */
    @Inject(method = "doCloseContainer", at = @At("HEAD"))
    private void simukraft$finishRtsRemoteMenu(CallbackInfo callback) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        RtsRemoteMenuAccess.finishMenu(player, player.containerMenu);
        RtsRemoteCitizenAccess.finishMenu(player, player.containerMenu);
    }
}
