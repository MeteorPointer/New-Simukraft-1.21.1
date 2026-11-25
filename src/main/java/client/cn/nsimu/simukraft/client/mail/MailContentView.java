package client.cn.nsimu.simukraft.client.mail;

import client.cn.nsimu.simukraft.client.citizen.CitizenAvatarFactory;
import common.cn.nsimu.simukraft.network.mail.MailCategory;
import common.cn.nsimu.simukraft.network.mail.MailContactData;
import common.cn.nsimu.simukraft.network.mail.MailMessageData;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 邮件正文区域：市民和城市标签是类似聊天软件的气泡对话，系统标签是左侧列表加右侧正文。
 *
 * <p>布局只在数据版本或窗口尺寸变化时重算，渲染时复用。
 */
final class MailContentView {
    private static final ResourceLocation CHAT_BUBBLE = MailScreenLayout.texture("chattxt.png");
    private static final ResourceLocation SYSTEM_ICON = MailScreenLayout.texture("mail_system_icon.png");
    private static final ResourceLocation CITY_ICON = MailScreenLayout.texture("mail_city_icon.png");
    private static final int BUBBLE_TEXTURE_SIZE = 128;
    // 贴图里绿色气泡占 x 3..64，灰色气泡占 x 65..126，两者高度都是 0..62。
    private static final int OUTGOING_U = 3;
    private static final int INCOMING_U = 65;
    private static final int BUBBLE_REGION_WIDTH = 62;
    private static final int BUBBLE_REGION_HEIGHT = 63;
    private static final int BUBBLE_SOURCE_BORDER = 8;
    private static final int BUBBLE_BORDER = 4;
    private static final int PADDING = 6;
    private static final int GAP = 8;
    private static final int TEXT_DARK = 0xFF17282B;
    private static final int TEXT_MUTED = 0xFF5B6E71;

    private final List<MailMessageData> messages = new ArrayList<>();
    private final List<Bubble> bubbles = new ArrayList<>();
    private MailCategory category = MailCategory.CITIZEN;
    private int layoutVersion = -1;
    private int layoutWidth = -1;
    private int layoutHeight = -1;
    private int contentHeight;
    private double contentScroll;
    private double listScroll;
    private int selectedMail;
    private boolean stickToBottom = true;

    private record Bubble(MailMessageData message, int x, int y, int width, int height,
                          List<FormattedCharSequence> lines, int avatarX, int avatarY) {
    }

    void setCategory(MailCategory category) {
        this.category = category;
        contentScroll = 0.0D;
        listScroll = 0.0D;
        selectedMail = 0;
        stickToBottom = category != MailCategory.SYSTEM;
        layoutVersion = -1;
    }

    MailCategory category() {
        return category;
    }

    List<MailMessageData> messages() {
        return messages;
    }

    MailMessageData selectedSystemMail() {
        return category == MailCategory.SYSTEM && !messages.isEmpty() ? messages.get(Math.min(selectedMail, messages.size() - 1)) : null;
    }

