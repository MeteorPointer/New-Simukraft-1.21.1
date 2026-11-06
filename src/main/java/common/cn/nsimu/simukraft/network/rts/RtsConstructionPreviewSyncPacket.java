package common.cn.nsimu.simukraft.network.rts;

import common.cn.nsimu.simukraft.SimuKraft;
import common.cn.nsimu.simukraft.network.clientbound.ClientboundNetworkBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * RTS 施工预览快照：客户端用它加载结构并绘制与放置预览相同的网格。
 */
public record RtsConstructionPreviewSyncPacket(List<Entry> entries) implements CustomPacketPayload {
    public static final int MAX_ENTRIES = 32;
    public static final int MAX_NAME_LENGTH = 128;

    public static final Type<RtsConstructionPreviewSyncPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "rts_construction_preview_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RtsConstructionPreviewSyncPacket> STREAM_CODEC =
            StreamCodec.of(RtsConstructionPreviewSyncPacket::encode, RtsConstructionPreviewSyncPacket::decode);

    public RtsConstructionPreviewSyncPacket {
        entries = entries == null ? List.of() : entries.stream()
                .filter(entry -> entry != null && entry.taskId() != null && entry.origin() != null)
                .limit(MAX_ENTRIES)
                .toList();
    }

    @Override
    public Type<RtsConstructionPreviewSyncPacket> type() {
        return TYPE;
    }

    /**
     * encode: 写入有限的施工预览列表。
     */
    private static void encode(RegistryFriendlyByteBuf buffer, RtsConstructionPreviewSyncPacket packet) {
        buffer.writeVarInt(packet.entries().size());
        for (Entry entry : packet.entries()) {
            buffer.writeUUID(entry.taskId());
            buffer.writeUtf(entry.category(), MAX_NAME_LENGTH);
            buffer.writeUtf(entry.buildingFileName(), MAX_NAME_LENGTH);
            buffer.writeUtf(entry.structureFileName(), MAX_NAME_LENGTH);
            buffer.writeBlockPos(entry.origin());
            buffer.writeVarInt(entry.rotationDegrees());
            buffer.writeVarInt(entry.currentBlockIndex());
            buffer.writeVarInt(entry.totalBlocks());
            buffer.writeUtf(entry.builderName(), MAX_NAME_LENGTH);
            buffer.writeUtf(entry.displayName(), MAX_NAME_LENGTH);
        }
    }

    /**
     * decode: 限制条数和字符串长度，避免异常包撑大内存。
     */
    private static RtsConstructionPreviewSyncPacket decode(RegistryFriendlyByteBuf buffer) {
        int count = Math.min(Math.max(0, buffer.readVarInt()), MAX_ENTRIES);
        ArrayList<Entry> entries = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            entries.add(new Entry(
                    buffer.readUUID(),
                    buffer.readUtf(MAX_NAME_LENGTH),
                    buffer.readUtf(MAX_NAME_LENGTH),
                    buffer.readUtf(MAX_NAME_LENGTH),
                    buffer.readBlockPos(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readUtf(MAX_NAME_LENGTH),
                    buffer.readUtf(MAX_NAME_LENGTH)));
        }
        return new RtsConstructionPreviewSyncPacket(entries);
    }

    /**
     * handle: 切到客户端线程更新施工预览。
     */
    public static void handle(RtsConstructionPreviewSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientboundNetworkBridge.handleRtsConstructionPreviewSync(packet));
    }

    public record Entry(UUID taskId,
                        String category,
                        String buildingFileName,
                        String structureFileName,
                        BlockPos origin,
                        int rotationDegrees,
                        int currentBlockIndex,
                        int totalBlocks,
                        String builderName,
                        String displayName) {
        public Entry {
            category = trim(category);
            buildingFileName = trim(buildingFileName);
            structureFileName = trim(structureFileName);
            builderName = trim(builderName);
            displayName = trim(displayName);
            rotationDegrees = Math.floorMod(rotationDegrees, 360);
            currentBlockIndex = Math.max(0, currentBlockIndex);
            totalBlocks = Math.max(0, totalBlocks);
        }

        private static String trim(String value) {
            if (value == null) {
                return "";
            }
            String trimmed = value.trim();
            return trimmed.length() > MAX_NAME_LENGTH ? trimmed.substring(0, MAX_NAME_LENGTH) : trimmed;
        }
    }
}
