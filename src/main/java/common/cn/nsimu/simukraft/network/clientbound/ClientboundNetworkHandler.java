package common.cn.nsimu.simukraft.network.clientbound;

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
import common.cn.nsimu.simukraft.network.commercial.CommercialControlBoxOpenResponsePacket;
import common.cn.nsimu.simukraft.network.commercial.CommercialTradeOpenResponsePacket;
import common.cn.nsimu.simukraft.network.exchange.ExchangeControlBoxOpenResponsePacket;
import common.cn.nsimu.simukraft.network.farmland.FarmlandBoxBoundsResponsePacket;
import common.cn.nsimu.simukraft.network.farmland.FarmlandBoxOpenResponsePacket;
import common.cn.nsimu.simukraft.network.geology.GeologicalSurveyHintPacket;
import common.cn.nsimu.simukraft.network.guidebook.ReloadGuideBookPacket;
import common.cn.nsimu.simukraft.network.hud.HudSyncPacket;
import common.cn.nsimu.simukraft.network.industrial.IndustrialControlBoxOpenResponsePacket;
import common.cn.nsimu.simukraft.network.industrial.IndustrialControlBoxViewUpdatePacket;
import common.cn.nsimu.simukraft.network.manifest.ManifestHeldOpenResponsePacket;
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

/**
 * ClientboundNetworkHandler: 客户端网络响应处理抽象，避免 common 网络包直接依赖客户端实现。
 */
public interface ClientboundNetworkHandler {
    ClientboundNetworkHandler NOOP = new ClientboundNetworkHandler() {
    };

    /**
     * handleBuildingCacheReload: 处理建筑缓存刷新包。
     */
    default void handleBuildingCacheReload(BuildingCacheReloadPacket packet) {
    }

    /**
     * handleResidentialControlBoxBoundsUpdate: 处理住宅控制箱范围显示更新。
     */
    default void handleResidentialControlBoxBoundsUpdate(ResidentialControlBoxBoundsUpdatePacket packet) {
    }

    /**
     * handleResidentialControlBoxOpenResponse: 处理住宅控制箱打开响应。
     */
    default void handleResidentialControlBoxOpenResponse(ResidentialControlBoxOpenResponsePacket packet) {
    }

    /**
     * handleResidentialControlBoxViewUpdate: 处理住宅控制箱视图刷新。
     */
    default void handleResidentialControlBoxViewUpdate(ResidentialControlBoxViewUpdatePacket packet) {
    }

    /**
     * handleCityChunkSync: 处理城市区块缓存同步。
     */
    default void handleCityChunkSync(CityChunkSyncPacket packet) {
    }

    /**
     * handleCityCoreOpenResponse: 处理城市核心打开响应。
     */
    default void handleCityCoreOpenResponse(CityCoreOpenResponsePacket packet) {
    }

    /**
     * handleCityCoreMapResponse: 处理城市地图响应。
     */
    default void handleCityCoreMapResponse(CityCoreMapResponsePacket packet) {
    }

    /**
     * handleCityCoreMembersResponse: 处理城市成员界面响应。
     */
    default void handleCityCoreMembersResponse(CityCoreMembersResponsePacket packet) {
    }

    /**
     * handleCityCitizenManageResponse: 处理城市市民管理界面响应。
     */
    default void handleCityCitizenManageResponse(CityCitizenManageResponsePacket packet) {
    }

    /**
     * handleCityCitizenFamilyGraphResponse: 处理市民五代关系图画布。
     */
    default void handleCityCitizenFamilyGraphResponse(CityCitizenFamilyGraphResponsePacket packet) {
    }

    /**
     * handleFarmlandBoxBoundsResponse: 处理农田范围预览响应。
     */
    default void handleFarmlandBoxBoundsResponse(FarmlandBoxBoundsResponsePacket packet) {
    }

    /**
     * handleFarmlandBoxOpenResponse: 处理农田箱打开响应。
     */
    default void handleFarmlandBoxOpenResponse(FarmlandBoxOpenResponsePacket packet) {
    }

    /**
     * handleHudSync: 处理 HUD 数据同步。
     */
    default void handleHudSync(HudSyncPacket packet) {
    }

