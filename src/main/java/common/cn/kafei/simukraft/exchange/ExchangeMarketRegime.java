package common.cn.kafei.simukraft.exchange;

import java.util.Locale;

/** ExchangeMarketRegime: 当日市况牌，由城市经营得分决定，不再开盘抽签。 */
public enum ExchangeMarketRegime {
    MIXED(0.0D, 0.035D),
    BULL(0.018D, 0.022D),
    BEAR(-0.018D, 0.022D);

    private final double drift;
    private final double noise;

    ExchangeMarketRegime(double drift, double noise) {
        this.drift = drift;
        this.noise = noise;
    }

    public double drift() {
        return drift;
    }

    public double noise() {
        return noise;
    }

    public String translationKey() {
        return "gui.simukraft.exchange.regime." + name().toLowerCase(Locale.ROOT);
    }

    /** fromScore: 大盘分高于 0.16 为景气，低于 -0.16 为低迷。 */
    public static ExchangeMarketRegime fromScore(double marketScore) {
        if (marketScore >= 0.16D) {
            return BULL;
        }
        if (marketScore <= -0.16D) {
            return BEAR;
        }
        return MIXED;
    }

    public static ExchangeMarketRegime fromName(String name) {
        for (ExchangeMarketRegime regime : values()) {
            if (regime.name().equalsIgnoreCase(name)) {
                return regime;
            }
        }
        return MIXED;
    }
}
