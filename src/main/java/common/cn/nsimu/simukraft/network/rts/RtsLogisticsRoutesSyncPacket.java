package common.cn.nsimu.simukraft.network.rts;

import common.cn.nsimu.simukraft.SimuKraft;
import common.cn.nsimu.simukraft.logistics.LogisticsControlBoxService;
import common.cn.nsimu.simukraft.network.clientbound.ClientboundNetworkBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * RTS 物流线路快照：端点已经按传输方向排好，客户端只负责画线。
 */
public record RtsLogisticsRoutesSyncPacket(BlockPos anchor, List<Line> lines) implements CustomPacketPayload {
    public static final Type<RtsLogisticsRoutesSyncPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "rts_logistics_routes_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RtsLogisticsRoutesSyncPacket> STREAM_CODEC =
            StreamCodec.of(RtsLogisticsRoutesSyncPacket::encode, RtsLogisticsRoutesSyncPacket::decode);

    public RtsLogisticsRoutesSyncPacket {
        lines = lines == null ? List.of() : lines.stream()
                .filter(line -> line != null && line.from() != null && line.to() != null)
                .limit(LogisticsControlBoxService.RouteLine.MAX_LINES)
                .toList();
    }

    @Override
    public Type<RtsLogisticsRoutesSyncPacket> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buffer, RtsLogisticsRoutesSyncPacket packet) {
        buffer.writeBlockPos(packet.anchor());
        buffer.writeVarInt(packet.lines().size());
        for (Line line : packet.lines()) {
            buffer.writeBlockPos(line.from());
            buffer.writeBlockPos(line.to());
        }
    }

    private static RtsLogisticsRoutesSyncPacket decode(RegistryFriendlyByteBuf buffer) {
        BlockPos anchor = buffer.readBlockPos();
        int count = Math.min(Math.max(0, buffer.readVarInt()), LogisticsControlBoxService.RouteLine.MAX_LINES);
        ArrayList<Line> lines = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            lines.add(new Line(buffer.readBlockPos(), buffer.readBlockPos()));
        }
        return new RtsLogisticsRoutesSyncPacket(anchor, lines);
    }

    public static void handle(RtsLogisticsRoutesSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientboundNetworkBridge.handleRtsLogisticsRoutesSync(packet));
    }

    public record Line(BlockPos from, BlockPos to) {
        public Line {
            from = from == null ? BlockPos.ZERO : from.immutable();
            to = to == null ? BlockPos.ZERO : to.immutable();
        }
    }
}
