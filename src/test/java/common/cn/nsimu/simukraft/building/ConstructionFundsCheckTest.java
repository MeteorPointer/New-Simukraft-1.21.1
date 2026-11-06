package common.cn.nsimu.simukraft.building;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConstructionFundsCheckTest {
    /**
     * isShortWhenCostExceedsFunds: 费用高于余额时需要询问。
     */
    @Test
    void isShortWhenCostExceedsFunds() {
        assertTrue(ConstructionFundsCheck.isShort(20.0D, 5.0D));
        assertFalse(ConstructionFundsCheck.isShort(5.0D, 20.0D));
        assertFalse(ConstructionFundsCheck.isShort(10.0D, 10.0D));
        assertFalse(ConstructionFundsCheck.isShort(0.0D, 0.0D));
    }
}
