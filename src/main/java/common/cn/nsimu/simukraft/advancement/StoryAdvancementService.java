package common.cn.nsimu.simukraft.advancement;

import common.cn.nsimu.simukraft.SimuKraft;
import common.cn.nsimu.simukraft.city.group.CityUserGroup;
import common.cn.nsimu.simukraft.city.group.CityUserGroupService;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * StoryAdvancementService: 发放没有原版触发器的剧情成就。
 */
public final class StoryAdvancementService {
    private StoryAdvancementService() {
    }

    /**
     * grant: 给一名玩家发放成就。已经完成时不再弹提示。
     */
    public static void grant(ServerPlayer player, String path, String criterion) {
        if (player == null || player.getServer() == null || path == null || criterion == null) {
            return;
        }
        AdvancementHolder advancement = player.getServer().getAdvancements().get(
                ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, path));
        if (advancement == null || player.getAdvancements().getOrStartProgress(advancement).isDone()) {
            return;
        }
        player.getAdvancements().award(advancement, criterion);
    }

    /**
     * grantCity: 发给这座城市的在线成员。没有城市时发给当前维度里的玩家。
     */
    public static void grantCity(ServerLevel level, UUID cityId, String path, String criterion) {
        if (level == null) {
            return;
        }
        if (cityId == null) {
            for (ServerPlayer player : level.players()) {
                grant(player, path, criterion);
            }
            return;
        }
        for (ServerPlayer player : CityUserGroupService.onlinePlayers(level, CityUserGroup.members(cityId))) {
            grant(player, path, criterion);
        }
    }
}