    /** 最近一封别人发来的消息，用作默认回复对象。 */
    MailMessageData latestIncoming() {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (!messages.get(i).outgoing()) return messages.get(i);
        }
        return null;
    }

    // ── 布局 ───────────────────────────────────────────────────────────────────

    void layout(Font font, MailScreenLayout layout) {
        int[] area = contentRect(layout);
        if (layoutVersion == MailClientState.version() && layoutWidth == area[2] && layoutHeight == area[3]) return;
        boolean firstLayout = layoutVersion < 0;
        layoutVersion = MailClientState.version();
        layoutWidth = area[2];
        layoutHeight = area[3];
        messages.clear();
        for (MailMessageData message : MailClientState.messages()) {
            if (message.category() == category) messages.add(message);
        }
        if (category == MailCategory.SYSTEM) {
            messages.sort(Comparator.comparingLong(MailMessageData::id).reversed());
            selectedMail = Math.min(selectedMail, Math.max(0, messages.size() - 1));
            contentHeight = systemBodyHeight(font, layout);
        } else {
            messages.sort(Comparator.comparingLong(MailMessageData::id));
            layoutBubbles(font, area);
            if (firstLayout || stickToBottom) contentScroll = maxContentScroll();
        }
        contentScroll = clamp(contentScroll, maxContentScroll());
    }

    private void layoutBubbles(Font font, int[] area) {
        bubbles.clear();
        int avatar = avatarSize(area);
        int maxText = Math.max(60, Math.round(area[2] * 0.58F));
        int y = PADDING;
        for (MailMessageData message : messages) {
            List<FormattedCharSequence> lines = new ArrayList<>();
            if (!message.subject().getString().isEmpty()) {
                lines.addAll(font.split(message.subject().copy().withStyle(ChatFormatting.BOLD), maxText));
            }
            lines.addAll(font.split(message.body(), maxText));
            if (lines.isEmpty()) lines.add(FormattedCharSequence.EMPTY);
            int textWidth = 0;
            for (FormattedCharSequence line : lines) textWidth = Math.max(textWidth, font.width(line));
            int width = textWidth + PADDING * 2 + 2;
            int height = lines.size() * (font.lineHeight + 2) + PADDING * 2 - 2;
            int headerHeight = font.lineHeight + 3;
            int bubbleY = y + headerHeight;
            int avatarX = message.outgoing() ? area[2] - PADDING - avatar : PADDING;
            int bubbleX = message.outgoing() ? avatarX - GAP - width : avatarX + avatar + GAP;
            bubbles.add(new Bubble(message, bubbleX, bubbleY, width, height, lines, avatarX, y));
            y = bubbleY + Math.max(height, avatar) + GAP * 2;
        }
        contentHeight = y;
    }

    private int systemBodyHeight(Font font, MailScreenLayout layout) {
        MailMessageData mail = selectedSystemMail();
        if (mail == null) return 0;
        int[] detail = layout.rect(MailScreenLayout.SYSTEM_DETAIL);
        int lines = font.split(mail.body(), Math.max(40, detail[2] - PADDING * 2)).size();
        return headerBlockHeight(font) + lines * (font.lineHeight + 3) + PADDING * 2;
    }

    private static int headerBlockHeight(Font font) {
        return PADDING + (font.lineHeight + 4) * 3 + 6;
    }

    // ── 渲染 ───────────────────────────────────────────────────────────────────

    void render(GuiGraphics graphics, Font font, MailScreenLayout layout, String selectedKey) {
        layout(font, layout);
        if (messages.isEmpty()) {
            int[] area = layout.rect(MailScreenLayout.CONTENT);
            graphics.drawCenteredString(font, Component.translatable("screen.simukraft.mail.empty." + category.name().toLowerCase(java.util.Locale.ROOT)),
                    area[0] + area[2] / 2, area[1] + area[3] / 2 - font.lineHeight / 2, TEXT_MUTED);
            return;
        }
        if (category == MailCategory.SYSTEM) {
            renderSystemList(graphics, font, layout);
            renderSystemDetail(graphics, font, layout);
        } else {
            renderChat(graphics, font, layout, selectedKey);
        }
    }

    private void renderChat(GuiGraphics graphics, Font font, MailScreenLayout layout, String selectedKey) {
        int[] area = contentRect(layout);
        int avatar = avatarSize(area);
        int offsetY = area[1] - (int) Math.round(contentScroll);
        MailScreenLayout.enableScissor(graphics, area);
        for (Bubble bubble : bubbles) {
            int top = offsetY + bubble.avatarY();
            if (top > area[1] + area[3] || offsetY + bubble.y() + bubble.height() < area[1]) continue;
            MailMessageData message = bubble.message();
            boolean selected = message.senderKey().equals(selectedKey);
            drawHeader(graphics, font, message, area[0], top, bubble, area);
            drawAvatar(graphics, message, area[0] + bubble.avatarX(), top, avatar, selected);
            int bx = area[0] + bubble.x();
            int by = offsetY + bubble.y();
            drawBubble(graphics, bx, by, bubble.width(), bubble.height(), message.outgoing());
            // 绿色气泡用深色字，灰色气泡用带阴影的白字，两种底色上都清楚。
            int color = message.outgoing() ? TEXT_DARK : 0xFFFFFFFF;
            for (int i = 0; i < bubble.lines().size(); i++) {
                graphics.drawString(font, bubble.lines().get(i), bx + PADDING + 1,
                        by + PADDING + i * (font.lineHeight + 2), color, !message.outgoing());
            }
        }
        graphics.disableScissor();
    }

    private void drawHeader(GuiGraphics graphics, Font font, MailMessageData message, int areaX, int top, Bubble bubble, int[] area) {
        Component name = message.outgoing() ? Component.translatable("screen.simukraft.mail.me") : message.sender();
        Component header = Component.empty();
        if (message.broadcast()) header = header.copy().append(Component.translatable("screen.simukraft.mail.broadcast_tag")).append(" ");
        header = header.copy().append(name).append(Component.literal("  ")).append(MailScreen.dayLabel(message.timestamp()));
        int width = font.width(header);
        int x = message.outgoing() ? areaX + bubble.x() + bubble.width() - width : areaX + bubble.x();
        graphics.drawString(font, header, Math.max(area[0] + 2, x), top, TEXT_MUTED, false);
    }

    private static void drawAvatar(GuiGraphics graphics, MailMessageData message, int x, int y, int size, boolean selected) {
        graphics.fill(x - 1, y - 1, x + size + 1, y + size + 1, selected ? 0xFFFFD34D : 0xFF2F4C50);
        if (message.outgoing()) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                PlayerFaceRenderer.draw(graphics, minecraft.player.getSkin(), x, y, size);
                return;
            }
        }
        drawContactAvatar(graphics, message.senderKey(), x, y, size);
    }

    /** 按名片 key 画头像：市民画皮肤脸，城市和系统画图标。 */
    static void drawContactAvatar(GuiGraphics graphics, String key, int x, int y, int size) {
        if (key != null && key.startsWith(MailCategory.CITIZEN_PREFIX)) {
            MailContactData contact = MailClientState.contact(key);
            CitizenAvatarFactory.blitHead(graphics, contact == null ? "" : contact.skinPath(), x, y, size, false);
            return;
        }
        graphics.fill(x, y, x + size, y + size, 0xFFDDF4F4);
        ResourceLocation icon = key != null && key.startsWith(MailCategory.CITY_PREFIX) ? CITY_ICON : SYSTEM_ICON;
        int inset = Math.max(1, size / 8);
        graphics.blit(icon, x + inset, y + inset, size - inset * 2, size - inset * 2, 0.0F, 0.0F, 16, 16, 16, 16);
    }

    /** 九宫格绘制气泡：四角和边框保持原始粗细，只拉伸中间，避免整张贴图被拉变形。 */
    private static void drawBubble(GuiGraphics graphics, int x, int y, int width, int height, boolean outgoing) {
        int u = outgoing ? OUTGOING_U : INCOMING_U;
        int sb = BUBBLE_SOURCE_BORDER;
        int b = BUBBLE_BORDER;
        int midW = BUBBLE_REGION_WIDTH - sb * 2;
        int midH = BUBBLE_REGION_HEIGHT - sb * 2;
        int innerW = Math.max(0, width - b * 2);
        int innerH = Math.max(0, height - b * 2);
        int[][] columns = {{x, b, u, sb}, {x + b, innerW, u + sb, midW}, {x + b + innerW, b, u + BUBBLE_REGION_WIDTH - sb, sb}};
        int[][] rows = {{y, b, 0, sb}, {y + b, innerH, sb, midH}, {y + b + innerH, b, BUBBLE_REGION_HEIGHT - sb, sb}};
        for (int[] row : rows) {
            for (int[] column : columns) {
                if (column[1] <= 0 || row[1] <= 0) continue;
                graphics.blit(CHAT_BUBBLE, column[0], row[0], column[1], row[1], column[2], row[2], column[3], row[3],
                        BUBBLE_TEXTURE_SIZE, BUBBLE_TEXTURE_SIZE);
            }
        }
    }

    private void renderSystemList(GuiGraphics graphics, Font font, MailScreenLayout layout) {
        int[] list = layout.rect(MailScreenLayout.SYSTEM_LIST);
        int rowHeight = rowHeight(font);
        listScroll = clamp(listScroll, Math.max(0, messages.size() * rowHeight - list[3]));
        MailScreenLayout.enableScissor(graphics, list);
        int textWidth = list[2] - PADDING * 2 - 8;
        for (int i = 0; i < messages.size(); i++) {
            int y = list[1] + i * rowHeight - (int) Math.round(listScroll);
            if (y + rowHeight < list[1] || y > list[1] + list[3]) continue;
            MailMessageData mail = messages.get(i);
            if (i == selectedMail) graphics.fill(list[0], y, list[0] + list[2], y + rowHeight - 1, 0x402D8C8C);
            graphics.fill(list[0] + 4, y + rowHeight - 1, list[0] + list[2] - 4, y + rowHeight, 0x3326383A);
            if (mail.unread()) graphics.fill(list[0] + 3, y + PADDING + 2, list[0] + 6, y + PADDING + 5, 0xFFE05A4F);
            Component subject = mail.unread() ? mail.subject().copy().withStyle(ChatFormatting.BOLD) : mail.subject();
            graphics.drawString(font, truncate(font, subject, textWidth), list[0] + PADDING + 4, y + PADDING, TEXT_DARK, false);
            Component meta = mail.sender().copy().append("  ").append(MailScreen.dayLabel(mail.timestamp()));
            graphics.drawString(font, truncate(font, meta, textWidth), list[0] + PADDING + 4,
                    y + PADDING + font.lineHeight + 3, TEXT_MUTED, false);
        }
        graphics.disableScissor();
        graphics.fill(list[0] + list[2] + 8, list[1] + 4, list[0] + list[2] + 9, list[1] + list[3] - 4, 0x5526383A);
    }

    private void renderSystemDetail(GuiGraphics graphics, Font font, MailScreenLayout layout) {
        MailMessageData mail = selectedSystemMail();
        if (mail == null) return;
        int[] detail = layout.rect(MailScreenLayout.SYSTEM_DETAIL);
        int x = detail[0] + PADDING;
        int width = Math.max(40, detail[2] - PADDING * 2);
        int y = detail[1] + PADDING - (int) Math.round(contentScroll);
        MailScreenLayout.enableScissor(graphics, detail);
        graphics.drawString(font, truncate(font, mail.subject().copy().withStyle(ChatFormatting.BOLD), width), x, y, TEXT_DARK, false);
        y += font.lineHeight + 4;
        graphics.drawString(font, truncate(font, Component.translatable("screen.simukraft.mail.from", mail.sender()), width), x, y, TEXT_MUTED, false);
        y += font.lineHeight + 4;
        graphics.drawString(font, MailScreen.dayLabel(mail.timestamp()), x, y, TEXT_MUTED, false);
        y += font.lineHeight + 4;
        graphics.fill(x, y, x + width, y + 1, 0x5526383A);
        y += 6;
        for (FormattedCharSequence line : font.split(mail.body(), width)) {
            graphics.drawString(font, line, x, y, TEXT_DARK, false);
            y += font.lineHeight + 3;
        }
        graphics.disableScissor();
    }

    private static FormattedCharSequence truncate(Font font, Component text, int width) {
        if (font.width(text) <= width) return text.getVisualOrderText();
        Component ellipsis = Component.literal("...");
        String clipped = font.substrByWidth(text, Math.max(1, width - font.width(ellipsis))).getString();
        return Component.literal(clipped).withStyle(text.getStyle()).append(ellipsis).getVisualOrderText();
    }

    // ── 交互 ───────────────────────────────────────────────────────────────────

    /** 点击气泡返回其名片 key；点击系统邮件行切换正文并返回 "system"；未命中返回 null。 */
    String click(Font font, MailScreenLayout layout, double mouseX, double mouseY) {
        if (category == MailCategory.SYSTEM) {
            int[] list = layout.rect(MailScreenLayout.SYSTEM_LIST);
            if (!MailScreenLayout.inside(list, mouseX, mouseY)) return null;
            int row = (int) ((mouseY - list[1] + listScroll) / rowHeight(font));
            if (row < 0 || row >= messages.size()) return null;
            selectedMail = row;
            contentScroll = 0.0D;
            layoutVersion = -1;
            return messages.get(row).senderKey();
        }
        int[] area = contentRect(layout);
        if (!MailScreenLayout.inside(area, mouseX, mouseY)) return null;
        int avatar = avatarSize(area);
        double localY = mouseY - area[1] + contentScroll;
        double localX = mouseX - area[0];
        for (Bubble bubble : bubbles) {
            boolean inBubble = localX >= bubble.x() && localX < bubble.x() + bubble.width()
                    && localY >= bubble.avatarY() && localY < bubble.y() + bubble.height();
            boolean inAvatar = localX >= bubble.avatarX() && localX < bubble.avatarX() + avatar
                    && localY >= bubble.avatarY() && localY < bubble.avatarY() + avatar;
            if (inBubble || inAvatar) return bubble.message().senderKey();
        }
        return null;
    }

    /** 鼠标滚轮：系统标签在列表上滚动列表，其余位置滚动正文。 */
    void scroll(Font font, MailScreenLayout layout, double mouseX, double mouseY, double amount) {
        if (category == MailCategory.SYSTEM && layout.contains(MailScreenLayout.SYSTEM_LIST, mouseX, mouseY)) {
            int[] list = layout.rect(MailScreenLayout.SYSTEM_LIST);
            listScroll = clamp(listScroll - amount * rowHeight(font), Math.max(0, messages.size() * rowHeight(font) - list[3]));
            return;
        }
        scrollContent(-amount * (font.lineHeight + 3) * 3);
    }

    void scrollContent(double delta) {
        contentScroll = clamp(contentScroll + delta, maxContentScroll());
        stickToBottom = category != MailCategory.SYSTEM && contentScroll >= maxContentScroll() - 1.0D;
    }

    double scrollFraction() {
        int max = maxContentScroll();
        return max <= 0 ? 0.0D : contentScroll / max;
    }

    void setScrollFraction(double fraction) {
        contentScroll = clamp(fraction * maxContentScroll(), maxContentScroll());
        stickToBottom = category != MailCategory.SYSTEM && contentScroll >= maxContentScroll() - 1.0D;
    }

    double pageSize() {
        return Math.max(1, layoutHeight) * 0.8D;
    }

    private int maxContentScroll() {
        return Math.max(0, contentHeight - layoutHeight);
    }

    private int[] contentRect(MailScreenLayout layout) {
        return layout.rect(category == MailCategory.SYSTEM ? MailScreenLayout.SYSTEM_DETAIL : MailScreenLayout.CONTENT);
    }

    private static int avatarSize(int[] area) {
        return Math.max(14, Math.min(24, area[3] / 12));
    }

    private static int rowHeight(Font font) {
        return font.lineHeight * 2 + PADDING * 2 + 3;
    }

    private static double clamp(double value, double max) {
        return Math.max(0.0D, Math.min(max, value));
    }
}
