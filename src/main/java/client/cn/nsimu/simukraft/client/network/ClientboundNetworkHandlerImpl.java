package client.cn.nsimu.simukraft.client.network;

import client.cn.nsimu.simukraft.client.ClientSimukraftData;
import client.cn.nsimu.simukraft.client.bank.BankControlBoxScreenOpener;
import client.cn.nsimu.simukraft.client.buildbox.BuildBoxScreenOpener;
import client.cn.nsimu.simukraft.client.buildbox.BuildingBoundsRenderer;
import client.cn.nsimu.simukraft.client.buildbox.BuildingCacheService;
import client.cn.nsimu.simukraft.client.buildbox.PlannerMaterialSelectionScreenOpener;
import client.cn.nsimu.simukraft.client.citizen.CityCitizenManageScreen;
import client.cn.nsimu.simukraft.client.city.CityCoreScreenOpener;
import client.cn.nsimu.simukraft.client.city.ClientCityChunkCache;
import client.cn.nsimu.simukraft.client.commercial.CommercialControlBoxScreenOpener;
import client.cn.nsimu.simukraft.client.compat.ClientCompatHooks;
import client.cn.nsimu.simukraft.client.controlbox.ResidentialControlBoxScreenOpener;
import client.cn.nsimu.simukraft.client.exchange.ExchangeControlBoxScreenOpener;
import client.cn.nsimu.simukraft.client.farmland.FarmlandBoxScreenOpener;
import client.cn.nsimu.simukraft.client.farmland.FarmlandHoverPreview;
import client.cn.nsimu.simukraft.client.geology.GeologicalSurveyHintOverlay;
import client.cn.nsimu.simukraft.client.guidebook.GuideBookLang;
import client.cn.nsimu.simukraft.client.guidebook.GuideBookLoader;
import client.cn.nsimu.simukraft.client.hire.NpcHireScreen;
import client.cn.nsimu.simukraft.client.industrial.IndustrialControlBoxScreenOpener;
import client.cn.nsimu.simukraft.client.logistics.LogisticsClientBoxScreenOpener;
import client.cn.nsimu.simukraft.client.logistics.LogisticsServerBoxScreenOpener;
import client.cn.nsimu.simukraft.client.manifest.ManifestScreen;
import client.cn.nsimu.simukraft.client.medical.MedicalControlBoxScreenOpener;
import client.cn.nsimu.simukraft.client.path.NpcPathDebugRenderer;
import client.cn.nsimu.simukraft.client.toast.ClientInfoToast;
import common.cn.nsimu.simukraft.commercial.CommercialTradeUiRoot;
import common.cn.nsimu.simukraft.network.bank.BankControlBoxOpenResponsePacket;
import common.cn.nsimu.simukraft.network.building.BuildingCacheReloadPacket;
import common.cn.nsimu.simukraft.network.building.controlbox.ResidentialControlBoxBoundsUpdatePacket;
import common.cn.nsimu.simukraft.network.building.controlbox.ResidentialControlBoxOpenResponsePacket;
import common.cn.nsimu.simukraft.network.building.controlbox.ResidentialControlBoxViewUpdatePacket;
import common.cn.nsimu.simukraft.network.citizen.manage.CityCitizenFamilyGraphResponsePacket;
import common.cn.nsimu.simukraft.network.citizen.manage.CityCitizenManageResponsePacket;
import common.cn.nsimu.simukraft.network.city.chunk.CityChunkSyncPacket;
import common.cn.nsimu.simukraft.network.city.core.CityCoreOpenResponsePacket;
import common.cn.nsimu.simukraft.network.city.map.CityCoreMapResponsePacket;
import common.cn.nsimu.simukraft.network.city.member.CityCoreMembersResponsePacket;
import common.cn.nsimu.simukraft.network.clientbound.ClientboundNetworkHandler;
import common.cn.nsimu.simukraft.network.commercial.CommercialControlBoxOpenResponsePacket;
import common.cn.nsimu.simukraft.network.commercial.CommercialTradeOpenResponsePacket;
import common.cn.nsimu.simukraft.network.exchange.ExchangeControlBoxOpenResponsePacket;
import common.cn.nsimu.simukraft.network.farmland.FarmlandBoxBoundsResponsePacket;
import common.cn.nsimu.simukraft.network.farmland.FarmlandBoxOpenResponsePacket;
import common.cn.nsimu.simukraft.network.geology.GeologicalSurveyHintPacket;
import common.cn.nsimu.simukraft.network.guidebook.ReloadGuideBookPacket;
import common.cn.nsimu.simukraft.network.hud.HudSyncPacket;
import common.cn.nsimu.simukraft.network.industrial.IndustrialControlBoxOpenResponsePacket;
import common.cn.nsimu.simukraft.network.manifest.ManifestHeldOpenResponsePacket;
import common.cn.nsimu.simukraft.network.industrial.IndustrialControlBoxViewUpdatePacket;
import common.cn.nsimu.simukraft.network.manifest.ManifestOpenResponsePacket;
import common.cn.nsimu.simukraft.network.logistics.LogisticsClientBoxOpenResponsePacket;
import common.cn.nsimu.simukraft.network.logistics.LogisticsServerBoxOpenResponsePacket;
import common.cn.nsimu.simukraft.network.logistics.LogisticsWarehouseGridResponsePacket;
import common.cn.nsimu.simukraft.network.medical.MedicalControlBoxOpenResponsePacket;
import common.cn.nsimu.simukraft.network.npc.hire.NpcHireListResponsePacket;
import common.cn.nsimu.simukraft.network.npc.state.EmploymentStateResponsePacket;
import common.cn.nsimu.simukraft.network.path.NpcPathDebugSyncPacket;
import common.cn.nsimu.simukraft.network.planner.PlannerMaterialScanResponsePacket;
import common.cn.nsimu.simukraft.network.rts.RtsBuildingBoundsSyncPacket;
import common.cn.nsimu.simukraft.network.toast.InfoToastPacket;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ClientboundNetworkHandlerImpl: 客户端网络响应实现，二次封装具体 UI 与渲染缓存调用。
 */
