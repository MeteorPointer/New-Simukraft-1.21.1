package client.cn.nsimu.simukraft.client.mail;

import client.cn.nsimu.simukraft.client.input.SimuKraftKeyMappings;
import common.cn.nsimu.simukraft.SimuKraft;
import common.cn.nsimu.simukraft.registry.ModSoundEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 邮件教程层：聚光灯遮罩、艾莉丝立绘张嘴动画、逐字对白和说话音。
 *
 * <p>遮罩按真实像素逐行绘制，目标处的圆角矩形不画任何像素，露出下面的界面；洞外两圈半透明过渡做柔边。
 * 宿主界面负责把 {@link Target} 换算成 GUI 坐标矩形，并转发点击和按键。
 */
final class MailTutorialOverlay {
    private static final ResourceLocation[] MOUTH_FRAMES = {
            texture("alice_mouth_closed.png"), texture("alice_mouth_half.png"),
            texture("alice_mouth_open.png"), texture("alice_mouth_half.png")
    };
    private static final int PORTRAIT_TEXTURE_WIDTH = 256;
    private static final int PORTRAIT_TEXTURE_HEIGHT = 320;
    private static final int MASK_ALPHA = 0xB4;
    private static final long CHAR_MILLIS = 38L;
    private static final long PAUSE_MILLIS = 220L;
    private static final long MOUTH_FRAME_MILLIS = 85L;
    private static final int BOX_HEIGHT = 66;
    private static final int TEXT_COLOR = 0xFFF2FBFB;

    /** 聚光灯对准的界面区域。 */
    enum Target {
        NONE, HUD_ICON, ROW_CITIZEN, ROW_SYSTEM, ROW_CITY, CONTENT, INFO_PANEL, INPUT
    }

    record Step(String textKey, Target target) {
    }

    /** 由宿主把目标换算成 GUI 坐标 {x, y, w, h}；返回 null 表示不挖洞。 */
    interface TargetResolver {
        int[] bounds(Target target);
    }

    private final List<Step> steps;
    private final TargetResolver resolver;
    private int index;
    private long stepStartedAt = -1L;
    private String text = "";
    private long[] revealTimes = new long[0];
    private int soundedChars;

