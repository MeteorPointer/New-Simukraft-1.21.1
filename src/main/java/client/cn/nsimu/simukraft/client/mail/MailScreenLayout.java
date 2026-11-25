package client.cn.nsimu.simukraft.client.mail;

import common.cn.nsimu.simukraft.SimuKraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * 邮件界面布局：背景素材按 1920x1080 设计，所有区域先用素材坐标描述，再按当前窗口换算成 GUI 坐标。
 */
final class MailScreenLayout {
    static final int SOURCE_WIDTH = 1920;
    static final int SOURCE_HEIGHT = 1080;

    static final int[][] TABS = {{66, 353, 186, 172}, {66, 540, 186, 172}, {66, 727, 186, 172}};
    static final int[][] TAB_ARROWS = {{92, 248, 54, 41}, {92, 956, 54, 41}};
    static final int[][] SCROLL_BUTTONS = {{1458, 245, 68, 59}, {1458, 918, 68, 59}};
    static final int[] THUMB = {1465, 319, 55, 84};
    static final int THUMB_TRAVEL = 515;
    static final int[] SCROLL_TRACK = {1458, 319, 68, 599};
    static final int[] MAP = {1540, 266, 326, 200};
    static final int[] INFO = {1542, 502, 322, 470};
    static final int[] CONTENT = {300, 274, 1144, 620};
    static final int[] SYSTEM_LIST = {300, 274, 430, 620};
    static final int[] SYSTEM_DETAIL = {748, 274, 696, 620};
    static final int[] INPUT = {304, 912, 1026, 52};
    static final int[] ENTER = {1338, 902, 104, 65};
    static final int[][] WINDOW_BUTTONS = {{1723, 24, 53, 53}, {1781, 24, 53, 53}, {1842, 23, 53, 53}};

    private int width;
    private int height;

    void resize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    int x(int sourceX) {
        return Math.round(sourceX * (float) width / SOURCE_WIDTH);
    }

    int y(int sourceY) {
        return Math.round(sourceY * (float) height / SOURCE_HEIGHT);
    }

    /** 把素材矩形换算成 GUI 坐标 {x, y, w, h}。 */
    int[] rect(int[] source) {
        int x0 = x(source[0]);
        int y0 = y(source[1]);
        return new int[]{x0, y0, x(source[0] + source[2]) - x0, y(source[1] + source[3]) - y0};
    }

    boolean contains(int[] source, double mouseX, double mouseY) {
        return inside(rect(source), mouseX, mouseY);
    }

    static boolean inside(int[] rect, double mouseX, double mouseY) {
        return rect != null && mouseX >= rect[0] && mouseX < rect[0] + rect[2]
                && mouseY >= rect[1] && mouseY < rect[1] + rect[3];
    }

    void drawTexture(GuiGraphics graphics, ResourceLocation texture, int[] source) {
        int[] r = rect(source);
        graphics.blit(texture, r[0], r[1], r[2], r[3], 0.0F, 0.0F, source[2], source[3], source[2], source[3]);
    }

    static void enableScissor(GuiGraphics graphics, int[] rect) {
        graphics.enableScissor(rect[0], rect[1], rect[0] + rect[2], rect[1] + rect[3]);
    }

    static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "textures/gui/" + name);
    }
}
