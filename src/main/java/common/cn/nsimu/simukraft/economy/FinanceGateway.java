package common.cn.nsimu.simukraft.economy;

import common.cn.nsimu.simukraft.city.CityService;
import common.cn.nsimu.simukraft.city.FinanceTransactionData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/** 城市财政统一网关：所有收支和流水记录都从这里进入。 */
public final class FinanceGateway {
    private FinanceGateway() {
    }

    /** 入账并记录收入流水；必须在服务端主线程调用。 */
    public static boolean deposit(ServerLevel level, UUID cityId, ServerPlayer actor, double amount, String reason) {
        return deposit(level, cityId, actor, amount, reason, true);
    }

    /** 入账并按需记录收入流水，供已由外层补记流水的原子流程使用。 */
    public static boolean deposit(ServerLevel level, UUID cityId, ServerPlayer actor, double amount,
                                  String reason, boolean recordLedger) {
        double normalized = EconomyService.normalizeAmount(amount);
        if (level == null || cityId == null || normalized <= 0.0D || !CityService.depositFunds(level, cityId, normalized)) {
            return false;
        }
        if (recordLedger) record(level, cityId, actor, normalized, EconomyService.getCityBalance(level, cityId),
                FinanceTransactionData.Type.INCOME, reason);
        return true;
    }

    /** 扣款并记录支出流水；余额校验和扣款在服务端完成。 */
    public static boolean withdraw(ServerLevel level, UUID cityId, ServerPlayer actor, double amount, String reason) {
        return withdraw(level, cityId, actor, amount, reason, true);
    }

    /** 扣款并按需记录支出流水，供需要事务回滚的流程使用。 */
    public static boolean withdraw(ServerLevel level, UUID cityId, ServerPlayer actor, double amount,
                                   String reason, boolean recordLedger) {
        double normalized = EconomyService.normalizeAmount(amount);
        if (level == null || cityId == null || normalized <= 0.0D || !CityService.withdrawFunds(level, cityId, normalized)) {
            return false;
        }
        if (recordLedger) record(level, cityId, actor, normalized, EconomyService.getCityBalance(level, cityId),
                FinanceTransactionData.Type.EXPENSE, reason);
        return true;
    }

    /** 统一记录已经完成余额变更的流水，兼容原子业务的提交阶段。 */
    public static void record(ServerLevel level, UUID cityId, ServerPlayer actor, double amount,
                              double balanceAfter, FinanceTransactionData.Type type, String reason) {
        double normalized = type == FinanceTransactionData.Type.SYSTEM
                ? amount : EconomyService.normalizeAmount(Math.abs(amount));
        FinanceLedgerService.record(level, cityId, actor, normalized, balanceAfter, type, reason);
    }
}
