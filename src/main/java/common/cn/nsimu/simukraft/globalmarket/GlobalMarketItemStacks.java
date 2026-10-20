package common.cn.nsimu.simukraft.globalmarket;

import common.cn.nsimu.simukraft.SimuKraft;
import common.cn.nsimu.simukraft.commercial.CommercialTradeView;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * GlobalMarketItemStacks: 上架报价保存/还原完整物品栈。
 */
public final class GlobalMarketItemStacks {
    private GlobalMarketItemStacks() {
    }

    /**
     * encode: 把物品栈写成 SNBT，空物品返回空串。
     */
    public static String encode(HolderLookup.Provider registries, ItemStack stack) {
        if (registries == null || stack == null || stack.isEmpty()) {
            return "";
        }
        try {
            Tag tag = stack.save(registries);
            return tag.toString();
        } catch (Exception exception) {
            SimuKraft.LOGGER.warn("Simukraft: Failed to encode global market item stack {}", stack, exception);
            return "";
        }
    }

    /**
     * decode: 优先还原完整物品栈，失败则退回物品 ID。
     */
    public static ItemStack decode(HolderLookup.Provider registries, CommercialTradeView.ResourceEntry resource) {
        if (resource == null || "money".equalsIgnoreCase(resource.type())) {
            return ItemStack.EMPTY;
        }
        return decode(registries, resource.stackNbt(), resource.itemId(), resource.count());
    }

    /**
     * decode: 从 SNBT 或物品 ID 还原物品栈。
     */
    public static ItemStack decode(HolderLookup.Provider registries, String stackNbt, String itemId, int count) {
        if (registries != null && stackNbt != null && !stackNbt.isBlank()) {
            try {
                CompoundTag tag = TagParser.parseTag(stackNbt);
                ItemStack stack = ItemStack.parseOptional(registries, tag);
                if (!stack.isEmpty()) {
                    return stack;
                }
            } catch (Exception exception) {
                SimuKraft.LOGGER.warn("Simukraft: Failed to decode global market item stack {}", stackNbt, exception);
            }
        }
        if (itemId == null || itemId.isBlank() || count <= 0) {
            return ItemStack.EMPTY;
        }
        try {
            return BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(itemId))
                    .map(item -> item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item, Math.max(1, count)))
                    .orElse(ItemStack.EMPTY);
        } catch (Exception exception) {
            return ItemStack.EMPTY;
        }
    }
}
