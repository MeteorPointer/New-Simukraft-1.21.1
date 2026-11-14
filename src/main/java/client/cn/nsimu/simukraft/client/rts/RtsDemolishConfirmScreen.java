package client.cn.nsimu.simukraft.client.rts;

import client.cn.nsimu.simukraft.client.buildbox.BuildingBoundsRenderer;
import client.cn.nsimu.simukraft.client.city.ClientCityChunkCache;
import client.cn.nsimu.simukraft.client.toast.ClientInfoToast;
import common.cn.nsimu.simukraft.building.BuildingTerritoryValidator;
import common.cn.nsimu.simukraft.config.ServerConfig;
import common.cn.nsimu.simukraft.network.rts.RtsDemolishPacket;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * RTS 拆除确认：先询问再发送拆除请求，并且不暂停单人游戏。
 */
@OnlyIn(Dist.CLIENT)
public final class RtsDemolishConfirmScreen extends ConfirmScreen {
    private RtsDemolishConfirmScreen(BooleanConsumer callback, Component message) {
        super(callback,
                Component.translatable("gui.simukraft.rts.demolish.confirm.title"),
                message,
                Component.translatable("gui.simukraft.rts.demolish.confirm.yes"),
                Component.translatable("gui.button.cancel"));
    }

    /**
     * open: 打开拆除确认。同意后才把目标发给服务端。
     */
    public static void open(BlockPos targetPos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (targetPos == null || minecraft.player == null) {
            return;
        }
        BlockPos pos = targetPos.immutable();
        if (ServerConfig.claimProtectionEnabled() && !isInCurrentCityTerritory(pos)) {
            ClientInfoToast.show(
                    Component.translatable("toast.simukraft.title"),
                    Component.translatable("message.simukraft.rts.demolish.outside_city"),
                    "warning");
            if (minecraft.screen instanceof RtsContextMenuScreen) {
                minecraft.setScreen(null);
            }
            return;
        }
        minecraft.setScreen(new RtsDemolishConfirmScreen(confirmed -> {
            if (confirmed) {
                PacketDistributor.sendToServer(new RtsDemolishPacket(pos));
                RtsSelectionManager.clearBlockSelection(pos);
            }
            if (minecraft.screen instanceof RtsDemolishConfirmScreen) {
                minecraft.setScreen(null);
            }
        }, confirmMessage(pos)));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static boolean isInCurrentCityTerritory(BlockPos pos) {
        return BuildingTerritoryValidator.isPositionInChunks(pos, ClientCityChunkCache.getInstance().getCurrentCityChunks());
    }

    private static Component confirmMessage(BlockPos pos) {
        Component name = targetName(pos);
        if (name.getString().isBlank()) {
            return Component.translatable("gui.simukraft.rts.demolish.confirm.message_unnamed");
        }
        return Component.translatable("gui.simukraft.rts.demolish.confirm.message", name);
    }

    private static Component targetName(BlockPos pos) {
        String buildingName = BuildingBoundsRenderer.knownRtsBuildingNameAt(pos);
        if (!buildingName.isBlank()) {
            return Component.literal(buildingName);
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            BlockState state = minecraft.level.getBlockState(pos);
            if (!state.isAir()) {
                return state.getBlock().getName();
            }
        }
        return Component.empty();
    }
}
