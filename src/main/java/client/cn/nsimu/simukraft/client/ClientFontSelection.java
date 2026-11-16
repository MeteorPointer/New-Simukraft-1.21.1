package client.cn.nsimu.simukraft.client;

import common.cn.nsimu.simukraft.config.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/** 解析客户端字体配置，并限制英语专用字体的使用范围。 */
public final class ClientFontSelection {
    private static final String[] FONT_NAMES = {"default", "jingluo", "kaishu", "song", "heiti", "xingkai", "beautiful_es"};
    private ClientFontSelection() {
    }

    /** effectiveFontId: 返回当前应替换默认字体的资源 ID。 */
    public static ResourceLocation effectiveFontId() {
        String name = ClientConfig.fontName();
        if ("beautiful_es".equals(name) && !isEnglish()) {
            name = ClientConfig.DEFAULT_FONT;
        }
        // FontManager 的 font/目录会被 FileToIdConverter 去掉，注册 ID 是 simukraft:<字体名>。
        return ResourceLocation.fromNamespaceAndPath("simukraft", name);
    }

    /** isCustomFont: 判断是否启用了需要关闭阴影的模组字体。 */
    public static boolean isCustomFont() {
        String name = ClientConfig.fontName();
        return !"default".equals(name) && !("beautiful_es".equals(name) && !isEnglish());
    }

    /** isEnglish: 判断当前客户端是否使用英语语言。 */
    public static boolean isEnglish() {
        String language = Minecraft.getInstance().options.languageCode;
        return language != null && language.toLowerCase(java.util.Locale.ROOT).startsWith("en");
    }

    /** labelKey: 返回配置页面使用的字体显示键。 */
    public static String labelKey() {
        String name = ClientConfig.fontName();
        if ("beautiful_es".equals(name) && !isEnglish()) name = ClientConfig.DEFAULT_FONT;
        return "gui.simukraft.config.client.font." + name;
    }

    /** nextName: 按当前语言可用项循环选择字体。 */
    public static String nextName(String current) {
        int start = 0;
        for (int i = 0; i < FONT_NAMES.length; i++) {
            if (FONT_NAMES[i].equals(current)) { start = i; break; }
        }
        for (int offset = 1; offset <= FONT_NAMES.length; offset++) {
            String candidate = FONT_NAMES[(start + offset) % FONT_NAMES.length];
            if (!"beautiful_es".equals(candidate) || isEnglish()) return candidate;
        }
        return ClientConfig.DEFAULT_FONT;
    }
}
