package client.cn.nsimu.simukraft.client.ui;

import com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SDFRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import common.cn.nsimu.simukraft.SimuKraft;
import common.cn.nsimu.simukraft.config.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

/**
 * 圆角半透明毛玻璃底色。只替换本模组界面的面板底，不改原版界面。
 */
@OnlyIn(Dist.CLIENT)
public final class SimuKraftGlassTextures {
    static final float PANEL_RADIUS = 8.0F;
    static final float CARD_RADIUS = 5.0F;
    private static final int PANEL_TINT = 0xB01C2430;
    private static final int CARD_TINT = 0x8C2A3442;
    private static final int BORDER = 0x66D7E6F2;
    private static final int BLUR_RADIUS = 2;
    private static final int DOWNSCALE = 4;
    private static final ResourceLocation SHADER_LOCATION = ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "rounded_glass");
    private static int sourceTexture = -1;
    private static int blurredTexture = -1;
    private static int smallTexture = -1;
    private static int horizontalTexture = -1;
    private static int downsampleFramebuffer = -1;
    private static int horizontalFramebuffer = -1;
    private static int sourceWidth;
    private static int sourceHeight;
    private static boolean captured;
    private static ShaderInstance roundedShader;

    private SimuKraftGlassTextures() {
    }

    /**
     * onRegisterShaders: 注册圆角裁切着色器。
     */
    public static void onRegisterShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(), SHADER_LOCATION, DefaultVertexFormat.POSITION_TEX),
                    shader -> roundedShader = shader);
        } catch (java.io.IOException exception) {
            SimuKraft.LOGGER.warn("圆角毛玻璃着色器注册失败，将回退为半透明圆角", exception);
        }
    }

    /**
     * enabled: 读取客户端配置，判断是否启用毛玻璃。
     */
    public static boolean enabled() {
        return ClientConfig.glassUiEnabled();
    }

    /**
     * panel: 窗口和主面板底色。
     */
    public static IGuiTexture panel(int fallbackColor, int fallbackBorder) {
        if (!enabled()) {
            return new ColorRectTexture(fallbackColor);
        }
        return layered(PANEL_RADIUS, PANEL_TINT, BORDER, 1.0F);
    }

    /**
     * card: 卡片和次级面板底色。
     */
    public static IGuiTexture card(int fallbackColor, int fallbackBorder) {
        if (!enabled()) {
            return new ColorRectTexture(fallbackColor);
        }
        return layered(CARD_RADIUS, CARD_TINT, BORDER, 1.0F);
    }

    /**
     * fill: 在原版 GuiGraphics 上绘制圆角毛玻璃。关闭时保持原来的直角填充。
     */
    public static void fill(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        if (width <= 0 || height <= 0) {
            return;
        }
        if (!enabled()) {
            graphics.fill(x, y, x + width, y + height, color);
            return;
        }
        new FrostedGlassTexture(Math.min(PANEL_RADIUS, Math.min(width, height) / 2.0F))
                .draw(graphics, 0, 0, x, y, width, height, 0);
        new SDFRectTexture()
                .setRadius(Math.min(PANEL_RADIUS, Math.min(width, height) / 2.0F))
                .setStroke(1.0F)
                .setColor(soften(color))
                .setBorderColor(BORDER)
                .draw(graphics, 0, 0, x, y, width, height, 0);
    }

    /**
     * applyElement: 把已有矩形底色换成圆角毛玻璃，保留物品图标等非底色层。
     */
    public static void applyElement(UIElement element) {
        if (!enabled() || element == null || element.getStyle() == null) {
            return;
        }
        IGuiTexture texture = element.getStyle().backgroundTexture();
        IGuiTexture replaced = replace(texture, role(element));
        if (replaced != texture) {
            element.style(style -> style.backgroundTexture(replaced));
        }
    }

    private static IGuiTexture replace(IGuiTexture texture, Role role) {
        if (texture == null || texture == IGuiTexture.EMPTY || texture instanceof FrostedGlassTexture) {
            return texture;
        }
        if (texture instanceof ColorRectTexture rect) {
            return layered(role.radius, soften(rect.color), BORDER, role.stroke);
        }
        if (texture instanceof GuiTextureGroup group) {
            IGuiTexture[] textures = group.getTextures();
            if (textures == null || textures.length == 0) {
                return texture;
            }
            IGuiTexture[] replaced = new IGuiTexture[textures.length];
            boolean changed = false;
            for (int index = 0; index < textures.length; index++) {
                replaced[index] = replaceLayer(textures[index], role);
                changed |= replaced[index] != textures[index];
            }
            return changed ? new GuiTextureGroup(replaced) : texture;
        }
        if (texture instanceof SpriteTexture && role != Role.CONTENT) {
            return layered(role.radius, PANEL_TINT, BORDER, role.stroke);
        }
        return texture;
    }

    private static IGuiTexture replaceLayer(IGuiTexture texture, Role role) {
        if (texture instanceof ColorRectTexture || texture instanceof SpriteTexture) {
            return replace(texture, role);
        }
        return texture;
    }

    private static Role role(UIElement element) {
        if (element.hasClass("simukraft_card_content_panel") || element.hasClass("simukraft_card_slot")
                || element.hasClass("simukraft_card_shadow") || element.hasClass("simukraft_grid_panel")
                || element.hasClass("simukraft_badge")) {
            return Role.CARD;
        }
        return Role.PANEL;
    }

    private static IGuiTexture layered(float radius, int tint, int border, float stroke) {
        return new GuiTextureGroup(
                new FrostedGlassTexture(radius),
                new SDFRectTexture().setRadius(radius).setStroke(stroke).setColor(tint).setBorderColor(border));
    }

    private static IGuiTexture solid(int color, int border) {
        return new GuiTextureGroup(new ColorRectTexture(color), new ColorBorderTexture(2, border));
    }

    private static int soften(int color) {
        int alpha = (color >>> 24) & 0xFF;
        int softened = alpha == 0xFF ? 0xB0 : Math.max(0x55, Math.min(0xC8, alpha));
        return (softened << 24) | (color & 0x00FFFFFF);
    }

    /**
     * captureBackdrop: 在界面控件绘制前复制当前画面，作为毛玻璃底图。
     */
    public static void captureBackdrop() {
        if (!enabled() || !RenderSystem.isOnRenderThread()) {
            return;
        }
        if (captured && blurredTexture != -1) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        int width = minecraft.getWindow().getWidth();
        int height = minecraft.getWindow().getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        ensureTextures(width, height);
        int mainTexture = minecraft.getMainRenderTarget().getColorTextureId();
        int previousFramebuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int[] previousViewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, previousViewport);
        try {
            GlStateManager._bindTexture(sourceTexture);
            GlStateManager._glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height);
            blur(width, height);
        } catch (RuntimeException exception) {
            SimuKraft.LOGGER.warn("圆角毛玻璃底图生成失败，本帧回退为半透明圆角", exception);
        } finally {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFramebuffer);
            GL11.glViewport(previousViewport[0], previousViewport[1], previousViewport[2], previousViewport[3]);
            GlStateManager._bindTexture(mainTexture);
        }
        captured = blurredTexture != -1;
    }

    /**
     * releaseBackdrop: 关闭界面后丢掉底图，下次打开再截当前世界。
     */
    public static void releaseBackdrop() {
        if (!RenderSystem.isOnRenderThread()) {
            return;
        }
        captured = false;
        deleteTextures();
    }

    private static void ensureTextures(int width, int height) {
        if (sourceTexture != -1 && sourceWidth == width && sourceHeight == height) {
            return;
        }
        captured = false;
        deleteTextures();
        sourceWidth = width;
        sourceHeight = height;
        int smallWidth = Math.max(1, width / DOWNSCALE);
        int smallHeight = Math.max(1, height / DOWNSCALE);
        sourceTexture = createTexture(width, height);
        blurredTexture = createTexture(width, height);
        smallTexture = createTexture(smallWidth, smallHeight);
        horizontalTexture = createTexture(smallWidth, smallHeight);
        downsampleFramebuffer = GL30.glGenFramebuffers();
        horizontalFramebuffer = GL30.glGenFramebuffers();
    }

    private static int createTexture(int width, int height) {
        int texture = TextureUtil.generateTextureId();
        GlStateManager._bindTexture(texture);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 0L);
        return texture;
    }

    private static void blur(int width, int height) {
        int smallWidth = Math.max(1, width / DOWNSCALE);
        int smallHeight = Math.max(1, height / DOWNSCALE);
        blitTo(sourceTexture, downsampleFramebuffer, smallTexture, smallWidth, smallHeight);
        boxBlur(smallTexture, smallWidth, smallHeight, horizontalFramebuffer, horizontalTexture, true);
        boxBlur(horizontalTexture, smallWidth, smallHeight, downsampleFramebuffer, smallTexture, false);
        blitTo(smallTexture, horizontalFramebuffer, blurredTexture, width, height);
    }

    private static void blitTo(int source, int framebuffer, int destination, int destinationWidth, int destinationHeight) {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, destination, 0);
        GL11.glViewport(0, 0, destinationWidth, destinationHeight);
        drawTexturedQuad(source, destinationWidth, destinationHeight);
    }

    private static void boxBlur(int source, int width, int height, int framebuffer, int destination, boolean horizontal) {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, destination, 0);
        GL11.glViewport(0, 0, width, height);
        GlStateManager._bindTexture(source);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        Matrix4f matrix = new Matrix4f().setOrtho(0.0F, width, height, 0.0F, -1000.0F, 1000.0F);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        for (int offset = -BLUR_RADIUS; offset <= BLUR_RADIUS; offset++) {
            float shiftU = horizontal ? offset / (float) width : 0.0F;
            float shiftV = horizontal ? 0.0F : offset / (float) height;
            buffer.addVertex(matrix, 0.0F, height, 0.0F).setUv(shiftU, 1.0F + shiftV);
            buffer.addVertex(matrix, width, height, 0.0F).setUv(1.0F + shiftU, 1.0F + shiftV);
            buffer.addVertex(matrix, width, 0.0F, 0.0F).setUv(1.0F + shiftU, shiftV);
            buffer.addVertex(matrix, 0.0F, 0.0F, 0.0F).setUv(shiftU, shiftV);
        }
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.disableBlend();
    }

    private static void drawTexturedQuad(int texture, int viewportWidth, int viewportHeight) {
        GlStateManager._bindTexture(texture);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.disableBlend();
        Matrix4f matrix = new Matrix4f().setOrtho(0.0F, viewportWidth, viewportHeight, 0.0F, -1000.0F, 1000.0F);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.addVertex(matrix, 0.0F, viewportHeight, 0.0F).setUv(0.0F, 1.0F);
        buffer.addVertex(matrix, viewportWidth, viewportHeight, 0.0F).setUv(1.0F, 1.0F);
        buffer.addVertex(matrix, viewportWidth, 0.0F, 0.0F).setUv(1.0F, 0.0F);
        buffer.addVertex(matrix, 0.0F, 0.0F, 0.0F).setUv(0.0F, 0.0F);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    private static void deleteTextures() {
        if (sourceTexture != -1) {
            GlStateManager._deleteTexture(sourceTexture);
            sourceTexture = -1;
        }
        if (blurredTexture != -1) {
            GlStateManager._deleteTexture(blurredTexture);
            blurredTexture = -1;
        }
        if (smallTexture != -1) {
            GlStateManager._deleteTexture(smallTexture);
            smallTexture = -1;
        }
        if (horizontalTexture != -1) {
            GlStateManager._deleteTexture(horizontalTexture);
            horizontalTexture = -1;
        }
        if (downsampleFramebuffer != -1) {
            GL30.glDeleteFramebuffers(downsampleFramebuffer);
            downsampleFramebuffer = -1;
        }
        if (horizontalFramebuffer != -1) {
            GL30.glDeleteFramebuffers(horizontalFramebuffer);
            horizontalFramebuffer = -1;
        }
    }

    private enum Role {
        PANEL(PANEL_RADIUS, 1.0F),
        CARD(CARD_RADIUS, 1.0F),
        CONTENT(0.0F, 0.0F);

        private final float radius;
        private final float stroke;

        Role(float radius, float stroke) {
            this.radius = radius;
            this.stroke = stroke;
        }
    }

    /**
     * 绘制圆角裁切后的模糊底图。没有底图时退回半透明圆角。
     */
    private static final class FrostedGlassTexture implements IGuiTexture {
        private final float radius;

        private FrostedGlassTexture(float radius) {
            this.radius = radius;
        }

        @Override
        public void draw(GuiGraphics graphics, float mouseX, float mouseY, float x, float y, float width, float height, float partialTicks) {
            if (width <= 0.0F || height <= 0.0F || blurredTexture == -1) {
                return;
            }
            Minecraft minecraft = Minecraft.getInstance();
            int screenWidth = Math.max(1, minecraft.getWindow().getGuiScaledWidth());
            int screenHeight = Math.max(1, minecraft.getWindow().getGuiScaledHeight());
            float u0 = x / screenWidth;
            float u1 = (x + width) / screenWidth;
            float vTop = 1.0F - y / screenHeight;
            float vBottom = 1.0F - (y + height) / screenHeight;
            graphics.flush();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            float leftU = 0.0F;
            float rightU = 1.0F;
            float topV = 0.0F;
            float bottomV = 1.0F;
            if (roundedShader != null) {
                roundedShader.safeGetUniform("GlassSize").set(width, height);
                roundedShader.safeGetUniform("GlassRadius").set(radius);
                roundedShader.safeGetUniform("SampleUv").set(u0, vTop, u1, vBottom);
                RenderSystem.setShader(() -> roundedShader);
            } else {
                leftU = u0;
                rightU = u1;
                topV = vTop;
                bottomV = vBottom;
                RenderSystem.setShader(GameRenderer::getPositionTexShader);
            }
            RenderSystem.setShaderTexture(0, blurredTexture);
            Matrix4f matrix = graphics.pose().last().pose();
            BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            buffer.addVertex(matrix, x, y + height, 0.0F).setUv(leftU, bottomV);
            buffer.addVertex(matrix, x + width, y + height, 0.0F).setUv(rightU, bottomV);
            buffer.addVertex(matrix, x + width, y, 0.0F).setUv(rightU, topV);
            buffer.addVertex(matrix, x, y, 0.0F).setUv(leftU, topV);
            BufferUploader.drawWithShader(buffer.buildOrThrow());
            RenderSystem.disableBlend();
        }
    }
}
