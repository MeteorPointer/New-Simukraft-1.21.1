package common.cn.kafei.simukraft.city;

import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.building.PlacedBuildingService;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenManager;
import common.cn.kafei.simukraft.citizen.CitizenService;
import common.cn.kafei.simukraft.city.poi.CityPoiData;
import common.cn.kafei.simukraft.city.poi.CityPoiManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.Objects;
import java.util.UUID;

/** Keeps building and citizen district ids aligned with the chunk that currently contains them. */
public final class DistrictOwnershipSync {
    private DistrictOwnershipSync() {
    }

    public static UUID districtAt(ServerLevel level, BlockPos pos, UUID cityId) {
        if (level == null || pos == null) {
            return null;
        }
        return DistrictManager.get(level).byChunk(new ChunkPos(pos))
                .filter(district -> cityId == null || cityId.equals(district.parentCityId()))
                .map(DistrictData::districtId)
                .orElse(null);
    }

    public static void applyCitizen(ServerLevel level, CitizenData citizen) {
        if (level == null || citizen == null) {
            return;
        }
        UUID next = districtForCitizen(level, citizen);
        if (!Objects.equals(next, citizen.districtId())) {
            citizen.setDistrictId(next);
        }
    }

    public static void retagCity(ServerLevel level, UUID cityId) {
        if (level == null || cityId == null) {
            return;
        }
        for (PlacedBuildingRecord record : PlacedBuildingService.getBuildings(level)) {
            if (!cityId.equals(record.cityId())) {
                continue;
            }
            UUID next = districtAt(level, record.worldOrigin(), cityId);
            if (!Objects.equals(next, record.districtId())) {
                PlacedBuildingService.register(level, record.withDistrictId(next));
            }
        }
        CitizenManager citizens = CitizenManager.get(level);
        for (CitizenData citizen : citizens.allCitizens()) {
            if (citizen.dead() || !cityId.equals(citizen.cityId())) {
                continue;
            }
            UUID previous = citizen.districtId();
            applyCitizen(level, citizen);
            if (!Objects.equals(previous, citizen.districtId())) {
                CitizenService.save(level, citizen.uuid());
            }
        }
    }

    private static UUID districtForCitizen(ServerLevel level, CitizenData citizen) {
        if (citizen.workplacePos() != null) {
            return districtAt(level, citizen.workplacePos(), citizen.cityId());
        }
        if (citizen.homeId() == null) {
            return null;
        }
        CityPoiData home = CityPoiManager.get(level).getPoi(citizen.homeId());
        return home == null ? null : districtAt(level, home.pos(), citizen.cityId());
    }
}
