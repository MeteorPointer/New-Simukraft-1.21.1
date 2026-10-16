package common.cn.kafei.simukraft.mining;

import common.cn.kafei.simukraft.SimuKraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** MiningBoxManager: 保存当前维度每个挖矿盒的范围、模式和开关。 */
public final class MiningBoxManager extends SavedData {
    private static final String DATA_NAME = SimuKraft.MOD_ID + "_mining_boxes";
    private static final Factory<MiningBoxManager> FACTORY = new Factory<>(
            MiningBoxManager::new,
            MiningBoxManager::load,
            null);

    private final ConcurrentMap<BlockPos, MiningBoxState> boxes = new ConcurrentHashMap<>();

    public static MiningBoxManager get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public MiningBoxState getOrCreate(BlockPos pos) {
        return boxes.computeIfAbsent(pos.immutable(), MiningBoxState::create);
    }

    public MiningBoxState update(MiningBoxState state) {
        boxes.put(state.pos(), state);
        setDirty();
        return state;
    }

    public java.util.Collection<MiningBoxState> states() {
        return java.util.List.copyOf(boxes.values());
    }

    public void remove(BlockPos pos) {
        if (boxes.remove(pos) != null) {
            setDirty();
        }
    }

    private static MiningBoxManager load(CompoundTag tag, HolderLookup.Provider registries) {
        MiningBoxManager manager = new MiningBoxManager();
        ListTag list = tag.getList("Boxes", CompoundTag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            MiningBoxState state = MiningBoxState.fromTag(list.getCompound(index));
            manager.boxes.put(state.pos(), state);
        }
        return manager;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (MiningBoxState state : boxes.values()) {
            list.add(state.toTag());
        }
        tag.put("Boxes", list);
        return tag;
    }
}
