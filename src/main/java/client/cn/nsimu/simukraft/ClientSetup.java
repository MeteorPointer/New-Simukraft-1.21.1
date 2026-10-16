package client.cn.nsimu.simukraft;

import client.cn.nsimu.simukraft.client.CityEntryHud;
import client.cn.nsimu.simukraft.client.ClientHUDOverlay;
import client.cn.nsimu.simukraft.client.ClientSimukraftData;
import client.cn.nsimu.simukraft.client.buildbox.BuildingBoundsRenderer;
import client.cn.nsimu.simukraft.client.buildbox.BuildingCacheService;
import client.cn.nsimu.simukraft.client.buildbox.BuildingPreviewManager;
import client.cn.nsimu.simukraft.client.city.ClientCityChunkCache;
import client.cn.nsimu.simukraft.client.city.ClientCityMapTerrainCache;
import client.cn.nsimu.simukraft.client.city.map.SimuMapManager;
import client.cn.nsimu.simukraft.client.exchange.ExchangeControlBoxScreenOpener;
import client.cn.nsimu.simukraft.client.farmland.FarmlandHoverPreview;
import client.cn.nsimu.simukraft.client.freecamera.FreeCameraManager;
import client.cn.nsimu.simukraft.client.geology.GeologicalSurveyHintOverlay;
import client.cn.nsimu.simukraft.client.path.NpcPathDebugRenderer;
import client.cn.nsimu.simukraft.client.rts.RtsMiniMapRenderer;
import client.cn.nsimu.simukraft.client.rts.RtsSelectionManager;
import client.cn.nsimu.simukraft.client.selection.TwoPointSelectionManager;
import client.cn.nsimu.simukraft.client.toast.ClientInfoToast;
import common.cn.nsimu.simukraft.SimuKraft;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = SimuKraft.MOD_ID, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderGuiPost(RenderGuiEvent.Post event) {
        if (Minecraft.getInstance().options.hideGui) {
            return;
        }
        ClientHUDOverlay.render(event);
        GeologicalSurveyHintOverlay.render(event);
        CityEntryHud.render(event.getGuiGraphics(), event.getPartialTick().getGameTimeDeltaPartialTick(true));
        RtsMiniMapRenderer.render(event.getGuiGraphics());
        RtsSelectionManager.renderHoldProgress(event.getGuiGraphics());
        if (Minecraft.getInstance().screen == null) {
            ClientInfoToast.render(event.getGuiGraphics());
        }
    }

    /**
     * onScreenRenderPost: 在屏幕控件绘制完成后渲染通知，防止控件遮挡。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        ClientInfoToast.render(event.getGuiGraphics());
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(ClientPlayerNetworkEvent.LoggingIn event) {
        SimuMapManager.shutdownIfPresent();
        SimuMapManager.getInstance().init();
        BuildingCacheService.ensureInitialized();
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientInfoToast.clear();
        RtsMiniMapRenderer.clear();
        BuildingPreviewManager.clearPreview();
        BuildingBoundsRenderer.clearAll();
        ClientCityChunkCache.getInstance().clearAllWorlds();
        ClientCityMapTerrainCache.getInstance().clear();
        ClientSimukraftData.resetAllClientState();
        ClientHUDOverlay.resetCache();
        GeologicalSurveyHintOverlay.clear();
        SimuMapManager.shutdownIfPresent();
        FreeCameraManager.deactivate();
        RtsSelectionManager.clear();
        TwoPointSelectionManager.clear();
        NpcPathDebugRenderer.clear();
        FarmlandHoverPreview.clear();
        CityEntryHud.reset();
    }

    @SubscribeEvent
    public static void onClientTickPost(ClientTickEvent.Post event) {
        if (SimuMapManager.isAvailable()) {
            SimuMapManager.getInstance().tick();
        }
        CityEntryHud.onClientTick();
        RtsSelectionManager.onClientTick();
        RtsMiniMapRenderer.onClientTick();
        ExchangeControlBoxScreenOpener.onClientTick();
        client.cn.nsimu.simukraft.client.caution.CautionTapePreview.onClientTick();
    }

    @SubscribeEvent
    public static void onClientChunkLoad(ChunkEvent.Load event) {
        if (!SimuMapManager.isAvailable() || !event.getLevel().isClientSide()) {
            return;
        }
        SimuMapManager.getInstance().onClientChunkLoaded((net.minecraft.world.level.Level) event.getLevel(), event.getChunk());
    }

    /**
     * onClientChunkUnload: 释放该区块的已扫描标记，下次加载会重新采样。
     */
    @SubscribeEvent
    public static void onClientChunkUnload(ChunkEvent.Unload event) {
        if (!SimuMapManager.isAvailable() || !event.getLevel().isClientSide()) {
            return;
        }
        SimuMapManager.getInstance().onClientChunkUnloaded(event.getChunk().getPos());
    }
}
