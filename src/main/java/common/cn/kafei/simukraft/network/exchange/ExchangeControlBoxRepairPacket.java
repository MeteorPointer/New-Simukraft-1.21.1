package common.cn.kafei.simukraft.network.exchange;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.building.BuildingIntegrityService;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.economy.EconomyService;
import common.cn.kafei.simukraft.exchange.ExchangeControlBoxService;
import common.cn.kafei.simukraft.network.rts.RtsRemoteMenuAccess;
import common.cn.kafei.simukraft.network.toast.InfoToastService;
import common.cn.kafei.simukraft.registry.ModBlocks;
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

/** ExchangeControlBoxRepairPacket: 按建筑完整性补全交易所。 */
public record ExchangeControlBoxRepairPacket(BlockPos pos) implements CustomPacketPayload {
    public static final Type<ExchangeControlBoxRepairPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "exchange_control_box_repair"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ExchangeControlBoxRepairPacket> STREAM_CODEC =
            StreamCodec.of(ExchangeControlBoxRepairPacket::encode, ExchangeControlBoxRepairPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** encode: 写入控制箱坐标。 */
    public static void encode(RegistryFriendlyByteBuf buffer, ExchangeControlBoxRepairPacket packet) {
        buffer.writeBlockPos(packet.pos());
    }

    /** decode: 读取控制箱坐标。 */
    public static ExchangeControlBoxRepairPacket decode(RegistryFriendlyByteBuf buffer) {
        return new ExchangeControlBoxRepairPacket(buffer.readBlockPos());
    }

    /** handle: 补全交易所建筑并刷新界面。 */
    public static void handle(ExchangeControlBoxRepairPacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (!player.blockPosition().closerThan(packet.pos(), 16.0D) && !RtsRemoteMenuAccess.hasAccess(player, packet.pos())) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.exchange.too_far"));
            return;
        }
        if (!level.getBlockState(packet.pos()).is(ModBlocks.EXCHANGE_CONTROL_BOX.get())) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.exchange.not_found"));
            return;
        }
        PlacedBuildingRecord building = ExchangeControlBoxService.resolveBuilding(level, packet.pos());
        BuildingIntegrityService.RepairResult result = BuildingIntegrityService.repair(level, player, building);
        switch (result.status()) {
            case SUCCESS -> InfoToastService.success(player, repairMessage(result));
            case NO_REPAIR_NEEDED -> InfoToastService.success(player, Component.translatable("message.simukraft.building_integrity.no_repair_needed"));
            case NOT_ENOUGH_FUNDS -> InfoToastService.warning(player, Component.translatable("message.simukraft.building_integrity.not_enough_funds", money(result.cost())));
            case MATERIALS_REQUIRED -> InfoToastService.warning(player, Component.translatable("message.simukraft.building_integrity.materials_required", result.manualRepairBlocks()));
            case UNAVAILABLE -> InfoToastService.warning(player, Component.translatable("message.simukraft.building_integrity.unavailable"));
            case NO_BUILDING -> InfoToastService.warning(player, Component.translatable("message.simukraft.building_integrity.no_building"));
        }
        PacketDistributor.sendToPlayer(player, ExchangeControlBoxOpenRequestPacket.snapshot(level, player, packet.pos(), ""));
    }

    private static Component repairMessage(BuildingIntegrityService.RepairResult result) {
        if (result.manualRepairBlocks() > 0) {
            return Component.translatable("message.simukraft.building_integrity.repaired_with_manual",
                    result.repairedBlocks(), money(result.cost()), result.manualRepairBlocks());
        }
        return Component.translatable("message.simukraft.building_integrity.repaired", result.repairedBlocks(), money(result.cost()));
    }

    private static String money(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
