package common.cn.kafei.simukraft.exchange;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExchangeEconomyProbeTest {
    @Test
    void captureWithoutAWorldStaysIdle() {
        assertEquals(ExchangeEconomySnapshot.idle(), ExchangeEconomyProbe.capture(null));
    }

    @Test
    void exchangeBankCommandAndLogisticsCashflowDoNotCountAsOperations() {
        assertTrue(ExchangeEconomyProbe.skipped("exchange_buy"));
        assertTrue(ExchangeEconomyProbe.skipped("exchange_sell"));
        assertTrue(ExchangeEconomyProbe.skipped("command_give"));
        assertTrue(ExchangeEconomyProbe.skipped("bank_deposit"));
        assertTrue(ExchangeEconomyProbe.skipped("logistics_fee"));
        assertFalse(ExchangeEconomyProbe.skipped("construction"));
        assertFalse(ExchangeEconomyProbe.skipped("residential_rent"));
        assertFalse(ExchangeEconomyProbe.skipped("commercial_tax"));
        assertFalse(ExchangeEconomyProbe.skipped(null));
        assertFalse(ExchangeEconomyProbe.skipped(""));
    }
}
