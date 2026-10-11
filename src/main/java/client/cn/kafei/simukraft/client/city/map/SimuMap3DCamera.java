package client.cn.kafei.simukraft.client.city.map;

import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

/**
 * 城市核心 3D 地图相机。
 * 水平方向留在屏幕 X，高度向上，深度向下，遮挡交给视图 Z。
 */
@OnlyIn(Dist.CLIENT)
public final class SimuMap3DCamera {
    /** 约 37 度俯角：墙面还在，顶面也能看见。 */
    public static final float PITCH = 0.65F;

    private SimuMap3DCamera() {
    }

    /**
     * modelMatrix: 方块坐标到界面像素。Y 轴朝上，+Z 朝屏幕下方。
     */
    public static Matrix4f modelMatrix(float centerX, float centerY, float yaw, float scale, float baseHeight) {
        Matrix4f matrix = new Matrix4f();
        matrix.translate(centerX, centerY, 0.0F);
        matrix.rotateX(-PITCH);
        matrix.rotateY(-yaw);
        matrix.scale(scale, -scale, scale);
        matrix.translate(0.0F, -baseHeight, 0.0F);
        return matrix;
    }

    /**
     * pan: 把屏幕拖动换算成世界 X/Z 位移，让光标下的地形跟着鼠标走。
     */
    public static void pan(float mouseX, float mouseY, float yaw, float scale, float[] outDxDz) {
        float safeScale = Math.max(scale, 0.01F);
        float sinPitch = Math.max(0.2F, Mth.sin(PITCH));
        float cos = Mth.cos(yaw);
        float sin = Mth.sin(yaw);
        float ax = mouseX / safeScale;
        float ay = mouseY / (safeScale * sinPitch);
        outDxDz[0] = ax * cos + ay * sin;
        outDxDz[1] = -ax * sin + ay * cos;
    }
}
