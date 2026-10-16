package client.cn.nsimu.simukraft.client.caution;

import common.cn.nsimu.simukraft.item.CautionTapeRollItem;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.joml.Vector3f;

/** CautionTapePreview: 起点已定后，瞄向合法终点时拉出警戒线粒子。 */
public final class CautionTapePreview {
    private static final DustParticleOptions DUST = new DustParticleOptions(new Vector3f(0.95F, 0.75F, 0.05F), 1.0F);

    private CautionTapePreview() {
    }

    public static void onClientTick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null || minecraft.player == null || minecraft.isPaused()) {
            return;
        }
        Player player = minecraft.player;
        ItemStack stack = heldTape(player);
        BlockPos start = stack == null ? null : CautionTapeRollItem.readStart(stack);
        if (start == null) {
            return;
        }
        spawn(minecraft, start.getX() + 0.5D, start.getY() + 0.7D, start.getZ() + 0.5D);
        HitResult hit = player.pick(8.0D, 0.0F, false);
        if (!(hit instanceof BlockHitResult blockHit) || blockHit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockPos end = blockHit.getBlockPos().relative(blockHit.getDirection());
        if (!CautionTapeRollItem.isStraightSameHeight(start, end)) {
            return;
        }
        for (BlockPos pos : CautionTapeRollItem.line(start, end)) {
            spawn(minecraft, pos.getX() + 0.5D, pos.getY() + 0.7D, pos.getZ() + 0.5D);
        }
    }

    private static ItemStack heldTape(Player player) {
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof CautionTapeRollItem) {
            return main;
        }
        ItemStack off = player.getOffhandItem();
        return off.getItem() instanceof CautionTapeRollItem ? off : null;
    }

    private static void spawn(Minecraft minecraft, double x, double y, double z) {
        minecraft.level.addParticle(DUST, x, y, z, 0.0D, 0.0D, 0.0D);
    }
}
