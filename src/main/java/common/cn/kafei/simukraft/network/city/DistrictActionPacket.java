package common.cn.kafei.simukraft.network.city;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.city.CityService;
import common.cn.kafei.simukraft.city.DistrictData;
import common.cn.kafei.simukraft.city.DistrictManager;
import common.cn.kafei.simukraft.city.DistrictRole;
import common.cn.kafei.simukraft.city.CityChunkManager;
import common.cn.kafei.simukraft.network.toast.InfoToastService;
import common.cn.kafei.simukraft.network.city.core.CityCoreOpenRequestPacket;
import common.cn.kafei.simukraft.network.city.map.CityCoreMapRequestPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.item.ItemStack;
import common.cn.kafei.simukraft.registry.ModItems;
import common.cn.kafei.simukraft.building.PlacedBuildingService;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record DistrictActionPacket(Action action, BlockPos corePos, UUID districtId, UUID targetPlayerId, String name, List<Long> chunks) implements CustomPacketPayload {
    private static final int MAX_CHUNKS = 256;
    public static final Type<DistrictActionPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "district_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DistrictActionPacket> STREAM_CODEC = StreamCodec.of(DistrictActionPacket::encode, DistrictActionPacket::decode);

    public DistrictActionPacket {
        name = name == null ? "" : name.length() > 32 ? name.substring(0, 32) : name;
        chunks = chunks == null ? List.of() : List.copyOf(chunks.size() > MAX_CHUNKS ? chunks.subList(0, MAX_CHUNKS) : chunks);
    }

    public DistrictActionPacket(Action action, BlockPos corePos, UUID districtId, String name, List<Long> chunks) {
        this(action, corePos, districtId, null, name, chunks);
    }

    public enum Action { CREATE, ASSIGN, MOVE_TO_CITY, BIND_CORE, UNBIND_CORE, RENAME, DELETE, INVITE, REMOVE_MEMBER, SET_MAYOR, SET_OFFICIAL, REMOVE_OFFICIAL }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    private static void encode(RegistryFriendlyByteBuf buffer, DistrictActionPacket packet) {
        buffer.writeVarInt(packet.action().ordinal());
        buffer.writeBlockPos(packet.corePos() == null ? BlockPos.ZERO : packet.corePos());
        buffer.writeBoolean(packet.districtId() != null); if (packet.districtId() != null) buffer.writeUUID(packet.districtId());
        buffer.writeBoolean(packet.targetPlayerId() != null); if (packet.targetPlayerId() != null) buffer.writeUUID(packet.targetPlayerId());
        buffer.writeUtf(packet.name(), 32); buffer.writeVarInt(packet.chunks().size()); packet.chunks().forEach(buffer::writeLong);
    }

    private static DistrictActionPacket decode(RegistryFriendlyByteBuf buffer) {
        Action action = Action.values()[Math.max(0, Math.min(Action.values().length - 1, buffer.readVarInt()))];
        BlockPos corePos = buffer.readBlockPos(); UUID districtId = buffer.readBoolean() ? buffer.readUUID() : null;
        UUID targetPlayerId = buffer.readBoolean() ? buffer.readUUID() : null;
        String name = buffer.readUtf(32); int size = buffer.readVarInt(); if (size < 0 || size > MAX_CHUNKS) throw new IllegalArgumentException("Invalid district chunk count");
        List<Long> chunks = new ArrayList<>(size); for (int i = 0; i < size; i++) chunks.add(buffer.readLong());
        return new DistrictActionPacket(action, corePos, districtId, targetPlayerId, name, chunks);
    }

    public static void handle(DistrictActionPacket packet, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            context.enqueueWork(() -> execute(packet, player, level));
        }
    }

    private static void execute(DistrictActionPacket packet, ServerPlayer player, ServerLevel level) {
        var city = CityService.findPlayerCity(level, player.getUUID()).orElse(null);
        if (city == null) { InfoToastService.warning(player, Component.translatable("message.simukraft.district.no_city")); return; }
        DistrictManager manager = DistrictManager.get(level);
        boolean ok = false;
        Set<Long> chunks = packet.chunks().stream().collect(Collectors.toUnmodifiableSet());
        switch (packet.action()) {
            case CREATE -> {
                if (CityService.hasPermission(level, city.cityId(), player.getUUID(), common.cn.kafei.simukraft.city.CityPermissionLevel.MAYOR)) {
                    CityChunkManager chunkManager = CityChunkManager.get(level);
                    // A selection commonly contains the parent core chunk because the map is
                    // centered on it. Core chunks are protected and cannot belong to a district,
                    // so ignore them while keeping the rest of the atomic create operation.
                    Set<Long> eligibleChunks = chunks.stream()
                            .filter(chunk -> !isProtectedCoreChunk(level, chunk))
                            .collect(Collectors.toUnmodifiableSet());
                    boolean allParentChunks = !eligibleChunks.isEmpty() && eligibleChunks.stream()
                            .allMatch(chunk -> city.cityId().equals(chunkManager.getChunkOwner(chunk)) && manager.byChunk(chunk).isEmpty());
                    if (allParentChunks) {
                        ok = manager.create(city.cityId(), packet.name(), player.getUUID(), player.getName().getString(), eligibleChunks) != null;
                        if (ok) {
                            InfoToastService.success(player, Component.translatable("message.simukraft.district.created", manager.byChunk(eligibleChunks.iterator().next()).map(DistrictData::name).orElse(packet.name()), eligibleChunks.size()));
                        }
                    } else if (eligibleChunks.isEmpty()) {
                        InfoToastService.warning(player, Component.translatable("message.simukraft.district.create_no_chunks"));
                    } else if (eligibleChunks.stream().anyMatch(chunk -> manager.byChunk(chunk).isPresent())) {
                        InfoToastService.warning(player, Component.translatable("message.simukraft.district.create_existing"));
                    } else {
                        InfoToastService.warning(player, Component.translatable("message.simukraft.district.create_not_owned"));
                    }
                } else {
                    InfoToastService.warning(player, Component.translatable("message.simukraft.district.no_permission"));
                }
            }
            case ASSIGN -> ok = common.cn.kafei.simukraft.city.DistrictService.assign(level, city.cityId(), player.getUUID(), packet.districtId(), chunks);
            case MOVE_TO_CITY -> ok = CityService.hasPermission(level, city.cityId(), player.getUUID(), common.cn.kafei.simukraft.city.CityPermissionLevel.MAYOR) && manager.moveToCity(city.cityId(), chunks);
            case BIND_CORE -> {
                DistrictData district = packet.districtId() == null ? manager.byChunk(new ChunkPos(packet.corePos())).orElse(null) : manager.get(packet.districtId()).orElse(null);
                if (district != null && district.parentCityId().equals(city.cityId()) && (district.hasPermission(player.getUUID(), DistrictRole.OFFICIAL) || CityService.hasPermission(level, city.cityId(), player.getUUID(), common.cn.kafei.simukraft.city.CityPermissionLevel.MAYOR))) {
                    ok = level.getBlockState(packet.corePos()).is(common.cn.kafei.simukraft.registry.ModBlocks.CITY_CORE.get())
                            && PlacedBuildingService.findByContainedPos(level, packet.corePos()) == null
                            && manager.bindCore(district.districtId(), new ChunkPos(packet.corePos()), packet.corePos().asLong());
                }
            }
            case UNBIND_CORE -> {
                DistrictData district = packet.districtId() == null ? null : manager.get(packet.districtId()).orElse(null);
                if (district != null && (CityService.hasPermission(level, city.cityId(), player.getUUID(), common.cn.kafei.simukraft.city.CityPermissionLevel.MAYOR) || district.hasPermission(player.getUUID(), DistrictRole.MAYOR))) {
                    ok = manager.unbindCore(district.districtId(), packet.corePos().asLong());
                    if (ok && level.getBlockState(packet.corePos()).is(common.cn.kafei.simukraft.registry.ModBlocks.CITY_CORE.get())) {
                        level.setBlock(packet.corePos(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
                        net.minecraft.world.level.block.Block.popResource(level, packet.corePos(), new ItemStack(ModItems.PORTABLE_CITY_CORE.get()));
                    }
                }
            }
            case RENAME -> ok = CityService.hasPermission(level, city.cityId(), player.getUUID(), common.cn.kafei.simukraft.city.CityPermissionLevel.MAYOR)
                    && manager.rename(packet.districtId(), city.cityId(), packet.name());
            case DELETE -> {
                if (CityService.hasPermission(level, city.cityId(), player.getUUID(), common.cn.kafei.simukraft.city.CityPermissionLevel.MAYOR)) {
                    DistrictData district = manager.get(packet.districtId()).orElse(null);
                    List<Long> cores = district == null ? List.of() : List.copyOf(district.cores());
                    ok = manager.delete(packet.districtId(), city.cityId());
                    if (ok) {
                        for (long core : cores) {
                            BlockPos pos = BlockPos.of(core);
                            if (level.getBlockState(pos).is(common.cn.kafei.simukraft.registry.ModBlocks.CITY_CORE.get())) {
                                level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
                                net.minecraft.world.level.block.Block.popResource(level, pos, new ItemStack(ModItems.PORTABLE_CITY_CORE.get()));
                            }
                        }
                    }
                }
            }
            case INVITE, REMOVE_MEMBER, SET_MAYOR, SET_OFFICIAL, REMOVE_OFFICIAL -> {
                DistrictData district = packet.districtId() == null ? null : manager.get(packet.districtId()).orElse(null);
                UUID target = packet.targetPlayerId();
                if (district != null && target != null && district.parentCityId().equals(city.cityId())) {
                    boolean cityMayor = CityService.hasPermission(level, city.cityId(), player.getUUID(), common.cn.kafei.simukraft.city.CityPermissionLevel.MAYOR);
                    boolean districtMayor = district.hasPermission(player.getUUID(), DistrictRole.MAYOR);
                    String targetName = level.getServer().getProfileCache().get(target).map(p -> p.getName()).orElse(target.toString());
                    if (packet.action() == Action.SET_MAYOR) ok = cityMayor && manager.setMayor(district.districtId(), target, targetName);
                    else if (packet.action() == Action.INVITE) ok = (cityMayor || districtMayor) && manager.addMember(district.districtId(), target, targetName, DistrictRole.RESIDENT);
                    else if (packet.action() == Action.REMOVE_MEMBER) ok = (cityMayor || districtMayor) && !target.equals(player.getUUID()) && manager.removeMember(district.districtId(), target);
                    else if (packet.action() == Action.SET_OFFICIAL) ok = (cityMayor || districtMayor) && manager.setMemberRole(district.districtId(), target, DistrictRole.OFFICIAL);
                    else if (packet.action() == Action.REMOVE_OFFICIAL) ok = (cityMayor || districtMayor) && manager.setMemberRole(district.districtId(), target, DistrictRole.RESIDENT);
                }
            }
        }
        if (!ok) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.district.failed"));
        }
        // Refresh the parent core snapshot so the management tab sees newly created,
        // renamed, assigned, or deleted districts without reopening the block.
        CityCoreOpenRequestPacket.openFor(level, player, packet.corePos());
        CityCoreMapRequestPacket.sendMap(level, player, packet.corePos());
    }

    private static boolean isProtectedCoreChunk(ServerLevel level, long chunkLong) {
        ChunkPos chunk = new ChunkPos(chunkLong);
        if (CityService.allCities(level).stream().anyMatch(city -> new ChunkPos(city.cityCorePos()).toLong() == chunkLong)) {
            return true;
        }
        return DistrictManager.get(level).all().stream()
                .flatMap(district -> district.cores().stream())
                .anyMatch(core -> new ChunkPos(BlockPos.of(core)).toLong() == chunk.toLong());
    }
}
