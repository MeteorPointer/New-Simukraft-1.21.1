package common.cn.nsimu.simukraft.network.building.controlbox;

import common.cn.nsimu.simukraft.SimuKraft;
import common.cn.nsimu.simukraft.network.clientbound.ClientboundNetworkBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * BuildingBoundsPreviewSyncPacket: 把建筑身份发给客户端，由客户端自己加载结构做预览。
 */
public record BuildingBoundsPreviewSyncPacket(BlockPos controlBoxPos,
                                              String category,
                                              String buildingFileName,
                                              String structureFileName,
                                              BlockPos origin,
                                              String facing,
                                              BlockPos min,
                                              BlockPos max) implements CustomPacketPayload {
    private static final int MAX_NAME_LENGTH = 128;

    public static final Type<BuildingBoundsPreviewSyncPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "building_bounds_preview_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BuildingBoundsPreviewSyncPacket> STREAM_CODEC =
            StreamCodec.of(BuildingBoundsPreviewSyncPacket::encode, BuildingBoundsPreviewSyncPacket::decode);

    public BuildingBoundsPreviewSyncPacket {
        category = trim(category);
        buildingFileName = trim(buildingFileName);
        structureFileName = trim(structureFileName);
        facing = trim(facing);
        controlBoxPos = controlBoxPos == null ? BlockPos.ZERO : controlBoxPos.immutable();
        origin = origin == null ? BlockPos.ZERO : origin.immutable();
        min = min == null ? BlockPos.ZERO : min.immutable();
        max = max == null ? BlockPos.ZERO : max.immutable();
    }

    @Override
    public Type<BuildingBoundsPreviewSyncPacket> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buffer, BuildingBoundsPreviewSyncPacket packet) {
        buffer.writeBlockPos(packet.controlBoxPos());
        buffer.writeUtf(packet.category(), MAX_NAME_LENGTH);
        buffer.writeUtf(packet.buildingFileName(), MAX_NAME_LENGTH);
        buffer.writeUtf(packet.structureFileName(), MAX_NAME_LENGTH);
        buffer.writeBlockPos(packet.origin());
        buffer.writeUtf(packet.facing(), 16);
        buffer.writeBlockPos(packet.min());
        buffer.writeBlockPos(packet.max());
    }

    private static BuildingBoundsPreviewSyncPacket decode(RegistryFriendlyByteBuf buffer) {
        return new BuildingBoundsPreviewSyncPacket(
                buffer.readBlockPos(),
                buffer.readUtf(MAX_NAME_LENGTH),
                buffer.readUtf(MAX_NAME_LENGTH),
                buffer.readUtf(MAX_NAME_LENGTH),
                buffer.readBlockPos(),
                buffer.readUtf(16),
                buffer.readBlockPos(),
                buffer.readBlockPos());
    }

    public static void handle(BuildingBoundsPreviewSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientboundNetworkBridge.handleBuildingBoundsPreviewSync(packet));
    }

    private static String trim(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() > MAX_NAME_LENGTH ? trimmed.substring(0, MAX_NAME_LENGTH) : trimmed;
    }
}
