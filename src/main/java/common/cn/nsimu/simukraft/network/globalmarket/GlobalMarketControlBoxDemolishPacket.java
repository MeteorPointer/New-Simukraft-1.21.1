package common.cn.nsimu.simukraft.network.globalmarket;

import common.cn.nsimu.simukraft.SimuKraft;
import common.cn.nsimu.simukraft.building.PlacedBuildingDemolitionService;
import common.cn.nsimu.simukraft.building.PlacedBuildingRecord;
import common.cn.nsimu.simukraft.city.CityData;
import common.cn.nsimu.simukraft.city.CityManager;
import common.cn.nsimu.simukraft.city.CityPermissionLevel;
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
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * GlobalMarketControlBoxDemolishPacket: 拆除全球市场建筑。
 */
public record GlobalMarketControlBoxDemolishPacket(BlockPos pos) implements CustomPacketPayload {
    public static final Type<GlobalMarketControlBoxDemolishPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "global_market_control_box_demolish"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GlobalMarketControlBoxDemolishPacket> STREAM_CODEC = StreamCodec.of(GlobalMarketControlBoxDemolishPacket::encode, GlobalMarketControlBoxDemolishPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, GlobalMarketControlBoxDemolishPacket packet) {
        buffer.writeBlockPos(packet.pos());
    }

    public static GlobalMarketControlBoxDemolishPacket decode(RegistryFriendlyByteBuf buffer) {
        return new GlobalMarketControlBoxDemolishPacket(buffer.readBlockPos());
    }

    public static void handle(GlobalMarketControlBoxDemolishPacket packet, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            handleFor(level, player, packet.pos());
        }
    }

    private static void handleFor(ServerLevel level, ServerPlayer player, BlockPos pos) {
        if (!player.blockPosition().closerThan(pos, 8.0D) && !RtsRemoteMenuAccess.hasAccess(player, pos)) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.global_market.too_far"));
            return;
        }
        if (!level.getBlockState(pos).is(ModBlocks.GLOBAL_MARKET_CONTROL_BOX.get())) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.global_market.not_found"));
            return;
        }
        PlacedBuildingRecord building = GlobalMarketControlBoxService.resolveBuilding(level, pos);
        if (building == null) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.global_market.no_building"));
            return;
        }
        if (!player.hasPermissions(2)) {
            if (building.cityId() == null) {
                InfoToastService.warning(player, Component.translatable("message.simukraft.no_permission"));
                return;
            }
            CityData city = CityManager.get(level).getCity(building.cityId()).orElse(null);
            if (city == null || !city.hasPermission(player.getUUID(), CityPermissionLevel.OFFICIAL)) {
                InfoToastService.warning(player, Component.translatable("message.simukraft.no_permission"));
                return;
            }
        }
        GlobalMarketControlBoxService.fireWorker(level, pos);
        if (PlacedBuildingDemolitionService.demolish(level, building)) {
            InfoToastService.success(player, Component.translatable("message.simukraft.global_market.demolished"));
        }
    }
}
