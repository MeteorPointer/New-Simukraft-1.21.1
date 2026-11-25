package client.cn.nsimu.simukraft.client.mail;

import common.cn.nsimu.simukraft.SimuKraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * 邮件界面布局：背景素材按 1920x1080 设计，所有区域先用素材坐标描述，再按当前窗口换算成 GUI 坐标。
 *
 * <p>新背景不再把标签板烘进图里：左侧是代码绘制的分类列表（一行一个分类，只有图标），
 * 列表左边是它自己的滑动条；右侧仍是正文滚动条。
 */
final class MailScreenLayout {
    static final int SOURCE_WIDTH = 1920;
    static final int SOURCE_HEIGHT = 1080;

    /** 左侧分类列表：三行等分 237..968，顺序和 MailCategory 一致（市民、系统、城市）。 */
    static final int[][] CATEGORY_ROWS = {{80, 237, 164, 243}, {80, 480, 164, 243}, {80, 723, 164, 245}};
    /** 列表滑动条的上下按钮，位置和尺寸取自背景里烘好的 68x59 按钮。 */
    static final int[] LIST_SCROLL_UP = {10, 237, 68, 59};
    static final int[] LIST_SCROLL_DOWN = {10, 910, 68, 59};
    /** 滑块行程：轨道 296..909，滑块高 84（取自素材 55x84）。 */
    static final int[] LIST_THUMB = {16, 296, 55, 84};
    static final int LIST_THUMB_TRAVEL = 529;

    static final int[] MAP = {1540, 266, 326, 200};
    static final int[] INFO = {1542, 502, 322, 470};
    static final int[] CONTENT = {300, 274, 1144, 620};
    static final int[] SYSTEM_LIST = {300, 274, 430, 620};
    static final int[] SYSTEM_DETAIL = {748, 274, 696, 620};
    static final int[] INPUT = {304, 912, 1026, 52};
    static final int[] ENTER = {1338, 902, 104, 65};
    static final int[][] WINDOW_BUTTONS = {{1723, 24, 53, 53}, {1781, 24, 53, 53}, {1842, 23, 53, 53}};

    static final int[][] SCROLL_BUTTONS = {{1458, 245, 68, 59}, {1458, 918, 68, 59}};
    static final int[] THUMB = {1465, 319, 55, 84};
    static final int THUMB_TRAVEL = 515;
    static final int[] SCROLL_TRACK = {1458, 319, 68, 599};

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
