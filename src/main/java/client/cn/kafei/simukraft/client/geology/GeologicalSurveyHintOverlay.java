package client.cn.kafei.simukraft.client.geology;

import common.cn.kafei.simukraft.block.LightBlockSpeech;
import common.cn.kafei.simukraft.util.TypewriterReveal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.Util;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.util.List;

/** GeologicalSurveyHintOverlay: 在准星右下方绘制地质锤短提示。 */

@OnlyIn(Dist.CLIENT)
public final class GeologicalSurveyHintOverlay {
    private static final long DISPLAY_MILLIS = 2_000L;
    private static final int MAX_TEXT_WIDTH = 180;
    private static final int SPEECH_TEXT_WIDTH = 240;
    private static final int OFFSET_X = 12;
    private static final int OFFSET_Y = 10;
    private static final int PADDING = 5;
    private static final int BACKGROUND_COLOR = 0xA6000000;
    private static final int TEXT_COLOR = 0xFFF2F2F2;

    private static Component message = Component.empty();
    private static String fullText = "";
    private static boolean typewriter;
    private static int millisPerChar = 70;
    private static long startedAtMillis;
    private static long expiresAtMillis;

    private GeologicalSurveyHintOverlay() {
    }

    /** show: 替换当前提示并重新开始两秒计时。 */
    public static void show(Component newMessage) {
        show(newMessage, false, 0);
    }

    /** showTypewriter: 用同一文本框逐字打出台词。 */
    public static void showTypewriter(Component newMessage, int lingerMillis) {
        show(newMessage, true, lingerMillis);
    }

    private static void show(Component newMessage, boolean useTypewriter, int lingerMillis) {
        Component resolved = resolveSpeechLanguage(newMessage);
        message = resolved != null ? resolved : Component.empty();
        fullText = message.getString();
        typewriter = useTypewriter && !fullText.isEmpty();
        startedAtMillis = Util.getMillis();
        if (typewriter) {
            int duration = lingerMillis > 0 ? lingerMillis : (int) DISPLAY_MILLIS;
            millisPerChar = TypewriterReveal.millisPerChar(fullText.length(), duration);
            expiresAtMillis = startedAtMillis + (long) millisPerChar * fullText.length() + 1_500L;
        } else {
            expiresAtMillis = startedAtMillis + DISPLAY_MILLIS;
        }
    }

    /** render: 绘制仍在有效期内的勘探提示。 */
    public static void render(RenderGuiEvent.Post event) {
        if (fullText == null || fullText.isBlank()) {
            return;
        }
        long now = Util.getMillis();
        if (now >= expiresAtMillis) {
            clear();
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null || minecraft.gui.getDebugOverlay().showDebugScreen()) {
            return;
        }

        String visible = typewriter
                ? TypewriterReveal.reveal(fullText, now - startedAtMillis, millisPerChar)
                : fullText;
        Font font = minecraft.font;
        int wrapWidth = typewriter ? SPEECH_TEXT_WIDTH : MAX_TEXT_WIDTH;
        List<net.minecraft.util.FormattedCharSequence> layoutLines = font.split(Component.literal(fullText), wrapWidth);
        List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.literal(visible), wrapWidth);
        if (layoutLines.isEmpty() || lines.isEmpty()) {
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        int maxLineWidth = layoutLines.stream().mapToInt(font::width).max().orElse(0);
        int boxWidth = maxLineWidth + PADDING * 2;
        int boxHeight = layoutLines.size() * font.lineHeight + PADDING * 2;
        int centerX = graphics.guiWidth() / 2;
        int centerY = graphics.guiHeight() / 2;
        int x = Math.min(centerX + OFFSET_X, graphics.guiWidth() - boxWidth - 4);
        int y = Math.min(centerY + OFFSET_Y, graphics.guiHeight() - boxHeight - 4);
        x = Math.max(4, x);
        y = Math.max(4, y);

        graphics.fill(x, y, x + boxWidth, y + boxHeight, BACKGROUND_COLOR);
        for (int line = 0; line < lines.size(); line++) {
            graphics.drawString(font, lines.get(line), x + PADDING, y + PADDING + line * font.lineHeight, TEXT_COLOR, false);
        }
    }

    /** clear: 清理退出服务器后的临时提示状态。 */
    public static void clear() {
        message = Component.empty();
        fullText = "";
        typewriter = false;
        expiresAtMillis = 0L;
    }

    private static Component resolveSpeechLanguage(Component incoming) {
        if (incoming == null) {
            return Component.empty();
        }
        String key = translationKey(incoming);
        if (!LightBlockSpeech.KEY.equals(key) && !LightBlockSpeech.KEY_EN.equals(key)) {
            return incoming;
        }
        return Component.literal(LightBlockSpeech.textForLanguage(selectedLanguage()));
    }

    private static String selectedLanguage() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getLanguageManager() == null) {
            return "";
        }
        String code = minecraft.getLanguageManager().getSelected();
        return code != null ? code : "";
    }

    private static String translationKey(Component component) {
        if (component.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents contents) {
            return contents.getKey();
        }
        return "";
    }
}
