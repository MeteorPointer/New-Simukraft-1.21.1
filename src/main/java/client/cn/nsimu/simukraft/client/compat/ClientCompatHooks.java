package client.cn.nsimu.simukraft.client.compat;

import common.cn.nsimu.simukraft.SimuKraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.ModList;

@OnlyIn(Dist.CLIENT)
public final class ClientCompatHooks {
    private static final String XAERO_WORLD_MAP_MOD_ID = "xaeroworldmap";
    private static final String XAERO_INTEGRATION_CLASS = "client.cn.nsimu.simukraft.client.compat.xaero.XaeroWorldMapIntegration";

    private ClientCompatHooks() {
    }

    /** refreshXaeroCityHighlights: Xaero 存在时刷新城市区块高亮缓存。 */
    public static void refreshXaeroCityHighlights() {
        if (!ModList.get().isLoaded(XAERO_WORLD_MAP_MOD_ID)) {
            return;
        }
        try {
            Class<?> integration = Class.forName(XAERO_INTEGRATION_CLASS);
            integration.getMethod("refreshCityHighlights").invoke(null);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            SimuKraft.LOGGER.warn("Simukraft: Failed to invoke Xaero city highlight refresh.", exception);
        }
    }
}
