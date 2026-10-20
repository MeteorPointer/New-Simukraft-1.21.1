package common.cn.nsimu.simukraft.commercial;

import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import common.cn.nsimu.simukraft.network.commercial.CommercialTradeOpenResponsePacket;
import net.minecraft.client.gui.Font;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

/**
 * GlobalMarketWarehousePanel: 全球市场仓库格子绘制与点击。
 */
final class GlobalMarketWarehousePanel {
    static final int COLS = 9;
    static final int ROWS = 3;
    static final int SLOT = 18;
    static final int X = 108;
    static final int Y = 18;

    private GlobalMarketWarehousePanel() {
    }

    static int maxScroll(int itemCount) {
        int rows = Math.max(0, (itemCount + COLS - 1) / COLS - ROWS);
        return Math.max(0, rows);
    }

    static int hitIndex(float mouseX, float mouseY, int left, int top, int scroll, int itemCount) {
        int localX = (int) mouseX - (left + X);
        int localY = (int) mouseY - (top + Y);
        if (localX < 0 || localY < 0 || localX >= COLS * SLOT || localY >= ROWS * SLOT) {
            return -1;
        }
        int col = localX / SLOT;
        int row = localY / SLOT;
        int index = (scroll + row) * COLS + col;
        return index >= 0 && index < itemCount ? index : -1;
    }

    @OnlyIn(Dist.CLIENT)
    static void render(GUIContext guiContext, Font font, int left, int top, List<CommercialTradeOpenResponsePacket.WarehouseSlot> items, int scroll) {
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int x = left + X + col * SLOT;
                int y = top + Y + row * SLOT;
                guiContext.graphics.fill(x, y, x + SLOT, y + SLOT, 0xFF8B8B8B);
                guiContext.graphics.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, 0xFF373737);
                int index = (scroll + row) * COLS + col;
                if (items == null || index >= items.size()) {
                    continue;
                }
                CommercialTradeOpenResponsePacket.WarehouseSlot slot = items.get(index);
                ItemStack stack = slot.stack();
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                ItemStack shown = stack.copy();
                shown.setCount(1);
                guiContext.graphics.renderFakeItem(shown, x + 1, y + 1);
                String label = countLabel(slot.count());
                guiContext.pose.pushPose();
                guiContext.pose.translate(0.0F, 0.0F, 200.0F);
                guiContext.graphics.drawString(font, label, x + SLOT - 1 - font.width(label), y + SLOT - 9, 0xFFFFFF, true);
                guiContext.pose.popPose();
            }
        }
    }

    private static String countLabel(int count) {
        if (count >= 1_000_000) {
            return (count / 1_000_000) + "M";
        }
        if (count >= 10_000) {
            return (count / 1000) + "k";
        }
        return String.valueOf(Math.max(0, count));
    }
}
