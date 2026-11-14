package common.cn.nsimu.simukraft.network.building.controlbox;

import common.cn.nsimu.simukraft.SimuKraft;
import common.cn.nsimu.simukraft.building.PlacedBuildingRecord;
import common.cn.nsimu.simukraft.building.PlacedBuildingService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * BuildingBoundsPreviewRequestPacket: 客户端切到 NBT 预览时向服务端索取建筑身份。
 */
public record BuildingBoundsPreviewRequestPacket(BlockPos controlBoxPos) implements CustomPacketPayload {
    private static final double MAX_DISTANCE_SQR = 64.0D * 64.0D;

    public static final Type<BuildingBoundsPreviewRequestPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "building_bounds_preview_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BuildingBoundsPreviewRequestPacket> STREAM_CODEC =
            StreamCodec.of(BuildingBoundsPreviewRequestPacket::encode, BuildingBoundsPreviewRequestPacket::decode);

    @Override
    public Type<BuildingBoundsPreviewRequestPacket> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buffer, BuildingBoundsPreviewRequestPacket packet) {
        buffer.writeBlockPos(packet.controlBoxPos());
    }

    private static BuildingBoundsPreviewRequestPacket decode(RegistryFriendlyByteBuf buffer) {
        return new BuildingBoundsPreviewRequestPacket(buffer.readBlockPos());
    }

    /**
     * handle: 只读该控制盒所在建筑的类别、文件和朝向。
     */
    public static void handle(BuildingBoundsPreviewRequestPacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        context.enqueueWork(() -> send(player, level, packet.controlBoxPos()));
    }

    private static void send(ServerPlayer player, ServerLevel level, BlockPos controlBoxPos) {
        if (player == null || level == null || controlBoxPos == null) {
            return;
        }
        if (player.distanceToSqr(controlBoxPos.getX() + 0.5D, controlBoxPos.getY() + 0.5D, controlBoxPos.getZ() + 0.5D) > MAX_DISTANCE_SQR) {
            return;
        }
        PlacedBuildingRecord building = PlacedBuildingService.findByContainedPos(level, controlBoxPos);
        if (building == null || building.worldOrigin() == null) {
            return;
        }
        PacketDistributor.sendToPlayer(player, new BuildingBoundsPreviewSyncPacket(
                controlBoxPos.immutable(),
                building.category() == null ? "" : building.category(),
                building.buildingFileName() == null ? "" : building.buildingFileName(),
                building.structureFileName() == null ? "" : building.structureFileName(),
                building.worldOrigin().immutable(),
                building.facing() == null ? "" : building.facing(),
                building.minPos() == null ? controlBoxPos : building.minPos().immutable(),
                building.maxPos() == null ? controlBoxPos : building.maxPos().immutable()));
    }
}
