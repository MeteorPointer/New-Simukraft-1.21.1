package common.cn.kafei.simukraft.city.group;

import common.cn.kafei.simukraft.network.toast.InfoToastService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public final class CityGroupMessageService {
    private CityGroupMessageService() {
    }

    // sendToCity: 向城市全体在线成员发送普通消息。
    public static int sendToCity(ServerLevel level, UUID cityId, Component message) {
        return send(level, CityUserGroup.members(cityId), Component.translatable("toast.simukraft.title"), message, "info", ItemStack.EMPTY);
    }

    // successToCity: 向城市全体在线成员发送成功事件消息。
    public static int successToCity(ServerLevel level, UUID cityId, Component message) {
        return send(level, CityUserGroup.members(cityId), Component.translatable("toast.simukraft.title"), message, "success", ItemStack.EMPTY);
    }

    // warningToCity: 向城市全体在线成员发送警告事件消息。
    public static int warningToCity(ServerLevel level, UUID cityId, Component message) {
        return send(level, CityUserGroup.members(cityId), Component.translatable("toast.simukraft.title"), message, "warning", ItemStack.EMPTY);
    }

    // materialToCity: 向城市用户组发送材料缺失消息。
    public static int materialToCity(ServerLevel level, UUID cityId, Component message, ItemStack iconStack) {
        return send(level, CityUserGroup.members(cityId), Component.translatable("toast.simukraft.material_title"), message, "warning", iconStack);
    }

    // fundsToCity: 向城市用户组发送建造资金不足消息。
    public static int fundsToCity(ServerLevel level, UUID cityId, Component message, ItemStack iconStack) {
        return send(level, CityUserGroup.members(cityId), Component.translatable("toast.simukraft.funds_title"), message, "warning", iconStack);
    }

    // send: 解析用户组并发送消息。
    public static int send(ServerLevel level, CityUserGroup group, Component title, Component message, String style, ItemStack iconStack) {
        return sendResolved(CityUserGroupService.onlinePlayers(level, group), title, message, style, iconStack);
    }

    // sendResolved: 向已解析的用户组快照发送消息。
    public static int sendResolved(Collection<ServerPlayer> players, Component title, Component message, String style, ItemStack iconStack) {
        if (players == null || players.isEmpty() || message == null) {
            return 0;
        }
        AtomicInteger sent = new AtomicInteger();
        players.forEach(player -> {
            if (player == null) {
                return;
            }
            InfoToastService.send(player, title, message, style, iconStack);
            sent.incrementAndGet();
        });
        return sent.get();
    }
}
