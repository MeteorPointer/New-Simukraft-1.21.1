package common.cn.kafei.simukraft.city;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

public final class DistrictMemberData {
    private final UUID playerId;
    private String playerName;
    private DistrictRole role;

    public DistrictMemberData(UUID playerId, String playerName, DistrictRole role) {
        this.playerId = playerId;
        this.playerName = playerName == null ? "" : playerName;
        this.role = role == null ? DistrictRole.RESIDENT : role;
    }

    public static DistrictMemberData fromTag(CompoundTag tag) {
        return new DistrictMemberData(tag.getUUID("PlayerId"), tag.getString("PlayerName"),
                DistrictRole.fromPower(tag.getInt("Role")));
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("PlayerId", playerId);
        tag.putString("PlayerName", playerName);
        tag.putInt("Role", role.power());
        return tag;
    }

    public UUID playerId() { return playerId; }
    public String playerName() { return playerName; }
    public DistrictRole role() { return role; }
    public void setPlayerName(String value) { if (value != null && !value.isBlank()) playerName = value; }
    public void setRole(DistrictRole value) { if (value != null) role = value; }
}
