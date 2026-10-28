package common.cn.nsimu.simukraft.network.city;

import common.cn.nsimu.simukraft.building.PlacedBuildingService;
import common.cn.nsimu.simukraft.city.*;
import common.cn.nsimu.simukraft.network.city.core.CityCoreOpenResponsePacket;
import common.cn.nsimu.simukraft.network.city.map.CityCoreMapResponsePacket;
import common.cn.nsimu.simukraft.network.city.member.CityCoreMembersResponsePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.*;


public final class CityNetworkViewFactory {
    private CityNetworkViewFactory() {
    }

    public static CityCoreOpenResponsePacket buildOpenResponse(ServerLevel level, BlockPos pos, UUID viewerId) {
        DistrictData districtContext = null;
        if (level != null && pos != null) {
            districtContext = DistrictManager.get(level).byChunk(new ChunkPos(pos))
                    .filter(district -> district.cores().contains(pos.asLong()))
                    .orElse(null);
        }
        Optional<CityData> city = districtContext == null
                ? CityService.findCityByCorePosForPlayer(level, pos, viewerId)
                : CityService.findCity(level, districtContext.parentCityId());
        Optional<CityData> playerCity = CityService.findPlayerCity(level, viewerId);
        CityPermissionLevel permissionLevel = city.map(data -> CityService.getPlayerPermission(data, viewerId)).orElse(CityPermissionLevel.CITIZEN);
        boolean canCreateCity = city.isEmpty() && playerCity.isEmpty();
        boolean canManageCity = city.map(data -> CityService.canManageCity(data, viewerId)).orElse(false);
        CityCoreOpenResponsePacket response = buildOpenResponse(level, pos, city, permissionLevel, canCreateCity, canManageCity);
        if (districtContext == null || !response.hasCity()) return response;
        return new CityCoreOpenResponsePacket(response.pos(), true, response.cityId(), response.cityName() + " " + districtContext.name(), response.funds(), response.cityLevel(), response.memberCount(), response.cityPopulation(), response.housingCapacity(), response.cityChunkCount(), response.cityEnclaveCount(), response.permissionLevel(), response.canCreateCity(), response.canManageCity(), response.financeEntries(), response.poiStats(), response.jobStats(), response.upgradeTargets(), response.upgradeProgress(), response.districts(), true, districtContext.name(), response.cityMembers());
    }

    public static CityCoreOpenResponsePacket buildOpenResponse(ServerLevel level, BlockPos pos, Optional<CityData> city, CityPermissionLevel permissionLevel, boolean canCreateCity, boolean canManageCity) {
        if (city.isEmpty()) {
            return new CityCoreOpenResponsePacket(pos, false, CityCoreOpenResponsePacket.EMPTY_CITY_ID, "", 0.0D, 0, 0, 0, 0, permissionLevel, canCreateCity, canManageCity, List.of(), List.of(), List.of(), List.of());
        }
        CityData data = city.get();
        if (level != null) {
            PlacedBuildingService.ensureCityPoisRegistered(level);
        }
        List<CityCoreOpenResponsePacket.FinanceEntry> financeEntries = data.financeTransactions().stream().limit(12).map(CityCoreOpenResponsePacket.FinanceEntry::from).toList();
        CityPopulationStats.Snapshot stats = level == null ? new CityPopulationStats.Snapshot(0, 0) : CityPopulationStats.snapshot(level, data.cityId());
        int cityChunkCount = 0;
        if (level != null) {
            cityChunkCount = CityChunkManager.get(level).getCityChunks(data.cityId()).size();
        }
        List<CityCoreOpenResponsePacket.UpgradeTarget> upgradeTargets = CityCoreOpenResponsePacket.UpgradeTarget.from(
                CityLevelDefinitionLoader.INSTANCE.futureLevels(data.cityLevel(), CityCoreOpenResponsePacket.MAX_UPGRADE_TARGETS));
        CityCoreOpenResponsePacket.UpgradeProgress upgradeProgress = CityCoreOpenResponsePacket.UpgradeProgress.from(data.upgradeState());
        List<CityCoreOpenResponsePacket.DistrictSummary> districtSummaries = level == null ? List.of() : DistrictManager.get(level).all().stream()
                .filter(district -> district.parentCityId().equals(data.cityId()))
                .sorted(Comparator.comparing(DistrictData::name, String.CASE_INSENSITIVE_ORDER))
                .map(district -> new CityCoreOpenResponsePacket.DistrictSummary(
                        district.districtId(), district.name(), district.color(), district.chunks().size(), district.cores().size(),
                        district.members().stream().filter(member -> member.role() == DistrictRole.MAYOR)
                                .map(DistrictMemberData::playerName).findFirst().orElse(""),
                        district.members().stream()
                                .sorted(Comparator.comparingInt((DistrictMemberData member) -> member.role().power()).reversed()
                                        .thenComparing(DistrictMemberData::playerName, String.CASE_INSENSITIVE_ORDER))
                                .limit(64)
                                .map(member -> new CityCoreOpenResponsePacket.DistrictMemberView(member.playerId(), member.playerName(), member.role().power()))
                                .toList()))
                .toList();
        List<CityCoreOpenResponsePacket.CityMemberRef> cityMembers = data.members().stream()
                .sorted(Comparator.comparing(CityMemberData::playerName, String.CASE_INSENSITIVE_ORDER))
                .limit(256)
                .map(member -> new CityCoreOpenResponsePacket.CityMemberRef(member.playerId(), member.playerName()))
                .toList();
        return new CityCoreOpenResponsePacket(pos, true, data.cityId(), data.cityName(), data.funds(), data.cityLevel(), data.members().size(), stats.population(), stats.housingCapacity(), cityChunkCount, 0, permissionLevel, canCreateCity, canManageCity, financeEntries, List.of(), List.of(), upgradeTargets, upgradeProgress, districtSummaries, cityMembers);
    }

