package common.cn.nsimu.simukraft.path;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * PathDistanceFieldCache: 按城市、目标和意图保留少量距离场。区段版本变了就丢掉。
 */
final class PathDistanceFieldCache {
    private static final int MAX_FIELDS = 8;
    private final LinkedHashMap<PathFieldKey, PathDistanceField> fields = new LinkedHashMap<>(8, 0.75F, true);

    /**
     * get: 版本还对得上才返回。对不上的条目立刻移除。
     */
    PathDistanceField get(PathFieldKey key, PathSectionEpochs epochs) {
        PathDistanceField field = fields.get(key);
        if (field == null) {
            return null;
        }
        if (epochs == null || !epochs.matches(field.stamp())) {
            fields.remove(key);
            return null;
        }
        return field;
    }

    /**
     * put: 放入新场，并丢掉最久没人用的，避免场把内存堆高。
     */
    void put(PathFieldKey key, PathDistanceField field) {
        if (key == null || field == null) {
            return;
        }
        fields.put(key, field);
        while (fields.size() > MAX_FIELDS) {
            Map.Entry<PathFieldKey, PathDistanceField> oldest = fields.entrySet().iterator().next();
            fields.remove(oldest.getKey());
        }
    }

    record PathFieldKey(ResourceLocation dimension, UUID cityId, BlockPos target, MovementIntent intent) {
    }
}
