package common.cn.nsimu.simukraft.logistics;

import common.cn.nsimu.simukraft.registry.ModItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogisticsManifestFilterServiceTest {
    /**
     * remainingFiltersIgnoresSatisfiedEntries: 已备齐的材料不会进入输送过滤。
     */
    @Test
    void remainingFiltersIgnoresSatisfiedEntries() {
        ItemStack stack = manifestWith(
                material("minecraft:iron_ingot", 10, 10),
                material("minecraft:oak_log", 8, 2));

        List<LogisticsItemFilter> filters = LogisticsManifestFilterService.remainingFilters(stack);

        assertEquals(1, filters.size());
        assertEquals("minecraft:oak_log", filters.getFirst().itemId());
    }

    /**
     * remainingFiltersEmptyWhenAllSatisfied: 材料全部备齐后不再输送。
     */
    @Test
    void remainingFiltersEmptyWhenAllSatisfied() {
        ItemStack stack = manifestWith(material("minecraft:iron_ingot", 4, 4));
        assertTrue(LogisticsManifestFilterService.remainingFilters(stack).isEmpty());
    }

    /**
     * manifestModeForcesWarehouseToClient: 清单过滤只能从仓库发往客户端。
     */
    @Test
    void manifestModeForcesWarehouseToClient() {
        LogisticsChannelData channel = new LogisticsChannelData(
                null, null, null,
                LogisticsDirection.CLIENT_TO_WAREHOUSE,
                "test",
                true,
                List.of(),
                0L,
                0,
                0,
                LogisticsChannelFilterMode.MANIFEST);
        assertEquals(LogisticsDirection.WAREHOUSE_TO_CLIENT, channel.direction());
        assertEquals(LogisticsChannelFilterMode.MANIFEST, channel.filterMode());
    }

    private static ItemStack manifestWith(CompoundTag... materials) {
        ItemStack stack = new ItemStack(ModItems.MANIFEST.get());
        CompoundTag tag = new CompoundTag();
        ListTag materialList = new ListTag();
        ListTag checkedList = new ListTag();
        for (CompoundTag material : materials) {
            materialList.add(material);
            checkedList.add(StringTag.valueOf(Boolean.toString(
                    material.getInt("Available") >= material.getInt("Count"))));
        }
        tag.put("Materials", materialList);
        tag.put("Checked", checkedList);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    private static CompoundTag material(String itemId, int count, int available) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Item", itemId);
        tag.putInt("Count", count);
        tag.putInt("Available", available);
        return tag;
    }
}
