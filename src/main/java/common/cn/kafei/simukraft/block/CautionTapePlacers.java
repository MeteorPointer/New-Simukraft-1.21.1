package common.cn.kafei.simukraft.block;

import common.cn.kafei.simukraft.SimuKraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** CautionTapePlacers: 每格警戒线记住放置者，用来反查他所属的城市。 */
public final class CautionTapePlacers extends SavedData {
    private static final String DATA_NAME = SimuKraft.MOD_ID + "_caution_tape_placers";
    private static final Factory<CautionTapePlacers> FACTORY = new Factory<>(
            CautionTapePlacers::new,
            CautionTapePlacers::load,
            null);

    private final ConcurrentMap<BlockPos, UUID> placers = new ConcurrentHashMap<>();

    public static CautionTapePlacers get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public void set(BlockPos pos, @Nullable UUID placerId) {
        if (pos == null) {
            return;
        }
        if (placerId == null) {
            if (placers.remove(pos) != null) {
                setDirty();
                CautionTapeZones.rebuild(pos);
            }
            return;
        }
        placers.put(pos.immutable(), placerId);
        setDirty();
        CautionTapeZones.rebuild(pos);
    }

    @Nullable
    public UUID get(BlockPos pos) {
        return pos == null ? null : placers.get(pos);
    }

    public Map<BlockPos, UUID> entries() {
        return Map.copyOf(placers);
    }

    public void remove(BlockPos pos) {
        set(pos, null);
    }

    private static CautionTapePlacers load(CompoundTag tag, HolderLookup.Provider registries) {
        CautionTapePlacers data = new CautionTapePlacers();
        ListTag list = tag.getList("Placers", CompoundTag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag entry = list.getCompound(index);
            if (!entry.hasUUID("Placer")) {
                continue;
            }
            data.placers.put(BlockPos.of(entry.getLong("Pos")), entry.getUUID("Placer"));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        placers.forEach((pos, placer) -> {
            CompoundTag entry = new CompoundTag();
            entry.putLong("Pos", pos.asLong());
            entry.putUUID("Placer", placer);
            list.add(entry);
        });
        tag.put("Placers", list);
        return tag;
    }
}
