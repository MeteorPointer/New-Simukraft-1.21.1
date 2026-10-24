package client.cn.nsimu.simukraft.client.compat.jei;

import common.cn.nsimu.simukraft.building.BuildingLocale;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * IndustrialJeiRecipe: 一条可被 JEI 索引的工业配方。
 * 输入槽和输出槽决定物品的配方查询与用途查询。
 */
public record IndustrialJeiRecipe(String id,
                                  String buildingName,
                                  String buildingNameEn,
                                  String recipeName,
                                  String recipeNameEn,
                                  List<InputSlot> inputs,
                                  List<OutputSlot> outputs) {
    public IndustrialJeiRecipe {
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
        outputs = outputs == null ? List.of() : List.copyOf(outputs);
    }

    /**
     * title: 当前语言下的配方名。
     */
    public Component title() {
        return Component.literal(localized(recipeName, recipeNameEn));
    }

    /**
     * buildingLine: 当前语言下的建筑名。
     */
    public Component buildingLine() {
        String name = localized(buildingName, buildingNameEn);
        if (name.isBlank()) {
            return Component.empty();
        }
        return Component.translatable("jei.simukraft.industrial.building", name);
    }

    private static String localized(String name, String nameEn) {
        if (BuildingLocale.preferEnglish() && nameEn != null && !nameEn.isBlank()) {
            return nameEn;
        }
        return name == null ? "" : name;
    }

    /**
     * InputSlot: 一个输入格。多个物品表示任选其一，标签物品会展开成这个列表。
     */
    public record InputSlot(List<ItemStack> stacks, boolean consume, boolean alternative, String tagId) {
        public InputSlot {
            stacks = stacks == null ? List.of() : List.copyOf(stacks);
            tagId = tagId == null ? "" : tagId;
        }
    }

    /**
     * OutputSlot: 一个输出格。数量、概率和随机范围只影响提示，不改变查询索引。
     */
    public record OutputSlot(ItemStack stack, int baseAmount, int randomRange, double probability, boolean ignoreMultiplier) {
        public OutputSlot {
            stack = stack == null ? ItemStack.EMPTY : stack;
            baseAmount = Math.max(1, baseAmount);
            randomRange = Math.max(0, randomRange);
        }
    }
}
