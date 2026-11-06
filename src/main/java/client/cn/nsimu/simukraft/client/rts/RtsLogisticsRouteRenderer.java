package client.cn.nsimu.simukraft.client.rts;

import client.cn.nsimu.simukraft.client.freecamera.FreeCameraManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import common.cn.nsimu.simukraft.network.rts.RtsLogisticsRoutesSyncPacket;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.List;

/**
 * RTS 物流线路渲染：按屏幕像素宽度画发光金带，缩放不改变粗细，白光沿传输方向移动。
 */
@OnlyIn(Dist.CLIENT)
public final class RtsLogisticsRouteRenderer {
    private static final int COLOR_HALO = 0x66FFB22A;
    private static final int COLOR_CORE = 0xEEFFE08A;
    private static final int COLOR_FLOW = 0xFFFFFFFF;
    private static final float HALO_PIXELS = 7.0F;
    private static final float CORE_PIXELS = 2.6F;
    private static final float FLOW_PIXELS = 2.2F;
    private static final long FLOW_PERIOD_MS = 1200L;
    private static final double FLOW_SPACING = 4.0D;
    private static final double FLOW_LENGTH = 1.15D;

    private RtsLogisticsRouteRenderer() {
    }

    /**
     * onRender: 在半透明方块之后画当前悬浮节点的线路。
     */
    public static void onRender(RenderLevelStageEvent event) {
        if (!FreeCameraManager.isRtsActive() || event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        List<RtsLogisticsRoutesSyncPacket.Line> lines = RtsLogisticsRouteManager.lines();
        if (!hasDrawableLine(lines)) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        Vec3 look = new Vec3(event.getCamera().getLookVector());
        Matrix4f matrix = event.getPoseStack().last().pose();
        double worldPerPixel = worldUnitsPerPixel(event.getProjectionMatrix());
        float phase = (Util.getMillis() % FLOW_PERIOD_MS) / (float) FLOW_PERIOD_MS;
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (RtsLogisticsRoutesSyncPacket.Line line : lines) {
            Vec3 from = center(line.from());
            Vec3 to = center(line.to());
            if (from.distanceToSqr(to) < 0.01D) {
                continue;
            }
            drawRibbon(buffer, matrix, camera, look, from, to, worldPerPixel * HALO_PIXELS * 0.5D, COLOR_HALO);
            drawRibbon(buffer, matrix, camera, look, from, to, worldPerPixel * CORE_PIXELS * 0.5D, COLOR_CORE);
            drawFlow(buffer, matrix, camera, look, from, to, worldPerPixel * FLOW_PIXELS * 0.5D, phase);
        }
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void drawFlow(BufferBuilder buffer, Matrix4f matrix, Vec3 camera, Vec3 look, Vec3 from, Vec3 to, double halfWidth, float phase) {
        Vec3 delta = to.subtract(from);
        double length = delta.length();
        if (length < 0.05D) {
            return;
        }
        Vec3 dir = delta.scale(1.0D / length);
        double dash = Math.min(FLOW_LENGTH, length * 0.35D);
        for (double distance = phase * FLOW_SPACING; distance < length; distance += FLOW_SPACING) {
            double end = Math.min(length, distance + dash);
            drawRibbon(buffer, matrix, camera, look, from.add(dir.scale(distance)), from.add(dir.scale(end)), halfWidth, COLOR_FLOW);
        }
    }

    private static void drawRibbon(BufferBuilder buffer, Matrix4f matrix, Vec3 camera, Vec3 look, Vec3 from, Vec3 to, double halfWidth, int color) {
        Vec3 side = sideVector(from, to, look, halfWidth);
        if (side == Vec3.ZERO) {
            return;
        }
        vertex(buffer, matrix, camera, from.subtract(side), color);
        vertex(buffer, matrix, camera, from.add(side), color);
        vertex(buffer, matrix, camera, to.add(side), color);
        vertex(buffer, matrix, camera, to.subtract(side), color);
    }

    private static Vec3 sideVector(Vec3 from, Vec3 to, Vec3 look, double halfWidth) {
        Vec3 dir = to.subtract(from);
        if (dir.lengthSqr() < 1.0E-8D || halfWidth <= 0.0D) {
            return Vec3.ZERO;
        }
        Vec3 side = dir.cross(look);
        if (side.lengthSqr() < 1.0E-8D) {
            side = dir.cross(new Vec3(0.0D, 1.0D, 0.0D));
        }
        if (side.lengthSqr() < 1.0E-8D) {
            return Vec3.ZERO;
        }
        return side.normalize().scale(halfWidth);
    }

    /**
     * worldUnitsPerPixel: 把当前投影下的 1 个屏幕像素换成世界单位，线宽才不随 RTS 缩放变化。
     */
    private static double worldUnitsPerPixel(Matrix4f projection) {
        int width = Math.max(1, Minecraft.getInstance().getWindow().getWidth());
        float scaleX = Math.abs(projection.m00());
        if (scaleX < 1.0E-6F) {
            return 0.02D;
        }
        return 2.0D / (scaleX * width);
    }

    private static boolean hasDrawableLine(List<RtsLogisticsRoutesSyncPacket.Line> lines) {
        for (RtsLogisticsRoutesSyncPacket.Line line : lines) {
            if (line != null && line.from() != null && line.to() != null
                    && center(line.from()).distanceToSqr(center(line.to())) >= 0.01D) {
                return true;
            }
        }
        return false;
    }

    private static Vec3 center(net.minecraft.core.BlockPos pos) {
        return new Vec3(pos.getX() + 0.5D, pos.getY() + 0.85D, pos.getZ() + 0.5D);
    }

    private static void vertex(BufferBuilder buffer, Matrix4f matrix, Vec3 camera, Vec3 pos, int color) {
        float red = ((color >> 16) & 0xFF) / 255.0F;
        float green = ((color >> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        float alpha = ((color >> 24) & 0xFF) / 255.0F;
        buffer.addVertex(matrix, (float) (pos.x - camera.x), (float) (pos.y - camera.y), (float) (pos.z - camera.z))
                .setColor(red, green, blue, alpha);
    }
}
