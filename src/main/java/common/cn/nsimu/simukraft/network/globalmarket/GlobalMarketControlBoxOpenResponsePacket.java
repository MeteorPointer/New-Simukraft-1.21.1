package common.cn.nsimu.simukraft.network.globalmarket;

import common.cn.nsimu.simukraft.SimuKraft;
import common.cn.nsimu.simukraft.globalmarket.GlobalMarketControlBoxView;
import common.cn.nsimu.simukraft.network.clientbound.ClientboundNetworkBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * GlobalMarketControlBoxOpenResponsePacket: 全球市场控制箱管理界面快照。
 */
public record GlobalMarketControlBoxOpenResponsePacket(BlockPos boxPos,
                                                       boolean hasBuilding,
                                                       String buildingName,
                                                       String statusKey,
                                                       boolean hasWorker,
                                                       UUID workerId,
                                                       String workerName,
                                                       double cityBalance,
                                                       boolean hasBuildingBounds,
                                                       BlockPos boundsMin,
                                                       BlockPos boundsMax,
                                                       boolean integrityAvailable,
                                                       double integrityPercent,
                                                       int integrityRepairableBlocks,
                                                       int integrityManualRepairBlocks,
                                                       double integrityRepairCost) implements CustomPacketPayload {
    public static final Type<GlobalMarketControlBoxOpenResponsePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "global_market_control_box_open_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GlobalMarketControlBoxOpenResponsePacket> STREAM_CODEC = StreamCodec.of(GlobalMarketControlBoxOpenResponsePacket::encode, GlobalMarketControlBoxOpenResponsePacket::decode);

    public static GlobalMarketControlBoxOpenResponsePacket from(GlobalMarketControlBoxView view) {
        return new GlobalMarketControlBoxOpenResponsePacket(
                view.boxPos(),
                view.hasBuilding(),
                view.buildingName(),
                view.statusKey(),
                view.hasWorker(),
                view.workerId(),
                view.workerName(),
                view.cityBalance(),
                view.hasBuildingBounds(),
                view.boundsMin(),
                view.boundsMax(),
                view.integrityAvailable(),
                view.integrityPercent(),
                view.integrityRepairableBlocks(),
                view.integrityManualRepairBlocks(),
                view.integrityRepairCost());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, GlobalMarketControlBoxOpenResponsePacket packet) {
        buffer.writeBlockPos(packet.boxPos());
        buffer.writeBoolean(packet.hasBuilding());
        buffer.writeUtf(packet.buildingName() != null ? packet.buildingName() : "", 256);
        buffer.writeUtf(packet.statusKey() != null ? packet.statusKey() : "", 256);
        buffer.writeBoolean(packet.hasWorker());
        if (packet.hasWorker() && packet.workerId() != null) {
            buffer.writeUUID(packet.workerId());
        }
        buffer.writeUtf(packet.workerName() != null ? packet.workerName() : "", 256);
        buffer.writeDouble(packet.cityBalance());
        buffer.writeBoolean(packet.hasBuildingBounds());
        buffer.writeBlockPos(packet.boundsMin());
        buffer.writeBlockPos(packet.boundsMax());
        buffer.writeBoolean(packet.integrityAvailable());
        buffer.writeDouble(packet.integrityPercent());
        buffer.writeVarInt(packet.integrityRepairableBlocks());
        buffer.writeVarInt(packet.integrityManualRepairBlocks());
        buffer.writeDouble(packet.integrityRepairCost());
    }

    public static GlobalMarketControlBoxOpenResponsePacket decode(RegistryFriendlyByteBuf buffer) {
        BlockPos boxPos = buffer.readBlockPos();
        boolean hasBuilding = buffer.readBoolean();
        String buildingName = buffer.readUtf(256);
        String statusKey = buffer.readUtf(256);
        boolean hasWorker = buffer.readBoolean();
        UUID workerId = hasWorker ? buffer.readUUID() : null;
        String workerName = buffer.readUtf(256);
        double cityBalance = buffer.readDouble();
        boolean hasBuildingBounds = buffer.readBoolean();
        BlockPos boundsMin = buffer.readBlockPos();
        BlockPos boundsMax = buffer.readBlockPos();
        boolean integrityAvailable = buffer.readBoolean();
        double integrityPercent = buffer.readDouble();
        int integrityRepairableBlocks = buffer.readVarInt();
        int integrityManualRepairBlocks = buffer.readVarInt();
        double integrityRepairCost = buffer.readDouble();
        return new GlobalMarketControlBoxOpenResponsePacket(boxPos, hasBuilding, buildingName, statusKey, hasWorker,
                workerId, workerName, cityBalance, hasBuildingBounds, boundsMin, boundsMax, integrityAvailable,
                integrityPercent, integrityRepairableBlocks, integrityManualRepairBlocks, integrityRepairCost);
    }

    public static void handle(GlobalMarketControlBoxOpenResponsePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientboundNetworkBridge.handleGlobalMarketControlBoxOpenResponse(packet));
    }
}
