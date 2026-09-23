package common.cn.kafei.simukraft.building;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildingStructureIdsTest {
    @Test
    void buildingFileBecomesStructureId() {
        ResourceLocation id = BuildingStructureIds.location("residential", "lumberjacks_house.sk");

        assertEquals(ResourceLocation.fromNamespaceAndPath("simukraft", "residential/lumberjacks_house"), id);
        assertTrue(BuildingStructureIds.matches("residential", "lumberjacks_house.sk", "lumberjacks_house.nbt", id));
    }

    @Test
    void categoryAliasesAndUnsafeNamesAreNormalized() {
        assertEquals("commercial/my_house", BuildingStructureIds.path("commerce", "My House.sk"));
        assertEquals("public/clinic", BuildingStructureIds.path("medical", "clinic.json"));
        assertNull(BuildingStructureIds.path("residential", "中文.sk"));
    }

    @Test
    void differentCategoryOrNamespaceDoesNotMatch() {
        ResourceLocation house = BuildingStructureIds.location("residential", "house.sk");

        assertFalse(BuildingStructureIds.matches("commercial", "house.sk", "house.nbt", house));
        assertFalse(BuildingStructureIds.matches("residential", "house.sk", "house.nbt",
                ResourceLocation.fromNamespaceAndPath("minecraft", "residential/house")));
    }

    @Test
    void structureFileStemMatchesWhenBlueprintNameDiffers() {
        ResourceLocation blueprint = BuildingStructureIds.location("industry", "mill_alt.nbt");

        assertTrue(BuildingStructureIds.matches("industry", "mill.sk", "mill_alt.nbt", blueprint));
    }
}
