package common.cn.nsimu.simukraft.citizen;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import common.cn.nsimu.simukraft.entity.CitizenEntity;
import common.cn.nsimu.simukraft.network.citizen.info.CitizenInfoResponsePacket;
import net.minecraft.world.entity.player.Player;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 隔离 common 容器持有器与客户端界面实现。
 */
public final class CitizenInfoUiBridge {
    private static final AtomicReference<Factory> FACTORY = new AtomicReference<>();

    private CitizenInfoUiBridge() {
    }

    /**
     * install：客户端启动时安装 LDLib 界面工厂。
     */
    public static void install(Factory factory) {
        FACTORY.set(factory);
    }

    /**
     * create：在物理客户端创建界面；服务端未安装时返回 null。
     */
    public static ModularUI create(CitizenInfoResponsePacket packet,
                                   CitizenInventory inventory,
                                   CitizenEntity owner,
                                   Player player) {
        Factory factory = FACTORY.get();
        return factory != null ? factory.create(packet, inventory, owner, player) : null;
    }

    @FunctionalInterface
    public interface Factory {
        ModularUI create(CitizenInfoResponsePacket packet,
                         CitizenInventory inventory,
                         CitizenEntity owner,
                         Player player);
    }
}
