package client.cn.nsimu.simukraft.client.mail;

import client.cn.nsimu.simukraft.client.city.map.SimuMapManager;
import client.cn.nsimu.simukraft.client.city.map.SimuMapRegion;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import common.cn.nsimu.simukraft.network.mail.MailCategory;
import common.cn.nsimu.simukraft.network.mail.MailContactData;
import common.cn.nsimu.simukraft.network.mail.MailMessageData;
import common.cn.nsimu.simukraft.network.mail.MailReplyPacket;
import common.cn.nsimu.simukraft.network.mail.MailStateService;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** 邮件界面：三个标签页、聊天气泡或邮件列表、发件方名片、发件方地图、回复输入和邮件内教程。 */
public final class MailScreen extends Screen {
    private static final ResourceLocation BACKGROUND = MailScreenLayout.texture("mail_background.png");
    private static final ResourceLocation LABEL = MailScreenLayout.texture("mail_label.png");
    private static final ResourceLocation[] TAB_ICONS = {
            MailScreenLayout.texture("mail_citizen_icon.png"), MailScreenLayout.texture("mail_system_icon.png"),
            MailScreenLayout.texture("mail_city_icon.png")
    };
    private static final MailCategory[] TAB_CATEGORIES = {MailCategory.CITIZEN, MailCategory.SYSTEM, MailCategory.CITY};
    private static final ResourceLocation ARROW_UP = MailScreenLayout.texture("mail_arrow_up.png");
    private static final ResourceLocation ARROW_DOWN = MailScreenLayout.texture("mail_arrow_down.png");
    private static final ResourceLocation ARROW_UP_PRESSED = MailScreenLayout.texture("mail_arrow_up_pressed.png");
    private static final ResourceLocation ARROW_DOWN_PRESSED = MailScreenLayout.texture("mail_arrow_down_pressed.png");
    private static final ResourceLocation SCROLL_THUMB = MailScreenLayout.texture("mail_scroll_thumb.png");
    private static final ResourceLocation SCROLL_THUMB_PRESSED = MailScreenLayout.texture("mail_scroll_thumb_pressed.png");
    private static final ResourceLocation SCROLL_UP = MailScreenLayout.texture("mail_scroll_up.png");
    private static final ResourceLocation SCROLL_DOWN = MailScreenLayout.texture("mail_scroll_down.png");
    private static final ResourceLocation SCROLL_UP_PRESSED = MailScreenLayout.texture("mail_scroll_up_pressed.png");
    private static final ResourceLocation SCROLL_DOWN_PRESSED = MailScreenLayout.texture("mail_scroll_down_pressed.png");
    private static final ResourceLocation ENTER = MailScreenLayout.texture("mail_enter.png");
    private static final ResourceLocation ENTER_PRESSED = MailScreenLayout.texture("mail_enter_pressed.png");
    private static final ResourceLocation[] WINDOW_BUTTONS = {
            MailScreenLayout.texture("mail_unpressed_minimize.png"), MailScreenLayout.texture("mail_unpressed_windowed.png"),
            MailScreenLayout.texture("mail_unpressed_close.png")
    };
    private static final ResourceLocation[] WINDOW_BUTTONS_PRESSED = {
            MailScreenLayout.texture("mail_minimize.png"), MailScreenLayout.texture("mail_windowed.png"),
            MailScreenLayout.texture("mail_close.png")
    };
    private static final double MAP_BLOCK_SCALE = 0.36D;
    private static final int TEXT_DARK = 0xFF17282B;
    private static final int TEXT_MUTED = 0xFF5B6E71;
    // 控件编号：0..2 窗口按钮，3..4 标签切换箭头，5..6 滚动按钮，7 发送，8 滑块，9 滑轨，10..12 标签页。
    private static final int CONTROL_ENTER = 7;
    private static final int CONTROL_THUMB = 8;
    private static final int CONTROL_TRACK = 9;
    private static final int CONTROL_TAB = 10;

