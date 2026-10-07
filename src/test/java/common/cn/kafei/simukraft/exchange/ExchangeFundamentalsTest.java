package common.cn.kafei.simukraft.exchange;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ExchangeFundamentalsTest {
    private final ExchangeCompany farm = new ExchangeCompany("mochen_dairy", "沐尘奶业", "agriculture", 0.95D, 0.018D);
    private final ExchangeCompany mine = new ExchangeCompany("laozhang_mining", "老张矿业", "mining", 1.05D, 0.030D);
    private final ExchangeCompany realty = new ExchangeCompany("xu_realty", "许氏地产", "realty", 1.30D, 0.024D);
    private final ExchangeCompany industry = new ExchangeCompany("lapis_industry", "青金石工业", "industry", 1.10D, 0.022D);

    @Test
    void idleCityKeepsIssuePriceAndFlatMarket() {
        ExchangeEconomySnapshot idle = ExchangeEconomySnapshot.idle();
        assertEquals(0.0D, ExchangeFundamentals.marketScore(idle), 0.0001D);
        assertEquals(0.0D, ExchangeFundamentals.sectorScore("agriculture", idle), 0.0001D);
        assertEquals(ExchangeMarketRegime.MIXED, ExchangeFundamentals.regime(idle));
        assertEquals(farm.basePrice(), ExchangeFundamentals.fairPrice(farm, idle), 0.0001D);
    }

    @Test
    void workingFarmsOutrunAnIdleMine() {
        ExchangeEconomySnapshot snapshot = city(12, 12, 400, 30, 20, 0, 0, 0, 1, 0, 6, 0, 0, 0, 0, 0, 5, 0, 0, 1, 0, 1);
        double farmRatio = ExchangeFundamentals.fairPrice(farm, snapshot) / farm.basePrice();
        double mineRatio = ExchangeFundamentals.fairPrice(mine, snapshot) / mine.basePrice();
        assertTrue(farmRatio > mineRatio);
        assertTrue(farmRatio > 1.0D);
        assertTrue(mineRatio < 1.0D);
    }

    @Test
    void tightHousingLiftsRealtyAndAGlutCutsIt() {
        ExchangeEconomySnapshot tight = city(30, 16, 200, 10, 10, 0, 40, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1);
        ExchangeEconomySnapshot glut = city(4, 40, 200, 10, 10, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1);
        assertTrue(ExchangeFundamentals.fairPrice(realty, tight) > ExchangeFundamentals.fairPrice(realty, glut));
    }

    @Test
    void cashAndPeopleSetTheSessionRegime() {
        ExchangeEconomySnapshot boom = city(40, 32, 2000, 150, 40, 80, 30, 40, 3, 3, 4, 1, 1, 1, 3, 4, 4, 4, 2, 3, 1, 3);
        ExchangeEconomySnapshot bust = city(3, 20, 8, 1, 50, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1);
        assertEquals(ExchangeMarketRegime.BULL, ExchangeMarketRegime.fromScore(ExchangeFundamentals.marketScore(boom)));
        assertEquals(ExchangeMarketRegime.BEAR, ExchangeMarketRegime.fromScore(ExchangeFundamentals.marketScore(bust)));
        assertTrue(ExchangeFundamentals.fairPrice(industry, boom) > industry.basePrice());
        assertTrue(ExchangeFundamentals.fairPrice(industry, bust) < industry.basePrice());
    }

    @Test
    void hourlyPriceStepsTowardFairValueAndCannotGap() {
        double fair = 1.50D;
        double rising = ExchangePriceMath.nextPrice(1.10D, industry, fair, ExchangeMarketRegime.MIXED, 0.0D);
        double falling = ExchangePriceMath.nextPrice(1.80D, industry, fair, ExchangeMarketRegime.MIXED, 0.0D);
        assertTrue(rising > 1.10D);
        assertTrue(rising <= 1.10D * 1.045D + 0.001D);
        assertTrue(falling < 1.80D);
        assertTrue(falling >= 1.80D * 0.955D - 0.001D);

        double price = 1.10D;
        for (int hour = 0; hour < 40; hour++) {
            price = ExchangePriceMath.nextPrice(price, industry, fair, ExchangeMarketRegime.MIXED, 0.0D);
        }
        assertTrue(Math.abs(fair - price) < Math.abs(fair - 1.10D));
        assertTrue(price <= fair + 0.02D);
    }

    @Test
    void tradesMoveThePrintAndStayInsideTheHourlyCollar() {
        double bought = ExchangePriceMath.tradeImpact(1.00D, industry, 50, 0.0D);
        double sold = ExchangePriceMath.tradeImpact(1.00D, industry, -50, 0.0D);
        double whale = ExchangePriceMath.tradeImpact(1.00D, industry, 10_000, 0.0D);
        assertTrue(bought > 1.00D);
        assertTrue(sold < 1.00D);
        assertEquals(1.00D, ExchangePriceMath.tradeImpact(1.00D, industry, 0, 0.0D), 0.0001D);
        assertTrue(whale <= 1.08D + 0.001D);
        assertTrue(whale >= 1.00D);
    }

    @Test
    void volumeCountsRealTradesAndTheHourlyShockIsStable() {
        int quiet = ExchangePriceMath.volume(1.00D, 1.01D, 0, 0.0D);
        int busy = ExchangePriceMath.volume(1.00D, 1.01D, 20, 0.0D);
        assertEquals(quiet + 20, busy);
        assertEquals(ExchangeFundamentals.shock("lapis_industry", 4L, 2), ExchangeFundamentals.shock("lapis_industry", 4L, 2));
        assertTrue(ExchangeFundamentals.shock("lapis_industry", 4L, 2) != ExchangeFundamentals.shock("lapis_industry", 4L, 3));
        for (int hour = 0; hour < 12; hour++) {
            double shock = ExchangeFundamentals.shock("mochen_dairy", 9L, hour);
            assertTrue(shock >= -1.0D && shock <= 1.0D);
        }
    }

    private static ExchangeEconomySnapshot city(int population, int housing, double funds, double income, double expense,
                                                 double construction, double rent, double commercialIncome,
                                                 int commercial, int industrial, int farmland, int medical,
                                                 int bank, int exchange, int rigs, int builders, int farmers,
                                                 int workers, int doctors, int clerks, int brokers, double level) {
        return new ExchangeEconomySnapshot(true, population, housing, funds, income, expense, construction, rent,
                commercialIncome, commercial, industrial, farmland, medical, bank, exchange, rigs, builders, farmers,
                workers, doctors, clerks, brokers, level);
    }
}
