package common.cn.kafei.simukraft.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class DistrictSqliteRepositoryTest {
    private static final String DIMENSION = "minecraft:overworld";

    @TempDir
    Path tempDir;

    @Test
    void saveAllSkipsOrphansSoAValidDistrictStillWrites() throws Exception {
        UUID cityId = UUID.randomUUID();
        UUID keptDistrictId = UUID.randomUUID();
        UUID orphanDistrictId = UUID.randomUUID();
        UUID mayorId = UUID.randomUUID();

        try (SimuSqliteDatabase database = openDatabase(tempDir.resolve("districts.sqlite"))) {
            CitySqliteRepository cities = new CitySqliteRepository(database);
            DistrictSqliteRepository districts = new DistrictSqliteRepository(database);
            try (Connection connection = database.borrowConnection()) {
                cities.saveAll(connection, citiesTag(cityTag(cityId, "Kept")), DIMENSION);
                districts.saveAll(connection, districtsTag(
                        districtTag(keptDistrictId, cityId, "East\u533a", 1L, BlockPos.ZERO.asLong(), mayorId),
                        districtTag(orphanDistrictId, UUID.randomUUID(), "Ghost\u533a", 2L, 0L, UUID.randomUUID())
                ), DIMENSION);
            }

            CompoundTag loaded = districts.loadAll(DIMENSION);
            assertNotNull(loaded);
            ListTag list = loaded.getList("Districts", CompoundTag.TAG_COMPOUND);
            assertEquals(1, list.size(), "orphan parent_city_id must not fail the whole district batch");
            CompoundTag kept = list.getCompound(0);
            assertEquals(keptDistrictId, kept.getUUID("DistrictId"));
            assertEquals(cityId, kept.getUUID("ParentCityId"));
            assertEquals("East\u533a", kept.getString("Name"));
            assertEquals(1, kept.getList("Chunks", LongTag.TAG_LONG).size());
            assertEquals(1L, ((LongTag) kept.getList("Chunks", LongTag.TAG_LONG).get(0)).getAsLong());
            assertEquals(1, kept.getList("Cores", LongTag.TAG_LONG).size());
            assertEquals(1, kept.getList("Members", CompoundTag.TAG_COMPOUND).size());
            assertEquals(mayorId, kept.getList("Members", CompoundTag.TAG_COMPOUND).getCompound(0).getUUID("PlayerId"));
        }
    }

    @Test
    void saveAllWithoutAParentCityWritesNothing() throws Exception {
        try (SimuSqliteDatabase database = openDatabase(tempDir.resolve("districts-orphan-only.sqlite"))) {
            DistrictSqliteRepository districts = new DistrictSqliteRepository(database);
            try (Connection connection = database.borrowConnection()) {
                districts.saveAll(connection, districtsTag(
                        districtTag(UUID.randomUUID(), UUID.randomUUID(), "Lost\u533a", 4L, 0L, UUID.randomUUID())
                ), DIMENSION);
            }
            assertNull(districts.loadAll(DIMENSION));
        }
    }

    private static CompoundTag districtsTag(CompoundTag... districts) {
        CompoundTag root = new CompoundTag();
        ListTag list = new ListTag();
        for (CompoundTag district : districts) {
            list.add(district);
        }
        root.put("Districts", list);
        return root;
    }

    private static CompoundTag districtTag(UUID districtId, UUID parentCityId, String name, long chunk, long core, UUID mayorId) {
        CompoundTag district = new CompoundTag();
        district.putUUID("DistrictId", districtId);
        district.putUUID("ParentCityId", parentCityId);
        district.putString("Name", name);
        district.putInt("Color", 0x336699);
        ListTag chunks = new ListTag();
        chunks.add(LongTag.valueOf(chunk));
        district.put("Chunks", chunks);
        ListTag cores = new ListTag();
        cores.add(LongTag.valueOf(core));
        district.put("Cores", cores);
        ListTag members = new ListTag();
        CompoundTag member = new CompoundTag();
        member.putUUID("PlayerId", mayorId);
        member.putString("PlayerName", "Mayor");
        member.putInt("Role", 2);
        members.add(member);
        district.put("Members", members);
        return district;
    }

    private static CompoundTag citiesTag(CompoundTag city) {
        CompoundTag root = new CompoundTag();
        ListTag list = new ListTag();
        list.add(city);
        root.put("Cities", list);
        return root;
    }

    private static CompoundTag cityTag(UUID cityId, String name) {
        CompoundTag city = new CompoundTag();
        city.putUUID("CityId", cityId);
        city.putString("CityName", name);
        city.putString("DimensionId", DIMENSION);
        city.putInt("CoreX", 1);
        city.putInt("CoreY", 64);
        city.putInt("CoreZ", 2);
        city.putDouble("Funds", 100.0D);
        city.putInt("CityLevel", 1);
        city.put("Members", new ListTag());
        city.put("FinanceTransactions", new ListTag());
        return city;
    }

    private static SimuSqliteDatabase openDatabase(Path databasePath) throws Exception {
        Constructor<SimuSqliteDatabase> constructor = SimuSqliteDatabase.class.getDeclaredConstructor(Path.class);
        constructor.setAccessible(true);
        return constructor.newInstance(databasePath);
    }
}