    private final MailScreenLayout layout = new MailScreenLayout();
    private final MailContentView content = new MailContentView();
    private final Map<MailCategory, String> selectedKeys = new EnumMap<>(MailCategory.class);
    private final Map<MailCategory, StringBuilder> drafts = new EnumMap<>(MailCategory.class);
    private final SimuMapManager mapManager = SimuMapManager.getInstance();
    private MailTutorialOverlay tutorial;
    private int activeTab;
    private int pressedControl = -1;
    private boolean draggingThumb;
    private boolean mapConsumerAcquired;
    private long mapScanChunk = Long.MIN_VALUE;
    private int seenVersion;

    public MailScreen(boolean withTutorial) {
        super(Component.translatable("screen.simukraft.mail.title"));
        for (MailCategory category : MailCategory.values()) drafts.put(category, new StringBuilder());
        content.setCategory(TAB_CATEGORIES[0]);
        seenVersion = MailClientState.version();
        if (withTutorial) {
            tutorial = new MailTutorialOverlay(List.of(
                    new MailTutorialOverlay.Step("tutorial.simukraft.mail.screen.1", MailTutorialOverlay.Target.TAB_CITIZEN),
                    new MailTutorialOverlay.Step("tutorial.simukraft.mail.screen.2", MailTutorialOverlay.Target.TAB_SYSTEM),
                    new MailTutorialOverlay.Step("tutorial.simukraft.mail.screen.3", MailTutorialOverlay.Target.TAB_CITY),
                    new MailTutorialOverlay.Step("tutorial.simukraft.mail.screen.4", MailTutorialOverlay.Target.CONTENT),
                    new MailTutorialOverlay.Step("tutorial.simukraft.mail.screen.5", MailTutorialOverlay.Target.INFO_PANEL),
                    new MailTutorialOverlay.Step("tutorial.simukraft.mail.screen.6", MailTutorialOverlay.Target.INPUT),
                    new MailTutorialOverlay.Step("tutorial.simukraft.mail.screen.7", MailTutorialOverlay.Target.NONE)),
                    this::tutorialBounds);
        }
    }

