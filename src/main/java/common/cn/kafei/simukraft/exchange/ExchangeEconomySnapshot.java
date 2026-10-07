package common.cn.kafei.simukraft.exchange;

/**
 * ExchangeEconomySnapshot: 一个交易日的城市经营快照。
 * 价格往这份快照算出的合理价靠，不按开盘骰子走。
 */
public record ExchangeEconomySnapshot(
        boolean live,
        int population,
        int housing,
        double funds,
        double income,
        double expense,
        double constructionSpend,
        double rentIncome,
        double commercialIncome,
        int commercialPois,
        int industrialPois,
        int farmlandPois,
        int medicalPois,
        int bankPois,
        int exchangePois,
        int miningRigs,
        int builders,
        int farmers,
        int industrialWorkers,
        int doctors,
        int commercialWorkers,
        int brokers,
        double averageCityLevel) {

    /** idle: 还没有城市时，合理价停在发行价，市况平盘。 */
    public static ExchangeEconomySnapshot idle() {
        return new ExchangeEconomySnapshot(false, 0, 0, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1.0D);
    }
}
