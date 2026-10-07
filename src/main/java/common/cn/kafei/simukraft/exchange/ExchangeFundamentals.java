package common.cn.kafei.simukraft.exchange;

import common.cn.kafei.simukraft.economy.EconomyService;

import java.util.Locale;

/**
 * ExchangeFundamentals: 把城市经营折成大盘和各行业的合理价。
 * 建筑看在建支出和建筑师，地产看入住率与房租，农业看农田和农夫，
 * 工业看工厂和工人，矿业看钻井，医疗看医院、医生和人口，
 * 传媒看人口和商铺，娱乐看人口和财力，科技看城市等级和交易所。
 */
public final class ExchangeFundamentals {
    private ExchangeFundamentals() {
    }

    /** marketScore: 资金流、人均财政、人口，范围 -1 到 1。没有城市时为 0。 */
    public static double marketScore(ExchangeEconomySnapshot snapshot) {
        if (snapshot == null || !snapshot.live()) {
            return 0.0D;
        }
        double perCapita = snapshot.population() <= 0 ? snapshot.funds() : snapshot.funds() / snapshot.population();
        double treasury = saturate(perCapita, 30.0D) * 2.0D - 1.0D;
        double flow = snapshot.income() + snapshot.expense();
        double cash = flow <= 1.0D ? 0.0D : clamp((snapshot.income() - snapshot.expense()) / flow, -1.0D, 1.0D);
        double people = saturate(snapshot.population(), 10.0D) * 2.0D - 0.5D;
        return clamp(0.40D * cash + 0.35D * treasury + 0.25D * people, -1.0D, 1.0D);
    }

    /** regime: 大盘分决定当日市况牌。 */
    public static ExchangeMarketRegime regime(ExchangeEconomySnapshot snapshot) {
        return ExchangeMarketRegime.fromScore(marketScore(snapshot));
    }

    /**
     * sectorScore: 行业相对大盘的景气，范围 -1 到 1。
     * 没有城市时为 0，合理价就等于发行价。
     */
    public static double sectorScore(String sector, ExchangeEconomySnapshot snapshot) {
        if (snapshot == null || !snapshot.live()) {
            return 0.0D;
        }
        double market = marketScore(snapshot);
        String key = sector == null ? "" : sector.toLowerCase(Locale.ROOT);
        if ("realty".equals(key)) {
            return realtyScore(snapshot, market);
        }
        if ("other".equals(key) || key.isEmpty()) {
            return market;
        }
        double activity = switch (key) {
            case "construction" -> blend(snapshot.builders(), 3.0D, snapshot.constructionSpend(), 40.0D, 0.55D);
            case "agriculture" -> blend(snapshot.farmlandPois(), 2.0D, snapshot.farmers(), 3.0D, 0.60D);
            case "industry" -> blend(snapshot.industrialPois(), 2.0D, snapshot.industrialWorkers(), 3.0D, 0.60D);
            case "mining" -> saturate(snapshot.miningRigs(), 2.0D);
            case "medical" -> 0.40D * saturate(snapshot.medicalPois(), 1.0D)
                    + 0.25D * saturate(snapshot.doctors(), 2.0D)
                    + 0.35D * saturate(snapshot.population(), 16.0D);
            case "media" -> 0.40D * saturate(snapshot.population(), 12.0D)
                    + 0.25D * saturate(snapshot.commercialPois(), 2.0D)
                    + 0.20D * saturate(snapshot.commercialWorkers(), 3.0D)
                    + 0.15D * saturate(snapshot.commercialIncome(), 30.0D);
            case "entertainment" -> 0.40D * saturate(snapshot.population(), 10.0D)
                    + 0.25D * saturate(snapshot.funds(), 200.0D)
                    + 0.20D * saturate(snapshot.commercialPois(), 2.0D)
                    + 0.15D * saturate(snapshot.commercialIncome(), 30.0D);
            case "tech" -> 0.30D * saturate(Math.max(0.0D, snapshot.averageCityLevel() - 1.0D), 2.0D)
                    + 0.25D * saturate(snapshot.population(), 15.0D)
                    + 0.25D * saturate(snapshot.brokers() + snapshot.exchangePois(), 2.0D)
                    + 0.20D * saturate(snapshot.bankPois(), 1.0D);
            default -> 0.35D;
        };
        double local = (activity - 0.35D) * 1.4D;
        return clamp(local * 0.82D + market * 0.18D, -1.0D, 1.0D);
    }

    /** fairPrice: 发行价乘上大盘和行业。一天之内价格往这个价靠，不会一步到位。 */
    public static double fairPrice(ExchangeCompany company, ExchangeEconomySnapshot snapshot) {
        double base = company != null ? company.basePrice() : 1.0D;
        double market = marketScore(snapshot);
        double sector = sectorScore(company != null ? company.sector() : "", snapshot);
        double multiplier = clamp(1.0D + 0.22D * market + 0.48D * sector, 0.55D, 1.85D);
        return EconomyService.normalizeAmount(Math.max(0.01D, base * multiplier));
    }

    /** shock: 同一公司、同一小时永远得到同一个 -1 到 1 的扰动，用来做买卖价差，不决定方向。 */
    public static double shock(String companyId, long day, int hour) {
        long mixed = day * 0x9E3779B97F4A7C15L ^ (long) hour * 0xBF58476D1CE4E5B9L;
        if (companyId != null) {
            mixed ^= (long) companyId.hashCode() * 0x94D049BB133111EBL;
        }
        mixed ^= mixed >>> 30;
        mixed *= 0xBF58476D1CE4E5B9L;
        mixed ^= mixed >>> 27;
        mixed *= 0x94D049BB133111EBL;
        mixed ^= mixed >>> 31;
        long bits = (mixed >>> 11) & ((1L << 53) - 1);
        return bits / (double) (1L << 53) * 2.0D - 1.0D;
    }

    private static double realtyScore(ExchangeEconomySnapshot snapshot, double market) {
        double occupancy = snapshot.housing() <= 0
                ? (snapshot.population() > 0 ? 1.0D : 0.5D)
                : snapshot.population() / (double) snapshot.housing();
        double demand = clamp((occupancy - 0.62D) * 1.8D, -1.0D, 1.0D);
        double rent = saturate(snapshot.rentIncome(), 15.0D) * 2.0D - 0.4D;
        return clamp(demand * 0.65D + clamp(rent, -1.0D, 1.0D) * 0.20D + market * 0.15D, -1.0D, 1.0D);
    }

    private static double blend(double primary, double primaryHalf, double secondary, double secondaryHalf, double primaryWeight) {
        return primaryWeight * saturate(primary, primaryHalf) + (1.0D - primaryWeight) * saturate(secondary, secondaryHalf);
    }

    private static double saturate(double value, double half) {
        if (value <= 0.0D || half <= 0.0D) {
            return 0.0D;
        }
        return value / (value + half);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