    /** 邮件时间显示为游戏天数，从第 1 天开始。 */
    static Component dayLabel(long timestamp) {
        return Component.translatable("screen.simukraft.mail.day", Math.max(0L, timestamp) / 24000L + 1L);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    protected void init() {
        super.init();
        layout.resize(width, height);
        mapManager.init();
        if (!mapConsumerAcquired && SimuMapManager.isAvailable()) {
            mapManager.acquireConsumer();
            mapConsumerAcquired = true;
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (seenVersion != MailClientState.version()) {
            seenVersion = MailClientState.version();
            if (MailClientState.hasUnread()) MailClientState.requestMarkRead();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        layout.resize(width, height);
        syncTutorialTab();
        graphics.blit(BACKGROUND, 0, 0, width, height, 0.0F, 0.0F,
                MailScreenLayout.SOURCE_WIDTH, MailScreenLayout.SOURCE_HEIGHT,
                MailScreenLayout.SOURCE_WIDTH, MailScreenLayout.SOURCE_HEIGHT);
        drawWindowButtons(graphics);
        drawTabs(graphics);
        drawArrowButtons(graphics);
        content.render(graphics, font, layout, selectedKeys.get(category()));
        drawScrollbar(graphics);
        drawMap(graphics);
        drawInfoPanel(graphics);
        drawInput(graphics);
        if (tutorial != null) {
            tutorial.render(graphics, font, width, height);
        } else {
            drawTooltips(graphics, mouseX, mouseY);
        }
    }

    private MailCategory category() {
        return TAB_CATEGORIES[activeTab];
    }

    // ── 绘制 ───────────────────────────────────────────────────────────────────

    private void drawWindowButtons(GuiGraphics graphics) {
        for (int i = 0; i < WINDOW_BUTTONS.length; i++) {
            layout.drawTexture(graphics, pressedControl == i ? WINDOW_BUTTONS_PRESSED[i] : WINDOW_BUTTONS[i],
                    MailScreenLayout.WINDOW_BUTTONS[i]);
        }
    }

    private void drawTabs(GuiGraphics graphics) {
        for (int i = 0; i < MailScreenLayout.TABS.length; i++) {
            if (i == activeTab) layout.drawTexture(graphics, LABEL, MailScreenLayout.TABS[i]);
            int[] bounds = MailScreenLayout.TABS[i];
            int iconSize = 64;
            int x = bounds[0] + (bounds[2] - iconSize) / 2 + 18;
            int y = bounds[1] + (bounds[3] - iconSize) / 2;
            graphics.blit(TAB_ICONS[i], layout.x(x), layout.y(y), layout.x(x + iconSize) - layout.x(x),
                    layout.y(y + iconSize) - layout.y(y), 0.0F, 0.0F, 16, 16, 16, 16);
        }
    }

    private void drawArrowButtons(GuiGraphics graphics) {
        layout.drawTexture(graphics, pressedControl == 3 ? ARROW_UP_PRESSED : ARROW_UP, MailScreenLayout.TAB_ARROWS[0]);
        layout.drawTexture(graphics, pressedControl == 4 ? ARROW_DOWN_PRESSED : ARROW_DOWN, MailScreenLayout.TAB_ARROWS[1]);
    }

    private void drawScrollbar(GuiGraphics graphics) {
        layout.drawTexture(graphics, pressedControl == 5 ? SCROLL_UP_PRESSED : SCROLL_UP, MailScreenLayout.SCROLL_BUTTONS[0]);
        layout.drawTexture(graphics, pressedControl == 6 ? SCROLL_DOWN_PRESSED : SCROLL_DOWN, MailScreenLayout.SCROLL_BUTTONS[1]);
        layout.drawTexture(graphics, draggingThumb ? SCROLL_THUMB_PRESSED : SCROLL_THUMB, thumbSource());
    }

    private int[] thumbSource() {
        int y = MailScreenLayout.THUMB[1] + (int) Math.round(MailScreenLayout.THUMB_TRAVEL * content.scrollFraction());
        return new int[]{MailScreenLayout.THUMB[0], y, MailScreenLayout.THUMB[2], MailScreenLayout.THUMB[3]};
    }

    private void drawInfoPanel(GuiGraphics graphics) {
        int[] panel = layout.rect(MailScreenLayout.INFO);
        int x = panel[0] + 6;
        int y = panel[1] + 6;
        int innerWidth = panel[2] - 12;
        graphics.drawString(font, Component.translatable("screen.simukraft.mail.info.title").withStyle(ChatFormatting.BOLD),
                x, y, TEXT_MUTED, false);
        y += font.lineHeight + 6;
        String key = infoKey();
        MailContactData contact = MailClientState.contact(key);
        MailScreenLayout.enableScissor(graphics, panel);
        if (contact == null) {
            for (FormattedCharSequence line : font.split(Component.translatable("screen.simukraft.mail.info.none"), innerWidth)) {
                graphics.drawString(font, line, x, y, TEXT_MUTED, false);
                y += font.lineHeight + 2;
            }
            graphics.disableScissor();
            return;
        }
        int avatar = Math.max(16, Math.min(32, panel[3] / 9));
        graphics.fill(x - 1, y - 1, x + avatar + 1, y + avatar + 1, 0xFF2F4C50);
        MailContentView.drawContactAvatar(graphics, key, x, y, avatar);
        int titleX = x + avatar + 6;
        List<FormattedCharSequence> title = font.split(contact.title().copy().withStyle(ChatFormatting.BOLD),
                Math.max(20, innerWidth - avatar - 6));
        int titleY = y + Math.max(0, (avatar - title.size() * (font.lineHeight + 1)) / 2);
        for (FormattedCharSequence line : title) {
            graphics.drawString(font, line, titleX, titleY, TEXT_DARK, false);
            titleY += font.lineHeight + 1;
        }
        y += avatar + 8;
        graphics.fill(x, y - 4, x + innerWidth, y - 3, 0x5526383A);
        for (Component line : contact.lines()) {
            for (FormattedCharSequence wrapped : font.split(line, innerWidth)) {
                graphics.drawString(font, wrapped, x, y, TEXT_DARK, false);
                y += font.lineHeight + 3;
            }
        }
        graphics.disableScissor();
    }

    private void drawInput(GuiGraphics graphics) {
        int[] input = layout.rect(MailScreenLayout.INPUT);
        int textY = input[1] + (input[3] - font.lineHeight) / 2 + 1;
        if (!category().allowsReply()) {
            graphics.drawString(font, Component.translatable("screen.simukraft.mail.input.system"), input[0] + 8, textY, TEXT_MUTED, false);
            return;
        }
        layout.drawTexture(graphics, pressedControl == CONTROL_ENTER ? ENTER_PRESSED : ENTER, MailScreenLayout.ENTER);
        StringBuilder draft = drafts.get(category());
        MailScreenLayout.enableScissor(graphics, input);
        if (draft.isEmpty()) {
            String target = replyTarget();
            MailContactData contact = MailClientState.contact(target);
            Component hint = target == null
                    ? Component.translatable("screen.simukraft.mail.input.no_target")
                    : Component.translatable("screen.simukraft.mail.input.reply_to", contact == null ? Component.literal("?") : contact.title());
            graphics.drawString(font, hint, input[0] + 8, textY, TEXT_MUTED, false);
        } else {
            String text = draft.toString();
            int available = input[2] - 16;
            // 文字超出输入框时只显示末尾，光标始终可见。
            String visible = font.width(text) <= available ? text : font.plainSubstrByWidth(text, available, true);
            graphics.drawString(font, visible, input[0] + 8, textY, TEXT_DARK, false);
            if ((System.currentTimeMillis() / 500L) % 2L == 0L) {
                int caretX = input[0] + 8 + font.width(visible) + 1;
                graphics.fill(caretX, textY - 1, caretX + 1, textY + font.lineHeight, TEXT_DARK);
            }
        }
        graphics.disableScissor();
    }

    private void drawTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        for (int i = 0; i < MailScreenLayout.TABS.length; i++) {
            if (layout.contains(MailScreenLayout.TABS[i], mouseX, mouseY)) {
                String name = TAB_CATEGORIES[i].name().toLowerCase(java.util.Locale.ROOT);
                graphics.renderComponentTooltip(font, List.of(
                        Component.translatable("screen.simukraft.mail.tab." + name),
                        Component.translatable("screen.simukraft.mail.tab." + name + ".desc").withStyle(ChatFormatting.GRAY)),
                        mouseX, mouseY);
                return;
            }
        }
        if (layout.contains(MailScreenLayout.TAB_ARROWS[0], mouseX, mouseY) || layout.contains(MailScreenLayout.TAB_ARROWS[1], mouseX, mouseY)) {
            graphics.renderTooltip(font, Component.translatable("screen.simukraft.mail.tab_switch"), mouseX, mouseY);
        } else if (layout.contains(MailScreenLayout.MAP, mouseX, mouseY)) {
            graphics.renderTooltip(font, Component.translatable("screen.simukraft.mail.map.tooltip"), mouseX, mouseY);
        }
    }

    // ── 地图 ───────────────────────────────────────────────────────────────────

    private void drawMap(GuiGraphics graphics) {
        int[] map = layout.rect(MailScreenLayout.MAP);
        graphics.fill(map[0], map[1], map[0] + map[2], map[1] + map[3], 0xFF18232A);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || !SimuMapManager.isAvailable()) return;
        MailContactData contact = MailClientState.contact(infoKey());
        boolean contactHere = contact != null && contact.dimension() != null
                && contact.dimension().equals(minecraft.level.dimension().location());
        int centerX = contactHere ? contact.x() : minecraft.player.getBlockX();
        int centerZ = contactHere ? contact.z() : minecraft.player.getBlockZ();
        long chunk = ((long) (centerX >> 4) << 32) | ((centerZ >> 4) & 0xFFFFFFFFL);
        if (chunk != mapScanChunk) {
            mapScanChunk = chunk;
            mapManager.forceScanArea(centerX >> 4, centerZ >> 4, 2);
            mapManager.forceRenderAll();
        }
        // 地图按素材坐标计算缩放，窗口大小变化时比例不变。
        double sourceCenterX = MailScreenLayout.MAP[0] + MailScreenLayout.MAP[2] / 2.0D;
        double sourceCenterY = MailScreenLayout.MAP[1] + MailScreenLayout.MAP[3] / 2.0D;
        MailScreenLayout.enableScissor(graphics, map);
        for (SimuMapRegion region : mapManager.getAllRegions()) {
            if (!region.isImageLoaded() && !region.hasData()) continue;
            double regionX = sourceCenterX + (region.regionX * 512.0D - centerX) * MAP_BLOCK_SCALE;
            double regionY = sourceCenterY + (region.regionZ * 512.0D - centerZ) * MAP_BLOCK_SCALE;
            double size = 512.0D * MAP_BLOCK_SCALE;
            if (regionX + size < MailScreenLayout.MAP[0] || regionX > MailScreenLayout.MAP[0] + MailScreenLayout.MAP[2]
                    || regionY + size < MailScreenLayout.MAP[1] || regionY > MailScreenLayout.MAP[1] + MailScreenLayout.MAP[3]) {
                continue;
            }
            drawMapRegion(graphics, region.getTextureId(), regionX, regionY, size);
        }
        graphics.disableScissor();
        int markerX = layout.x((int) Math.round(sourceCenterX));
        int markerY = layout.y((int) Math.round(sourceCenterY));
        graphics.fill(markerX - 3, markerY - 3, markerX + 3, markerY + 3, 0xFF202020);
        graphics.fill(markerX - 2, markerY - 2, markerX + 2, markerY + 2, contactHere ? 0xFFFFD400 : 0xFF6FD0FF);
        Component caption = contactHere
                ? Component.translatable("screen.simukraft.mail.map.contact", centerX, centerZ)
                : Component.translatable("screen.simukraft.mail.map.self");
        graphics.drawString(font, caption, map[0] + 3, map[1] + map[3] - font.lineHeight - 2, 0xFFE6F2F2, true);
    }

    private void drawMapRegion(GuiGraphics graphics, int textureId, double x, double y, double size) {
        if (textureId == -1) return;
        RenderSystem.setShaderTexture(0, textureId);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.enableBlend();
        Matrix4f matrix = graphics.pose().last().pose();
        float x0 = layout.x((int) Math.round(x));
        float y0 = layout.y((int) Math.round(y));
        float x1 = layout.x((int) Math.round(x + size));
        float y1 = layout.y((int) Math.round(y + size));
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.addVertex(matrix, x0, y1, 0).setUv(0, 1);
        buffer.addVertex(matrix, x1, y1, 0).setUv(1, 1);
        buffer.addVertex(matrix, x1, y0, 0).setUv(1, 0);
        buffer.addVertex(matrix, x0, y0, 0).setUv(0, 0);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.disableBlend();
    }

    // ── 选择与回复对象 ─────────────────────────────────────────────────────────

    /** 右侧名片显示的对象：先看玩家点选的，再看最近来信，最后退回本城。 */
    private String infoKey() {
        String selected = selectedKeys.get(category());
        if (selected != null) return selected;
        if (category() == MailCategory.SYSTEM) {
            MailMessageData mail = content.selectedSystemMail();
            if (mail != null) return mail.senderKey();
        }
        MailMessageData latest = content.latestIncoming();
        if (latest != null) return latest.senderKey();
        String own = MailClientState.ownContactKey();
        return own.isEmpty() ? null : own;
    }

    /** 当前标签的回复对象；没有可回复对象时返回 null，最终仍由服务端校验。 */
    private String replyTarget() {
        MailCategory category = category();
        String selected = selectedKeys.get(category);
        if (matchesCategory(category, selected)) return selected;
        MailMessageData latest = content.latestIncoming();
        if (latest != null && matchesCategory(category, latest.senderKey())) return latest.senderKey();
        String own = MailClientState.ownContactKey();
        return category == MailCategory.CITY && !own.isEmpty() ? own : null;
    }

    private static boolean matchesCategory(MailCategory category, String key) {
        if (key == null) return false;
        return category == MailCategory.CITIZEN ? key.startsWith(MailCategory.CITIZEN_PREFIX)
                : category == MailCategory.CITY && key.startsWith(MailCategory.CITY_PREFIX);
    }

    private void selectTab(int tab) {
        int next = Math.floorMod(tab, TAB_CATEGORIES.length);
        if (next == activeTab) return;
        activeTab = next;
        content.setCategory(category());
    }

    private void submitInput() {
        MailCategory category = category();
        StringBuilder draft = drafts.get(category);
        String target = replyTarget();
        if (!category.allowsReply() || draft.isEmpty() || target == null) return;
        PacketDistributor.sendToServer(new MailReplyPacket(category, target, draft.toString()));
        draft.setLength(0);
        content.scrollContent(Double.MAX_VALUE);
    }

    // ── 教程 ───────────────────────────────────────────────────────────────────

    private void syncTutorialTab() {
        if (tutorial == null) return;
        switch (tutorial.currentTarget()) {
            case TAB_CITIZEN -> selectTab(0);
            case TAB_SYSTEM -> selectTab(1);
            case TAB_CITY -> selectTab(2);
            case CONTENT, INFO_PANEL, INPUT -> selectTab(0);
            default -> {
            }
        }
    }

    private int[] tutorialBounds(MailTutorialOverlay.Target target) {
        return switch (target) {
            case TAB_CITIZEN -> layout.rect(MailScreenLayout.TABS[0]);
            case TAB_SYSTEM -> layout.rect(MailScreenLayout.TABS[1]);
            case TAB_CITY -> layout.rect(MailScreenLayout.TABS[2]);
            case CONTENT -> layout.rect(MailScreenLayout.CONTENT);
            case INFO_PANEL -> layout.rect(new int[]{MailScreenLayout.MAP[0], MailScreenLayout.MAP[1],
                    MailScreenLayout.MAP[2], MailScreenLayout.INFO[1] + MailScreenLayout.INFO[3] - MailScreenLayout.MAP[1]});
            case INPUT -> layout.rect(new int[]{MailScreenLayout.INPUT[0], MailScreenLayout.ENTER[1],
                    MailScreenLayout.ENTER[0] + MailScreenLayout.ENTER[2] - MailScreenLayout.INPUT[0], MailScreenLayout.ENTER[3]});
            default -> null;
        };
    }

    private void endTutorial() {
        tutorial = null;
        MailClientState.finishTutorial();
    }

    // ── 输入 ───────────────────────────────────────────────────────────────────

    private int controlAt(double mouseX, double mouseY) {
        for (int i = 0; i < MailScreenLayout.WINDOW_BUTTONS.length; i++) {
            if (layout.contains(MailScreenLayout.WINDOW_BUTTONS[i], mouseX, mouseY)) return i;
        }
        for (int i = 0; i < MailScreenLayout.TABS.length; i++) {
            if (layout.contains(MailScreenLayout.TABS[i], mouseX, mouseY)) return CONTROL_TAB + i;
        }
        for (int i = 0; i < MailScreenLayout.TAB_ARROWS.length; i++) {
            if (layout.contains(MailScreenLayout.TAB_ARROWS[i], mouseX, mouseY)) return 3 + i;
        }
        for (int i = 0; i < MailScreenLayout.SCROLL_BUTTONS.length; i++) {
            if (layout.contains(MailScreenLayout.SCROLL_BUTTONS[i], mouseX, mouseY)) return 5 + i;
        }
        if (category().allowsReply() && layout.contains(MailScreenLayout.ENTER, mouseX, mouseY)) return CONTROL_ENTER;
        if (layout.contains(thumbSource(), mouseX, mouseY)) return CONTROL_THUMB;
        if (layout.contains(MailScreenLayout.SCROLL_TRACK, mouseX, mouseY)) return CONTROL_TRACK;
        return -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (tutorial != null) {
            if (button == 1 || tutorial.advance()) endTutorial();
            return true;
        }
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        int control = controlAt(mouseX, mouseY);
        if (control >= 0 && control <= 2) {
            pressedControl = control;
            return true;
        }
        if (control == 3 || control == 4) {
            pressedControl = control;
            selectTab(activeTab + (control == 3 ? -1 : 1));
            return true;
        }
        if (control == 5 || control == 6) {
            pressedControl = control;
            content.scrollContent((control == 5 ? -1 : 1) * (font.lineHeight + 3) * 3.0D);
            return true;
        }
        if (control == CONTROL_ENTER) {
            pressedControl = control;
            submitInput();
            return true;
        }
        if (control == CONTROL_THUMB || control == CONTROL_TRACK) {
            draggingThumb = true;
            dragThumbTo(mouseY);
            return true;
        }
        if (control >= CONTROL_TAB) {
            selectTab(control - CONTROL_TAB);
            return true;
        }
        String key = content.click(font, layout, mouseX, mouseY);
        if (key != null) {
            selectedKeys.put(category(), key);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void dragThumbTo(double mouseY) {
        double trackStart = layout.y(MailScreenLayout.THUMB[1] + MailScreenLayout.THUMB[3] / 2);
        double travel = Math.max(1.0D, layout.y(MailScreenLayout.THUMB[1] + MailScreenLayout.THUMB_TRAVEL) - layout.y(MailScreenLayout.THUMB[1]));
        content.setScrollFraction(Math.max(0.0D, Math.min(1.0D, (mouseY - trackStart) / travel)));
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingThumb && button == 0) {
            dragThumbTo(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseReleased(mouseX, mouseY, button);
        boolean handled = pressedControl >= 0 || draggingThumb;
        int released = pressedControl;
        pressedControl = -1;
        draggingThumb = false;
        // 最小化和关闭都退出界面；窗口化按钮只有按下反馈。
        if ((released == 0 || released == 2) && layout.contains(MailScreenLayout.WINDOW_BUTTONS[released], mouseX, mouseY)) {
            onClose();
        }
        return handled || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (tutorial == null) content.scroll(font, layout, mouseX, mouseY, verticalAmount);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (tutorial != null) {
            if (keyCode == 256) {
                endTutorial();
            } else if ((keyCode == 32 || keyCode == 257 || keyCode == 335) && tutorial.advance()) {
                endTutorial();
            }
            return true;
        }
        StringBuilder draft = drafts.get(category());
        if (keyCode == 256) {
            onClose();
            return true;
        }
        if (keyCode == 257 || keyCode == 335) {
            submitInput();
            return true;
        }
        if (keyCode == 259 && !draft.isEmpty()) {
            draft.setLength(draft.offsetByCodePoints(draft.length(), -1));
            return true;
        }
        if (keyCode == 266 || keyCode == 267) {
            content.scrollContent((keyCode == 266 ? -1 : 1) * content.pageSize());
            return true;
        }
        if (Screen.isPaste(keyCode) && category().allowsReply()) {
            append(Minecraft.getInstance().keyboardHandler.getClipboard());
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (tutorial != null || !category().allowsReply()) return false;
        if (!Character.isISOControl(codePoint)) {
            append(String.valueOf(codePoint));
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    private void append(String text) {
        StringBuilder draft = drafts.get(category());
        text.codePoints().filter(c -> !Character.isISOControl(c) && c != '§').forEach(c -> {
            if (draft.codePointCount(0, draft.length()) < MailStateService.MAX_REPLY_LENGTH) draft.appendCodePoint(c);
        });
    }

    @Override
    public void onClose() {
        if (tutorial != null) endTutorial();
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
