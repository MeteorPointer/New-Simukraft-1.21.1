package client.cn.kafei.simukraft.client.selection;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import common.cn.kafei.simukraft.planner.PlanOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;

@OnlyIn(Dist.CLIENT)
public final class TwoPointSelectionManager {
    private static volatile SelectionState state = SelectionState.inactive();

    private TwoPointSelectionManager() {
    }

    public static void start(SelectionMode mode, BlockPos ownerPos, @Nullable PlanOperation operation) {
        state = new SelectionState(true, mode, ownerPos != null ? ownerPos.immutable() : BlockPos.ZERO, operation, null, null);
    }

    public static void clear() {
        state = SelectionState.inactive();
    }

    public static SelectionState state() {
        return state;
    }

    public static boolean isActive() {
        return state.active();
    }

    public static void setPoint1(BlockPos pos) {
        if (pos == null || !state.active()) {
            return;
        }
        state = state.withPoint1(normalizePoint(pos));
    }

    public static void setPoint2(BlockPos pos) {
        if (pos == null || !state.active()) {
            return;
        }
        state = state.withPoint2(normalizePoint(pos));
    }

    public static boolean hasBothPoints() {
        SelectionState current = state;
        return current.point1() != null && current.point2() != null;
    }

    @Nullable
    public static AABB selectedAabb() {
        SelectionState current = state;
        if (current.point1() == null || current.point2() == null) {
            return null;
        }
        BlockPos min = min(current.point1(), current.point2());
        BlockPos max = max(current.point1(), current.point2());
        return new AABB(min.getX(), min.getY(), min.getZ(), max.getX() + 1, max.getY() + 1, max.getZ() + 1);
    }

    public static BlockPos min(BlockPos first, BlockPos second) {
        return new BlockPos(
                Math.min(first.getX(), second.getX()),
                Math.min(first.getY(), second.getY()),
                Math.min(first.getZ(), second.getZ()));
    }

    public static BlockPos max(BlockPos first, BlockPos second) {
        return new BlockPos(
                Math.max(first.getX(), second.getX()),
                Math.max(first.getY(), second.getY()),
                Math.max(first.getZ(), second.getZ()));
    }

    private static BlockPos normalizePoint(BlockPos pos) {
        SelectionState current = state;
        if (current.mode() == SelectionMode.FARMLAND) {
            return new BlockPos(pos.getX(), current.ownerPos().getY(), pos.getZ());
        }
        return pos.immutable();
    }

    public enum SelectionMode {
        PLANNING,
        FARMLAND,
        LOGISTICS
    }

    public record SelectionState(boolean active,
                                 SelectionMode mode,
                                 BlockPos ownerPos,
                                 @Nullable PlanOperation operation,
                                 @Nullable BlockPos point1,
                                 @Nullable BlockPos point2) {
        private static SelectionState inactive() {
            return new SelectionState(false, SelectionMode.PLANNING, BlockPos.ZERO, null, null, null);
        }

        private SelectionState withPoint1(BlockPos pos) {
            return new SelectionState(active, mode, ownerPos, operation, pos.immutable(), point2);
        }

        private SelectionState withPoint2(BlockPos pos) {
            return new SelectionState(active, mode, ownerPos, operation, point1, pos.immutable());
        }
    }
}