    MailTutorialOverlay(List<Step> steps, TargetResolver resolver) {
        this.steps = List.copyOf(steps);
        this.resolver = resolver;
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "textures/gui/tutorial/" + name);
    }

    boolean finished() {
        return index >= steps.size();
    }

    Target currentTarget() {
        return finished() ? Target.NONE : steps.get(index).target();
    }

    /** 点击或按键：正在打字时先显示整句，否则进入下一步。返回 true 表示全部步骤结束。 */
    boolean advance() {
        if (finished()) return true;
        long now = System.currentTimeMillis();
        if (visibleChars(now) < text.length()) {
            stepStartedAt = now - revealTimes[revealTimes.length - 1] - 1L;
            soundedChars = text.length();
            return false;
        }
        index++;
        stepStartedAt = -1L;
        return finished();
    }

    void render(GuiGraphics graphics, Font font, int width, int height) {
        if (finished()) return;
        long now = System.currentTimeMillis();
        if (stepStartedAt < 0L) startStep(now);
        int[] hole = resolver.bounds(currentTarget());
        drawMask(graphics, width, height, hole, now);
        int visible = visibleChars(now);
        playVoice(visible);
        boolean boxAtTop = hole != null && hole[1] + hole[3] / 2 > height * 0.55F;
        drawPortraitAndBox(graphics, font, width, height, boxAtTop, visible, now);
    }

    private void startStep(long now) {
        stepStartedAt = now;
        Component keyName = SimuKraftKeyMappings.MAIL_OPEN.getTranslatedKeyMessage();
        text = Component.translatable(steps.get(index).textKey(), keyName).getString();
        revealTimes = new long[text.length()];
        long time = 0L;
        for (int i = 0; i < text.length(); i++) {
            time += CHAR_MILLIS;
            revealTimes[i] = time;
            if (isPause(text.charAt(i))) time += PAUSE_MILLIS;
        }
        soundedChars = 0;
    }

    private int visibleChars(long now) {
        if (stepStartedAt < 0L) return 0;
        long elapsed = now - stepStartedAt;
        int count = 0;
        while (count < revealTimes.length && revealTimes[count] <= elapsed) count++;
        return count;
    }

    /** 每露出两个可发音字符播放一次说话音，标点和空白不发音。 */
    private void playVoice(int visible) {
        while (soundedChars < visible) {
            char c = text.charAt(soundedChars);
            soundedChars++;
            if (Character.isWhitespace(c) || isPause(c) || soundedChars % 2 != 0) continue;
            float pitch = 1.15F + ThreadLocalRandom.current().nextFloat() * 0.25F;
            Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(ModSoundEvents.MAIL_TUTORIAL_VOICE.get(), pitch, 0.6F));
        }
    }

    private boolean speaking(long now) {
        int visible = visibleChars(now);
        if (visible >= text.length()) return false;
        // 停在标点上时闭嘴，模拟换气。
        return visible == 0 || !isPause(text.charAt(visible - 1));
    }

    private static boolean isPause(char c) {
        return "，。！？、；：,.!?;:…—".indexOf(c) >= 0;
    }

    // ── 聚光灯遮罩 ─────────────────────────────────────────────────────────────

    private static void drawMask(GuiGraphics graphics, int width, int height, int[] hole, long now) {
        double scale = Minecraft.getInstance().getWindow().getGuiScale();
        int pixelWidth = (int) Math.ceil(width * scale);
        int pixelHeight = (int) Math.ceil(height * scale);
        int full = MASK_ALPHA << 24;
        graphics.pose().pushPose();
        graphics.pose().scale((float) (1.0D / scale), (float) (1.0D / scale), 1.0F);
        if (hole == null) {
            graphics.fill(0, 0, pixelWidth, pixelHeight, full);
            graphics.pose().popPose();
            return;
        }
        // 洞比目标外扩几像素并轻微呼吸；小目标是胶囊形，大区域是圆角矩形。
        double padding = (3.0D + 1.5D * (1.0D + Math.sin(now / 260.0D))) * scale;
        double cx = (hole[0] + hole[2] / 2.0D) * scale;
        double cy = (hole[1] + hole[3] / 2.0D) * scale;
        double halfW = hole[2] / 2.0D * scale + padding;
        double halfH = hole[3] / 2.0D * scale + padding;
        double radius = Math.min(Math.min(halfW, halfH), 14.0D * scale);
        double feather = 4.0D * scale;
        double[][] rings = {
                {halfW + feather * 2.0D, halfH + feather * 2.0D, radius + feather * 2.0D},
                {halfW + feather, halfH + feather, radius + feather},
                {halfW, halfH, radius}};
        int[] colors = {full, (MASK_ALPHA * 2 / 3) << 24, (MASK_ALPHA / 3) << 24};
        int top = (int) Math.floor(cy - rings[0][1]);
        int bottom = (int) Math.ceil(cy + rings[0][1]);
        graphics.fill(0, 0, pixelWidth, Math.max(0, top), full);
        graphics.fill(0, Math.min(pixelHeight, bottom), pixelWidth, pixelHeight, full);
        for (int y = Math.max(0, top); y < Math.min(pixelHeight, bottom); y++) {
            double dy = y + 0.5D - cy;
            int left = 0;
            int right = pixelWidth;
            for (int ring = 0; ring < rings.length; ring++) {
                double halfWidth = halfWidth(rings[ring][0], rings[ring][1], rings[ring][2], dy);
                if (halfWidth < 0.0D) {
                    graphics.fill(left, y, right, y + 1, colors[ring]);
                    left = right;
                    break;
                }
                int innerLeft = (int) Math.round(cx - halfWidth);
                int innerRight = (int) Math.round(cx + halfWidth);
                graphics.fill(left, y, Math.max(left, innerLeft), y + 1, colors[ring]);
                graphics.fill(Math.min(right, innerRight), y, right, y + 1, colors[ring]);
                left = Math.max(left, innerLeft);
                right = Math.min(right, innerRight);
            }
            // 最内圈以内保持透明，这就是挖掉的部分。
        }
        graphics.pose().popPose();
    }

    /** 圆角矩形在某一行的半宽；该行不与形状相交时返回 -1。 */
    private static double halfWidth(double halfW, double halfH, double radius, double dy) {
        double y = Math.abs(dy);
        if (y >= halfH) return -1.0D;
        double straight = halfH - radius;
        if (y <= straight) return halfW;
        double inCorner = y - straight;
        return halfW - radius + Math.sqrt(Math.max(0.0D, radius * radius - inCorner * inCorner));
    }

    // ── 立绘与对白框 ───────────────────────────────────────────────────────────

    private void drawPortraitAndBox(GuiGraphics graphics, Font font, int width, int height,
                                    boolean boxAtTop, int visible, long now) {
        int portraitHeight = Math.max(90, Math.min(220, Math.round(height * 0.5F)));
        int portraitWidth = Math.round(portraitHeight * PORTRAIT_TEXTURE_WIDTH / (float) PORTRAIT_TEXTURE_HEIGHT);
        int portraitX = width - portraitWidth - 6;
        int portraitY = height - portraitHeight;
        int frame = speaking(now) ? (int) ((now / MOUTH_FRAME_MILLIS) % MOUTH_FRAMES.length) : 0;
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 200.0F);
        graphics.blit(MOUTH_FRAMES[frame], portraitX, portraitY, portraitWidth, portraitHeight,
                0.0F, 0.0F, PORTRAIT_TEXTURE_WIDTH, PORTRAIT_TEXTURE_HEIGHT,
                PORTRAIT_TEXTURE_WIDTH, PORTRAIT_TEXTURE_HEIGHT);

        int boxX = 10;
        int boxRight = portraitX + portraitWidth / 6;
        int boxY = boxAtTop ? 34 : height - BOX_HEIGHT - 10;
        graphics.fill(boxX - 2, boxY - 2, boxRight + 2, boxY + BOX_HEIGHT + 2, 0xFF7FE3E8);
        graphics.fill(boxX, boxY, boxRight, boxY + BOX_HEIGHT, 0xF0182634);
        Component name = Component.translatable("tutorial.simukraft.mail.speaker");
        int tabWidth = font.width(name) + 12;
        graphics.fill(boxX + 6, boxY - 13, boxX + 6 + tabWidth, boxY - 1, 0xFF2D8C8C);
        graphics.drawString(font, name, boxX + 12, boxY - 11, 0xFFFFFFFF, false);

        int textWidth = Math.max(40, boxRight - boxX - 16);
        List<FormattedCharSequence> lines = font.split(Component.literal(text.substring(0, visible)), textWidth);
        int maxLines = (BOX_HEIGHT - 18) / (font.lineHeight + 2);
        for (int i = 0; i < Math.min(maxLines, lines.size()); i++) {
            graphics.drawString(font, lines.get(i), boxX + 8, boxY + 7 + i * (font.lineHeight + 2), TEXT_COLOR, false);
        }
        Component hint = Component.translatable("tutorial.simukraft.mail.hint", index + 1, steps.size());
        int hintColor = visible >= text.length() && (now / 450L) % 2L == 0L ? 0xFFBFEFF0 : 0xFF6F9497;
        graphics.drawString(font, hint, boxRight - font.width(hint) - 6, boxY + BOX_HEIGHT - font.lineHeight - 3,
                hintColor, false);
        graphics.pose().popPose();
    }
}
