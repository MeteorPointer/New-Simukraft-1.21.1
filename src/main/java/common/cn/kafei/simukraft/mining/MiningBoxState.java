package common.cn.kafei.simukraft.mining;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;

/** MiningBoxState: 玩家两点框选的范围，以及从最高 Y 往下挖的进度。 */
public record MiningBoxState(BlockPos pos,
                             @Nullable BlockPos point1,
                             @Nullable BlockPos point2,
                             int nextDigY,
                             MiningBoxMode mode,
                             boolean glassCoverPlaced,
                             boolean running) {
    public MiningBoxState {
        pos = pos == null ? BlockPos.ZERO : pos.immutable();
        point1 = point1 == null ? null : point1.immutable();
        point2 = point2 == null ? null : point2.immutable();
        mode = mode == null ? MiningBoxMode.OPEN : mode;
    }

    public static MiningBoxState create(BlockPos pos) {
        return new MiningBoxState(pos, null, null, pos == null ? 0 : pos.getY(), MiningBoxMode.OPEN, false, false);
    }

    public boolean hasArea() {
        return point1 != null && point2 != null && !point1.equals(point2);
    }

    /** topY: 两点里更高的那一层，挖掘从这里开始。 */
    public int topY() {
        if (!hasArea()) {
            return pos.getY();
        }
        return Math.max(point1.getY(), point2.getY());
    }

    public int currentDigY() {
        return Math.min(nextDigY, topY());
    }

    public int width() {
        if (!hasArea()) {
            return 0;
        }
        return Math.abs(point1.getX() - point2.getX()) + 1;
    }

    public int depth() {
        if (!hasArea()) {
            return 0;
        }
        return Math.abs(point1.getZ() - point2.getZ()) + 1;
    }

    public BlockPos minCorner() {
        if (!hasArea()) {
            return pos;
        }
        return new BlockPos(
                Math.min(point1.getX(), point2.getX()),
                topY(),
                Math.min(point1.getZ(), point2.getZ()));
    }

    public BlockPos maxCorner() {
        if (!hasArea()) {
            return pos;
        }
        return new BlockPos(
                Math.max(point1.getX(), point2.getX()),
                topY(),
                Math.max(point1.getZ(), point2.getZ()));
    }

    public MiningBoxState withArea(BlockPos first, BlockPos second) {
        MiningBoxState updated = new MiningBoxState(pos, first, second, 0, mode, false, running);
        return new MiningBoxState(pos, updated.point1(), updated.point2(), updated.topY(), mode, false, running);
    }

    public MiningBoxState cycleMode() {
        return new MiningBoxState(pos, point1, point2, topY(), mode.next(), false, running);
    }

    public MiningBoxState toggleRunning() {
        return new MiningBoxState(pos, point1, point2, nextDigY, mode, glassCoverPlaced, !running);
    }

    public MiningBoxState withDigY(int digY) {
        return new MiningBoxState(pos, point1, point2, digY, mode, glassCoverPlaced, running);
    }

    public MiningBoxState withGlassCoverPlaced() {
        return new MiningBoxState(pos, point1, point2, topY() - 1, mode, true, running);
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("Pos", pos.asLong());
        tag.putBoolean("HasPoint1", point1 != null);
        tag.putBoolean("HasPoint2", point2 != null);
        if (point1 != null) {
            tag.putLong("Point1", point1.asLong());
        }
        if (point2 != null) {
            tag.putLong("Point2", point2.asLong());
        }
        tag.putInt("NextDigY", nextDigY);
        tag.putString("Mode", mode.name());
        tag.putBoolean("GlassCoverPlaced", glassCoverPlaced);
        tag.putBoolean("Running", running);
        return tag;
    }

    public static MiningBoxState fromTag(CompoundTag tag) {
        BlockPos pos = BlockPos.of(tag.getLong("Pos"));
        BlockPos point1 = tag.getBoolean("HasPoint1") ? BlockPos.of(tag.getLong("Point1")) : null;
        BlockPos point2 = tag.getBoolean("HasPoint2") ? BlockPos.of(tag.getLong("Point2")) : null;
        int nextDigY = tag.contains("NextDigY") ? tag.getInt("NextDigY") : pos.getY();
        return new MiningBoxState(pos, point1, point2, nextDigY, MiningBoxMode.fromName(tag.getString("Mode")),
                tag.getBoolean("GlassCoverPlaced"), tag.getBoolean("Running"));
    }
}
