package common.cn.nsimu.simukraft.network.farmland;

import common.cn.nsimu.simukraft.SimuKraft;
import common.cn.nsimu.simukraft.citizen.CitizenData;
import common.cn.nsimu.simukraft.city.CityService;
import common.cn.nsimu.simukraft.farmland.FarmlandBoxService;
import common.cn.nsimu.simukraft.job.CitizenEmploymentService;
import common.cn.nsimu.simukraft.network.rts.RtsRemoteMenuAccess;
import common.cn.nsimu.simukraft.network.toast.InfoToastService;
import common.cn.nsimu.simukraft.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;


public record FarmlandBoxActionPacket(BlockPos pos, Action action) implements CustomPacketPayload {
    public static final Type<FarmlandBoxActionPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "farmland_box_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FarmlandBoxActionPacket> STREAM_CODEC = StreamCodec.of(FarmlandBoxActionPacket::encode, FarmlandBoxActionPacket::decode);

    public enum Action {
        TOGGLE_RUN,
        FIRE,
        DEMOLISH
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, FarmlandBoxActionPacket packet) {
        buffer.writeBlockPos(packet.pos());
        buffer.writeEnum(packet.action());
    }

    public static FarmlandBoxActionPacket decode(RegistryFriendlyByteBuf buffer) {
        return new FarmlandBoxActionPacket(buffer.readBlockPos(), buffer.readEnum(Action.class));
    }

    public static void handle(FarmlandBoxActionPacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = packet.pos();
        if (!player.blockPosition().closerThan(pos, 8.0D) && !RtsRemoteMenuAccess.hasAccess(player, pos)) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.farmland_box.too_far"));
            return;
        }
        if (!level.getBlockState(pos).is(ModBlocks.NSUK_FARMLAND_BOX.get())) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.farmland_box.not_found"));
            return;
        }
        UUID cityId = FarmlandBoxService.cityIdFor(level, pos);
        if (cityId == null || !CityService.canManageCity(level, cityId, player.getUUID())) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.farmland_box.no_permission"));
            return;
        }
        apply(level, player, pos, packet.action());
    }

    private static void apply(ServerLevel level, ServerPlayer player, BlockPos pos, Action action) {
        switch (action) {
            case TOGGLE_RUN -> {
                FarmlandBoxService.ToggleResult result = FarmlandBoxService.toggleRunning(level, pos);
                switch (result) {
                    case STARTED ->
                            InfoToastService.success(player, Component.translatable("message.simukraft.farmland_box.started"));
                    case STOPPED ->
                            InfoToastService.send(player, Component.translatable("message.simukraft.farmland_box.stopped"));
                    case NOT_CONFIGURED ->
                            InfoToastService.warning(player, Component.translatable("message.simukraft.farmland_box.not_configured"));
                    case NO_CHEST ->
                            InfoToastService.warning(player, Component.translatable("message.simukraft.farmland_box.no_chest"));
                    case NO_FARMER ->
                            InfoToastService.warning(player, Component.translatable("message.simukraft.farmland_box.no_farmer"));
                }
            }
            case FIRE -> fireFarmer(level, player, pos);
            case DEMOLISH -> {
                demolish(level, pos);
                InfoToastService.success(player, Component.translatable("message.simukraft.farmland_box.demolished"));
                return;
            }
        }
        PacketDistributor.sendToPlayer(player, FarmlandBoxOpenResponsePacket.from(FarmlandBoxService.buildView(level, pos)));
    }

    private static void fireFarmer(ServerLevel level, ServerPlayer player, BlockPos pos) {
        CitizenData farmer = FarmlandBoxService.findAssignedWorker(level, pos);
        if (farmer == null) {
            return;
        }
        CitizenEmploymentService.fire(level, farmer.uuid(), FarmlandBoxService.HIRE_SOURCE_TYPE, FarmlandBoxService.HIRE_ROLE, pos, "farmer_fired");
        // 解雇后停止耕作，避免空转。
        InfoToastService.success(player, Component.translatable("message.simukraft.fire_npc.success", farmer.name()));
    }

    private static void demolish(ServerLevel level, BlockPos pos) {
        CitizenData farmer = FarmlandBoxService.findAssignedWorker(level, pos);
        if (farmer != null) {
            CitizenEmploymentService.fire(level, farmer.uuid(), FarmlandBoxService.HIRE_SOURCE_TYPE, FarmlandBoxService.HIRE_ROLE, pos, "farmland_box_demolished");
        }
        // destroyBlock 不发 BreakEvent。POI 在 FarmlandBoxService.onRemoved 里注销。
        level.destroyBlock(pos, true);
    }
}
