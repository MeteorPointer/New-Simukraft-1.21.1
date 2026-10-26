package common.cn.nsimu.simukraft.exchange;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExchangeHistoryBackfillTest {
    private final ExchangeCompany company = new ExchangeCompany("lapis_industry", "青金石工业", "industry", 1.10D, 0.022D);

    @Test
    void emptyBookFillsEveryHourInTheWindow() {
        List<ExchangeCandle> candles = ExchangeHistoryBackfill.missing(
                company, ExchangeEconomySnapshot.idle(), ExchangeMarketRegime.MIXED, List.of(), 0L, 1L, 1);
        assertEquals(ExchangeMarketClock.HOURS_PER_SESSION + 2, candles.size());
        assertEquals(0L, candles.getFirst().marketDay());
        assertEquals(0, candles.getFirst().hourIndex());
        assertEquals(1L, candles.getLast().marketDay());
        assertEquals(1, candles.getLast().hourIndex());
        assertEquals(candles.getFirst().close(), candles.get(1).open(), 0.0001D);
    }

    @Test
    void prefixConnectsToTheFirstRealOpen() {
        ExchangeCandle real = new ExchangeCandle(1L, 0, 2.0D, 2.1D, 1.9D, 2.05D, 3);
        List<ExchangeCandle> candles = ExchangeHistoryBackfill.missing(
                company, ExchangeEconomySnapshot.idle(), ExchangeMarketRegime.MIXED, List.of(real), 0L, 1L, 2);
        assertEquals(ExchangeMarketClock.HOURS_PER_SESSION + 2, candles.size());
        assertEquals(2.0D, candles.get(ExchangeMarketClock.HOURS_PER_SESSION - 1).close(), 0.0001D);
        assertEquals(0L, candles.get(ExchangeMarketClock.HOURS_PER_SESSION - 1).marketDay());
        assertTrue(candles.stream().noneMatch(candle -> candle.marketDay() == 1L && candle.hourIndex() == 0));
        assertEquals(2, candles.getLast().hourIndex());
    }

    @Test
    void completeWindowAddsNothing() {
        ExchangeCandle first = new ExchangeCandle(5L, 0, 1.0D, 1.0D, 1.0D, 1.0D, 1);
        ExchangeCandle second = new ExchangeCandle(5L, 1, 1.0D, 1.0D, 1.0D, 1.0D, 1);
        List<ExchangeCandle> candles = ExchangeHistoryBackfill.missing(
                company, ExchangeEconomySnapshot.idle(), ExchangeMarketRegime.MIXED, List.of(first, second), 5L, 5L, 1);
        assertTrue(candles.isEmpty());
    }
}
