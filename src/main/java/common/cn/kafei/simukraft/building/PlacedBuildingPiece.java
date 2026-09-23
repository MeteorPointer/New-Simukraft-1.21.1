package common.cn.kafei.simukraft.building;

import common.cn.kafei.simukraft.registry.ModStructures;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.core.BlockPos;

/**
 * 运行时拼出来的结构片段，包围盒等于已建成建筑的占地。
 * 不会写入区块，也不摆放方块。
 */
public final class PlacedBuildingPiece extends StructurePiece {
    public PlacedBuildingPiece(BoundingBox boundingBox) {
        super(ModStructures.PLACED_BUILDING_PIECE.get(), 0, boundingBox);
    }

    public PlacedBuildingPiece(CompoundTag tag) {
        super(ModStructures.PLACED_BUILDING_PIECE.get(), tag);
    }

    public static PlacedBuildingPiece load(StructurePieceSerializationContext context, CompoundTag tag) {
        return new PlacedBuildingPiece(tag);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox box, ChunkPos chunkPos, BlockPos pos) {
    }
}
