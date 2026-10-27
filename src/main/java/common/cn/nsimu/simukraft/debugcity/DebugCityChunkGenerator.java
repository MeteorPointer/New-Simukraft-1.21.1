package common.cn.nsimu.simukraft.debugcity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import common.cn.nsimu.simukraft.SimuKraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 调试城市世界类型使用的平坦网格生成器，不新增维度。
 */
public final class DebugCityChunkGenerator extends ChunkGenerator {
    public static final ResourceKey<WorldPreset> PRESET_KEY = ResourceKey.create(
            Registries.WORLD_PRESET,
            ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "debug_city"));
    public static final MapCodec<DebugCityChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(DebugCityChunkGenerator::getBiomeSource),
            Codec.INT.optionalFieldOf("citizen_count", DebugCityLayout.DEFAULT_CITIZENS).forGetter(DebugCityChunkGenerator::citizenCount),
            Codec.INT.optionalFieldOf("city_scale", DebugCityLayout.DEFAULT_SCALE).forGetter(DebugCityChunkGenerator::cityScale)
    ).apply(instance, DebugCityChunkGenerator::new));
    private static final int MIN_Y = -64;
    private static final int GEN_DEPTH = 384;
    private static final int SEA_LEVEL = 63;
    private static final BlockState[] GRASS_COLUMN = column(Blocks.GRASS_BLOCK.defaultBlockState());
    private static final BlockState[] ROAD_COLUMN = column(Blocks.STONE_BRICKS.defaultBlockState());
    private final int citizenCount;
    private final int cityScale;

    public DebugCityChunkGenerator(BiomeSource biomeSource, int citizenCount, int cityScale) {
        super(biomeSource);
        this.citizenCount = DebugCityLayout.clampCitizens(citizenCount);
        this.cityScale = DebugCityLayout.clampScale(cityScale);
    }

    /**
     * citizenCount：创建世界时选定的居民数量。
     */
    public int citizenCount() {
        return citizenCount;
    }

    /**
     * cityScale：创建世界时选定的网格边长。
     */
    public int cityScale() {
        return cityScale;
    }

    /**
     * register：把调试城市生成器登记到世界类型可用的区块生成器表。
     */
    public static void register(RegisterEvent event) {
        event.register(Registries.CHUNK_GENERATOR, helper -> helper.register(
                ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "debug_city"),
                CODEC));
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public void applyCarvers(net.minecraft.server.level.WorldGenRegion level, long seed, RandomState random, BiomeManager biomeManager, StructureManager structureManager, ChunkAccess chunk, GenerationStep.Carving step) {
    }

    @Override
    public void buildSurface(net.minecraft.server.level.WorldGenRegion level, StructureManager structureManager, RandomState random, ChunkAccess chunk) {
    }

    @Override
    public void spawnOriginalMobs(net.minecraft.server.level.WorldGenRegion level) {
    }

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
    }

    @Override
    public void createStructures(RegistryAccess registryAccess, net.minecraft.world.level.chunk.ChunkGeneratorStructureState structureState, StructureManager structureManager, ChunkAccess chunk, StructureTemplateManager structureTemplateManager) {
    }

    @Override
    public void createReferences(WorldGenLevel level, StructureManager structureManager, ChunkAccess chunk) {
    }

    @Override
    public int getGenDepth() {
        return GEN_DEPTH;
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState random, StructureManager structureManager, ChunkAccess chunk) {
        ChunkPos chunkPos = chunk.getPos();
        Heightmap oceanFloor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap worldSurface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int minY = chunk.getMinBuildHeight();
        for (int localX = 0; localX < 16; localX++) {
            int worldX = chunkPos.getMinBlockX() + localX;
            for (int localZ = 0; localZ < 16; localZ++) {
                BlockState[] column = DebugCityLayout.isRoad(worldX, chunkPos.getMinBlockZ() + localZ) ? ROAD_COLUMN : GRASS_COLUMN;
                for (int index = 0; index < column.length; index++) {
                    int y = minY + index;
                    BlockState state = column[index];
                    chunk.setBlockState(cursor.set(localX, y, localZ), state, false);
                    oceanFloor.update(localX, y, localZ, state);
                    worldSurface.update(localX, y, localZ, state);
                }
            }
        }
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public int getSeaLevel() {
        return SEA_LEVEL;
    }

    @Override
    public int getMinY() {
        return MIN_Y;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        return DebugCityLayout.SURFACE_Y + 1;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
        BlockState[] source = DebugCityLayout.isRoad(x, z) ? ROAD_COLUMN : GRASS_COLUMN;
        return new NoiseColumn(MIN_Y, source.clone());
    }

    @Override
    public int getSpawnHeight(LevelHeightAccessor level) {
        return DebugCityLayout.SURFACE_Y + 1;
    }

    @Override
    public void addDebugScreenInfo(List<String> lines, RandomState random, BlockPos pos) {
        lines.add("simukraft: debug city");
    }

    private static BlockState[] column(BlockState surface) {
        int height = DebugCityLayout.SURFACE_Y - MIN_Y + 1;
        BlockState[] states = new BlockState[height];
        states[0] = Blocks.BEDROCK.defaultBlockState();
        for (int index = 1; index < states.length; index++) {
            int y = MIN_Y + index;
            if (y < DebugCityLayout.SURFACE_Y - 3) {
                states[index] = Blocks.STONE.defaultBlockState();
            } else if (y < DebugCityLayout.SURFACE_Y) {
                states[index] = Blocks.DIRT.defaultBlockState();
            } else {
                states[index] = surface;
            }
        }
        return states;
    }
}
