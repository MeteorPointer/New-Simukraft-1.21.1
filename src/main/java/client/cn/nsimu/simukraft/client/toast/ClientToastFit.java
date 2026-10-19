package client.cn.nsimu.simukraft.client.toast;

import java.util.function.IntUnaryOperator;

/**
 * ClientToastFit: 在最小字号约束下按宽度优先或高度优先扩展通知框。
 */
final class ClientToastFit {
    private ClientToastFit() {
    }

    /**
     * resolve: 若最小字号放不下，按模式先加宽或先加高。
     */
    static int[] resolve(int preferredWidth, int preferredHeight, int maxWidth, int maxHeight,
                         float minScale, ClientToastConfig.FitMode fitMode,
                         IntUnaryOperator requiredHeightAtLogicalWidth) {
        int width = Math.max(1, preferredWidth);
        int height = Math.max(1, preferredHeight);
        if (fits(width, height, minScale, requiredHeightAtLogicalWidth)) {
            return new int[]{width, height};
        }
        ClientToastConfig.FitMode mode = fitMode != null ? fitMode : ClientToastConfig.FitMode.HEIGHT;
        if (mode == ClientToastConfig.FitMode.WIDTH) {
            width = growWidth(width, height, maxWidth, minScale, requiredHeightAtLogicalWidth);
            if (!fits(width, height, minScale, requiredHeightAtLogicalWidth)) {
                height = growHeight(width, height, maxHeight, minScale, requiredHeightAtLogicalWidth);
            }
        } else {
            height = growHeight(width, height, maxHeight, minScale, requiredHeightAtLogicalWidth);
            if (!fits(width, height, minScale, requiredHeightAtLogicalWidth)) {
                width = growWidth(width, height, maxWidth, minScale, requiredHeightAtLogicalWidth);
            }
        }
        return new int[]{width, height};
    }

    /**
     * fits: 判断在最小字号下内容能否装进目标矩形。
     */
    static boolean fits(int width, int height, float minScale, IntUnaryOperator requiredHeightAtLogicalWidth) {
        float scale = Math.max(0.05F, minScale);
        int logicalWidth = Math.max(1, Math.round(width / scale));
        return scale * requiredHeightAtLogicalWidth.applyAsInt(logicalWidth) <= height + 0.01F;
    }

    private static int growWidth(int startWidth, int height, int maxWidth, float minScale,
                                 IntUnaryOperator requiredHeightAtLogicalWidth) {
        int cap = Math.max(startWidth, maxWidth);
        if (!fits(cap, height, minScale, requiredHeightAtLogicalWidth)) {
            return cap;
        }
        int low = startWidth;
        int high = cap;
        for (int i = 0; i < 12; i++) {
            int mid = (low + high) >>> 1;
            if (fits(mid, height, minScale, requiredHeightAtLogicalWidth)) {
                high = mid;
            } else {
                low = mid + 1;
            }
        }
        return high;
    }

    private static int growHeight(int width, int startHeight, int maxHeight, float minScale,
                                  IntUnaryOperator requiredHeightAtLogicalWidth) {
        int cap = Math.max(startHeight, maxHeight);
        if (!fits(width, cap, minScale, requiredHeightAtLogicalWidth)) {
            return cap;
        }
        int low = startHeight;
        int high = cap;
        for (int i = 0; i < 12; i++) {
            int mid = (low + high) >>> 1;
            if (fits(width, mid, minScale, requiredHeightAtLogicalWidth)) {
                high = mid;
            } else {
                low = mid + 1;
            }
        }
        return high;
    }
}