    /**
     * handleIndustrialControlBoxOpenResponse: 处理工业控制箱打开响应。
     */
    default void handleIndustrialControlBoxOpenResponse(IndustrialControlBoxOpenResponsePacket packet) {
    }

    /**
     * handleIndustrialControlBoxViewUpdate: 处理工业控制箱视图刷新。
     */
    default void handleIndustrialControlBoxViewUpdate(IndustrialControlBoxViewUpdatePacket packet) {
    }

    /**
     * handleCommercialControlBoxOpenResponse: 处理商业控制箱打开响应。
     */
    default void handleCommercialControlBoxOpenResponse(CommercialControlBoxOpenResponsePacket packet) {
    }

    /**
     * handleMedicalControlBoxOpenResponse：打开医疗控制箱 LDLib 界面。
     */
    default void handleMedicalControlBoxOpenResponse(MedicalControlBoxOpenResponsePacket packet) {
    }

    /**
     * handleBankControlBoxOpenResponse: 打开银行控制箱界面。
     */
    default void handleBankControlBoxOpenResponse(BankControlBoxOpenResponsePacket packet) {
    }

    /**
     * handleExchangeControlBoxOpenResponse: 打开交易所界面。
     */
    default void handleExchangeControlBoxOpenResponse(ExchangeControlBoxOpenResponsePacket packet) {
    }

    /**
     * handleCommercialTradeOpenResponse: 处理 NPC 商业交易界面响应。
     */
    default void handleCommercialTradeOpenResponse(CommercialTradeOpenResponsePacket packet) {
    }

    /**
     * handleLogisticsServerBoxOpenResponse: 处理物流服务器盒界面响应。
     */
    default void handleLogisticsServerBoxOpenResponse(LogisticsServerBoxOpenResponsePacket packet) {
    }

    /**
     * handleLogisticsClientBoxOpenResponse: 处理物流客户端盒界面响应。
     */
    default void handleLogisticsClientBoxOpenResponse(LogisticsClientBoxOpenResponsePacket packet) {
    }

    /**
     * handleLogisticsWarehouseGridResponse: 处理物流仓库 Menu 的物品快照。
     */
    default void handleLogisticsWarehouseGridResponse(LogisticsWarehouseGridResponsePacket packet) {
    }

    /**
     * handleNpcHireListResponse: 处理 NPC 雇佣列表响应。
     */
    default void handleNpcHireListResponse(NpcHireListResponsePacket packet) {
    }

    /**
     * handleEmploymentStateResponse: 处理岗位状态响应。
     */
    default void handleEmploymentStateResponse(EmploymentStateResponsePacket packet) {
    }

    /**
     * handleNpcPathDebugSync: 处理 NPC 路径调试同步。
     */
    default void handleNpcPathDebugSync(NpcPathDebugSyncPacket packet) {
    }

    /**
     * handlePlannerMaterialScanResponse: 处理规划材料扫描响应。
     */
    default void handlePlannerMaterialScanResponse(PlannerMaterialScanResponsePacket packet) {
    }

    /**
     * handleInfoToast: 处理客户端提示消息。
     */
    default void handleInfoToast(InfoToastPacket packet) {
    }

    /**
     * handleGeologicalSurveyHint: 处理地质锤准星提示。
     */
    default void handleGeologicalSurveyHint(GeologicalSurveyHintPacket packet) {
    }

    /**
     * handleReloadGuideBook: 重新读取指南书页面。
     */
    default void handleReloadGuideBook(ReloadGuideBookPacket packet) {
    }

    /**
     * handleRtsBuildingBoundsSync: 更新 RTS 客户端建筑边界快照。
     */
    default void handleRtsBuildingBoundsSync(RtsBuildingBoundsSyncPacket packet) {
    }

    /**
     * handleManifestOpenResponse: 打开贴墙清单界面。
     */
    default void handleManifestOpenResponse(ManifestOpenResponsePacket packet) {
    }

    /**
     * handleManifestHeldOpenResponse: 打开刷新后的手持清单界面。
     */
    default void handleManifestHeldOpenResponse(ManifestHeldOpenResponsePacket packet) {
    }
}
