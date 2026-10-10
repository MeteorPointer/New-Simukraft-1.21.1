package common.cn.kafei.simukraft.exchange;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ExchangePriceMathTest {
    private final ExchangeCompany industry = new ExchangeCompany("lapis_industry", "青金石工业", "industry", 1.10D, 0.022D);

    @Test
    void simpleMovingAverage_needsFullWindow() {
        List<ExchangeCandle> candles = List.of(
                candle(1.0D), candle(2.0D), candle(3.0D), candle(4.0D), candle(5.0D));
        assertTrue(Double.isNaN(ExchangePriceMath.simpleMovingAverage(candles, 3, 5)));
        assertTrue(Double.isNaN(ExchangePriceMath.simpleMovingAverage(null, 4, 3)));
        assertTrue(Double.isNaN(ExchangePriceMath.simpleMovingAverage(candles, 4, 0)));
        assertEquals(3.0D, ExchangePriceMath.simpleMovingAverage(candles, 4, 5), 0.0001D);
        assertEquals(4.0D, ExchangePriceMath.simpleMovingAverage(candles, 4, 3), 0.0001D);
    }

    @Test
    void usedTradeRoomBlocksASecondWhalePrint() {
        double first = ExchangePriceMath.tradeImpact(1.00D, industry, 10_000, 0.0D);
        assertTrue(first <= 1.08D + 0.001D);
        assertEquals(1.00D, ExchangePriceMath.tradeImpact(1.00D, industry, 10_000, 0.08D), 0.0001D);
        assertEquals(1.00D, ExchangePriceMath.tradeImpact(1.00D, industry, 0, 0.0D), 0.0001D);
    }

    @Test
    void volumeAddsOnlyPositivePlayerFills() {
        int quiet = ExchangePriceMath.volume(1.00D, 1.00D, 0, 0.0D);
        assertEquals(quiet, ExchangePriceMath.volume(1.00D, 1.00D, -20, 0.0D));
        assertEquals(quiet + 20, ExchangePriceMath.volume(1.00D, 1.00D, 20, 0.0D));
        assertTrue(ExchangePriceMath.volume(1.00D, 1.10D, 0, 1.0D) > quiet);
    }

    @Test
    void nextPriceUsesFairWhenCurrentIsMissingAndStaysOffZero() {
        double fromZero = ExchangePriceMath.nextPrice(0.0D, industry, 1.50D, ExchangeMarketRegime.MIXED, 0.0D);
        assertTrue(fromZero >= 0.01D);
        double rising = ExchangePriceMath.nextPrice(1.10D, industry, 1.50D, null, 0.0D);
        assertTrue(rising > 1.10D);
        assertTrue(rising <= 1.10D * 1.045D + 0.001D);
    }

    private static ExchangeCandle candle(double close) {
        return new ExchangeCandle(0L, 0, close, close, close, close, 1);
    }
}
