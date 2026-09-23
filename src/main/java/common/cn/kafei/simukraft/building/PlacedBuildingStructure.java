package common.cn.kafei.simukraft.building;

import com.mojang.serialization.MapCodec;
import common.cn.kafei.simukraft.registry.ModStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

/**
 * 只用于让原版结构查询能点名一座模拟都市建筑。
 * 不参与世界生成，真正的占地来自建筑结构数据库。
 */
public final class PlacedBuildingStructure extends Structure {
    public static final MapCodec<PlacedBuildingStructure> CODEC = simpleCodec(PlacedBuildingStructure::new);

    public PlacedBuildingStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        return Optional.empty();
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.PLACED_BUILDING.get();
    }
}
