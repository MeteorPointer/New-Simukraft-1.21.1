package common.cn.nsimu.simukraft.material;

import common.cn.nsimu.simukraft.city.group.CityGroupMessageService;
import common.cn.nsimu.simukraft.config.ServerConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class WorkMaterialNotificationService {
    private static final ConcurrentMap<MaterialNoticeKey, Long> NEXT_NOTICE_TICK = new ConcurrentHashMap<>();
    private static final int MAX_NOTICE_KEYS = 2048;

    private WorkMaterialNotificationService() {
    }

    public static void notifyMissing(ServerLevel level, UUID cityId, UUID taskId, String taskName, String citizenName, WorkMaterialResult result) {
        if (level == null || cityId == null || taskId == null || result == null || result.available()) {
            return;
        }
        long gameTime = level.getGameTime();
        MaterialNoticeKey key = new MaterialNoticeKey(level.dimension().location(), cityId, taskId, result.materialId());
        Long nextNoticeTick = NEXT_NOTICE_TICK.get(key);
        if (nextNoticeTick != null && nextNoticeTick > gameTime) {
            return;
        }
        cleanupExpired(gameTime);
        NEXT_NOTICE_TICK.put(key, gameTime + Math.max(20, ServerConfig.materialWarningCooldownTicks()));

        Component message = Component.translatable(
                "message.simukraft.material.missing",
                normalize(citizenName, "NPC"),
                normalize(taskName, "建筑"),
                result.acceptedMaterialsComponent()
        );
        ItemStack requested = result.requested();
        CityGroupMessageService.materialToCity(level, cityId, message, requested);
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

    private record MaterialNoticeKey(ResourceLocation dimensionId, UUID cityId, UUID taskId, String materialName) {
    }
}
