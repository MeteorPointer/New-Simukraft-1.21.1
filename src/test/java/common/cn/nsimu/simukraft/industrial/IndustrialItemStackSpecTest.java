package common.cn.nsimu.simukraft.industrial;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IndustrialItemStackSpecTest {
    @Test
    void serializesAndRestoresPureItemTag() {
        IndustrialItemStackSpec spec = IndustrialItemStackSpec.of("", "minecraft:saplings", "", "", "", java.util.List.of(), java.util.List.of());

        assertEquals("#minecraft:saplings", spec.serialized());

        IndustrialItemStackSpec restored = IndustrialItemStackSpec.fromSerialized("#minecraft:saplings");
        assertEquals("", restored.itemId());
        assertEquals("minecraft:saplings", restored.itemTag());
        assertEquals("#minecraft:saplings", restored.displayItemId());
    }

    @Test
    void keepsItemAndTagAsComplexConstraint() {
        IndustrialItemStackSpec spec = IndustrialItemStackSpec.of("minecraft:oak_sapling", "minecraft:saplings", "", "", "", java.util.List.of(), java.util.List.of());

        assertEquals("{\"item\":\"minecraft:oak_sapling\",\"tag\":\"minecraft:saplings\"}", spec.serialized());
    }
}
