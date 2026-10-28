package common.cn.nsimu.simukraft.network.city;

import common.cn.nsimu.simukraft.city.CityData;
import common.cn.nsimu.simukraft.city.CityPermissionLevel;
import common.cn.nsimu.simukraft.network.city.core.CityCoreOpenResponsePacket;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CityNetworkViewFactoryTest {
    @Test
    void buildOpenResponseSkipsUnusedOpenStats() {
        UUID cityId = UUID.randomUUID();
        UUID mayorId = UUID.randomUUID();
        BlockPos pos = new BlockPos(8, 64, 8);
        CityData city = new CityData(cityId, "Harbor", mayorId, "Mayor", pos);

        CityCoreOpenResponsePacket packet = CityNetworkViewFactory.buildOpenResponse(
                null, pos, Optional.of(city), CityPermissionLevel.MAYOR, false, true);

        assertTrue(packet.hasCity());
        assertEquals("Harbor", packet.cityName());
        assertEquals(cityId, packet.cityId());
        assertTrue(packet.poiStats().isEmpty());
        assertTrue(packet.jobStats().isEmpty());
        assertEquals(0, packet.cityEnclaveCount());
        assertEquals(1, packet.cityMembers().size());
        assertEquals(mayorId, packet.cityMembers().getFirst().playerId());
    }

    @Test
    void buildOpenResponseEmptyCitySkipsCityLookups() {
        BlockPos pos = new BlockPos(0, 64, 0);
        CityCoreOpenResponsePacket packet = CityNetworkViewFactory.buildOpenResponse(
                null, pos, Optional.empty(), CityPermissionLevel.CITIZEN, true, false);

        assertFalse(packet.hasCity());
        assertTrue(packet.poiStats().isEmpty());
        assertTrue(packet.jobStats().isEmpty());
        assertEquals(0, packet.cityEnclaveCount());
    }
}
