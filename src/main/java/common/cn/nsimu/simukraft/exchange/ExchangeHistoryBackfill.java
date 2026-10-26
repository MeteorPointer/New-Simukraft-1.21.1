package common.cn.nsimu.simukraft.exchange;

import common.cn.nsimu.simukraft.economy.EconomyService;

import java.util.ArrayList;
import java.util.List;

/**
 * ExchangeHistoryBackfill: 按现有定价规则补上缺失的小时 K 线，不覆盖已经记下的柱。
 */
final class ExchangeHistoryBackfill {
    private ExchangeHistoryBackfill() {
    }

    /**
     * missing: 窗口内还没有的小时柱。接到第一根真柱之前的一段会缩放到那根柱的开盘价。
     */
    static List<ExchangeCandle> missing(ExchangeCompany company,
                                        ExchangeEconomySnapshot economy,
                                        ExchangeMarketRegime regime,
                                        List<ExchangeCandle> existing,
                                        long startDay,
                                        long endDay,
                                        int endHour) {
        if (company == null || endHour < 0 || startDay > endDay) {
            return List.of();
        }
        List<ExchangeCandle> ordered = new ArrayList<>();
        if (existing != null) {
            ordered.addAll(existing);
            ordered.sort(ExchangeHistoryBackfill::byTime);
        }
        double sector = ExchangeFundamentals.sectorScore(company.sector(), economy);
        double price = company.basePrice();
        List<ExchangeCandle> prefix = new ArrayList<>();
        List<ExchangeCandle> created = new ArrayList<>();
        boolean seenReal = false;
        for (long day = startDay; day <= endDay; day++) {
            int lastHour = day == endDay ? endHour : ExchangeMarketClock.HOURS_PER_SESSION - 1;
            for (int hour = 0; hour <= lastHour; hour++) {
                ExchangeCandle real = find(ordered, day, hour);
                if (real != null) {
                    if (!seenReal && !prefix.isEmpty()) {
                        created.addAll(scaleToOpen(prefix, real.open()));
                        prefix.clear();
                    }
                    seenReal = true;
                    price = real.close();
                    continue;
                }
                ExchangeCandle candle = step(company, economy, regime, sector, price, day, hour);
                price = candle.close();
                if (seenReal) {
                    created.add(candle);
                } else {
                    prefix.add(candle);
                }
            }
        }
        if (!seenReal) {
            created.addAll(prefix);
        }
        return List.copyOf(created);
    }

    private static ExchangeCandle step(ExchangeCompany company,
                                       ExchangeEconomySnapshot economy,
                                       ExchangeMarketRegime regime,
                                       double sector,
                                       double previous,
                                       long day,
                                       int hour) {
        double fair = ExchangeFundamentals.fairPrice(company, economy);
        double next = ExchangePriceMath.nextPrice(previous, company, fair, regime,
                ExchangeFundamentals.shock(company.id(), day, hour));
        int volume = ExchangePriceMath.volume(previous, next, 0, sector);
        return new ExchangeCandle(day, hour, previous, Math.max(previous, next), Math.min(previous, next), next, volume);
    }

    private static List<ExchangeCandle> scaleToOpen(List<ExchangeCandle> candles, double targetOpen) {
        double lastClose = candles.get(candles.size() - 1).close();
        if (lastClose <= 0.0D || targetOpen <= 0.0D) {
            return candles;
        }
        double factor = targetOpen / lastClose;
        List<ExchangeCandle> scaled = new ArrayList<>(candles.size());
        for (int i = 0; i < candles.size(); i++) {
            ExchangeCandle candle = candles.get(i);
            double open = scale(candle.open(), factor);
            double close = i == candles.size() - 1 ? EconomyService.normalizeAmount(targetOpen) : scale(candle.close(), factor);
            double high = Math.max(scale(candle.high(), factor), Math.max(open, close));
            double low = Math.min(scale(candle.low(), factor), Math.min(open, close));
            scaled.add(new ExchangeCandle(candle.marketDay(), candle.hourIndex(), open, high, low, close, candle.volume()));
        }
        return scaled;
    }

    private static double scale(double price, double factor) {
        return EconomyService.normalizeAmount(Math.max(0.01D, price * factor));
    }

    private static ExchangeCandle find(List<ExchangeCandle> candles, long day, int hour) {
        for (ExchangeCandle candle : candles) {
            if (candle.marketDay() == day && candle.hourIndex() == hour) {
                return candle;
            }
        }
        return null;
    }

    private static int byTime(ExchangeCandle left, ExchangeCandle right) {
        int day = Long.compare(left.marketDay(), right.marketDay());
        return day != 0 ? day : Integer.compare(left.hourIndex(), right.hourIndex());
    }
}
