package client.cn.nsimu.simukraft.client.mail;

import client.cn.nsimu.simukraft.client.city.map.SimuMapManager;
import client.cn.nsimu.simukraft.client.city.map.SimuMapRegion;
import common.cn.nsimu.simukraft.SimuKraft;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/** 使用邮件素材绘制收件箱界面，并处理标签、邮件列表和回复输入。 */
public final class MailScreen extends Screen {
    private static final int BACKGROUND_WIDTH = 1920;
    private static final int BACKGROUND_HEIGHT = 1080;
    private static final ResourceLocation BACKGROUND = texture("mail_background.png");
    private static final ResourceLocation LABEL = texture("mail_label.png");
    private static final ResourceLocation[] TAB_ICONS = {
            texture("mail_citizen_icon.png"), texture("mail_system_icon.png"), texture("mail_city_icon.png")
    };
    private static final ResourceLocation ARROW_UP = texture("mail_arrow_up.png");
    private static final ResourceLocation ARROW_DOWN = texture("mail_arrow_down.png");
    private static final ResourceLocation ARROW_UP_PRESSED = texture("mail_arrow_up_pressed.png");
    private static final ResourceLocation ARROW_DOWN_PRESSED = texture("mail_arrow_down_pressed.png");
    private static final ResourceLocation SCROLL_THUMB = texture("mail_scroll_thumb.png");
    private static final ResourceLocation SCROLL_THUMB_PRESSED = texture("mail_scroll_thumb_pressed.png");
    private static final ResourceLocation SCROLL_UP = texture("mail_scroll_up.png");
    private static final ResourceLocation SCROLL_DOWN = texture("mail_scroll_down.png");
    private static final ResourceLocation SCROLL_UP_PRESSED = texture("mail_scroll_up_pressed.png");
    private static final ResourceLocation SCROLL_DOWN_PRESSED = texture("mail_scroll_down_pressed.png");
    private static final ResourceLocation ENTER = texture("mail_enter.png");
    private static final ResourceLocation ENTER_PRESSED = texture("mail_enter_pressed.png");
    private static final ResourceLocation MINIMIZE = texture("mail_unpressed_minimize.png");
    private static final ResourceLocation WINDOWED = texture("mail_unpressed_windowed.png");
    private static final ResourceLocation CLOSE = texture("mail_unpressed_close.png");
    private static final ResourceLocation MINIMIZE_PRESSED = texture("mail_minimize.png");
    private static final ResourceLocation WINDOWED_PRESSED = texture("mail_windowed.png");
    private static final ResourceLocation CLOSE_PRESSED = texture("mail_close.png");
    private static final int[][] TAB_BOUNDS = {{66, 353, 186, 172}, {66, 540, 186, 172}, {66, 727, 186, 172}};
    private static final int[][] LEFT_ARROW_BOUNDS = {{92, 248, 54, 41}, {92, 956, 54, 41}};
    private static final int[][] SCROLL_BUTTON_BOUNDS = {{1458, 245, 68, 59}, {1458, 918, 68, 59}};
    private static final int[] THUMB_BOUNDS = {1465, 319, 55, 84};
    private static final int THUMB_TRAVEL = 515;
    private static final int[] MAP_BOUNDS = {1540, 250, 326, 204};
    private static final double MAP_BLOCK_SCALE = 0.36D;
    private static final int[] ENTER_BOUNDS = {1338, 902, 104, 65};
    private static final int[][] WINDOW_BOUNDS = {{1723, 24, 53, 53}, {1781, 24, 53, 53}, {1842, 23, 53, 53}};
    private static final int MAX_VISIBLE_MESSAGES = 10;

    private final List<List<String>> conversations = new ArrayList<>();
    private final List<StringBuilder> drafts = new ArrayList<>();
    private final int openedUnreadCount;
    private final ResourceLocation senderDimension;
    private final int senderX;
    private final int senderZ;
    private final SimuMapManager mapManager = SimuMapManager.getInstance();
    private int activeTab;
    private int listOffset;
    private int pressedControl = -1;
    private boolean draggingThumb;
    private double dragStartY;
    private int dragStartOffset;
    private boolean mapConsumerAcquired;

