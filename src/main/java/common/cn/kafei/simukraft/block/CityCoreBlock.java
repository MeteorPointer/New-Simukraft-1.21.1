package common.cn.kafei.simukraft.block;

import common.cn.kafei.simukraft.city.CityData;
import common.cn.kafei.simukraft.city.CityService;
import common.cn.kafei.simukraft.city.DistrictData;
import common.cn.kafei.simukraft.city.DistrictManager;
import common.cn.kafei.simukraft.city.DistrictRole;
import common.cn.kafei.simukraft.building.PlacedBuildingService;
import common.cn.kafei.simukraft.network.city.core.CityCoreOpenRequestPacket;
import common.cn.kafei.simukraft.network.toast.InfoToastService;
import common.cn.kafei.simukraft.registry.ModBlocks;
import common.cn.kafei.simukraft.registry.ModSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;


public final class CityCoreBlock extends Block {
    public CityCoreBlock() {
        super(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.0F).explosionResistance(3600000.0F).sound(SoundType.METAL));
    }

    // getDrops: 已绑定城市核心会被保护恢复，不产生掉落以避免复制。
    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        Vec3 origin = params.getOptionalParameter(LootContextParams.ORIGIN);
        if (origin != null && (CityService.findCityByCorePos(params.getLevel(), BlockPos.containing(origin)).isPresent()
                || DistrictManager.get(params.getLevel()).byChunk(new net.minecraft.world.level.ChunkPos(BlockPos.containing(origin))).flatMap(d -> d.cores().stream().filter(v -> v == BlockPos.containing(origin).asLong()).findAny().map(v -> d)).isPresent())) {
            return List.of();
        }
        return super.getDrops(state, params);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            level.playSound(null, pos, ModSoundEvents.CITY_CORE_OPEN.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
            DistrictData district = DistrictManager.get(serverLevel).byChunk(new net.minecraft.world.level.ChunkPos(pos)).orElse(null);
            if (district != null && district.cores().stream().noneMatch(v -> v == pos.asLong())
                    && !CityService.hasCityAtCorePos(serverLevel, pos)) {
                boolean allowed = CityService.hasPermission(serverLevel, district.parentCityId(), serverPlayer.getUUID(), common.cn.kafei.simukraft.city.CityPermissionLevel.MAYOR)
                        || district.hasPermission(serverPlayer.getUUID(), DistrictRole.OFFICIAL);
                if (allowed && PlacedBuildingService.findByContainedPos(serverLevel, pos) == null
                        && DistrictManager.get(serverLevel).bindCore(district.districtId(), new net.minecraft.world.level.ChunkPos(pos), pos.asLong())) {
                    InfoToastService.success(serverPlayer, Component.translatable("message.simukraft.district.core_bound", district.name()));
                    CityCoreOpenRequestPacket.openFor(serverLevel, serverPlayer, pos);
                } else if (!allowed) {
                    InfoToastService.warning(serverPlayer, Component.translatable("message.simukraft.district.no_permission"));
                }
                return InteractionResult.sidedSuccess(level.isClientSide());
            }
            CityCoreOpenRequestPacket.openFor(serverLevel, serverPlayer, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide() && !state.is(oldState.getBlock())) {
            level.playSound(null, pos, SoundEvents.METAL_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (!(level instanceof ServerLevel serverLevel) || newState.is(state.getBlock())) {
            return;
        }
        CityData city = CityService.findCityByCorePos(serverLevel, pos).orElse(null);
        DistrictData district = DistrictManager.get(serverLevel).byChunk(new net.minecraft.world.level.ChunkPos(pos)).orElse(null);
        boolean districtCore = district != null && district.cores().stream().anyMatch(v -> v == pos.asLong());
        if (city == null && !districtCore) {
            return;
        }
        level.setBlock(pos, ModBlocks.CITY_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.METAL_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.players().forEach(player -> {
            if (player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) < 100.0D) {
                if (player instanceof ServerPlayer serverPlayer) {
                    InfoToastService.warning(serverPlayer, Component.translatable("message.simukraft.city_core.protected"));
                }
            }
        });
    }
}
