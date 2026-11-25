package common.cn.nsimu.simukraft.network.mail;

import common.cn.nsimu.simukraft.SimuKraft;
import common.cn.nsimu.simukraft.building.BuildingTaskData;
import common.cn.nsimu.simukraft.city.CityData;
import common.cn.nsimu.simukraft.city.CityMemberData;
import common.cn.nsimu.simukraft.city.CityService;
import common.cn.nsimu.simukraft.event.BuildingConstructionEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/** 城市消息：本城建筑落成、城市成立和升级发给本城成员，城市成立和升级同时向其他城市广播。 */
public final class MailCityNewsService {
    private MailCityNewsService() {
    }

    /** 监听建造完成事件，通知该城市全部成员。 */
    public static void onConstructionComplete(BuildingConstructionEvent.Complete event) {
        BuildingTaskData task = event.task();
        if (task == null || task.cityId() == null) return;
        try {
            CityData city = CityService.findCity(event.level(), task.cityId()).orElse(null);
            if (city == null) return;
            String builder = event.citizen() == null ? "-" : event.citizen().name();
            toMembers(event.level(), city, Component.translatable("mail.simukraft.city.building_complete.subject"),
                    Component.translatable("mail.simukraft.city.building_complete.body", task.displayName(), builder));
        } catch (RuntimeException exception) {
            SimuKraft.LOGGER.error("Failed to send construction mail for city {}", task.cityId(), exception);
        }
    }

    /** 城市刚成立：给创建者发欢迎信，并向全服广播。 */
    public static void cityFounded(ServerLevel level, CityData city, String founderName) {
        if (level == null || city == null) return;
        toMembers(level, city, Component.translatable("mail.simukraft.city.founded.subject"),
                Component.translatable("mail.simukraft.city.founded.body", founderName, city.cityName()));
        MailStateService.broadcast(level.getServer(), MailCategory.CITY, key(city), Component.literal(city.cityName()),
                Component.translatable("mail.simukraft.city.news.founded.subject"),
                Component.translatable("mail.simukraft.city.news.founded.body", city.cityName(), founderName));
    }

    /** 城市升级完成：通知本城成员，并向其他城市广播。 */
    public static void cityUpgraded(ServerLevel level, CityData city, int cityLevel, String levelName) {
        if (level == null || city == null) return;
        toMembers(level, city, Component.translatable("mail.simukraft.city.upgraded.subject"),
                Component.translatable("mail.simukraft.city.upgraded.body", city.cityName(), cityLevel, levelName));
        MailStateService.broadcast(level.getServer(), MailCategory.CITY, key(city), Component.literal(city.cityName()),
                Component.translatable("mail.simukraft.city.news.upgraded.subject"),
                Component.translatable("mail.simukraft.city.news.upgraded.body", city.cityName(), cityLevel, levelName));
    }

    private static void toMembers(ServerLevel level, CityData city, Component subject, Component body) {
        for (CityMemberData member : city.members()) {
            MailStateService.deliver(level.getServer(), member.playerId(), MailCategory.CITY, key(city),
                    Component.literal(city.cityName()), subject, body, true);
        }
    }

    private static String key(CityData city) {
        return MailCategory.CITY_PREFIX + city.cityId();
    }
}
