package client.cn.nsimu.simukraft.client.compat.jei;

import common.cn.nsimu.simukraft.SimuKraft;
import common.cn.nsimu.simukraft.building.BuildingCatalog;
import common.cn.nsimu.simukraft.industrial.IndustrialDefinition;
import common.cn.nsimu.simukraft.industrial.IndustrialDefinitionLoader;
import common.cn.nsimu.simukraft.industrial.IndustrialItemStackSpec;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * IndustrialJeiRecipes: 从工业建筑包收集 JEI 可查询的配方。
 */
public final class IndustrialJeiRecipes {
    private IndustrialJeiRecipes() {
    }

    /**
     * collect: 读取 industry 分类里的配方输入和输出。失败时返回空列表，避免拖垮 JEI。
     */
    public static List<IndustrialJeiRecipe> collect() {
        try {
            return collectUnsafely();
        } catch (Exception exception) {
            SimuKraft.LOGGER.warn("Simukraft: Failed to collect industrial recipes for JEI.", exception);
            return List.of();
        }
    }

    private static List<IndustrialJeiRecipe> collectUnsafely() {
        HolderLookup.Provider registries = registries();
        List<IndustrialJeiRecipe> recipes = new ArrayList<>();
        for (BuildingCatalog.BuildingDefinition building : BuildingCatalog.listBuildings("industry")) {
            IndustrialDefinition definition = IndustrialDefinitionLoader.loadForCatalog(building).definition();
            if (definition == null || definition.recipes().isEmpty()) {
                continue;
            }
            int index = 0;
            for (IndustrialDefinition.RecipeDefinition recipe : definition.recipes()) {
                IndustrialJeiRecipe view = toRecipe(building, definition, recipe, index++, registries);
                if (view != null) {
                    recipes.add(view);
                }
            }
        }
        return List.copyOf(recipes);
    }

    private static IndustrialJeiRecipe toRecipe(BuildingCatalog.BuildingDefinition building,
                                                IndustrialDefinition definition,
                                                IndustrialDefinition.RecipeDefinition recipe,
                                                int index,
                                                HolderLookup.Provider registries) {
        List<IndustrialJeiRecipe.InputSlot> inputs = new ArrayList<>();
        for (IndustrialDefinition.InputRequirement requirement : recipe.inputs()) {
            appendInput(requirement, inputs, registries);
        }
        List<IndustrialJeiRecipe.OutputSlot> outputs = new ArrayList<>();
        for (IndustrialDefinition.ProductOutput output : recipe.outputs()) {
            ItemStack stack = stackOf(output.spec(), output.baseAmount(), registries);
            if (!stack.isEmpty()) {
                outputs.add(new IndustrialJeiRecipe.OutputSlot(stack, output.baseAmount(), output.randomRange(), output.probability(), output.ignoreMultiplier()));
            }
        }
        if (inputs.isEmpty() && outputs.isEmpty()) {
            return null;
        }
        String id = building.packageKey() + "|" + building.metaFileName() + "|" + definition.id() + "|" + recipe.id() + "|" + index;
        return new IndustrialJeiRecipe(
                id,
                firstText(building.displayName(), definition.name(), building.metaFileName()),
                firstText(building.displayNameEn(), definition.nameEn()),
                firstText(recipe.name(), recipe.id()),
                recipe.nameEn(),
                inputs,
                outputs
        );
    }

    /**
     * appendInput: 与关系拆成多个格子，或关系放进同一个格子供 JEI 轮换并建立查询。
     */
    private static void appendInput(IndustrialDefinition.InputRequirement requirement,
                                    List<IndustrialJeiRecipe.InputSlot> slots,
                                    HolderLookup.Provider registries) {
        if (requirement instanceof IndustrialDefinition.ItemRequirement item) {
            List<ItemStack> stacks = stacksOf(item.spec(), item.count(), registries);
            if (!stacks.isEmpty()) {
                slots.add(new IndustrialJeiRecipe.InputSlot(stacks, item.consume(), false, tagId(item.spec())));
            }
            return;
        }
        if (!(requirement instanceof IndustrialDefinition.InputRequirementGroup group)) {
            return;
        }
        if (group.logic() == IndustrialDefinition.InputLogic.ANY) {
            List<ItemStack> alternatives = new ArrayList<>();
            boolean consume = true;
            for (IndustrialDefinition.ItemRequirement leaf : group.itemLeaves()) {
                alternatives.addAll(stacksOf(leaf.spec(), leaf.count(), registries));
                consume &= leaf.consume();
            }
            if (!alternatives.isEmpty()) {
                slots.add(new IndustrialJeiRecipe.InputSlot(alternatives, consume, true, ""));
            }
            return;
        }
        for (IndustrialDefinition.InputRequirement child : group.children()) {
            appendInput(child, slots, registries);
        }
    }

    private static List<ItemStack> stacksOf(IndustrialItemStackSpec spec, int count, HolderLookup.Provider registries) {
        if (spec == null || spec.isEmpty()) {
            return List.of();
        }
        if (spec.itemId().isBlank() && spec.itemStackText().isBlank() && !spec.itemTag().isBlank()) {
            return tagStacks(spec.itemTag(), count);
        }
        ItemStack stack = stackOf(spec, count, registries);
        return stack.isEmpty() ? List.of() : List.of(stack);
    }

    private static ItemStack stackOf(IndustrialItemStackSpec spec, int count, HolderLookup.Provider registries) {
        if (spec == null || spec.isEmpty()) {
            return ItemStack.EMPTY;
        }
        int safeCount = Math.max(1, count);
        ItemStack stack = spec.stack(safeCount, registries);
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (stack.getCount() != safeCount) {
            stack.setCount(safeCount);
        }
        return stack;
    }

    private static List<ItemStack> tagStacks(String tagId, int count) {
        try {
            TagKey<Item> tag = TagKey.create(Registries.ITEM, ResourceLocation.parse(tagId));
            int safeCount = Math.max(1, count);
            List<ItemStack> stacks = new ArrayList<>();
            BuiltInRegistries.ITEM.getTagOrEmpty(tag).forEach(holder -> stacks.add(new ItemStack(holder, safeCount)));
            return List.copyOf(stacks);
        } catch (Exception exception) {
            return List.of();
        }
    }

    private static String tagId(IndustrialItemStackSpec spec) {
        if (spec == null || spec.itemTag().isBlank() || !spec.itemId().isBlank() || !spec.itemStackText().isBlank()) {
            return "";
        }
        return spec.itemTag();
    }

    private static HolderLookup.Provider registries() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            return minecraft.level.registryAccess();
        }
        if (minecraft.getConnection() != null) {
            return minecraft.getConnection().registryAccess();
        }
        return null;
    }

    private static String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
