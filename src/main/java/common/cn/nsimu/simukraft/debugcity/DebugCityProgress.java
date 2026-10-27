package common.cn.nsimu.simukraft.debugcity;

import common.cn.nsimu.simukraft.SimuKraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.List;
import java.util.UUID;

/**
 * 调试城市的生成进度，避免重进世界后重复铺城。
 */
public final class DebugCityProgress extends SavedData {
    private static final String DATA_NAME = SimuKraft.MOD_ID + "_debug_city";
    private static final Factory<DebugCityProgress> FACTORY = new Factory<>(DebugCityProgress::new, DebugCityProgress::load, null);
    private static final int PHASE_WAITING = 0;
    private static final int PHASE_CLAIMING = 1;
    private static final int PHASE_POPULATING = 2;

    private boolean configured;
    private boolean done;
    private boolean announced;
    private int phase = PHASE_WAITING;
    private int citizenCount = 1;
    private int side = 1;
    private int claimIndex;
    private int citizenIndex;
    private UUID cityId;
    private transient List<DebugCityLayout.Cell> occupiedCells;

    public DebugCityProgress() {
    }

    /**
     * get：读取当前世界的调试城市进度。
     */
    public static DebugCityProgress get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    private static DebugCityProgress load(CompoundTag tag, HolderLookup.Provider registries) {
        DebugCityProgress progress = new DebugCityProgress();
        progress.configured = tag.getBoolean("Configured");
        progress.done = tag.getBoolean("Done");
        progress.announced = tag.getBoolean("Announced");
        progress.phase = tag.getInt("Phase");
        progress.citizenCount = tag.getInt("CitizenCount");
        progress.side = tag.getInt("Side");
        progress.claimIndex = tag.getInt("ClaimIndex");
        progress.citizenIndex = tag.getInt("CitizenIndex");
        if (tag.hasUUID("CityId")) {
            progress.cityId = tag.getUUID("CityId");
        }
        return progress;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("Configured", configured);
        tag.putBoolean("Done", done);
        tag.putBoolean("Announced", announced);
        tag.putInt("Phase", phase);
        tag.putInt("CitizenCount", citizenCount);
        tag.putInt("Side", side);
        tag.putInt("ClaimIndex", claimIndex);
        tag.putInt("CitizenIndex", citizenIndex);
        if (cityId != null) {
            tag.putUUID("CityId", cityId);
        }
        return tag;
    }

    public boolean done() {
        return done;
    }

    public boolean announced() {
        return announced;
    }

    public boolean waitingForPlayer() {
        return phase == PHASE_WAITING;
    }

    public boolean claiming() {
        return phase == PHASE_CLAIMING;
    }

    public boolean populating() {
        return phase == PHASE_POPULATING;
    }

    public int citizenCount() {
        return citizenCount;
    }

    public int side() {
        return side;
    }

    public int claimIndex() {
        return claimIndex;
    }

    public int citizenIndex() {
        return citizenIndex;
    }

    public UUID cityId() {
        return cityId;
    }

    /**
     * configure：第一次进入时记下居民数和网格边长。
     */
    public void configure(int citizens, int cityScale) {
        this.citizenCount = DebugCityLayout.clampCitizens(citizens);
        this.side = DebugCityLayout.sideLength(this.citizenCount, cityScale);
        this.configured = true;
        setDirty();
    }

    public boolean configured() {
        return configured;
    }

    public void markAnnounced() {
        this.announced = true;
        setDirty();
    }

    public void beginClaiming(UUID city) {
        this.cityId = city;
        this.phase = PHASE_CLAIMING;
        this.claimIndex = 0;
        setDirty();
    }

    public void advanceClaim() {
        this.claimIndex++;
        if (this.claimIndex >= this.side * this.side) {
            this.phase = PHASE_POPULATING;
        }
        setDirty();
    }

    public void advanceCitizen() {
        this.citizenIndex++;
        if (this.citizenIndex >= this.citizenCount) {
            this.done = true;
        }
        setDirty();
    }

    public void finish() {
        this.done = true;
        setDirty();
    }

    /**
     * occupiedCells：按本次快照的规模返回居民地块，顺序在一次生成中保持不变。
     */
    public List<DebugCityLayout.Cell> occupiedCells() {
        if (occupiedCells == null) {
            occupiedCells = DebugCityLayout.occupiedCells(citizenCount, side);
        }
        return occupiedCells;
    }
}