@OnlyIn(Dist.CLIENT)
public final class ClientboundNetworkHandlerImpl implements ClientboundNetworkHandler {
    public static final ClientboundNetworkHandlerImpl INSTANCE = new ClientboundNetworkHandlerImpl();

    private ClientboundNetworkHandlerImpl() {
    }

    @Override
    public void handleBuildingCacheReload(BuildingCacheReloadPacket packet) {
        BuildingCacheService.reload();
    }

    @Override
    public void handleRtsBuildingBoundsSync(RtsBuildingBoundsSyncPacket packet) {
        BuildingBoundsRenderer.setRtsBuildingBounds(packet.entries().stream()
                .map(entry -> new BuildingBoundsRenderer.RtsBuildingBounds(
                        new net.minecraft.world.phys.AABB(
                                Math.min(entry.min().getX(), entry.max().getX()),
                                Math.min(entry.min().getY(), entry.max().getY()),
                                Math.min(entry.min().getZ(), entry.max().getZ()),
                                Math.max(entry.min().getX(), entry.max().getX()) + 1.0D,
                                Math.max(entry.min().getY(), entry.max().getY()) + 1.0D,
                                Math.max(entry.min().getZ(), entry.max().getZ()) + 1.0D),
                        entry.displayName()))
                .toList());
    }

    @Override
    public void handleResidentialControlBoxBoundsUpdate(ResidentialControlBoxBoundsUpdatePacket packet) {
        BuildingBoundsRenderer.updateDisplayedBuildingBounds(
                packet.controlBoxPos(),
                packet.hasBuildingBounds(),
                packet.boundsMin(),
                packet.boundsMax(),
                packet.residentialPoiPositions()
        );
    }

    @Override
    public void handleResidentialControlBoxOpenResponse(ResidentialControlBoxOpenResponsePacket packet) {
        ResidentialControlBoxScreenOpener.open(packet);
    }

    @Override
    public void handleResidentialControlBoxViewUpdate(ResidentialControlBoxViewUpdatePacket packet) {
        ResidentialControlBoxScreenOpener.refreshIfOpen(packet.view());
    }

    @Override
    public void handleCityChunkSync(CityChunkSyncPacket packet) {
        ClientCityChunkCache cache = ClientCityChunkCache.getInstance();
        Map<UUID, ClientCityChunkCache.CityCoreEntry> cores = new ConcurrentHashMap<>();
        packet.cityCores().forEach((cityId, core) -> cores.put(cityId, new ClientCityChunkCache.CityCoreEntry(core.pos(), core.cityName())));
        cache.updateAllCityChunks(packet.currentCityId(), packet.cityChunks());
        cache.updateAllCityCores(cores);
        cache.updateDistricts(packet.districts());
        ClientCompatHooks.refreshXaeroCityHighlights();
    }

    @Override
    public void handleCityCoreOpenResponse(CityCoreOpenResponsePacket packet) {
        CityCoreScreenOpener.open(packet);
    }

    @Override
    public void handleCityCoreMapResponse(CityCoreMapResponsePacket packet) {
        CityCoreScreenOpener.openMap(packet);
    }

    @Override
    public void handleCityCoreMembersResponse(CityCoreMembersResponsePacket packet) {
        CityCoreScreenOpener.openMembers(packet);
    }

    @Override
    public void handleCityCitizenManageResponse(CityCitizenManageResponsePacket packet) {
        CityCitizenManageScreen.open(packet);
    }

    @Override
    public void handleCityCitizenFamilyGraphResponse(CityCitizenFamilyGraphResponsePacket packet) {
        CityCoreScreenOpener.openFamilyGraph(packet);
    }

    @Override
    public void handleFarmlandBoxBoundsResponse(FarmlandBoxBoundsResponsePacket packet) {
        FarmlandHoverPreview.receiveBounds(packet.pos(), packet.hasPlot(), packet.min(), packet.max());
    }