    public MailScreen(int openedUnreadCount, ResourceLocation senderDimension, int senderX, int senderZ) {
        super(Component.empty());
        this.openedUnreadCount = Math.max(0, Math.min(99, openedUnreadCount));
        this.senderDimension = senderDimension;
        this.senderX = senderX;
        this.senderZ = senderZ;
        for (int i = 0; i < TAB_BOUNDS.length; i++) {
            conversations.add(new ArrayList<>());
            drafts.add(new StringBuilder());
        }
        if (this.openedUnreadCount > 0) {
            conversations.get(0).add(Component.translatable("screen.simukraft.mail.message.test_body").getString());
        }
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "textures/gui/" + name);
    }

    /** 禁用原版菜单背景，避免模糊和暗色层覆盖邮件窗口。 */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    protected void init() {
        super.init();
        mapManager.init();
        if (senderDimension != null && Minecraft.getInstance().level != null
                && senderDimension.equals(Minecraft.getInstance().level.dimension().location())) {
            mapManager.acquireConsumer();
            mapConsumerAcquired = true;
            mapManager.forceScanArea(senderX >> 4, senderZ >> 4, 2);
            mapManager.forceRenderAll();
        }
    }

    /** 绘制完整背景、素材控件和邮件内容。 */
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.blit(BACKGROUND, 0, 0, width, height,
                0.0F, 0.0F, BACKGROUND_WIDTH, BACKGROUND_HEIGHT,
                BACKGROUND_WIDTH, BACKGROUND_HEIGHT);
        drawWindowButtons(graphics);
        drawTabs(graphics);
        drawArrowButtons(graphics);
        drawScrollbar(graphics);
        drawMailContent(graphics);
        drawSenderMap(graphics);
        drawInput(graphics);
    }

    private void drawWindowButtons(GuiGraphics graphics) {
        drawTexture(graphics, MINIMIZE, WINDOW_BOUNDS[0]);
        drawTexture(graphics, WINDOWED, WINDOW_BOUNDS[1]);
        drawTexture(graphics, CLOSE, WINDOW_BOUNDS[2]);
        if (pressedControl >= 0 && pressedControl <= 2) {
            ResourceLocation texture = switch (pressedControl) {
                case 0 -> MINIMIZE_PRESSED;
                case 1 -> WINDOWED_PRESSED;
                default -> CLOSE_PRESSED;
            };
            drawTexture(graphics, texture, WINDOW_BOUNDS[pressedControl]);
        }
    }

    private void drawTabs(GuiGraphics graphics) {
        for (int i = 0; i < TAB_BOUNDS.length; i++) {
            if (i == activeTab) {
                drawTexture(graphics, LABEL, TAB_BOUNDS[i]);
            }
            int[] bounds = TAB_BOUNDS[i];
            int iconSize = 64;
            int x = bounds[0] + (bounds[2] - iconSize) / 2 + 18;
            int y = bounds[1] + (bounds[3] - iconSize) / 2;
            graphics.blit(TAB_ICONS[i], scaleX(x), scaleY(y), scaleX(iconSize), scaleY(iconSize),
                    0.0F, 0.0F, 16, 16, 16, 16);
        }
    }

    private void drawArrowButtons(GuiGraphics graphics) {
        drawTexture(graphics, pressedControl == 3 ? ARROW_UP_PRESSED : ARROW_UP, LEFT_ARROW_BOUNDS[0]);
        drawTexture(graphics, pressedControl == 4 ? ARROW_DOWN_PRESSED : ARROW_DOWN, LEFT_ARROW_BOUNDS[1]);
    }

    private void drawScrollbar(GuiGraphics graphics) {
        drawTexture(graphics, pressedControl == 5 ? SCROLL_UP_PRESSED : SCROLL_UP, SCROLL_BUTTON_BOUNDS[0]);
        drawTexture(graphics, pressedControl == 6 ? SCROLL_DOWN_PRESSED : SCROLL_DOWN, SCROLL_BUTTON_BOUNDS[1]);
        int maxOffset = maxListOffset();
        int y = THUMB_BOUNDS[1] + (maxOffset == 0 ? 0 : Math.round(THUMB_TRAVEL * listOffset / (float) maxOffset));
        int[] thumb = {THUMB_BOUNDS[0], y, THUMB_BOUNDS[2], THUMB_BOUNDS[3]};
        drawTexture(graphics, draggingThumb ? SCROLL_THUMB_PRESSED : SCROLL_THUMB, thumb);
    }

    private void drawMailContent(GuiGraphics graphics) {
        int count = messageCount();
        int listX = 316;
        int listY = 278;
        graphics.enableScissor(scaleX(listX), scaleY(listY), scaleX(1440), scaleY(882));
        if (count == 0) {
            drawCentered(graphics, Component.translatable("screen.simukraft.mail.empty"), 875, 580, 0xFF26383A);
        } else {
            int start = Math.min(listOffset, Math.max(0, count - MAX_VISIBLE_MESSAGES));
            int y = listY;
            for (int i = start; i < Math.min(count, start + MAX_VISIBLE_MESSAGES); i++) {
                String message = conversations.get(activeTab).get(i);
                List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.literal(message), 320);
                int textWidth = lines.stream().mapToInt(font::width).max().orElse(0);
                int bubbleWidth = Math.min(720, Math.max(120, textWidth * 2 + 24));
                int bubbleHeight = Math.max(42, lines.size() * 22 + 16);
                boolean outgoing = activeTab != 0;
                int x = outgoing ? 1430 - bubbleWidth : listX;
                graphics.fill(scaleX(x), scaleY(y), scaleX(x + bubbleWidth), scaleY(y + bubbleHeight),
                        outgoing ? 0xFFD2F2D2 : 0xFFE4ECEC);
                graphics.enableScissor(scaleX(x + 10), scaleY(y + 6),
                        scaleX(x + bubbleWidth - 8), scaleY(y + bubbleHeight - 4));
                for (int line = 0; line < lines.size(); line++) {
                    graphics.drawString(font, lines.get(line), scaleX(x + 12), scaleY(y + 8 + line * 22),
                            0xFF23383A, false);
                }
                graphics.disableScissor();
                y += bubbleHeight + 12;
            }
        }
        graphics.disableScissor();
    }

    private void drawSenderMap(GuiGraphics graphics) {
        int mapX = MAP_BOUNDS[0];
        int mapY = MAP_BOUNDS[1];
        int mapWidth = MAP_BOUNDS[2];
        int mapHeight = MAP_BOUNDS[3];
        graphics.fill(scaleX(mapX), scaleY(mapY), scaleX(mapX + mapWidth), scaleY(mapY + mapHeight), 0xFF18232A);

        Minecraft minecraft = Minecraft.getInstance();
        if (openedUnreadCount > 0 && senderDimension != null && minecraft.level != null
                && senderDimension.equals(minecraft.level.dimension().location()) && SimuMapManager.isAvailable()) {
            double centerX = mapX + mapWidth / 2.0D;
            double centerY = mapY + mapHeight / 2.0D;
            graphics.enableScissor(scaleX(mapX), scaleY(mapY), scaleX(mapX + mapWidth), scaleY(mapY + mapHeight));
            for (SimuMapRegion region : mapManager.getAllRegions()) {
                if (!region.isImageLoaded() && !region.hasData()) {
                    continue;
                }
                double regionX = centerX + (region.regionX * 512.0D - senderX) * MAP_BLOCK_SCALE;
                double regionY = centerY + (region.regionZ * 512.0D - senderZ) * MAP_BLOCK_SCALE;
                double regionSize = 512.0D * MAP_BLOCK_SCALE;
                if (regionX + regionSize < mapX || regionX > mapX + mapWidth
                        || regionY + regionSize < mapY || regionY > mapY + mapHeight) {
                    continue;
                }
                drawMapRegion(graphics, region.getTextureId(), regionX, regionY, regionSize);
            }
            graphics.disableScissor();
            drawSenderMarker(graphics, scaleX((int) Math.round(centerX)), scaleY((int) Math.round(centerY)));
        }
    }

    private void drawMapRegion(GuiGraphics graphics, int textureId, double x, double y, double size) {
        if (textureId == -1) {
            return;
        }
        RenderSystem.setShaderTexture(0, textureId);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.enableBlend();
        Matrix4f matrix = graphics.pose().last().pose();
        float x0 = scaleX((int) Math.round(x));
        float y0 = scaleY((int) Math.round(y));
        float x1 = scaleX((int) Math.round(x + size));
        float y1 = scaleY((int) Math.round(y + size));
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.addVertex(matrix, x0, y1, 0).setUv(0, 1);
        buffer.addVertex(matrix, x1, y1, 0).setUv(1, 1);
        buffer.addVertex(matrix, x1, y0, 0).setUv(1, 0);
        buffer.addVertex(matrix, x0, y0, 0).setUv(0, 0);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.disableBlend();
    }

    private void drawSenderMarker(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 5, y - 5, x + 5, y + 5, 0xFF202020);
        graphics.fill(x - 3, y - 3, x + 3, y + 3, 0xFFFFD400);
    }

    private void drawInput(GuiGraphics graphics) {
        drawTexture(graphics, pressedControl == 7 ? ENTER_PRESSED : ENTER, ENTER_BOUNDS);
        StringBuilder input = drafts.get(activeTab);
        if (!input.isEmpty()) {
            graphics.enableScissor(scaleX(300), scaleY(902), scaleX(1328), scaleY(967));
            graphics.drawString(font, input.toString(), scaleX(312), scaleY(925), 0xFF17282B, false);
            graphics.disableScissor();
        }
    }

    private void drawTexture(GuiGraphics graphics, ResourceLocation texture, int[] bounds) {
        int x = scaleX(bounds[0]);
        int y = scaleY(bounds[1]);
        int w = scaleX(bounds[2]);
        int h = scaleY(bounds[3]);
        graphics.blit(texture, x, y, w, h, 0.0F, 0.0F, bounds[2], bounds[3], bounds[2], bounds[3]);
    }

    private void drawCentered(GuiGraphics graphics, Component text, int sourceX, int sourceY, int color) {
        graphics.drawCenteredString(font, text, scaleX(sourceX), scaleY(sourceY), color);
    }

    private int scaleX(int sourceX) {
        return Math.round(sourceX * (float) width / BACKGROUND_WIDTH);
    }

    private int scaleY(int sourceY) {
        return Math.round(sourceY * (float) height / BACKGROUND_HEIGHT);
    }

    private int messageCount() {
        return conversations.get(activeTab).size();
    }

    private int maxListOffset() {
        return Math.max(0, messageCount() - MAX_VISIBLE_MESSAGES);
    }

    private void selectTab(int tab) {
        activeTab = tab;
        listOffset = 0;
    }

    private void scrollList(int amount) {
        listOffset = Math.max(0, Math.min(maxListOffset(), listOffset + amount));
    }

    private int controlAt(double mouseX, double mouseY) {
        for (int i = 0; i < WINDOW_BOUNDS.length; i++) if (contains(WINDOW_BOUNDS[i], mouseX, mouseY)) return i;
        for (int i = 0; i < TAB_BOUNDS.length; i++) if (contains(TAB_BOUNDS[i], mouseX, mouseY)) return 10 + i;
        for (int i = 0; i < LEFT_ARROW_BOUNDS.length; i++) if (contains(LEFT_ARROW_BOUNDS[i], mouseX, mouseY)) return 3 + i;
        for (int i = 0; i < SCROLL_BUTTON_BOUNDS.length; i++) if (contains(SCROLL_BUTTON_BOUNDS[i], mouseX, mouseY)) return 5 + i;
        if (contains(ENTER_BOUNDS, mouseX, mouseY)) return 7;
        if (contains(thumbBounds(), mouseX, mouseY)) return 8;
        return -1;
    }

    private int[] thumbBounds() {
        int y = THUMB_BOUNDS[1] + (maxListOffset() == 0 ? 0 : Math.round(THUMB_TRAVEL * listOffset / (float) maxListOffset()));
        return new int[]{THUMB_BOUNDS[0], y, THUMB_BOUNDS[2], THUMB_BOUNDS[3]};
    }

    private boolean contains(int[] bounds, double mouseX, double mouseY) {
        double x = scaleX(bounds[0]);
        double y = scaleY(bounds[1]);
        return mouseX >= x && mouseX < x + scaleX(bounds[2])
                && mouseY >= y && mouseY < y + scaleY(bounds[3]);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        int control = controlAt(mouseX, mouseY);
        if (control >= 0 && control <= 2) {
            pressedControl = control;
            return true;
        }
        if (control >= 3 && control <= 6) {
            pressedControl = control;
            scrollList(control == 3 || control == 5 ? -1 : 1);
            return true;
        }
        if (control == 7) {
            pressedControl = control;
            submitInput();
            return true;
        }
        if (control == 8) {
            draggingThumb = true;
            dragStartY = mouseY;
            dragStartOffset = listOffset;
            return true;
        }
        if (control >= 10) {
            selectTab(control - 10);
            return true;
        }
        int row = rowAt(mouseY);
        if (row >= 0) {
            return true;
        }
        return inputBoundsContains(mouseX, mouseY) || super.mouseClicked(mouseX, mouseY, button);
    }

    private int rowAt(double mouseY) {
        int top = scaleY(260);
        int rowHeight = Math.max(1, scaleY(34));
        if (mouseY < top || mouseY >= scaleY(955)) return -1;
        int row = (int) ((mouseY - top) / rowHeight);
        return row < Math.min(MAX_VISIBLE_MESSAGES, messageCount() - listOffset) ? row : -1;
    }

    private boolean inputBoundsContains(double mouseX, double mouseY) {
        return mouseX >= scaleX(300) && mouseX < scaleX(1330)
                && mouseY >= scaleY(902) && mouseY < scaleY(967);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingThumb && button == 0) {
            int travel = Math.max(1, scaleY(THUMB_TRAVEL));
            int maxOffset = maxListOffset();
            listOffset = maxOffset == 0 ? 0 : Math.max(0, Math.min(maxOffset,
                    dragStartOffset + (int) Math.round((mouseY - dragStartY) * maxOffset / travel)));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            boolean handled = pressedControl >= 0 || draggingThumb;
            int released = pressedControl;
            pressedControl = -1;
            draggingThumb = false;
            if (released == 2 && contains(WINDOW_BOUNDS[2], mouseX, mouseY)) onClose();
            return handled || super.mouseReleased(mouseX, mouseY, button);
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scrollList(verticalAmount < 0 ? 1 : -1);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        StringBuilder input = drafts.get(activeTab);
        if (keyCode == 256) {
            onClose();
            return true;
        }
        if (keyCode == 257 || keyCode == 335) {
            submitInput();
            return true;
        }
        if (keyCode == 259 && !input.isEmpty()) {
            input.deleteCharAt(input.length() - 1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        StringBuilder input = drafts.get(activeTab);
        if (!Character.isISOControl(codePoint) && input.length() < 160) {
            input.append(codePoint);
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    private void submitInput() {
        StringBuilder input = drafts.get(activeTab);
        if (input.isEmpty()) return;
        conversations.get(activeTab).add(input.toString());
        input.setLength(0);
        listOffset = maxListOffset();
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public void removed() {
        if (mapConsumerAcquired && SimuMapManager.isAvailable()) {
            mapManager.releaseConsumer();
            mapConsumerAcquired = false;
        }
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
