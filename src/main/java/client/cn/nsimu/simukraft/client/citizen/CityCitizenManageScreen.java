package client.cn.nsimu.simukraft.client.citizen;

import client.cn.nsimu.simukraft.client.city.CityCoreScreenOpener;
import common.cn.nsimu.simukraft.network.citizen.manage.CityCitizenManageResponsePacket;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class CityCitizenManageScreen {
    private CityCitizenManageScreen() {
    }

    public static void open(CityCitizenManageResponsePacket packet) {
        CityCoreScreenOpener.openCitizens(packet);
    }
}
