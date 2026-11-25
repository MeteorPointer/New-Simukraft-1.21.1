package common.cn.nsimu.simukraft.network.mail;

import common.cn.nsimu.simukraft.citizen.CitizenData;
import common.cn.nsimu.simukraft.citizen.CitizenService;
import common.cn.nsimu.simukraft.city.CityData;
import common.cn.nsimu.simukraft.city.CityMemberData;
import common.cn.nsimu.simukraft.city.CityPermissionLevel;
import common.cn.nsimu.simukraft.city.CityPopulationStats;
import common.cn.nsimu.simukraft.city.CityService;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** 按当前存档生成邮件右侧名片，并解析回复对象。仅本包使用。 */
final class MailContactCards {
    private static final int MAX_CONTACTS = 48;
    private static final int ACK_VARIANTS = 3;

    private MailContactCards() {
    }

    /** 一次批量同步内复用的城市名片和人口统计，避免广播时对每个玩家重复统计。 */
    static final class Cache {
        private final Map<String, MailContactData> cards = new HashMap<>();
        private final Map<UUID, CityLocation> playerCities = new HashMap<>();
    }

    /** 回复对象和由回复触发的投递。 */
    record ReplyTarget(String key, List<Delivery> deliveries) {
    }

    record Delivery(UUID recipientId, String senderKey, Component sender, Component subject, Component body, boolean unread) {
    }

    record CityLocation(ServerLevel level, CityData city) {
    }

    static String ownCityKey(ServerPlayer player) {
        CityLocation city = playerCity(player.server, player.getUUID(), null);
        return city == null ? "" : MailCategory.CITY_PREFIX + city.city().cityId();
    }

    static List<MailContactData> build(ServerPlayer player, List<MailMessageData> messages, String ownCityKey, Cache cache) {
        Set<String> keys = new LinkedHashSet<>();
        if (!ownCityKey.isEmpty()) keys.add(ownCityKey);
        for (MailMessageData message : messages) {
            if (keys.size() >= MAX_CONTACTS) break;
            keys.add(message.senderKey());
        }
        CityLocation own = playerCity(player.server, player.getUUID(), cache);
        List<MailContactData> contacts = new ArrayList<>(keys.size());
        for (String key : keys) {
            MailContactData card = card(player.server, key, own, cache);
            if (card != null) contacts.add(card);
        }
        return contacts;
    }

    /** 校验回复对象：市民必须属于玩家城市，城市必须仍然存在，玩家发城市消息前必须有城市。 */
    static ReplyTarget resolveReplyTarget(ServerPlayer player, MailCategory category, String targetKey, String text) {
        if (targetKey == null) return null;
        MinecraftServer server = player.server;
        CityLocation own = playerCity(server, player.getUUID(), null);
        if (own == null) return null;
        String ownKey = MailCategory.CITY_PREFIX + own.city().cityId();
        if (category == MailCategory.CITIZEN && targetKey.startsWith(MailCategory.CITIZEN_PREFIX)) {
            CitizenData citizen = citizen(server, targetKey);
            if (citizen == null || citizen.dead() || !Objects.equals(citizen.cityId(), own.city().cityId())) return null;
            int variant = Math.floorMod(Objects.hash(citizen.uuid(), text, server.overworld().getGameTime()), ACK_VARIANTS);
            return new ReplyTarget(targetKey, List.of(new Delivery(player.getUUID(), targetKey,
                    Component.literal(citizen.name()), Component.empty(),
                    Component.translatable("mail.simukraft.citizen.ack." + variant), false)));
        }
        if (category == MailCategory.CITY && targetKey.startsWith(MailCategory.CITY_PREFIX)) {
            CityLocation target = city(server, targetKey);
            if (target == null) return null;
            boolean sameCity = target.city().cityId().equals(own.city().cityId());
            Component sender = sameCity
                    ? Component.literal(player.getGameProfile().getName())
                    : Component.translatable("mail.simukraft.sender.city_player", player.getGameProfile().getName(), own.city().cityName());
            List<Delivery> deliveries = new ArrayList<>();
            for (CityMemberData member : target.city().members()) {
                if (member.playerId().equals(player.getUUID())) continue;
                deliveries.add(new Delivery(member.playerId(), ownKey, sender, Component.empty(), Component.literal(text), true));
            }
            return new ReplyTarget(targetKey, deliveries);
        }
        return null;
    }

    /** 查找玩家所属城市及其维度；同一批同步内结果会缓存。 */
    static CityLocation playerCity(MinecraftServer server, UUID playerId, Cache cache) {
        if (cache != null && cache.playerCities.containsKey(playerId)) return cache.playerCities.get(playerId);
        CityLocation found = null;
        for (ServerLevel level : server.getAllLevels()) {
            Optional<CityData> city = CityService.findPlayerCity(level, playerId);
            if (city.isPresent() && CityService.belongsToLevel(level, city.get())) {
                found = new CityLocation(level, city.get());
                break;
            }
        }
        if (cache != null) cache.playerCities.put(playerId, found);
        return found;
    }

    static CityLocation city(MinecraftServer server, String key) {
        UUID cityId = parse(key, MailCategory.CITY_PREFIX);
        if (cityId == null) return null;
        for (ServerLevel level : server.getAllLevels()) {
            Optional<CityData> city = CityService.findCity(level, cityId);
            if (city.isPresent() && CityService.belongsToLevel(level, city.get())) return new CityLocation(level, city.get());
        }
        return null;
    }

