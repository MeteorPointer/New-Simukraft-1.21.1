package common.cn.nsimu.simukraft.guidebook;

import common.cn.nsimu.simukraft.SimuKraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/**
 * 把指南书索引加入资源重载。
 */
@EventBusSubscriber(modid = SimuKraft.MOD_ID)
public final class GuideBookReloadEvents {
    private GuideBookReloadEvents() {
    }

    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(GuideBookReloadListener.INSTANCE);
    }
}
