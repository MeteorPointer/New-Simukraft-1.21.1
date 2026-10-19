package client.cn.nsimu.simukraft.client.toast;

import common.cn.nsimu.simukraft.config.ClientConfig;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Locale;

/**
 * ClientToastConfig: 解析通知布局的客户端配置。
 */
@OnlyIn(Dist.CLIENT)
public final class ClientToastConfig {
    /**
     * 通知布局使用的六个屏幕锚点。
     */
    public enum Anchor {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT,
        TOP_CENTER,
        BOTTOM_CENTER
    }

    /**
     * FitMode: 最小字号仍放不下时优先加宽或加高。
     */
    public enum FitMode {
        WIDTH,
        HEIGHT;

        /**
         * fromName: 解析扩展模式，未知值回退到高度优先。
         */
        public static FitMode fromName(String value) {
            if (value != null && value.equalsIgnoreCase(WIDTH.name())) {
                return WIDTH;
            }
            return HEIGHT;
        }
    }

    public static final int MIN_WIDTH = 120;
    public static final int MAX_WIDTH = 512;
    public static final int MIN_HEIGHT = 36;
    public static final int MAX_HEIGHT = 160;

    private ClientToastConfig() {
    }

    /**
     * getAnchor: 获取当前通知锚点。
     */
    public static Anchor getAnchor() {
        try {
            return Anchor.valueOf(ClientConfig.toastAnchorName().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return Anchor.TOP_RIGHT;
        }
    }

    /**
     * width: 获取当前通知宽度。
     */
    public static int width() {
        return ClientConfig.toastWidth();
    }

    /**
     * height: 获取当前通知高度。
     */
    public static int height() {
        return ClientConfig.toastHeight();
    }

    /**
     * minTextScale: 获取通知文字最小缩放，物品图标跟随该值。
     */
    public static float minTextScale() {
        return ClientConfig.toastMinTextScale();
    }

    /**
     * fitMode: 获取放不下时的扩展方向。
     */
    public static FitMode fitMode() {
        return FitMode.fromName(ClientConfig.toastFitModeName());
    }

    /**
     * calculatePosition: 按锚点与偏移计算首个通知的位置。
     */
    public static int[] calculatePosition(int screenWidth, int screenHeight, int toastWidth, int toastHeight) {
        return calculatePosition(
                getAnchor(),
                ClientConfig.toastPosX(),
                ClientConfig.toastPosY(),
                screenWidth,
                screenHeight,
                toastWidth,
                toastHeight);
    }

    /**
     * calculatePosition: 用编辑器中的临时布局计算首个通知的位置。
     */
    public static int[] calculatePosition(Anchor anchor, int offsetX, int offsetY, int screenWidth,
                                          int screenHeight, int toastWidth, int toastHeight) {
        Anchor safeAnchor = anchor != null ? anchor : Anchor.TOP_RIGHT;
        int x;
        int y;
        switch (safeAnchor) {
            case TOP_LEFT -> {
                x = offsetX;
                y = offsetY;
            }
            case TOP_RIGHT -> {
                x = screenWidth - toastWidth + offsetX;
                y = offsetY;
            }
            case BOTTOM_LEFT -> {
                x = offsetX;
                y = screenHeight - toastHeight + offsetY;
            }
            case BOTTOM_RIGHT -> {
                x = screenWidth - toastWidth + offsetX;
                y = screenHeight - toastHeight + offsetY;
            }
            case TOP_CENTER -> {
                x = (screenWidth - toastWidth) / 2 + offsetX;
                y = offsetY;
            }
            case BOTTOM_CENTER -> {
                x = (screenWidth - toastWidth) / 2 + offsetX;
                y = screenHeight - toastHeight + offsetY;
            }
            default -> {
                x = screenWidth - toastWidth + offsetX;
                y = offsetY;
            }
        }
        return new int[]{x, y};
    }

    /**
     * reset: 重置通知布局配置。
     */
    public static void reset() {
        ClientConfig.resetToastDefaults();
    }
}
