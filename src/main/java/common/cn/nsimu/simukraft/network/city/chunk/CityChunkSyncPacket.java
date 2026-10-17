package common.cn.nsimu.simukraft.network.city.chunk;

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
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


public record CityChunkSyncPacket(UUID currentCityId,
                                  Map<UUID, Set<Long>> cityChunks,
                                  Map<UUID, CityCoreEntry> cityCores,
                                  List<DistrictEntry> districts) implements CustomPacketPayload {
    public static final Type<CityChunkSyncPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "city_chunk_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CityChunkSyncPacket> STREAM_CODEC = StreamCodec.of(CityChunkSyncPacket::encode, CityChunkSyncPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, CityChunkSyncPacket packet) {
        buffer.writeBoolean(packet.currentCityId() != null);
        if (packet.currentCityId() != null) {
            buffer.writeUUID(packet.currentCityId());
        }
        buffer.writeVarInt(packet.cityChunks().size());
        packet.cityChunks().forEach((cityId, chunks) -> {
            buffer.writeUUID(cityId);
            buffer.writeVarInt(chunks.size());
            chunks.forEach(buffer::writeLong);
        });
        buffer.writeVarInt(packet.cityCores().size());
        packet.cityCores().forEach((cityId, core) -> {
            buffer.writeUUID(cityId);
            buffer.writeBlockPos(core.pos());
            buffer.writeUtf(core.cityName(), 64);
        });
        List<DistrictEntry> districts = packet.districts() == null ? List.of() : packet.districts();
        buffer.writeVarInt(districts.size());
        for (DistrictEntry district : districts) {
            buffer.writeUUID(district.districtId());
            buffer.writeBoolean(district.cityId() != null);
            if (district.cityId() != null) {
                buffer.writeUUID(district.cityId());
            }
            buffer.writeUtf(district.name() == null ? "" : district.name(), 64);
            Set<Long> chunks = district.chunks() == null ? Set.of() : district.chunks();
            buffer.writeVarInt(chunks.size());
            chunks.forEach(buffer::writeLong);
        }
    }

    public static CityChunkSyncPacket decode(RegistryFriendlyByteBuf buffer) {
        UUID currentCityId = buffer.readBoolean() ? buffer.readUUID() : null;
        int cityCount = buffer.readVarInt();
        Map<UUID, Set<Long>> cityChunks = new ConcurrentHashMap<>();
        for (int cityIndex = 0; cityIndex < cityCount; cityIndex++) {
            UUID cityId = buffer.readUUID();
            int chunkCount = buffer.readVarInt();
            Set<Long> chunks = ConcurrentHashMap.newKeySet();
            for (int chunkIndex = 0; chunkIndex < chunkCount; chunkIndex++) {
                chunks.add(buffer.readLong());
            }
            cityChunks.put(cityId, Set.copyOf(chunks));
        }
        int coreCount = buffer.readVarInt();
        Map<UUID, CityCoreEntry> cityCores = new ConcurrentHashMap<>();
        for (int coreIndex = 0; coreIndex < coreCount; coreIndex++) {
            cityCores.put(buffer.readUUID(), new CityCoreEntry(buffer.readBlockPos(), buffer.readUtf(64)));
        }
        int districtCount = buffer.readVarInt();
        List<DistrictEntry> districts = new ArrayList<>(districtCount);
        for (int districtIndex = 0; districtIndex < districtCount; districtIndex++) {
            UUID districtId = buffer.readUUID();
            UUID cityId = buffer.readBoolean() ? buffer.readUUID() : null;
            String name = buffer.readUtf(64);
            int chunkCount = buffer.readVarInt();
            Set<Long> chunks = ConcurrentHashMap.newKeySet();
            for (int chunkIndex = 0; chunkIndex < chunkCount; chunkIndex++) {
                chunks.add(buffer.readLong());
            }
            districts.add(new DistrictEntry(districtId, cityId, name, Set.copyOf(chunks)));
        }
        return new CityChunkSyncPacket(currentCityId, Map.copyOf(cityChunks), Map.copyOf(cityCores), List.copyOf(districts));
    }

    public static void handle(CityChunkSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientboundNetworkBridge.handleCityChunkSync(packet));
    }

    public record CityCoreEntry(BlockPos pos, String cityName) {
    }

    /**
     * DistrictEntry: 一个分区的名称和所占区块，供客户端进入横幅查询。
     */
    public record DistrictEntry(UUID districtId, UUID cityId, String name, Set<Long> chunks) {
    }
}
