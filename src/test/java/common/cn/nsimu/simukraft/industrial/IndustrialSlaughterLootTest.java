package common.cn.nsimu.simukraft.industrial;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * IndustrialSlaughterLootTest: 工业工人等级与屠宰掠夺等级的对应关系。
 */
class IndustrialSlaughterLootTest {
    @Test
    void lootingIsOneBelowWorkerLevel() {
        assertEquals(0, IndustrialEntityActionService.slaughterLootingLevel(1));
        assertEquals(1, IndustrialEntityActionService.slaughterLootingLevel(2));
        assertEquals(19, IndustrialEntityActionService.slaughterLootingLevel(20));
    }

    @Test
    void lootingNeverGoesNegative() {
        assertEquals(0, IndustrialEntityActionService.slaughterLootingLevel(0));
        assertEquals(0, IndustrialEntityActionService.slaughterLootingLevel(-3));
    }
}
