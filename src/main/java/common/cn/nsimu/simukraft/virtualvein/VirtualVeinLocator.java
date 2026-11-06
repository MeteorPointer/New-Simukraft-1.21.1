package common.cn.nsimu.simukraft.virtualvein;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * VirtualVeinLocator: 在有限矿区格子里找最近的指定矿脉，不整图扫描。
 */
public final class VirtualVeinLocator {
    public static final int DEFAULT_CELL_RADIUS = 5;
    public static final int MAX_CELL_RADIUS = 12;

    private VirtualVeinLocator() {
    }

    /**
     * suggestionIds: 给指令补全矿脉 id。
     */
    public static List<String> suggestionIds() {
        ArrayList<String> ids = new ArrayList<>();
        for (VirtualVeinDefinition definition : VirtualVeinDefinitionLoader.INSTANCE.definitions()) {
            ids.add(definition.id());
        }
        return List.copyOf(ids);
    }

    /**
     * matches: 矿脉 id、去掉 _vein 的短名、中英文显示名都能对上。
     */
    public static boolean matches(VirtualVeinSlot slot, String query) {
        if (slot == null || query == null || query.isBlank()) {
            return false;
        }
        String normalized = normalize(query);
        if (normalized.isEmpty()) {
            return false;
        }
        if (normalize(slot.veinId()).equals(normalized) || shortId(slot.veinId()).equals(normalized)) {
            return true;
        }
        if (normalize(slot.displayName()).equals(normalized)) {
            return true;
        }
        for (VirtualVeinDefinition definition : VirtualVeinDefinitionLoader.INSTANCE.definitions()) {
            if (!definition.id().equals(slot.veinId())) {
                continue;
            }
            if (normalize(definition.displayName()).equals(normalized)
                    || normalize(definition.displayNameEn()).equals(normalized)
                    || shortId(definition.id()).equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    /**
     * matchingSlot: 返回矿区里第一条仍可开采、且名称对得上的矿脉。
     */
    public static Optional<VirtualVeinSlot> matchingSlot(VirtualVeinFieldProfile profile, String query) {
        if (profile == null) {
            return Optional.empty();
        }
        for (VirtualVeinSlot slot : profile.slots()) {
            if (slot != null && slot.state() == VirtualVeinSlotState.ACTIVE && matches(slot, query)) {
                return Optional.of(slot);
            }
        }
        return Optional.empty();
    }

    /**
     * nearestAmong: 在已有矿区档案里挑离原点最近的匹配矿脉。
     */
    public static Optional<NearestVein> nearestAmong(BlockPos origin, String query, List<VirtualVeinFieldProfile> fields) {
        if (origin == null || fields == null || fields.isEmpty()) {
            return Optional.empty();
        }
        NearestVein best = null;
        long bestDistance = Long.MAX_VALUE;
        for (VirtualVeinFieldProfile field : fields) {
            if (field == null || field.key() == null) {
                continue;
            }
            Optional<VirtualVeinSlot> slot = matchingSlot(field, query);
            if (slot.isEmpty()) {
                continue;
            }
            long distance = horizontalDistanceSquared(origin, field.key().centerX(), field.key().centerZ());
            if (best == null || distance < bestDistance) {
                bestDistance = distance;
                best = new NearestVein(field, slot.get(), horizontalDistance(bestDistance));
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * findNearest: 从玩家所在矿区向外扩圈查询，默认 5 圈（约 1280 格），最多 12 圈。
     */
    public static Optional<NearestVein> findNearest(ServerLevel level, BlockPos origin, String query, int cellRadius) {
        if (level == null || origin == null || query == null || query.isBlank()
                || !level.dimension().equals(Level.OVERWORLD)) {
            return Optional.empty();
        }
        int radius = Math.max(1, Math.min(cellRadius, MAX_CELL_RADIUS));
        int originCellX = Math.floorDiv(origin.getX(), VirtualVeinFieldResolver.FIELD_SIZE);
        int originCellZ = Math.floorDiv(origin.getZ(), VirtualVeinFieldResolver.FIELD_SIZE);
        int seaLevel = level.getSeaLevel();
        int half = VirtualVeinFieldResolver.FIELD_SIZE / 2;
        ArrayList<VirtualVeinFieldProfile> fields = new ArrayList<>();
        for (int deltaX = -radius; deltaX <= radius; deltaX++) {
            for (int deltaZ = -radius; deltaZ <= radius; deltaZ++) {
                int sampleX = (originCellX + deltaX) * VirtualVeinFieldResolver.FIELD_SIZE + half;
                int sampleZ = (originCellZ + deltaZ) * VirtualVeinFieldResolver.FIELD_SIZE + half;
                VirtualVeinLookupResult lookup = VirtualVeinService.getOrCreateField(
                        level, new BlockPos(sampleX, seaLevel, sampleZ));
                if (lookup.isReady() && lookup.profile() != null) {
                    fields.add(lookup.profile());
                }
            }
        }
        return nearestAmong(origin, query, fields);
    }

    static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    static String shortId(String veinId) {
        String normalized = normalize(veinId);
        return normalized.endsWith("_vein") ? normalized.substring(0, normalized.length() - 5) : normalized;
    }

    private static long horizontalDistanceSquared(BlockPos origin, int centerX, int centerZ) {
        long deltaX = (long) origin.getX() - centerX;
        long deltaZ = (long) origin.getZ() - centerZ;
        return deltaX * deltaX + deltaZ * deltaZ;
    }

    private static int horizontalDistance(long distanceSquared) {
        return (int) Math.round(Math.sqrt(distanceSquared));
    }

    /**
     * NearestVein: 最近矿区中心、匹配矿脉和水平距离。
     */
    public record NearestVein(VirtualVeinFieldProfile field, VirtualVeinSlot slot, int distanceBlocks) {
        public NearestVein {
            field = java.util.Objects.requireNonNull(field, "field");
            slot = java.util.Objects.requireNonNull(slot, "slot");
            distanceBlocks = Math.max(0, distanceBlocks);
        }

        @Nullable
        public VirtualVeinFieldKey key() {
            return field.key();
        }
    }
}
