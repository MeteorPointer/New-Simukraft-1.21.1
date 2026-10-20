package common.cn.nsimu.simukraft.job;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CityJobMobilityServiceTest {
    @Test
    void resolveHireRole_mapsPublicBuildingStaff() {
        assertEquals(CityJobType.DOCTOR, CityJobMobilityService.resolveHireRole("doctor"));
        assertEquals(CityJobType.BANKER, CityJobMobilityService.resolveHireRole("teller"));
        assertEquals(CityJobType.BANKER, CityJobMobilityService.resolveHireRole("banker"));
        assertEquals(CityJobType.BROKER, CityJobMobilityService.resolveHireRole("broker"));
        assertEquals(CityJobType.MARKET_TRADER, CityJobMobilityService.resolveHireRole("market_trader"));
    }
}
