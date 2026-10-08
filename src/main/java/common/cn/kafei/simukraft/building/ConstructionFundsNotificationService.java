package common.cn.kafei.simukraft.building;

import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.city.group.CityGroupMessageService;
import common.cn.kafei.simukraft.config.ServerConfig;
import common.cn.kafei.simukraft.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 建造扣款后城市没钱时，向城市成员发带冷却的弹窗，规则对齐材料请求。
 * 钱够继续盖之后清掉冷却，下一次再没钱可以立刻提示。
 */
public final class ConstructionFundsNotificationService {
    private static final ConcurrentMap<FundsNoticeKey, Long> NEXT_NOTICE_TICK = new ConcurrentHashMap<>();
    private static final int MAX_NOTICE_KEYS = 2048;

    private ConstructionFundsNotificationService() {
    }

    public static void notifyInsufficient(ServerLevel level, CitizenData citizen, BuildingTaskData task) {
        if (citizen == null || task == null) {
            return;
        }
        notifyInsufficient(level, task.cityId(), task.taskId(), task.displayName(), citizen.name());
    }

    public static void notifyInsufficient(ServerLevel level, UUID cityId, UUID taskId, String taskName, String citizenName) {
        if (level == null || cityId == null || taskId == null) {
            return;
        }
        long gameTime = level.getGameTime();
        FundsNoticeKey key = new FundsNoticeKey(level.dimension().location(), cityId, taskId);
        Long nextNoticeTick = NEXT_NOTICE_TICK.get(key);
        if (isCoolingDown(nextNoticeTick, gameTime)) {
            return;
        }
        cleanupExpired(gameTime);
        NEXT_NOTICE_TICK.put(key, nextAllowedTick(gameTime, ServerConfig.materialWarningCooldownTicks()));

        Component message = Component.translatable(
                "message.simukraft.construction.insufficient_funds",
                normalize(citizenName, "NPC"),
                normalize(taskName, "建筑")
        );
        CityGroupMessageService.fundsToCity(level, cityId, message, new ItemStack(ModItems.GOLD_COIN.get()));
    }

    public static void clear(ServerLevel level, UUID cityId, UUID taskId) {
        if (level == null || cityId == null || taskId == null) {
            return;
        }
        NEXT_NOTICE_TICK.remove(new FundsNoticeKey(level.dimension().location(), cityId, taskId));
    }

    /** 扣完这一笔后余额为 0，且建筑还没盖完，才提示资金不足。 */
    public static boolean shouldWarnAfterSuccessfulCharge(boolean constructionStillRunning, double remainingFunds) {
        return constructionStillRunning && remainingFunds <= 0.0D;
    }

    static boolean isCoolingDown(Long nextNoticeTick, long gameTime) {
        return nextNoticeTick != null && nextNoticeTick > gameTime;
    }

    static long nextAllowedTick(long gameTime, int cooldownTicks) {
        return gameTime + Math.max(20, cooldownTicks);
    }

    private static void cleanupExpired(long gameTime) {
        if (NEXT_NOTICE_TICK.size() < MAX_NOTICE_KEYS) {
            return;
        }
        NEXT_NOTICE_TICK.entrySet().removeIf(entry -> entry.getValue() <= gameTime);
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record FundsNoticeKey(ResourceLocation dimensionId, UUID cityId, UUID taskId) {
    }
}
