package common.cn.nsimu.simukraft.network.rts;

import common.cn.nsimu.simukraft.SimuKraft;
import common.cn.nsimu.simukraft.logistics.LogisticsControlBoxService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * RTS 物流线路请求：只读悬浮节点已经连上的线路。
 */
public record RtsLogisticsRoutesRequestPacket(BlockPos pos) implements CustomPacketPayload {
    private static final double MAX_DISTANCE = 192.0D;

    public static final Type<RtsLogisticsRoutesRequestPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "rts_logistics_routes_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RtsLogisticsRoutesRequestPacket> STREAM_CODEC =
            StreamCodec.of(RtsLogisticsRoutesRequestPacket::encode, RtsLogisticsRoutesRequestPacket::decode);

    @Override
    public Type<RtsLogisticsRoutesRequestPacket> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buffer, RtsLogisticsRoutesRequestPacket packet) {
        buffer.writeBlockPos(packet.pos());
    }

    private static RtsLogisticsRoutesRequestPacket decode(RegistryFriendlyByteBuf buffer) {
        return new RtsLogisticsRoutesRequestPacket(buffer.readBlockPos());
    }

    /**
     * handle: 在服务端主线程返回该物流盒的线路端点。
     */
    public static void handle(RtsLogisticsRoutesRequestPacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        context.enqueueWork(() -> send(player, level, packet.pos()));
    }

    private static void send(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (player == null || level == null || pos == null
                || !RtsChunkViewService.isActive(level, player)
                || !RtsChunkViewService.isTargetReachable(level, player, pos, MAX_DISTANCE)) {
            return;
        }
        List<RtsLogisticsRoutesSyncPacket.Line> lines = LogisticsControlBoxService.routesAt(level, pos).stream()
                .map(line -> new RtsLogisticsRoutesSyncPacket.Line(line.from(), line.to()))
                .toList();
        PacketDistributor.sendToPlayer(player, new RtsLogisticsRoutesSyncPacket(pos, lines));
    }
}
