package common.cn.nsimu.simukraft.commercial;

import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import common.cn.nsimu.simukraft.registry.ModItems;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import javax.annotation.Nullable;

/**
 * GlobalMarketModeTabStrip: 全球市场左上角原版背包式 Tab，购买/上架可衔接。
 */
final class GlobalMarketModeTabStrip {
    static final int TAB_WIDTH = 26;
    static final int TAB_HEIGHT = 32;
    static final int TAB_SPACING = 27;
    static final int TAB_TOP_OFFSET = 28;
    static final int HIT_X = 0;
    static final int HIT_Y = 0;
    private static final ResourceLocation[] UNSELECTED_TOP_TABS = {
            ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_top_unselected_1"),
            ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_top_unselected_2"),
            ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_top_unselected_3")
    };
    private static final ResourceLocation[] SELECTED_TOP_TABS = {
            ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_top_selected_1"),
            ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_top_selected_2"),
            ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_top_selected_3")
    };

    enum Mode {
        BUY("gui.simukraft.global_market.mode.buy"),
        LIST("gui.simukraft.global_market.mode.list"),
        WAREHOUSE("gui.simukraft.global_market.mode.warehouse");

        private final String translationKey;

        Mode(String translationKey) {
            this.translationKey = translationKey;
        }

        String translationKey() {
            return translationKey;
        }
    }

    private GlobalMarketModeTabStrip() {
    }

    static int tabCount(boolean canList) {
        return canList ? 3 : 1;
    }

    static Mode[] tabs(boolean canList) {
        return canList ? new Mode[]{Mode.BUY, Mode.LIST, Mode.WAREHOUSE} : new Mode[]{Mode.BUY};
    }

    static int hitWidth(boolean canList) {
        return TAB_SPACING * tabCount(canList);
    }

    /**
     * renderUnselected: 先画未选中 Tab，让面板盖住底部接缝。
     */
    @OnlyIn(Dist.CLIENT)
    static void renderUnselected(GUIContext guiContext, Font font, int left, int rootTop, Mode mode, boolean canList) {
        render(guiContext, font, left, rootTop, mode, canList, false);
    }

    /**
     * renderSelected: 面板之后再画选中 Tab，纹理接到窗口上沿。
     */
    @OnlyIn(Dist.CLIENT)
    static void renderSelected(GUIContext guiContext, Font font, int left, int rootTop, Mode mode, boolean canList) {
        render(guiContext, font, left, rootTop, mode, canList, true);
    }

    @Nullable
    static Mode hit(float mouseX, float mouseY, int left, int rootTop, boolean canList) {
        Mode[] tabs = tabs(canList);
        for (int i = 0; i < tabs.length; i++) {
            int x = left + HIT_X + i * TAB_SPACING;
            int y = rootTop + HIT_Y;
            if (mouseX >= x && mouseX < x + TAB_WIDTH && mouseY >= y && mouseY < y + TAB_HEIGHT) {
                return tabs[i];
            }
        }
        return null;
    }

    @Nullable
    static Component hoveredName(float mouseX, float mouseY, int left, int rootTop, boolean canList) {
        Mode mode = hit(mouseX, mouseY, left, rootTop, canList);
        return mode == null ? null : Component.translatable(mode.translationKey());
    }

    @OnlyIn(Dist.CLIENT)
    private static void render(GUIContext guiContext, Font font, int left, int rootTop, Mode mode, boolean canList, boolean selectedPass) {
        Mode[] tabs = tabs(canList);
        for (int i = 0; i < tabs.length; i++) {
            Mode tab = tabs[i];
            boolean selected = tab == mode;
            if (selected != selectedPass) {
                continue;
            }
            int x = left + HIT_X + i * TAB_SPACING;
            int y = rootTop + HIT_Y;
            ResourceLocation[] sprites = selected ? SELECTED_TOP_TABS : UNSELECTED_TOP_TABS;
            guiContext.graphics.blitSprite(sprites[Mth.clamp(i, 0, sprites.length - 1)], x, y, TAB_WIDTH, TAB_HEIGHT);
            ItemStack icon = icon(tab);
            int iconX = x + 5;
            int iconY = y + 9;
            guiContext.pose.pushPose();
            guiContext.pose.translate(0.0F, 0.0F, 100.0F);
            guiContext.graphics.renderFakeItem(icon, iconX, iconY);
            guiContext.graphics.renderItemDecorations(font, icon, iconX, iconY);
            guiContext.pose.popPose();
        }
    }

    private static ItemStack icon(Mode mode) {
        return switch (mode) {
            case LIST -> new ItemStack(Items.CHEST);
            case WAREHOUSE -> new ItemStack(Items.BARREL);
            default -> new ItemStack(ModItems.GOLD_COIN.get());
        };
    }
}
