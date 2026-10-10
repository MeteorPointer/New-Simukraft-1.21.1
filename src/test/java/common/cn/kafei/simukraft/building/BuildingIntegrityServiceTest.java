package common.cn.kafei.simukraft.building;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildingIntegrityServiceTest {
    @Test
    void snapshotPercentIsIntactOverTotalAndFullWhenEmpty() {
        assertEquals(70.0D, new BuildingIntegrityService.IntegritySnapshot(true, 10, 7).percent(), 0.0001D);
        assertEquals(100.0D, new BuildingIntegrityService.IntegritySnapshot(true, 0, 0).percent(), 0.0001D);
        assertEquals(0.0D, new BuildingIntegrityService.IntegritySnapshot(true, 8, 0).percent(), 0.0001D);
    }

    @Test
    void repairResultCountsSuccessAndMaterialsRequiredSeparately() {
        assertTrue(new BuildingIntegrityService.RepairResult(
                BuildingIntegrityService.RepairStatus.SUCCESS, 3, 0, 1.5D).success());
        assertTrue(new BuildingIntegrityService.RepairResult(
                BuildingIntegrityService.RepairStatus.NO_REPAIR_NEEDED, 0, 0, 0.0D).success());
        assertFalse(new BuildingIntegrityService.RepairResult(
                BuildingIntegrityService.RepairStatus.MATERIALS_REQUIRED, 0, 2, 0.0D).success());
        assertFalse(new BuildingIntegrityService.RepairResult(
                BuildingIntegrityService.RepairStatus.NOT_ENOUGH_FUNDS, 4, 0, 2.0D).success());
        assertFalse(new BuildingIntegrityService.RepairResult(
                BuildingIntegrityService.RepairStatus.UNAVAILABLE, 0, 0, 0.0D).success());
    }
}
