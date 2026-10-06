package common.cn.kafei.simukraft.city;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class DistrictData {
    private final UUID districtId;
    private final UUID parentCityId;
    private String name;
    private final int color;
    private final Set<Long> chunks = ConcurrentHashMap.newKeySet();
    private final Set<Long> cores = ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<UUID, DistrictMemberData> members = new ConcurrentHashMap<>();

    public DistrictData(UUID districtId, UUID parentCityId, String name, int color) {
        this.districtId = districtId;
        this.parentCityId = parentCityId;
        this.name = name == null ? "District" : name.trim();
        this.color = color;
    }

    public static DistrictData fromTag(CompoundTag tag) {
        DistrictData data = new DistrictData(tag.getUUID("DistrictId"), tag.getUUID("ParentCityId"),
                tag.getString("Name"), tag.getInt("Color"));
        ListTag chunks = tag.getList("Chunks", LongTag.TAG_LONG);
        for (int i = 0; i < chunks.size(); i++) data.chunks.add(((LongTag) chunks.get(i)).getAsLong());
        ListTag cores = tag.getList("Cores", LongTag.TAG_LONG);
        for (int i = 0; i < cores.size(); i++) data.cores.add(((LongTag) cores.get(i)).getAsLong());
        ListTag members = tag.getList("Members", CompoundTag.TAG_COMPOUND);
        for (int i = 0; i < members.size(); i++) {
            DistrictMemberData member = DistrictMemberData.fromTag(members.getCompound(i));
            data.members.put(member.playerId(), member);
        }
        return data;
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("DistrictId", districtId);
        tag.putUUID("ParentCityId", parentCityId);
        tag.putString("Name", name);
        tag.putInt("Color", color);
        ListTag chunkTags = new ListTag(); chunks.forEach(v -> chunkTags.add(LongTag.valueOf(v))); tag.put("Chunks", chunkTags);
        ListTag coreTags = new ListTag(); cores.forEach(v -> coreTags.add(LongTag.valueOf(v))); tag.put("Cores", coreTags);
        ListTag memberTags = new ListTag(); members.values().forEach(v -> memberTags.add(v.toTag())); tag.put("Members", memberTags);
        return tag;
    }

    public UUID districtId() { return districtId; }
    public UUID parentCityId() { return parentCityId; }
    public synchronized String name() { return name; }
    public synchronized void setName(String value) { if (value != null && !value.isBlank()) name = value.trim(); }
    public int color() { return color; }
    public Set<Long> chunks() { return Collections.unmodifiableSet(chunks); }
    public Set<Long> cores() { return Collections.unmodifiableSet(cores); }
    public DistrictMemberData member(UUID playerId) { return members.get(playerId); }
    public Set<DistrictMemberData> members() { return Set.copyOf(members.values()); }
    public void addChunk(long chunk) { chunks.add(chunk); }
    public void removeChunk(long chunk) { chunks.remove(chunk); }
    public void addCore(BlockPos pos) { if (pos != null) cores.add(pos.asLong()); }
    public void removeCore(BlockPos pos) { if (pos != null) cores.remove(pos.asLong()); }
    public boolean hasPermission(UUID playerId, DistrictRole role) {
        DistrictMemberData member = member(playerId);
        return member != null && member.role().atLeast(role);
    }
    public void addOrUpdateMember(UUID playerId, String playerName, DistrictRole role) {
        if (playerId == null) return;
        members.compute(playerId, (id, current) -> {
            if (current == null) return new DistrictMemberData(id, playerName, role);
            current.setPlayerName(playerName); current.setRole(role); return current;
        });
    }
    public boolean removeMember(UUID playerId) { return playerId != null && members.remove(playerId) != null; }
}
