package common.cn.nsimu.simukraft.network.rts;

import common.cn.nsimu.simukraft.SimuKraft;
import common.cn.nsimu.simukraft.building.BuilderConstructionService;
import common.cn.nsimu.simukraft.building.ConstructionPreviewSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * RTS 施工预览请求：只读附近未完工任务，不修改服务端状态。
 */
public record RtsConstructionPreviewRequestPacket() implements CustomPacketPayload {
    private static final double MAX_DISTANCE_SQR = 192.0D * 192.0D;

    public static final Type<RtsConstructionPreviewRequestPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "rts_construction_preview_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RtsConstructionPreviewRequestPacket> STREAM_CODEC =
            StreamCodec.unit(new RtsConstructionPreviewRequestPacket());

    @Override
    public Type<RtsConstructionPreviewRequestPacket> type() {
        return TYPE;
    }

    /**
     * handle: 在服务端主线程返回玩家 RTS 视野附近的施工快照。
     */
    public static void handle(RtsConstructionPreviewRequestPacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        context.enqueueWork(() -> sendNearby(player, level));
    }

    /**
     * sendNearby: 按当前 RTS 焦点生成受限施工快照。
     */
    public static void sendNearby(ServerPlayer player, ServerLevel level) {
        if (player == null || level == null || !RtsChunkViewService.isActive(level, player)) {
            return;
        }
        ChunkPos center = RtsChunkViewService.viewCenter(level, player);
        List<RtsConstructionPreviewSyncPacket.Entry> entries = BuilderConstructionService.nearbyPreviews(
                        level,
                        center.getMiddleBlockX(),
                        center.getMiddleBlockZ(),
                        MAX_DISTANCE_SQR,
                        RtsConstructionPreviewSyncPacket.MAX_ENTRIES)
                .stream()
                .map(RtsConstructionPreviewRequestPacket::toEntry)
                .toList();
        PacketDistributor.sendToPlayer(player, new RtsConstructionPreviewSyncPacket(entries));
    }

    /**
     * refreshNear: 任务开始或结束时，立刻刷新附近正在使用 RTS 的玩家。
     */
    public static void refreshNear(ServerLevel level, BlockPos origin) {
        if (level == null || origin == null) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (!RtsChunkViewService.isActive(level, player)) {
                continue;
            }
            ChunkPos center = RtsChunkViewService.viewCenter(level, player);
            double deltaX = origin.getX() + 0.5D - center.getMiddleBlockX();
            double deltaZ = origin.getZ() + 0.5D - center.getMiddleBlockZ();
            if (deltaX * deltaX + deltaZ * deltaZ <= MAX_DISTANCE_SQR) {
                sendNearby(player, level);
            }
        }
    }

    private static RtsConstructionPreviewSyncPacket.Entry toEntry(ConstructionPreviewSnapshot snapshot) {
        return new RtsConstructionPreviewSyncPacket.Entry(
                snapshot.taskId(),
                snapshot.category(),
                snapshot.buildingFileName(),
                snapshot.structureFileName(),
                snapshot.origin(),
                snapshot.rotationDegrees(),
                snapshot.currentBlockIndex(),
                snapshot.totalBlocks(),
                snapshot.builderName(),
                snapshot.displayName());
    }
}