    static CitizenData citizen(MinecraftServer server, String key) {
        UUID citizenId = parse(key, MailCategory.CITIZEN_PREFIX);
        return citizenId == null ? null : CitizenService.findCitizen(server.overworld(), citizenId).orElse(null);
    }

    private static MailContactData card(MinecraftServer server, String key, CityLocation own, Cache cache) {
        if (MailCategory.SYSTEM_KEY.equals(key)) {
            return new MailContactData(key, Component.translatable("mail.simukraft.sender.city_system"),
                    List.of(Component.translatable("mail.simukraft.contact.system.line1"),
                            Component.translatable("mail.simukraft.contact.system.line2")), "", null, 0, 0);
        }
        if (key.startsWith(MailCategory.CITIZEN_PREFIX)) return citizenCard(server, key);
        if (key.startsWith(MailCategory.CITY_PREFIX)) {
            boolean member = own != null && key.equals(MailCategory.CITY_PREFIX + own.city().cityId());
            String cacheKey = key + (member ? "|member" : "|public");
            MailContactData cached = cache.cards.get(cacheKey);
            if (cached != null) return cached;
            MailContactData card = cityCard(server, key, member);
            if (card != null) cache.cards.put(cacheKey, card);
            return card;
        }
        return null;
    }

    private static MailContactData citizenCard(MinecraftServer server, String key) {
        CitizenData citizen = citizen(server, key);
        if (citizen == null) {
            return new MailContactData(key, Component.translatable("mail.simukraft.contact.citizen.missing"),
                    List.of(Component.translatable("mail.simukraft.contact.citizen.missing_hint")), "", null, 0, 0);
        }
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("mail.simukraft.contact.citizen.job", Component.translatable(citizen.jobType().translationKey())));
        lines.add(Component.translatable("mail.simukraft.contact.citizen.age", citizen.age()));
        lines.add(Component.translatable(citizen.homeId() != null
                ? "mail.simukraft.contact.citizen.home.yes" : "mail.simukraft.contact.citizen.home.no"));
        lines.add(Component.translatable("mail.simukraft.contact.citizen.health",
                String.format(Locale.ROOT, "%.0f", citizen.health() * 5.0D)));
        lines.add(Component.translatable("mail.simukraft.contact.citizen.happiness",
                String.format(Locale.ROOT, "%.0f", citizen.happiness())));
        lines.add(citizen.dead()
                ? Component.translatable("mail.simukraft.contact.citizen.dead")
                : citizen.disease().isActive()
                ? Component.translatable("mail.simukraft.contact.citizen.sick", Component.translatable(citizen.disease().translationKey()))
                : Component.translatable("mail.simukraft.contact.citizen.well"));
        CityLocation city = citizen.cityId() == null ? null : city(server, MailCategory.CITY_PREFIX + citizen.cityId());
        if (city != null) lines.add(Component.translatable("mail.simukraft.contact.citizen.city", city.city().cityName()));
        ResourceLocation dimension = ResourceLocation.tryParse(citizen.dimensionId());
        Optional<ChunkPos> chunk = citizen.lastKnownChunk();
        if (dimension == null || chunk.isEmpty()) {
            return new MailContactData(key, Component.literal(citizen.name()), lines, citizen.skinPath(), null, 0, 0);
        }
        return new MailContactData(key, Component.literal(citizen.name()), lines, citizen.skinPath(),
                dimension, chunk.get().getMiddleBlockX(), chunk.get().getMiddleBlockZ());
    }

    private static MailContactData cityCard(MinecraftServer server, String key, boolean member) {
        CityLocation location = city(server, key);
        if (location == null) {
            return new MailContactData(key, Component.translatable("mail.simukraft.contact.city.missing"),
                    List.of(Component.translatable("mail.simukraft.contact.city.missing_hint")), "", null, 0, 0);
        }
        CityData city = location.city();
        CityPopulationStats.Snapshot stats = CityPopulationStats.snapshot(location.level(), city.cityId());
        String mayor = city.members().stream().filter(m -> m.permissionLevel() == CityPermissionLevel.MAYOR)
                .map(CityMemberData::playerName).findFirst().orElse("-");
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(member ? "mail.simukraft.contact.city.own" : "mail.simukraft.contact.city.other"));
        lines.add(Component.translatable("mail.simukraft.contact.city.level", city.cityLevel()));
        lines.add(Component.translatable("mail.simukraft.contact.city.mayor", mayor));
        lines.add(Component.translatable("mail.simukraft.contact.city.population", stats.population(), stats.housingCapacity()));
        lines.add(Component.translatable("mail.simukraft.contact.city.members", city.members().size()));
        if (member) {
            lines.add(Component.translatable("mail.simukraft.contact.city.funds", String.format(Locale.ROOT, "%.2f", city.funds())));
        }
        lines.add(Component.translatable("mail.simukraft.contact.city.core", city.cityCorePos().getX(), city.cityCorePos().getZ()));
        return new MailContactData(key, Component.literal(city.cityName()), lines, "",
                location.level().dimension().location(), city.cityCorePos().getX(), city.cityCorePos().getZ());
    }

    private static UUID parse(String key, String prefix) {
        if (key == null || !key.startsWith(prefix)) return null;
        try {
            return UUID.fromString(key.substring(prefix.length()));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
