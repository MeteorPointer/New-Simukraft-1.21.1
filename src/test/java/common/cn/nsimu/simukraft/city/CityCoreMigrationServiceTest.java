package common.cn.nsimu.simukraft.city;

import common.cn.nsimu.simukraft.network.city.core.CityCoreManageCityPacket;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CityCoreMigrationServiceTest {
    @Test
    void cost_isTwoHundred() {
        assertEquals(200.0D, CityCoreMigrationService.COST);
    }

    @Test
    void managePacket_acceptsMigrateAction() {
        assertEquals(CityCoreManageCityPacket.Action.MIGRATE, CityCoreManageCityPacket.Action.fromName("MIGRATE"));
        assertEquals(CityCoreManageCityPacket.Action.MIGRATE, CityCoreManageCityPacket.Action.fromName("migrate"));
    }

    @Test
    void migrateTarget_rejectsNullInput() {
        assertTrue(CityCoreMigrationService.migrateTarget(null, null, null).isEmpty());
    }
}
