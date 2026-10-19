package common.cn.nsimu.simukraft.logistics;

import common.cn.nsimu.simukraft.block.ManifestBlock;
import common.cn.nsimu.simukraft.block.entity.ManifestBlockEntity;
import common.cn.nsimu.simukraft.item.ManifestItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LogisticsManifestFilterService: 从目标客户端表面清单解析待输送材料。
 */
public final class LogisticsManifestFilterService {
    public static final int BUILD_BOX_REFRESH_INTERVAL_TICKS = 40;

    private LogisticsManifestFilterService() {
    }

    /**
     * resolve: 读取贴在客户端表面的清单，建筑盒来源按间隔刷新剩余材料。
     */
    public static List<LogisticsItemFilter> resolve(ServerLevel level, LogisticsClientData client) {
        if (level == null || client == null) {
            return List.of();
        }
        boolean refreshBuildBox = level.getGameTime() % BUILD_BOX_REFRESH_INTERVAL_TICKS == 0L;
        Map<String, LogisticsItemFilter> filters = new LinkedHashMap<>();
        for (ItemStack stack : adjacentManifests(level, client.boxPos(), refreshBuildBox)) {
            for (LogisticsItemFilter filter : remainingFilters(stack)) {
                filters.putIfAbsent(filter.itemId(), filter);
            }
        }
        return List.copyOf(filters.values());
    }

    /**
     * remainingFilters: 把清单里尚未备齐的材料转成物流过滤项。
     */
    public static List<LogisticsItemFilter> remainingFilters(ItemStack stack) {
        List<LogisticsItemFilter> filters = new ArrayList<>();
        for (ManifestItem.MaterialEntry entry : ManifestItem.getMaterials(stack)) {
            if (entry.itemId() == null || entry.itemId().isBlank()) {
                continue;
            }
            if (entry.available() >= entry.count()) {
                continue;
            }
            filters.add(LogisticsItemFilter.item(entry.itemId()));
        }
        return List.copyOf(filters);
    }

    private static List<ItemStack> adjacentManifests(ServerLevel level, BlockPos clientPos, boolean refreshBuildBox) {
        List<ItemStack> stacks = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            BlockPos manifestPos = clientPos.relative(direction);
            if (!level.isLoaded(manifestPos)) {
                continue;
            }
            BlockState state = level.getBlockState(manifestPos);
            if (!ManifestBlock.isAttachedTo(state, manifestPos, clientPos)) {
                continue;
            }
            if (!(level.getBlockEntity(manifestPos) instanceof ManifestBlockEntity entity)) {
                continue;
            }
            ItemStack stack = entity.toItem();
            if (refreshBuildBox && ManifestItem.refreshFromLinkedBuildBox(stack, level)) {
                entity.loadFromItem(stack);
            }
            stacks.add(stack);
        }
        return stacks;
    }
}
