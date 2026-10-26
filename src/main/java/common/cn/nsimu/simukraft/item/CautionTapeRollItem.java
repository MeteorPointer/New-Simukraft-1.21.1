package common.cn.nsimu.simukraft.item;

import common.cn.nsimu.simukraft.block.CautionTapeBlock;
import common.cn.nsimu.simukraft.block.CautionTapePlacers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * CautionTapeRollItem: 一卷可铺 50 格。右键定起点，再瞄同一高度的直线终点。
 */
public final class CautionTapeRollItem extends ItemNameBlockItem {
    public static final int USES_PER_ROLL = 50;

    private final Block tapeBlock;

    public CautionTapeRollItem(Block tapeBlock, Properties properties) {
        super(tapeBlock, properties);
        this.tapeBlock = tapeBlock;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        if (player != null && player.isShiftKeyDown()) {
            clearStart(stack);
            return InteractionResult.SUCCESS;
        }
        BlockPos point = context.getClickedPos().relative(context.getClickedFace());
        BlockPos start = readStart(stack);
        if (start == null) {
            writeStart(stack, point);
            if (!level.isClientSide() && player != null) {
                player.displayClientMessage(Component.translatable("message.simukraft.caution_tape.start",
                        point.getX(), point.getY(), point.getZ()), true);
            }
            return InteractionResult.SUCCESS;
        }
        if (!isStraightSameHeight(start, point)) {
            if (!level.isClientSide() && player != null) {
                player.displayClientMessage(Component.translatable("message.simukraft.caution_tape.not_straight"), true);
            }
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide()) {
            int placed = placeLine(level, start, point, stack, player);
            clearStart(stack);
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.simukraft.caution_tape.placed", placed), true);
            }
        }
        return InteractionResult.SUCCESS;
    }

    public static boolean isStraightSameHeight(BlockPos start, BlockPos end) {
        if (start == null || end == null || start.equals(end)) {
            return false;
        }
        if (start.getY() != end.getY()) {
            return false;
        }
        boolean sameX = start.getX() == end.getX();
        boolean sameZ = start.getZ() == end.getZ();
        return sameX ^ sameZ;
    }

    public static List<BlockPos> line(BlockPos start, BlockPos end) {
        List<BlockPos> positions = new ArrayList<>();
        if (!isStraightSameHeight(start, end)) {
            return positions;
        }
        int stepX = Integer.signum(end.getX() - start.getX());
        int stepZ = Integer.signum(end.getZ() - start.getZ());
        BlockPos cursor = start;
        positions.add(cursor);
        while (!cursor.equals(end)) {
            cursor = cursor.offset(stepX, 0, stepZ);
            positions.add(cursor);
        }
        return positions;
    }

    @Nullable
    public static BlockPos readStart(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.getBoolean("HasStart")) {
            return null;
        }
        return new BlockPos(tag.getInt("StartX"), tag.getInt("StartY"), tag.getInt("StartZ"));
    }

    public static int remainingUses(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        return Math.max(0, stack.getMaxDamage() - stack.getDamageValue());
    }

    public static boolean consumeUse(ItemStack stack) {
        if (remainingUses(stack) <= 0) {
            return false;
        }
        int next = stack.getDamageValue() + 1;
        if (next >= stack.getMaxDamage()) {
            stack.shrink(1);
        } else {
            stack.setDamageValue(next);
        }
        return true;
    }

    private int placeLine(Level level, BlockPos start, BlockPos end, ItemStack stack, Player player) {
        int placed = 0;
        boolean bl = player != null && player.hasInfiniteMaterials();
        List<BlockPos> placedPositions = new ArrayList<>();
        for (BlockPos pos : line(start, end)) {
            if (remainingUses(stack) <= 0 && !bl) {
                break;
            }
            BlockState existing = level.getBlockState(pos);
            if (existing.is(tapeBlock)) {
                continue;
            }
            if (!existing.isAir() && !existing.canBeReplaced()) {
                continue;
            }
            BlockState tape = CautionTapeBlock.applyConnections(tapeBlock.defaultBlockState(), level, pos);
            if (!level.setBlock(pos, tape, Block.UPDATE_ALL)) {
                continue;
            }
            placedPositions.add(pos.immutable());
            if (level instanceof ServerLevel serverLevel && player != null) {
                CautionTapePlacers.get(serverLevel).set(pos, player.getUUID());
            }
            if (!bl) consumeUse(stack);
            placed++;
        }
        for (BlockPos pos : placedPositions) {
            CautionTapeBlock.refreshSelf(level, pos);
        }
        return placed;
    }

    private static void writeStart(ItemStack stack, BlockPos point) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putBoolean("HasStart", true);
        tag.putInt("StartX", point.getX());
        tag.putInt("StartY", point.getY());
        tag.putInt("StartZ", point.getZ());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private static void clearStart(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.remove("HasStart");
        tag.remove("StartX");
        tag.remove("StartY");
        tag.remove("StartZ");
        if (tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }
}
