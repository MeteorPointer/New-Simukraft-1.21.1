package common.cn.nsimu.simukraft.industrial;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public record IndustrialInputPlan(List<IndustrialDefinition.ItemRequirement> consumptions) {
    public IndustrialInputPlan {
        consumptions = consumptions != null ? List.copyOf(consumptions) : List.of();
    }

    public boolean hasConsumptions() {
        return !consumptions.isEmpty();
    }

    public List<ItemStack> toStacks() {
        return toStacks(null);
    }

    public List<ItemStack> toStacks(HolderLookup.Provider registries) {
        List<ItemStack> stacks = new ArrayList<>();
        for (IndustrialDefinition.ItemRequirement input : consumptions) {
            appendStacks(stacks, input, registries);
        }
        return List.copyOf(stacks);
    }

    private static void appendStacks(List<ItemStack> stacks, IndustrialDefinition.ItemRequirement input, HolderLookup.Provider registries) {
        ItemStack template = input.spec().stack(1, registries);
        if (template.isEmpty()) {
            return;
        }
        int remaining = Math.max(1, input.count());
        int maxStackSize = Math.max(1, template.getMaxStackSize());
        while (remaining > 0) {
            int amount = Math.min(remaining, maxStackSize);
            stacks.add(input.spec().stack(amount, registries));
            remaining -= amount;
        }
    }
}
