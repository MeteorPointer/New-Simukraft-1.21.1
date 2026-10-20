package common.cn.nsimu.simukraft.network.globalmarket;

import common.cn.nsimu.simukraft.SimuKraft;
import common.cn.nsimu.simukraft.building.BuildingIntegrityService;
import common.cn.nsimu.simukraft.building.PlacedBuildingRecord;
import common.cn.nsimu.simukraft.globalmarket.GlobalMarketControlBoxService;
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

import java.util.Locale;

/**
 * GlobalMarketControlBoxActionPacket: 全球市场控制箱修理。
 */
public record GlobalMarketControlBoxActionPacket(BlockPos pos, Action action) implements CustomPacketPayload {
    public static final Type<GlobalMarketControlBoxActionPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "global_market_control_box_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GlobalMarketControlBoxActionPacket> STREAM_CODEC = StreamCodec.of(GlobalMarketControlBoxActionPacket::encode, GlobalMarketControlBoxActionPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, GlobalMarketControlBoxActionPacket packet) {
        buffer.writeBlockPos(packet.pos());
        buffer.writeEnum(packet.action());
    }

    public static GlobalMarketControlBoxActionPacket decode(RegistryFriendlyByteBuf buffer) {
        return new GlobalMarketControlBoxActionPacket(buffer.readBlockPos(), buffer.readEnum(Action.class));
    }

    public static void handle(GlobalMarketControlBoxActionPacket packet, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            if (!player.blockPosition().closerThan(packet.pos(), 16.0D)
                    && !RtsRemoteMenuAccess.hasAccess(player, packet.pos())) {
                InfoToastService.warning(player, Component.translatable("message.simukraft.global_market.too_far"));
                return;
            }
            if (!level.getBlockState(packet.pos()).is(ModBlocks.GLOBAL_MARKET_CONTROL_BOX.get())) {
                InfoToastService.warning(player, Component.translatable("message.simukraft.global_market.not_found"));
                return;
            }
            if (packet.action() == Action.REPAIR_BUILDING) {
                repairBuilding(level, player, packet.pos());
            }
            PacketDistributor.sendToPlayer(player, GlobalMarketControlBoxOpenResponsePacket.from(GlobalMarketControlBoxService.buildView(level, packet.pos())));
        }
    }

    private static void repairBuilding(ServerLevel level, ServerPlayer player, BlockPos pos) {
        PlacedBuildingRecord building = GlobalMarketControlBoxService.resolveBuilding(level, pos);
        BuildingIntegrityService.RepairResult result = BuildingIntegrityService.repair(level, player, building);
        switch (result.status()) {
            case SUCCESS -> InfoToastService.success(player, repairSuccessMessage(result));
            case NO_REPAIR_NEEDED ->
                    InfoToastService.success(player, Component.translatable("message.simukraft.building_integrity.no_repair_needed"));
            case NOT_ENOUGH_FUNDS ->
                    InfoToastService.warning(player, Component.translatable("message.simukraft.building_integrity.not_enough_funds", money(result.cost())));
            case MATERIALS_REQUIRED ->
                    InfoToastService.warning(player, Component.translatable("message.simukraft.building_integrity.materials_required", result.manualRepairBlocks()));
            case UNAVAILABLE ->
                    InfoToastService.warning(player, Component.translatable("message.simukraft.building_integrity.unavailable"));
            case NO_BUILDING ->
                    InfoToastService.warning(player, Component.translatable("message.simukraft.building_integrity.no_building"));
        }
    }

    private static Component repairSuccessMessage(BuildingIntegrityService.RepairResult result) {
        if (result.manualRepairBlocks() > 0) {
            return Component.translatable("message.simukraft.building_integrity.repaired_with_manual", result.repairedBlocks(), money(result.cost()), result.manualRepairBlocks());
        }
        return Component.translatable("message.simukraft.building_integrity.repaired", result.repairedBlocks(), money(result.cost()));
    }

    private static String money(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    public enum Action {
        REPAIR_BUILDING
    }
}
