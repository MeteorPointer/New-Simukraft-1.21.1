package client.cn.kafei.simukraft.client.city.map;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

class SimuMap3DCameraTest {
    @Test
    void modelMatrix_liftsHigherBlocksTowardTheTopOfTheScreen() {
        Matrix4f matrix = SimuMap3DCamera.modelMatrix(400.0F, 300.0F, 0.6F, 2.0F, 64.0F);
        Vector4f ground = matrix.transform(new Vector4f(0.0F, 64.0F, 0.0F, 1.0F));
        Vector4f roof = matrix.transform(new Vector4f(0.0F, 80.0F, 0.0F, 1.0F));

        assertTrue(roof.y < ground.y, "higher blocks should move up the screen, roof=" + roof.y + " ground=" + ground.y);
        assertTrue(Math.abs(roof.x - ground.x) < 0.01F);
    }

    @Test
    void modelMatrix_keepsBlockDepthSeparateFromHeight() {
        Matrix4f matrix = SimuMap3DCamera.modelMatrix(400.0F, 300.0F, 0.0F, 2.0F, 64.0F);
        Vector4f near = matrix.transform(new Vector4f(0.0F, 64.0F, -8.0F, 1.0F));
        Vector4f far = matrix.transform(new Vector4f(0.0F, 64.0F, 8.0F, 1.0F));
        Vector4f roof = matrix.transform(new Vector4f(0.0F, 80.0F, 0.0F, 1.0F));

        assertTrue(far.y > near.y);
        assertTrue(Math.abs(roof.z - near.z) > 0.5F, "height must not collapse into the same depth as a flat map");
    }

    @Test
    void pan_movesTheMapWithTheCursor() {
        float[] delta = new float[2];
        SimuMap3DCamera.pan(20.0F, 0.0F, 0.0F, 2.0F, delta);
        assertTrue(delta[0] > 0.0F);
        SimuMap3DCamera.pan(0.0F, 20.0F, 0.0F, 2.0F, delta);
        assertTrue(delta[1] > 0.0F);
    }
}