    public static CityCoreOpenResponsePacket buildCreatedCityResponse(ServerLevel level, BlockPos pos, CityData city, UUID viewerId) {
        if (city == null || viewerId == null) {
            return CityCoreOpenResponsePacket.from(pos, Optional.empty(), CityPermissionLevel.CITIZEN, false, false);
        }
        CityPermissionLevel permissionLevel = CityService.getPlayerPermission(city, viewerId);
        return CityCoreOpenResponsePacket.from(level, pos, Optional.of(city), permissionLevel, false, true);
    }

    public static CityCoreMembersResponsePacket buildMembersResponse(ServerLevel level, BlockPos pos, UUID viewerId) {
        return CityService.findCityByCorePosForPlayer(level, pos, viewerId)
                .map(city -> buildMembersResponse(level, pos, city, viewerId))
                .orElse(null);
    }

    public static CityCoreMembersResponsePacket buildMembersResponse(BlockPos pos, CityData city, UUID viewerId) {
        return buildMembersResponse(null, pos, city, viewerId);
    }

    public static CityCoreMembersResponsePacket buildMembersResponse(ServerLevel level, BlockPos pos, CityData city, UUID viewerId) {
        List<CityCoreMembersResponsePacket.MemberEntry> entries = city.members().stream()
                .sorted(Comparator.comparing((CityMemberData member) -> member.permissionLevel().ordinal()).reversed().thenComparing(CityMemberData::playerName))
                .map(member -> new CityCoreMembersResponsePacket.MemberEntry(member.playerId(), member.playerName(), member.permissionLevel()))
                .toList();
        CityPermissionLevel viewerPermission = CityService.getPlayerPermission(city, viewerId);
        List<CityCoreMembersResponsePacket.CandidateEntry> candidates = buildOnlineCandidates(level, city, viewerId);
        return new CityCoreMembersResponsePacket(pos, city.cityId(), city.cityName(), city.funds(), city.cityLevel(), entries, candidates, viewerPermission, CityService.canManageCity(city, viewerId));
    }

    private static List<CityCoreMembersResponsePacket.CandidateEntry> buildOnlineCandidates(ServerLevel level, CityData city, UUID viewerId) {
        if (level == null || level.getServer() == null) {
            return List.of();
        }
        Set<UUID> memberIds = new HashSet<>();
        city.members().forEach(m -> memberIds.add(m.playerId()));
        return level.getServer().getPlayerList().getPlayers().stream()
                .filter(p -> !memberIds.contains(p.getUUID()))
                .map(p -> new CityCoreMembersResponsePacket.CandidateEntry(p.getUUID(), p.getName().getString()))
                .toList();
    }

    public static CityCoreMapResponsePacket buildMapResponse(ServerLevel level, BlockPos pos, UUID viewerId) {
        Optional<CityData> cityOptional = CityService.findCityByCorePosForPlayer(level, pos, viewerId);
        if (cityOptional.isEmpty()) {
            return null;
        }
        CityData city = cityOptional.get();
        CityChunkManager chunkManager = CityChunkManager.get(level);
        Set<Long> chunks = chunkManager.getCityChunks(city.cityId());
        List<CityCoreMapResponsePacket.ChunkEntry> entries = new ArrayList<>(chunks.size());
        for (long chunkLong : chunks) {
            ChunkPos chunkPos = new ChunkPos(chunkLong);
            entries.add(new CityCoreMapResponsePacket.ChunkEntry(chunkPos.x, chunkPos.z));
        }
        ChunkPos centerChunk = new ChunkPos(pos);
        CityPermissionLevel permissionLevel = CityService.getPlayerPermission(city, viewerId);
        List<CityCoreMapResponsePacket.DistrictEntry> districts = DistrictManager.get(level).all().stream()
                .filter(district -> district.parentCityId().equals(city.cityId()))
                .map(district -> new CityCoreMapResponsePacket.DistrictEntry(district.districtId(), district.name(), district.color(), district.chunks().stream().map(value -> {
                    ChunkPos chunk = new ChunkPos(value);
                    return new CityCoreMapResponsePacket.ChunkEntry(chunk.x, chunk.z);
                }).toList()))
                .toList();
        return new CityCoreMapResponsePacket(pos, city.cityId(), city.cityName(), city.funds(), city.cityLevel(), city.members().size(), permissionLevel, CityService.canManageCity(city, viewerId), centerChunk.x, centerChunk.z, entries, districts);
    }
}
