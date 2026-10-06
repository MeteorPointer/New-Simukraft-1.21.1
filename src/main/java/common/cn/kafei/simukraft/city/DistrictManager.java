package common.cn.kafei.simukraft.city;

import common.cn.kafei.simukraft.SimuKraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import common.cn.kafei.simukraft.storage.SimuSqliteStorage;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class DistrictManager extends SavedData {
    private static final String DATA_NAME = SimuKraft.MOD_ID + "_districts";
    private static final Factory<DistrictManager> FACTORY = new Factory<>(DistrictManager::new, DistrictManager::load, null);
    private final ConcurrentMap<UUID, DistrictData> districts = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, UUID> chunkIndex = new ConcurrentHashMap<>();
    private volatile boolean sqliteLoaded;
    private volatile ServerLevel level;

    public static DistrictManager get(ServerLevel level) {
        DistrictManager manager = level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
        manager.level = level;
        manager.loadFromSqlite(level);
        return manager;
    }

    private synchronized void loadFromSqlite(ServerLevel level) {
        if (sqliteLoaded) return;
        sqliteLoaded = true;
        CompoundTag sqlite = SimuSqliteStorage.loadDistricts(level);
        // SQLite is authoritative only when it returned actual district rows. An empty or
        // unavailable read must not erase SavedData loaded from the world.
        if (sqlite == null || sqlite.isEmpty() || !sqlite.contains("Districts")) return;
        DistrictManager loaded = load(sqlite, level.registryAccess());
        districts.clear(); chunkIndex.clear(); districts.putAll(loaded.districts); chunkIndex.putAll(loaded.chunkIndex);
    }

    private void persist() {
        ServerLevel target = level();
        if (target != null) SimuSqliteStorage.saveDistricts(target, save(new CompoundTag(), target.registryAccess()));
    }

    private ServerLevel level() { return level; }

    private static DistrictManager load(CompoundTag tag, HolderLookup.Provider registries) {
        DistrictManager manager = new DistrictManager();
        ListTag list = tag.getList("Districts", CompoundTag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            DistrictData district = DistrictData.fromTag(list.getCompound(i));
            manager.districts.put(district.districtId(), district);
            district.chunks().forEach(chunk -> manager.chunkIndex.put(chunk, district.districtId()));
        }
        return manager;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag(); districts.values().forEach(d -> list.add(d.toTag())); tag.put("Districts", list); return tag;
    }

    public Collection<DistrictData> all() { return Set.copyOf(districts.values()); }
    public Optional<DistrictData> get(UUID id) { return Optional.ofNullable(districts.get(id)); }
    public Optional<DistrictData> byChunk(long chunk) { UUID id = chunkIndex.get(chunk); return id == null ? Optional.empty() : get(id); }
    public Optional<DistrictData> byChunk(ChunkPos chunk) { return chunk == null ? Optional.empty() : byChunk(chunk.toLong()); }
    public boolean nameExists(UUID cityId, String name) { return districts.values().stream().anyMatch(d -> d.parentCityId().equals(cityId) && d.name().equalsIgnoreCase(name)); }

    public synchronized DistrictData create(UUID cityId, String name, UUID mayorId, String mayorName, Set<Long> chunks) {
        name = normalizeDistrictName(name);
        if (cityId == null || !CityService.isValidCityName(name) || chunks == null || chunks.isEmpty() || nameExists(cityId, name)) return null;
        // A chunk can only belong to one district. Core chunks remain protected even
        // though they are still part of the parent city's claimed area.
        if (chunks.stream().anyMatch(chunk -> chunkIndex.containsKey(chunk))) return null;
        if (level != null && chunks.stream().anyMatch(chunk -> CityService.allCities(level).stream().anyMatch(city -> new ChunkPos(city.cityCorePos()).toLong() == chunk))) return null;
        if (level != null && chunks.stream().anyMatch(chunk -> districts.values().stream()
                .flatMap(district -> district.cores().stream())
                .anyMatch(core -> new ChunkPos(net.minecraft.core.BlockPos.of(core)).toLong() == chunk))) return null;
        UUID id = UUID.randomUUID();
        int color = stableColor(id);
        DistrictData district = new DistrictData(id, cityId, name, color);
        district.addOrUpdateMember(mayorId, mayorName, DistrictRole.MAYOR);
        for (long chunk : chunks) { district.addChunk(chunk); chunkIndex.put(chunk, id); }
        districts.put(id, district); setDirty(); persist(); return district;
    }

    public synchronized boolean assignChunks(UUID districtId, Set<Long> chunks) {
        DistrictData target = districts.get(districtId); if (target == null || chunks == null || chunks.isEmpty()) return false;
        for (long chunk : chunks) {
            if (level != null && CityService.allCities(level).stream().anyMatch(city -> new ChunkPos(city.cityCorePos()).toLong() == chunk)) return false;
            if (districts.values().stream().anyMatch(d -> d.cores().stream().mapToLong(Long::longValue).anyMatch(core -> new ChunkPos(net.minecraft.core.BlockPos.of(core)).toLong() == chunk))) return false;
        }
        for (long chunk : chunks) {
            if (level != null && CityService.allCities(level).stream().anyMatch(city -> new ChunkPos(city.cityCorePos()).toLong() == chunk)) return false;
            if (districts.values().stream().anyMatch(d -> d.cores().stream().mapToLong(Long::longValue).anyMatch(core -> new ChunkPos(net.minecraft.core.BlockPos.of(core)).toLong() == chunk))) return false;
            UUID oldId = chunkIndex.get(chunk);
            if (oldId != null && !oldId.equals(districtId)) districts.get(oldId).removeChunk(chunk);
            target.addChunk(chunk); chunkIndex.put(chunk, districtId);
        }
        setDirty(); persist(); return true;
    }

    public synchronized boolean moveToCity(UUID cityId, Set<Long> chunks) {
        if (cityId == null || chunks == null || chunks.isEmpty()) return false;
        for (long chunk : chunks) {
            if (level != null && CityService.allCities(level).stream().anyMatch(city -> new ChunkPos(city.cityCorePos()).toLong() == chunk)) return false;
            if (districts.values().stream().anyMatch(d -> d.cores().stream().mapToLong(Long::longValue).anyMatch(core -> new ChunkPos(net.minecraft.core.BlockPos.of(core)).toLong() == chunk))) return false;
        }
        for (long chunk : chunks) {
            if (level != null && CityService.allCities(level).stream().anyMatch(city -> new ChunkPos(city.cityCorePos()).toLong() == chunk)) return false;
            if (districts.values().stream().anyMatch(d -> d.cores().stream().mapToLong(Long::longValue).anyMatch(core -> new ChunkPos(net.minecraft.core.BlockPos.of(core)).toLong() == chunk))) return false;
            UUID oldId = chunkIndex.remove(chunk);
            if (oldId != null && districts.containsKey(oldId)) districts.get(oldId).removeChunk(chunk);
        }
        setDirty(); persist(); return true;
    }

    public synchronized boolean bindCore(UUID districtId, ChunkPos chunk, long corePosLong) {
        DistrictData district = districts.get(districtId);
        if (district == null || chunk == null || !district.chunks().contains(chunk.toLong())) return false;
        district.cores().forEach(existing -> { });
        district.addCore(net.minecraft.core.BlockPos.of(corePosLong)); setDirty(); persist(); return true;
    }

    public synchronized boolean unbindCore(UUID districtId, long corePosLong) {
        DistrictData district = districts.get(districtId); if (district == null) return false;
        district.removeCore(net.minecraft.core.BlockPos.of(corePosLong)); setDirty(); persist(); return true;
    }

    public synchronized boolean addMember(UUID districtId, UUID playerId, String playerName, DistrictRole role) {
        DistrictData district = districts.get(districtId);
        if (district == null || playerId == null || role == DistrictRole.MAYOR) return false;
        district.addOrUpdateMember(playerId, playerName, role); setDirty(); persist(); return true;
    }

    public synchronized boolean removeMember(UUID districtId, UUID playerId) {
        DistrictData district = districts.get(districtId);
        if (district == null || playerId == null) return false;
        DistrictMemberData member = district.member(playerId);
        if (member == null || member.role() == DistrictRole.MAYOR) return false;
        boolean removed = district.removeMember(playerId); if (removed) { setDirty(); persist(); } return removed;
    }

    public synchronized boolean setMemberRole(UUID districtId, UUID playerId, DistrictRole role) {
        DistrictData district = districts.get(districtId);
        if (district == null || playerId == null || role == null || role == DistrictRole.MAYOR) return false;
        DistrictMemberData member = district.member(playerId);
        if (member == null) return false;
        member.setRole(role); setDirty(); persist(); return true;
    }

    public synchronized boolean setMayor(UUID districtId, UUID playerId, String playerName) {
        DistrictData district = districts.get(districtId);
        if (district == null || playerId == null) return false;
        for (DistrictMemberData member : district.members()) {
            if (member.role() == DistrictRole.MAYOR) member.setRole(DistrictRole.RESIDENT);
        }
        district.addOrUpdateMember(playerId, playerName, DistrictRole.MAYOR); setDirty(); persist(); return true;
    }

    public boolean isEnabled(DistrictData district, ServerLevel level) {
        if (district == null || level == null || district.cores().isEmpty()) return false;
        return district.cores().stream().anyMatch(core -> level.getBlockState(net.minecraft.core.BlockPos.of(core)).is(common.cn.kafei.simukraft.registry.ModBlocks.CITY_CORE.get()));
    }

    public synchronized boolean rename(UUID districtId, UUID cityId, String name) {
        DistrictData district = districts.get(districtId);
        name = normalizeDistrictName(name);
        if (district == null || !district.parentCityId().equals(cityId) || !CityService.isValidCityName(name) || nameExistsExcept(cityId, name, districtId)) return false;
        district.setName(name); setDirty(); persist(); return true;
    }

    private boolean nameExistsExcept(UUID cityId, String name, UUID ignored) {
        return districts.values().stream().anyMatch(d -> d.parentCityId().equals(cityId) && !d.districtId().equals(ignored) && d.name().equalsIgnoreCase(name));
    }

    public static String normalizeName(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (!value.endsWith("区")) value += "区";
        return value;
    }

    /** Normalize using an ASCII source escape so the Chinese suffix survives source-file encoding. */
    public static String normalizeDistrictName(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (!value.endsWith("\u533a")) value += "\u533a";
        return value;
    }

    public synchronized boolean delete(UUID districtId, UUID cityId) {
        DistrictData district = districts.get(districtId);
        if (district == null || !district.parentCityId().equals(cityId)) return false;
        district.chunks().forEach(chunkIndex::remove);
        districts.remove(districtId); setDirty(); persist(); return true;
    }

    public static int stableColor(UUID id) {
        int[] colors = {0xFF4FC3F7, 0xFFFFB74D, 0xFF81C784, 0xFFE57373, 0xFFBA68C8, 0xFFFFD54F, 0xFF4DB6AC, 0xFFA1887F};
        return colors[Math.floorMod(id.hashCode(), colors.length)];
    }
}