    @Override
    public void handleFarmlandBoxOpenResponse(FarmlandBoxOpenResponsePacket packet) {
        FarmlandBoxScreenOpener.open(packet);
    }

    @Override
    public void handleHudSync(HudSyncPacket packet) {
        ClientSimukraftData.setCurrentDay(packet.currentDay());
        ClientSimukraftData.setCurrentPopulation(packet.worldPopulation());
        ClientSimukraftData.setCurrentCityName(packet.cityName());
        ClientSimukraftData.setCurrentCityFunds(packet.cityFunds());
        ClientSimukraftData.setCurrentCityPopulation(packet.cityPopulation());
        ClientSimukraftData.setPermissionLevel(packet.permissionLevel());
        ClientSimukraftData.setCreativeMode(packet.creativeMode());
    }

    @Override
    public void handleIndustrialControlBoxOpenResponse(IndustrialControlBoxOpenResponsePacket packet) {
        IndustrialControlBoxScreenOpener.open(packet);
    }

    @Override
    public void handleIndustrialControlBoxViewUpdate(IndustrialControlBoxViewUpdatePacket packet) {
        IndustrialControlBoxScreenOpener.refreshIfOpen(packet.view());
    }

    @Override
    public void handleCommercialControlBoxOpenResponse(CommercialControlBoxOpenResponsePacket packet) {
        CommercialControlBoxScreenOpener.open(packet);
    }

    @Override
    public void handleMedicalControlBoxOpenResponse(MedicalControlBoxOpenResponsePacket packet) {
        MedicalControlBoxScreenOpener.open(packet);
    }

    @Override
    public void handleBankControlBoxOpenResponse(BankControlBoxOpenResponsePacket packet) {
        BankControlBoxScreenOpener.open(packet);
    }

    @Override
    public void handleExchangeControlBoxOpenResponse(ExchangeControlBoxOpenResponsePacket packet) {
        ExchangeControlBoxScreenOpener.open(packet);
    }

    @Override
    public void handleCommercialTradeOpenResponse(CommercialTradeOpenResponsePacket packet) {
        CommercialTradeUiRoot.refreshActive(packet);
    }

    @Override
    public void handleLogisticsServerBoxOpenResponse(LogisticsServerBoxOpenResponsePacket packet) {
        LogisticsServerBoxScreenOpener.open(packet);
    }

    @Override
    public void handleLogisticsClientBoxOpenResponse(LogisticsClientBoxOpenResponsePacket packet) {
        LogisticsClientBoxScreenOpener.open(packet);
    }

    @Override
    public void handleLogisticsWarehouseGridResponse(LogisticsWarehouseGridResponsePacket packet) {
        // 推给旧版原生仓库页；服务端快照保留完整 ItemStack 组件，避免 NBT 物品串货。
        LogisticsServerBoxScreenOpener.pushWarehouseItems(
                packet.pos(),
                new java.util.ArrayList<>(packet.items()),
                new java.util.ArrayList<>(packet.actualCounts()));
    }

    @Override
    public void handleNpcHireListResponse(NpcHireListResponsePacket packet) {
        NpcHireScreen.open(packet);
    }

    @Override
    public void handleEmploymentStateResponse(EmploymentStateResponsePacket packet) {
        BuildBoxScreenOpener.applyEmploymentState(packet);
    }

    @Override
    public void handleNpcPathDebugSync(NpcPathDebugSyncPacket packet) {
        NpcPathDebugRenderer.update(packet);
    }

    @Override
    public void handlePlannerMaterialScanResponse(PlannerMaterialScanResponsePacket packet) {
        PlannerMaterialSelectionScreenOpener.open(packet);
    }

    @Override
    public void handleInfoToast(InfoToastPacket packet) {
        ClientInfoToast.show(packet.title(), packet.message(), packet.style(), packet.iconStack());
    }

    /**
     * handleGeologicalSurveyHint: 显示地质锤准星提示。
     */
    @Override
    public void handleGeologicalSurveyHint(GeologicalSurveyHintPacket packet) {
        if (packet.typewriter()) {
            GeologicalSurveyHintOverlay.showTypewriter(packet.message(), packet.lingerMillis());
        } else {
            GeologicalSurveyHintOverlay.show(packet.message());
        }
    }

    @Override
    public void handleReloadGuideBook(ReloadGuideBookPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getResourceManager() == null) {
            return;
        }
        GuideBookLang.reload(minecraft.getResourceManager());
        GuideBookLoader.reload(minecraft.getResourceManager());
    }

    /**
     * handleManifestOpenResponse: 打开贴墙清单界面。
     */
    @Override
    public void handleManifestOpenResponse(ManifestOpenResponsePacket packet) {
        ManifestScreen.openFromBlock(packet.stack(), packet.pos());
    }

    /**
     * handleManifestHeldOpenResponse: 打开刷新后的手持清单。
     */
    @Override
    public void handleManifestHeldOpenResponse(ManifestHeldOpenResponsePacket packet) {
        ManifestScreen.open(packet.stack(), packet.hand());
    }
}
