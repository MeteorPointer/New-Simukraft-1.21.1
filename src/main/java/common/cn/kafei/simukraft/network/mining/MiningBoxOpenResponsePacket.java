package common.cn.kafei.simukraft.network.mining;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.network.clientbound.ClientboundNetworkBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** MiningBoxOpenResponsePacket: 挖矿盒界面快照。 */
public record MiningBoxOpenResponsePacket(BlockPos pos,
                                          boolean hasMiner,
                                          UUID minerId,
                                          String minerName,
                                          boolean hasArea,
                                          BlockPos point1,
                                          BlockPos point2,
                                          int topY,
                                          int width,
                                          int depth,
                                          String mode,
                                          boolean running) implements CustomPacketPayload {
    public static final Type<MiningBoxOpenResponsePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "mining_box_open_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MiningBoxOpenResponsePacket> STREAM_CODEC =
            StreamCodec.of(MiningBoxOpenResponsePacket::encode, MiningBoxOpenResponsePacket::decode);

    public MiningBoxOpenResponsePacket {
        minerName = minerName == null ? "" : minerName;
        point1 = point1 == null ? BlockPos.ZERO : point1;
        point2 = point2 == null ? BlockPos.ZERO : point2;
        mode = mode == null || mode.isBlank() ? "SURFACE" : mode;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, MiningBoxOpenResponsePacket packet) {
        buffer.writeBlockPos(packet.pos());
        buffer.writeBoolean(packet.hasMiner());
        buffer.writeBoolean(packet.minerId() != null);
        if (packet.minerId() != null) {
            buffer.writeUUID(packet.minerId());
        }
        buffer.writeUtf(packet.minerName(), 64);
        buffer.writeBoolean(packet.hasArea());
        buffer.writeBlockPos(packet.point1());
        buffer.writeBlockPos(packet.point2());
        buffer.writeVarInt(packet.topY());
        buffer.writeVarInt(packet.width());
        buffer.writeVarInt(packet.depth());
        buffer.writeUtf(packet.mode(), 32);
        buffer.writeBoolean(packet.running());
    }

    public static MiningBoxOpenResponsePacket decode(RegistryFriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        boolean hasMiner = buffer.readBoolean();
        UUID minerId = buffer.readBoolean() ? buffer.readUUID() : null;
        String minerName = buffer.readUtf(64);
        boolean hasArea = buffer.readBoolean();
        BlockPos point1 = buffer.readBlockPos();
        BlockPos point2 = buffer.readBlockPos();
        int topY = buffer.readVarInt();
        int width = buffer.readVarInt();
        int depth = buffer.readVarInt();
        return new MiningBoxOpenResponsePacket(pos, hasMiner, minerId, minerName, hasArea, point1, point2,
                topY, width, depth, buffer.readUtf(32), buffer.readBoolean());
    }

    public static void handle(MiningBoxOpenResponsePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientboundNetworkBridge.handleMiningBoxOpenResponse(packet));
    }
}
