package common.cn.kafei.simukraft.city;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DistrictManagerTest {
    @Test
    void districtNameAlwaysEndsWithTheDistrictSuffix() {
        assertEquals("East\u533a", DistrictManager.normalizeDistrictName("East"));
        assertEquals("East\u533a", DistrictManager.normalizeDistrictName("East\u533a"));
        assertEquals("\u533a", DistrictManager.normalizeDistrictName(null));
        assertEquals("\u533a", DistrictManager.normalizeDistrictName("  "));
    }

    @Test
    void setMayorDemotesThePreviousMayor() {
        DistrictManager manager = new DistrictManager();
        UUID cityId = UUID.randomUUID();
        UUID firstMayor = UUID.randomUUID();
        UUID nextMayor = UUID.randomUUID();
        DistrictData district = manager.create(cityId, "East", firstMayor, "Mayor", Set.of(1L));
        assertNotNull(district);

        assertTrue(manager.setMayor(district.districtId(), nextMayor, "Next"));
        assertEquals(DistrictRole.MAYOR, district.member(nextMayor).role());
        assertEquals(DistrictRole.RESIDENT, district.member(firstMayor).role());
        assertFalse(manager.grantRole(district.districtId(), firstMayor, "Mayor", DistrictRole.MAYOR));
        assertEquals(DistrictRole.RESIDENT, district.member(firstMayor).role());
    }

    @Test
    void mayorCannotBeDemotedByRoleChanges() {
        DistrictManager manager = new DistrictManager();
        UUID cityId = UUID.randomUUID();
        UUID mayorId = UUID.randomUUID();
        UUID officialId = UUID.randomUUID();
        DistrictData district = manager.create(cityId, "East", mayorId, "Mayor", Set.of(1L));

        assertNotNull(district);
        assertEquals("East\u533a", district.name());
        assertFalse(manager.setMemberRole(district.districtId(), mayorId, DistrictRole.OFFICIAL));
        assertFalse(manager.grantRole(district.districtId(), mayorId, "Mayor", DistrictRole.OFFICIAL));
        assertEquals(DistrictRole.MAYOR, district.member(mayorId).role());
        assertTrue(manager.grantRole(district.districtId(), officialId, "Official", DistrictRole.OFFICIAL));
        assertEquals(DistrictRole.OFFICIAL, district.member(officialId).role());
        assertTrue(manager.setMemberRole(district.districtId(), officialId, DistrictRole.RESIDENT));
        assertEquals(DistrictRole.RESIDENT, district.member(officialId).role());
    }

    @Test
    void movingChunkBackToCityReleasesOnlyThatDistrict() {
        DistrictManager manager = new DistrictManager();
        UUID cityId = UUID.randomUUID();
        DistrictData first = manager.create(cityId, "North", UUID.randomUUID(), "Mayor", Set.of(4L, 5L));
        DistrictData second = manager.create(cityId, "South", UUID.randomUUID(), "Mayor", Set.of(6L));

        assertTrue(manager.moveToCity(cityId, Set.of(4L)));
        assertFalse(first.chunks().contains(4L));
        assertTrue(first.chunks().contains(5L));
        assertTrue(manager.byChunk(4L).isEmpty());
        assertEquals(second.districtId(), manager.byChunk(6L).orElseThrow().districtId());
    }

    @Test
    void removingCityDropsItsDistrictsAndCores() {
        DistrictManager manager = new DistrictManager();
        UUID cityId = UUID.randomUUID();
        UUID otherCityId = UUID.randomUUID();
        DistrictData owned = manager.create(cityId, "West", UUID.randomUUID(), "Mayor", Set.of(8L));
        DistrictData other = manager.create(otherCityId, "Keep", UUID.randomUUID(), "Mayor", Set.of(9L));
        assertTrue(manager.bindCore(owned.districtId(), new net.minecraft.world.level.ChunkPos(8L), BlockPos.ZERO.asLong()));

        var cores = manager.removeCity(cityId);

        assertEquals(1, cores.size());
        assertTrue(manager.get(owned.districtId()).isEmpty());
        assertTrue(manager.byChunk(8L).isEmpty());
        assertTrue(manager.get(other.districtId()).isPresent());
        assertEquals(other.districtId(), manager.byChunk(9L).orElseThrow().districtId());
    }
}
