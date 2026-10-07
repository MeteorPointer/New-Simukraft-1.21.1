package common.cn.kafei.simukraft.exchange;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.city.CityData;
import common.cn.kafei.simukraft.city.CityPopulationStats;
import common.cn.kafei.simukraft.city.CityService;
import common.cn.kafei.simukraft.city.FinanceTransactionData;
import common.cn.kafei.simukraft.city.poi.CityPoiManager;
import common.cn.kafei.simukraft.city.poi.CityPoiType;
import common.cn.kafei.simukraft.job.CityJobAssignmentService;
import common.cn.kafei.simukraft.job.CityJobType;
import common.cn.kafei.simukraft.mineraldrilling.MineralDrillingBoxManager;
import net.minecraft.server.level.ServerLevel;

import java.util.Collection;

/** ExchangeEconomyProbe: 每个交易日读一次主世界城市，供当天的合理价使用。 */
public final class ExchangeEconomyProbe {
    private ExchangeEconomyProbe() {
    }

    /** capture: 汇总人口、财政、岗位和钻井。读失败时退回空市，价格停在发行价附近。 */
    public static ExchangeEconomySnapshot capture(ServerLevel level) {
        if (level == null) {
            return ExchangeEconomySnapshot.idle();
        }
        try {
            return captureUnchecked(level);
        } catch (RuntimeException exception) {
            SimuKraft.LOGGER.warn("Exchange economy snapshot failed", exception);
            return ExchangeEconomySnapshot.idle();
        }
    }

    private static ExchangeEconomySnapshot captureUnchecked(ServerLevel level) {
        Collection<CityData> cities = CityService.allCities(level);
        if (cities.isEmpty()) {
            return ExchangeEconomySnapshot.idle();
        }
        CityPoiManager pois = CityPoiManager.get(level);
        long since = level.getGameTime() - ExchangeMarketClock.TICKS_PER_DAY;
        int population = 0;
        int housing = 0;
        double funds = 0.0D;
        double income = 0.0D;
        double expense = 0.0D;
        double constructionSpend = 0.0D;
        double rentIncome = 0.0D;
        double commercialIncome = 0.0D;
        int commercialPois = 0;
        int industrialPois = 0;
        int farmlandPois = 0;
        int medicalPois = 0;
        int bankPois = 0;
        int exchangePois = 0;
        int builders = 0;
        int farmers = 0;
        int industrialWorkers = 0;
        int doctors = 0;
        int commercialWorkers = 0;
        int brokers = 0;
        double levelSum = 0.0D;
        int counted = 0;
        for (CityData city : cities) {
            if (city == null || city.cityId() == null) {
                continue;
            }
            counted++;
            funds += city.funds();
            levelSum += city.cityLevel();
            CityPopulationStats.Snapshot people = CityPopulationStats.snapshot(level, city.cityId());
            population += people.population();
            housing += people.housingCapacity();
            commercialPois += count(pois, city, CityPoiType.COMMERCIAL);
            industrialPois += count(pois, city, CityPoiType.INDUSTRIAL);
            farmlandPois += count(pois, city, CityPoiType.FARMLAND);
            medicalPois += count(pois, city, CityPoiType.MEDICAL);
            bankPois += count(pois, city, CityPoiType.BANK);
            exchangePois += count(pois, city, CityPoiType.EXCHANGE);
            builders += assigned(level, city, CityJobType.BUILDER);
            farmers += assigned(level, city, CityJobType.FARMER);
            industrialWorkers += assigned(level, city, CityJobType.INDUSTRIAL_WORKER);
            doctors += assigned(level, city, CityJobType.DOCTOR);
            commercialWorkers += assigned(level, city, CityJobType.COMMERCIAL_WORKER);
            brokers += assigned(level, city, CityJobType.BROKER);
            Ledger ledger = ledger(city, since);
            income += ledger.income;
            expense += ledger.expense;
            constructionSpend += ledger.construction;
            rentIncome += ledger.rent;
            commercialIncome += ledger.commercial;
        }
        if (counted == 0) {
            return ExchangeEconomySnapshot.idle();
        }
        int miningRigs = miningRigs(level);
        return new ExchangeEconomySnapshot(true, population, housing, funds, income, expense, constructionSpend, rentIncome,
                commercialIncome, commercialPois, industrialPois, farmlandPois, medicalPois, bankPois,
                exchangePois, miningRigs, builders, farmers, industrialWorkers, doctors, commercialWorkers, brokers,
                levelSum / counted);
    }

    private static int count(CityPoiManager pois, CityData city, CityPoiType type) {
        if (pois == null) {
            return 0;
        }
        return pois.getCityPois(city.cityId(), type).size();
    }

    private static int assigned(ServerLevel level, CityData city, CityJobType job) {
        return CityJobAssignmentService.getAssignedCount(level, city.cityId(), job);
    }

    private static int miningRigs(ServerLevel level) {
        try {
            return MineralDrillingBoxManager.get(level).all().size();
        } catch (RuntimeException exception) {
            SimuKraft.LOGGER.warn("Exchange probe skipped mining rigs", exception);
            return 0;
        }
    }

    private static Ledger ledger(CityData city, long since) {
        Ledger ledger = new Ledger();
        for (FinanceTransactionData transaction : city.financeTransactions()) {
            if (transaction == null || transaction.time() < since || skipped(transaction.reason())) {
                continue;
            }
            String reason = transaction.reason() == null ? "" : transaction.reason();
            if (transaction.type() == FinanceTransactionData.Type.INCOME) {
                ledger.income += transaction.amount();
                if ("residential_rent".equals(reason)) {
                    ledger.rent += transaction.amount();
                }
                if (reason.startsWith("commercial_") || "visitor_commercial_tax".equals(reason)) {
                    ledger.commercial += transaction.amount();
                }
            } else if (transaction.type() == FinanceTransactionData.Type.EXPENSE) {
                ledger.expense += transaction.amount();
                if ("construction".equals(reason)) {
                    ledger.construction += transaction.amount();
                }
            }
        }
        return ledger;
    }

    /** skipped: 转账、命令发钱和股票自己的成交不算经营现金流。 */
    private static boolean skipped(String reason) {
        if (reason == null || reason.isEmpty()) {
            return false;
        }
        return reason.startsWith("exchange_")
                || reason.startsWith("command_")
                || reason.startsWith("bank_")
                || reason.startsWith("logistics_");
    }

    private static final class Ledger {
        private double income;
        private double expense;
        private double construction;
        private double rent;
        private double commercial;
    }
}
