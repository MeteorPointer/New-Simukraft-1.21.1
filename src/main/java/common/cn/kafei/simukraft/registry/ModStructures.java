package common.cn.kafei.simukraft.registry;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.building.PlacedBuildingPiece;
import common.cn.kafei.simukraft.building.PlacedBuildingStructure;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModStructures {
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES = DeferredRegister.create(BuiltInRegistries.STRUCTURE_TYPE, SimuKraft.MOD_ID);
    public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECES = DeferredRegister.create(BuiltInRegistries.STRUCTURE_PIECE, SimuKraft.MOD_ID);

    public static final DeferredHolder<StructureType<?>, StructureType<PlacedBuildingStructure>> PLACED_BUILDING = STRUCTURE_TYPES.register(
            "placed_building",
            () -> () -> PlacedBuildingStructure.CODEC
    );
    public static final DeferredHolder<StructurePieceType, StructurePieceType> PLACED_BUILDING_PIECE = STRUCTURE_PIECES.register(
            "placed_building",
            () -> PlacedBuildingPiece::load
    );

    private ModStructures() {
    }

    public static void register(IEventBus modEventBus) {
        STRUCTURE_TYPES.register(modEventBus);
        STRUCTURE_PIECES.register(modEventBus);
    }
}
