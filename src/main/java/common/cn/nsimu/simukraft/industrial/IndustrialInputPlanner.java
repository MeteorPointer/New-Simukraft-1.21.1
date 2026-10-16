package common.cn.nsimu.simukraft.industrial;

import common.cn.nsimu.simukraft.material.GenericContainerAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.*;

/**
 * 工业输入规划器：在消耗前先确定 AND/OR 表达式实际使用哪一组物品。
 */
public final class IndustrialInputPlanner {
    private static final int MAX_AVAILABLE_CRAFTS = 4096;

    private IndustrialInputPlanner() {
    }

    public static Optional<IndustrialInputPlan> plan(ServerLevel level,
                                                     List<BlockPos> containers,
                                                     List<IndustrialDefinition.InputRequirement> requirements,
                                                     int craftCount) {
        if (requirements == null || requirements.isEmpty()) {
            return Optional.of(new IndustrialInputPlan(List.of()));
        }
        PlanState state = new PlanState(
                countAvailable(level, containers, requirementSpecs(requirements)),
                new LinkedHashMap<>(),
                new ArrayList<>());
        PlanState planned = state;
        int safeCraftCount = Math.max(1, craftCount);
        for (int craft = 0; craft < safeCraftCount; craft++) {
            planned = planAll(requirements, planned, craft == 0);
            if (planned == null) {
                return Optional.empty();
            }
        }
        return Optional.of(new IndustrialInputPlan(planned.consumptions()));
    }

    public static int availableCraftCount(ServerLevel level,
                                          List<BlockPos> containers,
                                          List<IndustrialDefinition.InputRequirement> requirements) {
        if (requirements == null || requirements.isEmpty()) {
            return 0;
        }
        if (!IndustrialInputRequirements.hasConsumableItem(requirements)) {
            return plan(level, containers, requirements, 1).isPresent() ? 1 : 0;
        }
        int high = maxCraftCountCeiling(level, containers, requirements);
        int low = 0;
        while (low < high) {
            int middle = low + (high - low + 1) / 2;
            if (plan(level, containers, requirements, middle).isPresent()) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        return low;
    }

    private static PlanState planAll(List<IndustrialDefinition.InputRequirement> requirements,
                                     PlanState state,
                                     boolean checkNonConsumables) {
        PlanState current = state;
        for (IndustrialDefinition.InputRequirement requirement : requirements) {
            current = planRequirement(requirement, current, checkNonConsumables);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private static PlanState planRequirement(IndustrialDefinition.InputRequirement requirement,
                                             PlanState state,
                                             boolean checkNonConsumables) {
        if (requirement instanceof IndustrialDefinition.ItemRequirement item) {
            return planItem(item, state, checkNonConsumables);
        }
        if (requirement instanceof IndustrialDefinition.InputRequirementGroup group) {
            if (group.logic() == IndustrialDefinition.InputLogic.ANY) {
                for (IndustrialDefinition.InputRequirement child : group.children()) {
                    PlanState candidate = planRequirement(child, state, checkNonConsumables);
                    if (candidate != null) {
                        return candidate;
                    }
                }
                return null;
            }
            return planAll(group.children(), state, checkNonConsumables);
        }
        return null;
    }

    private static PlanState planItem(IndustrialDefinition.ItemRequirement input, PlanState state, boolean checkNonConsumables) {
        IndustrialItemStackSpec spec = input.spec();
        int required = Math.max(1, input.count());
        if (!input.consume()) {
            return checkNonConsumables ? reserveInput(state, spec, required) : state;
        }
        int available = state.available().getOrDefault(spec, 0);
        int reserved = state.reserved().getOrDefault(spec, 0);
        if (available - reserved < required) {
            return null;
        }
        PlanState next = state.copy();
        next.available().put(spec, available - required);
        next.consumptions().add(new IndustrialDefinition.ItemRequirement(spec, required, true));
        return next;
    }

    private static PlanState reserveInput(PlanState state, IndustrialItemStackSpec spec, int required) {
        int available = state.available().getOrDefault(spec, 0);
        int reserved = state.reserved().getOrDefault(spec, 0);
        if (available - reserved < required) {
            return null;
        }
        PlanState next = state.copy();
        next.reserved().put(spec, reserved + required);
        return next;
    }

    private static int maxCraftCountCeiling(ServerLevel level,
                                            List<BlockPos> containers,
                                            List<IndustrialDefinition.InputRequirement> requirements) {
        Map<IndustrialItemStackSpec, Integer> counts = countAvailable(level, containers, consumableSpecs(requirements));
        long total = 0L;
        for (int count : counts.values()) {
            total += Math.max(0, count);
            if (total >= MAX_AVAILABLE_CRAFTS) {
                return MAX_AVAILABLE_CRAFTS;
            }
        }
        return (int) Math.min(MAX_AVAILABLE_CRAFTS, total);
    }

    private static Map<IndustrialItemStackSpec, Integer> countAvailable(ServerLevel level,
                                                                        List<BlockPos> containers,
                                                                        Set<IndustrialItemStackSpec> specs) {
        Map<IndustrialItemStackSpec, Integer> counts = new LinkedHashMap<>();
        for (IndustrialItemStackSpec spec : specs) {
            counts.put(spec, countSpec(level, containers, spec));
        }
        return counts;
    }

    private static int countSpec(ServerLevel level, List<BlockPos> containers, IndustrialItemStackSpec spec) {
        if (level == null || containers == null || containers.isEmpty()) {
            return 0;
        }
        int count = 0;
        Set<BlockPos> visited = new LinkedHashSet<>();
        for (BlockPos container : containers) {
            if (!GenericContainerAccess.isContainer(level, container)) {
                continue;
            }
            BlockPos canonical = GenericContainerAccess.canonicalContainerPos(level, container);
            if (!visited.add(canonical)) {
                continue;
            }
            for (GenericContainerAccess.SlotSnapshot snapshot : GenericContainerAccess.snapshotSlots(level, canonical)) {
                if (spec.matches(snapshot.stack(), level.registryAccess())) {
                    count = safeAdd(count, snapshot.stack().getCount());
                }
            }
        }
        return count;
    }

    private static Set<IndustrialItemStackSpec> requirementSpecs(List<IndustrialDefinition.InputRequirement> requirements) {
        Set<IndustrialItemStackSpec> specs = new LinkedHashSet<>();
        for (IndustrialDefinition.ItemRequirement input : IndustrialInputRequirements.flattenItems(requirements)) {
            specs.add(input.spec());
        }
        return specs;
    }

    private static Set<IndustrialItemStackSpec> consumableSpecs(List<IndustrialDefinition.InputRequirement> requirements) {
        Set<IndustrialItemStackSpec> specs = new LinkedHashSet<>();
        for (IndustrialDefinition.ItemRequirement input : IndustrialInputRequirements.flattenItems(requirements)) {
            if (input.consume()) {
                specs.add(input.spec());
            }
        }
        return specs;
    }

    private static int safeAdd(int first, int second) {
        long result = (long) first + Math.max(0, second);
        return result > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) result;
    }

    private record PlanState(Map<IndustrialItemStackSpec, Integer> available,
                             Map<IndustrialItemStackSpec, Integer> reserved,
                             List<IndustrialDefinition.ItemRequirement> consumptions) {
        private PlanState copy() {
            return new PlanState(new LinkedHashMap<>(available), new LinkedHashMap<>(reserved), new ArrayList<>(consumptions));
        }
    }
}
