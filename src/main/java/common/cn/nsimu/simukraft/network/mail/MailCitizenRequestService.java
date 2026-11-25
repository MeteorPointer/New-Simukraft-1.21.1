package common.cn.nsimu.simukraft.network.mail;

import common.cn.nsimu.simukraft.SimuKraft;
import common.cn.nsimu.simukraft.citizen.CitizenData;
import common.cn.nsimu.simukraft.citizen.CitizenService;
import common.cn.nsimu.simukraft.city.CityData;
import common.cn.nsimu.simukraft.city.CityMemberData;
import common.cn.nsimu.simukraft.city.CityService;
import common.cn.nsimu.simukraft.config.ServerConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/**
 * 市民诉求邮件：市民发现城市缺住房、缺岗位、生病或不开心时，给城市成员写信。
 *
 * <p>每隔 {@code citizenRequestIntervalTicks} 用游标检查少量城市，每座城市一次最多发一封，
 * 同类诉求按 {@code citizenRequestCooldownDays} 冷却，冷却日写进 mail_schedule。
 */
public final class MailCitizenRequestService {
    private static final double LOW_HAPPINESS = 40.0D;
    private static final int MIN_ADULTS_FOR_HAPPINESS = 3;
    private static final ConcurrentHashMap<MinecraftServer, AtomicInteger> CURSORS = new ConcurrentHashMap<>();

    private MailCitizenRequestService() {
    }

    /** 诉求种类，按优先级排列。 */
    private enum Kind {
        HOUSING(citizen -> citizen.homeId() == null),
        JOBS(CitizenService::isHireable),
        MEDICAL(citizen -> citizen.disease().isActive()),
        HAPPINESS(citizen -> citizen.happiness() < LOW_HAPPINESS);

        private final Predicate<CitizenData> affected;

        Kind(Predicate<CitizenData> affected) {
            this.affected = affected;
        }
    }

    public static void tick(MinecraftServer server) {
        if (server == null || server.overworld() == null) return;
        long gameTime = server.overworld().getGameTime();
        if (gameTime % Math.max(100, ServerConfig.mailCitizenRequestIntervalTicks()) != 0L) return;
        List<CityRef> cities = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            for (CityData city : CityService.allCities(level)) cities.add(new CityRef(level, city));
        }
        if (cities.isEmpty()) return;
        AtomicInteger cursor = CURSORS.computeIfAbsent(server, key -> new AtomicInteger());
        int count = Math.min(cities.size(), Math.max(1, ServerConfig.mailCitizenRequestCitiesPerPass()));
        for (int i = 0; i < count; i++) {
            CityRef ref = cities.get(Math.floorMod(cursor.getAndIncrement(), cities.size()));
            try {
                checkCity(server, ref.city(), gameTime);
            } catch (RuntimeException exception) {
                SimuKraft.LOGGER.error("Failed to check citizen requests for city {}", ref.city().cityId(), exception);
            }
        }
    }

    static void clearServerCaches(MinecraftServer server) {
        if (server != null) CURSORS.remove(server);
    }

    private static void checkCity(MinecraftServer server, CityData city, long gameTime) {
        if (city.members().isEmpty()) return;
        List<CitizenData> adults = CitizenService.listCitizensByCity(server.overworld(), city.cityId()).stream()
                .filter(citizen -> !citizen.child())
                .toList();
        if (adults.isEmpty()) return;
        long day = gameTime / 24000L;
        int cooldown = Math.max(1, ServerConfig.mailCitizenRequestCooldownDays());
        for (Kind kind : Kind.values()) {
            String key = "request:" + city.cityId() + ":" + kind.name();
            Long last = MailScheduleStore.get(server, key);
            if (last != null && day - last < cooldown) continue;
            List<CitizenData> affected = adults.stream().filter(kind.affected).toList();
            if (!applies(kind, adults, affected)) continue;
            CitizenData speaker = affected.get(Math.floorMod((int) (gameTime / 20L), affected.size()));
            MailScheduleStore.put(server, key, day);
            send(server, city, kind, speaker, affected.size());
            return;
        }
    }

    private static boolean applies(Kind kind, List<CitizenData> adults, List<CitizenData> affected) {
        if (affected.isEmpty()) return false;
        if (kind != Kind.HAPPINESS) return true;
        if (adults.size() < MIN_ADULTS_FOR_HAPPINESS) return false;
        return adults.stream().mapToDouble(CitizenData::happiness).average().orElse(100.0D) < LOW_HAPPINESS;
    }

    private static void send(MinecraftServer server, CityData city, Kind kind, CitizenData speaker, int affectedCount) {
        String prefix = "mail.simukraft.request." + kind.name().toLowerCase(java.util.Locale.ROOT);
        String senderKey = MailCategory.CITIZEN_PREFIX + speaker.uuid();
        Component sender = Component.literal(speaker.name());
        Component subject = Component.translatable(prefix + ".subject");
        Component body = Component.translatable(prefix + ".body", speaker.name(), affectedCount, city.cityName());
        for (CityMemberData member : city.members()) {
            UUID recipient = member.playerId();
            MailStateService.deliver(server, recipient, MailCategory.CITIZEN, senderKey, sender, subject, body, true);
        }
    }

    private record CityRef(ServerLevel level, CityData city) {
    }
}
