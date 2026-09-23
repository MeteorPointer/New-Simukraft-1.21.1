package common.cn.kafei.simukraft.building;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * 把建筑结构数据库接到原版 {@code StructureManager} 查询上。
 * FTB Quests 的结构任务只调用这组查询，不会直接读 SQLite。
 */
public final class BuildingStructurePort {
    private BuildingStructurePort() {
    }

    /** structureId: 这座已建成建筑对应的结构 ID，文件名无法编码时返回 null。 */
    public static ResourceLocation structureId(PlacedBuildingRecord record) {
        if (record == null) {
            return null;
        }
        String fileName = record.buildingFileName();
        if (fileName == null || fileName.isBlank()) {
            fileName = record.structureFileName();
        }
        return BuildingStructureIds.location(record.category(), fileName);
    }

    /**
     * findAt: 玩家所在格落在已建成建筑里，且该建筑的结构满足条件时，返回一个不落盘的结构起点。
     */
    public static StructureStart findAt(LevelAccessor level, BlockPos pos, Predicate<Holder<Structure>> predicate) {
        if (!(level instanceof ServerLevel serverLevel) || pos == null || predicate == null) {
            return null;
        }
        Registry<Structure> registry = serverLevel.registryAccess().registryOrThrow(Registries.STRUCTURE);
        for (PlacedBuildingRecord record : PlacedBuildingService.getBuildings(serverLevel)) {
            if (!nearBuilding(record, pos)) {
                continue;
            }
            StructureStart start = startFor(registry, record, predicate);
            if (start != null) {
                return start;
            }
        }
        return null;
    }

    /**
     * findAt: 按 FTB Quests 传入的结构对象查询。
     * 用注册表 ID 比较，避免结构修饰器换成另一个实例后 {@code ==} 对不上。
     */
    public static StructureStart findAt(LevelAccessor level, BlockPos pos, Structure structure) {
        if (!(level instanceof ServerLevel serverLevel) || pos == null || structure == null) {
            return null;
        }
        Registry<Structure> registry = serverLevel.registryAccess().registryOrThrow(Registries.STRUCTURE);
        ResourceLocation asked = registry.getKey(structure);
        if (asked == null) {
            return null;
        }
        for (PlacedBuildingRecord record : PlacedBuildingService.getBuildings(serverLevel)) {
            if (nearBuilding(record, pos) && ownsStructure(record, asked)) {
                return syntheticStart(structure, record);
            }
        }
        return null;
    }

    private static StructureStart startFor(Registry<Structure> registry, PlacedBuildingRecord record, Predicate<Holder<Structure>> predicate) {
        ResourceLocation id = structureId(record);
        if (id == null || !ownsStructure(record, id)) {
            return null;
        }
        Optional<Holder.Reference<Structure>> holder = registry.getHolder(ResourceKey.create(Registries.STRUCTURE, id));
        if (holder.isEmpty() || !predicate.test(holder.get())) {
            return null;
        }
        return syntheticStart(holder.get().value(), record);
    }

    private static boolean ownsStructure(PlacedBuildingRecord record, ResourceLocation structureId) {
        return BuildingStructureIds.matches(record.category(), record.buildingFileName(), record.structureFileName(), structureId);
    }

    private static StructureStart syntheticStart(Structure structure, PlacedBuildingRecord record) {
        BoundingBox box = detectionBounds(record);
        BlockPos anchor = record.worldOrigin() != null ? record.worldOrigin() : record.minPos();
        if (box == null || anchor == null) {
            return null;
        }
        return new StructureStart(structure, new ChunkPos(anchor), 0, new PiecesContainer(List.of(new PlacedBuildingPiece(box))));
    }

    /** 建筑占地再向外扩 1 格。贴着外墙、门口或屋顶时，玩家脚所在的格子仍算进入该建筑。 */
    private static boolean nearBuilding(PlacedBuildingRecord record, BlockPos pos) {
        BoundingBox box = detectionBounds(record);
        return box != null && box.isInside(pos);
    }

    private static BoundingBox detectionBounds(PlacedBuildingRecord record) {
        BoundingBox box = bounds(record);
        return box == null ? null : box.inflatedBy(1);
    }

    private static BoundingBox bounds(PlacedBuildingRecord record) {
        if (record == null || record.minPos() == null || record.maxPos() == null) {
            return null;
        }
        BlockPos min = record.minPos();
        BlockPos max = record.maxPos();
        return new BoundingBox(
                Math.min(min.getX(), max.getX()),
                Math.min(min.getY(), max.getY()),
                Math.min(min.getZ(), max.getZ()),
                Math.max(min.getX(), max.getX()),
                Math.max(min.getY(), max.getY()),
                Math.max(min.getZ(), max.getZ())
        );
    }
}
