package common.cn.kafei.simukraft.network.city.chunk;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.city.CityChunkManager;
import common.cn.kafei.simukraft.city.CityService;
import common.cn.kafei.simukraft.city.DistrictData;
import common.cn.kafei.simukraft.city.DistrictManager;
import common.cn.kafei.simukraft.city.DistrictOwnershipSync;
import common.cn.kafei.simukraft.city.group.CityGroupMessageService;
import common.cn.kafei.simukraft.network.city.core.CityCoreAccessValidator;
import common.cn.kafei.simukraft.network.city.map.CityCoreMapRequestPacket;
import common.cn.kafei.simukraft.network.hud.HudSyncService;
import common.cn.kafei.simukraft.network.toast.InfoToastService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;


public record CityChunkBatchReleasePacket(BlockPos pos, List<ChunkEntry> chunks) implements CustomPacketPayload {
    private static final int MAX_CHUNKS = 256;
    public static final Type<CityChunkBatchReleasePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "city_chunk_batch_release"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CityChunkBatchReleasePacket> STREAM_CODEC = StreamCodec.of(CityChunkBatchReleasePacket::encode, CityChunkBatchReleasePacket::decode);

    public CityChunkBatchReleasePacket {
        chunks = chunks == null ? List.of() : List.copyOf(chunks.size() > MAX_CHUNKS ? chunks.subList(0, MAX_CHUNKS) : chunks);
    }

    public static void encode(RegistryFriendlyByteBuf buffer, CityChunkBatchReleasePacket packet) {
        buffer.writeBlockPos(packet.pos());
        buffer.writeVarInt(packet.chunks().size());
        for (ChunkEntry chunk : packet.chunks()) {
            buffer.writeVarInt(chunk.chunkX());
            buffer.writeVarInt(chunk.chunkZ());
        }
    }

    public static CityChunkBatchReleasePacket decode(RegistryFriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        int size = buffer.readVarInt();
        if (size < 0 || size > MAX_CHUNKS) {
            throw new IllegalArgumentException("Invalid city chunk batch release size: " + size);
        }
        List<ChunkEntry> chunks = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            chunks.add(new ChunkEntry(buffer.readVarInt(), buffer.readVarInt()));
        }
        return new CityChunkBatchReleasePacket(pos, chunks);
    }

    public static void handle(CityChunkBatchReleasePacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        ServerLevel serverLevel = player.serverLevel();
        if (!CityCoreAccessValidator.canAccess(serverLevel, player, packet.pos())) {
            return;
        }
        CityService.findCityByCorePos(serverLevel, packet.pos()).ifPresent(city -> {
            CityChunkManager chunkManager = CityChunkManager.get(serverLevel);
            DistrictManager districtManager = DistrictManager.get(serverLevel);
            long parentCoreChunk = new ChunkPos(city.cityCorePos()).toLong();
            Set<Long> detach = new LinkedHashSet<>();
            List<Long> release = new ArrayList<>();
            for (ChunkEntry chunk : packet.chunks()) {
                long chunkLong = ChunkPos.asLong(chunk.chunkX(), chunk.chunkZ());
                if (chunkLong == parentCoreChunk || isDistrictCoreChunk(districtManager, city.cityId(), chunkLong)) {
                    continue;
                }
                DistrictData district = districtManager.byChunk(chunkLong).orElse(null);
                if (district != null && city.cityId().equals(district.parentCityId())) {
                    detach.add(chunkLong);
                }
                release.add(chunkLong);
            }
            boolean detached = !detach.isEmpty() && districtManager.moveToCity(city.cityId(), detach);
            int released = 0;
            for (long chunkLong : release) {
                if (detach.contains(chunkLong) && !detached) {
                    continue;
                }
                if (chunkManager.unclaimChunk(city.cityId(), chunkLong)) {
                    released++;
                }
            }
            if (detached || released > 0) {
                DistrictOwnershipSync.retagCity(serverLevel, city.cityId());
            }
            if (released > 0) {
                Component message = Component.translatable("message.simukraft.city_chunk.batch_released", released);
                CityGroupMessageService.successToCity(serverLevel, city.cityId(), message);
                CityChunkSyncService.syncToAll(serverLevel);
                HudSyncService.syncToCityGroup(serverLevel, city.cityId(), true);
            } else {
                InfoToastService.warning(player, Component.translatable("message.simukraft.city_chunk.release_failed"));
            }
            CityCoreMapRequestPacket.sendMap(serverLevel, player, packet.pos());
        });
    }

    @Override
    public @Nonnull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static boolean isDistrictCoreChunk(DistrictManager manager, java.util.UUID cityId, long chunkLong) {
        DistrictData district = manager.byChunk(chunkLong).orElse(null);
        if (district == null || !cityId.equals(district.parentCityId())) {
            return false;
        }
        return district.cores().stream().anyMatch(core -> new ChunkPos(net.minecraft.core.BlockPos.of(core)).toLong() == chunkLong);
    }

    public record ChunkEntry(int chunkX, int chunkZ) {
    }
}
