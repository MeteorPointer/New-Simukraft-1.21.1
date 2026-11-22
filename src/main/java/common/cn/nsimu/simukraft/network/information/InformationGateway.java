package common.cn.nsimu.simukraft.network.information;

import common.cn.nsimu.simukraft.network.mail.MailStateService;
import common.cn.nsimu.simukraft.city.group.CityGroupMessageService;
import common.cn.nsimu.simukraft.city.group.CityUserGroup;
import common.cn.nsimu.simukraft.network.toast.InfoToastService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/** 统一信息入口：将系统邮件、测试消息接入邮件状态与客户端同步链路。 */
public final class InformationGateway {
    private InformationGateway() {
    }

    /** 投递系统邮件，服务端业务层只需依赖此门面。 */
    public static int sendSystemMail(ServerPlayer player, String sender, String subject, String body, long timestamp) {
        return MailStateService.addSystemMail(player, sender, subject, body, timestamp);
    }

    /** 投递测试邮件，供调试命令验证统一信息链路。 */
    public static int sendTestMail(ServerPlayer player) {
        return MailStateService.receiveTest(player);
    }

    /** 统一普通提示入口，保留现有 Toast 客户端表现。 */
    public static void sendToast(ServerPlayer player, Component message) {
        InfoToastService.send(player, message);
    }

    /** 统一城市群组消息入口，当前复用既有城市成员解析和 Toast 分发。 */
    public static int sendCityMessage(ServerLevel level, UUID cityId, Component message) {
        return CityGroupMessageService.sendToCity(level, cityId, message);
    }

    /** 带样式和图标的城市信息入口。 */
    public static int sendCityMessage(ServerLevel level, UUID cityId, Component message,
                                      String style, ItemStack iconStack) {
        return CityGroupMessageService.send(level, CityUserGroup.members(cityId),
                Component.translatable("toast.simukraft.title"), message, style, iconStack);
    }
}
