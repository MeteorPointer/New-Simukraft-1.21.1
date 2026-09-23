package common.cn.kafei.simukraft.mixin;

import common.cn.kafei.simukraft.building.BuildingStructurePort;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Predicate;

/**
 * 原版结构查询找不到自然生成结构时，改查建筑结构数据库。
 * FTB Quests 的结构任务走的就是这几个方法。
 */
@Mixin(StructureManager.class)
public abstract class MixinStructureManager {
    @Shadow
    @Final
    private LevelAccessor level;

    @Inject(method = "getStructureWithPieceAt(Lnet/minecraft/core/BlockPos;Ljava/util/function/Predicate;)Lnet/minecraft/world/level/levelgen/structure/StructureStart;",
            at = @At("RETURN"), cancellable = true)
    private void simukraft$placedBuildingByPredicate(BlockPos pos, Predicate<Holder<Structure>> predicate, CallbackInfoReturnable<StructureStart> callback) {
        replaceWithPlacedBuilding(callback, BuildingStructurePort.findAt(this.level, pos, predicate));
    }

    @Inject(method = "getStructureWithPieceAt(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/levelgen/structure/Structure;)Lnet/minecraft/world/level/levelgen/structure/StructureStart;",
            at = @At("RETURN"), cancellable = true)
    private void simukraft$placedBuildingByStructure(BlockPos pos, Structure structure, CallbackInfoReturnable<StructureStart> callback) {
        replaceWithPlacedBuilding(callback, BuildingStructurePort.findAt(this.level, pos, structure));
    }

    @Inject(method = "getStructureAt", at = @At("RETURN"), cancellable = true)
    private void simukraft$placedBuildingBounds(BlockPos pos, Structure structure, CallbackInfoReturnable<StructureStart> callback) {
        replaceWithPlacedBuilding(callback, BuildingStructurePort.findAt(this.level, pos, structure));
    }

    private static void replaceWithPlacedBuilding(CallbackInfoReturnable<StructureStart> callback, StructureStart placed) {
        StructureStart current = callback.getReturnValue();
        if (current != null && current.isValid()) {
            return;
        }
        if (placed != null && placed.isValid()) {
            callback.setReturnValue(placed);
        }
    }
}
