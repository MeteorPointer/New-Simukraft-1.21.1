package common.cn.kafei.simukraft.exchange;

import common.cn.kafei.simukraft.economy.EconomyService;

import java.util.List;

/** ExchangePriceMath: 每小时向合理价回归，并计入这一小时的净买卖。 */
public final class ExchangePriceMath {
    private static final double HOURLY_STEP = 0.045D;
    private static final double TRADE_ROOM = 0.08D;

    private ExchangePriceMath() {
    }

    /**
     * nextPrice: 向合理价走近一截。shock 只贡献公司自己的价差，离合理价越远越小。
     * 净买卖用 tradeImpact，在这一步之前单独加上。
     */
    public static double nextPrice(double current, ExchangeCompany company, double fairPrice, ExchangeMarketRegime regime, double shock) {
        double fair = fairPrice > 0.0D ? fairPrice : company.basePrice();
        double price = current > 0.0D ? current : fair;
        double gap = (fair - price) / fair;
        double reversion = clamp(gap * 0.12D, -0.03D, 0.03D);
        double drift = regime == null ? 0.0D : regime.drift() * 0.15D;
        double noiseScale = company.volatility() * 0.35D / (1.0D + Math.abs(gap) * 3.0D);
        double change = clamp(reversion + drift + clamp(shock, -1.0D, 1.0D) * noiseScale, -HOURLY_STEP, HOURLY_STEP);
        return EconomyService.normalizeAmount(Math.max(0.01D, price * (1.0D + change)));
    }

    /**
     * tradeImpact: 净买单推高、净卖单压低。单小时最多 8%，大单也撞不到天花板以外。
     * signedShares 为正表示这一小时净买入。
     */
    public static double tradeImpact(double price, ExchangeCompany company, int signedShares, double impactAlreadyUsed) {
        if (signedShares == 0 || price <= 0.0D || company == null) {
            return price > 0.0D ? price : (company != null ? company.basePrice() : 0.01D);
        }
        double liquidity = 90.0D - company.volatility() * 800.0D;
        liquidity = clamp(liquidity, 40.0D, 120.0D);
        double pressure = signedShares / (Math.abs(signedShares) + liquidity);
        double change = pressure * 0.06D;
        double room = Math.max(0.0D, TRADE_ROOM - Math.max(0.0D, impactAlreadyUsed));
        change = clamp(change, -room, room);
        return EconomyService.normalizeAmount(Math.max(0.01D, price * (1.0D + change)));
    }

    /** volume: 行业越忙、波动越大、玩家成交越多，量越大。 */
    public static int volume(double previous, double next, int tradedShares, double sectorScore) {
        double move = previous <= 0.0D ? 0.0D : Math.abs(next - previous) / previous;
        double activity = clamp((sectorScore + 1.0D) * 0.5D, 0.0D, 1.0D);
        int organic = (int) Math.round(4.0D + activity * 28.0D + move * 160.0D);
        return Math.max(1, organic + Math.max(0, tradedShares));
    }

    /** simpleMovingAverage: 收盘价简单均线，样本不足返回 NaN。 */
    public static double simpleMovingAverage(List<ExchangeCandle> candles, int index, int period) {
        if (candles == null || period <= 0 || index < period - 1 || index >= candles.size()) {
            return Double.NaN;
        }
        double sum = 0.0D;
        for (int i = index - period + 1; i <= index; i++) {
            sum += candles.get(i).close();
        }
        return sum / period;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
