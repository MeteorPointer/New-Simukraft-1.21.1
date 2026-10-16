package common.cn.nsimu.simukraft.citizen;

import java.util.Locale;

public enum CitizenNameStyle {
    CHINESE("gui.simukraft.config.npc_name_style.chinese"),
    ENGLISH("gui.simukraft.config.npc_name_style.english");

    private final String translationKey;

    CitizenNameStyle(String translationKey) {
        this.translationKey = translationKey;
    }

    /**
     * translationKey: 返回配置界面展示用的翻译键。
     */
    public String translationKey() {
        return translationKey;
    }

    /**
     * fromName: 从配置或网络包字符串解析名字风格，非法值保持中式默认。
     */
    public static CitizenNameStyle fromName(String name) {
        if (name == null || name.isBlank()) {
            return CHINESE;
        }
        String normalized = name.trim().toUpperCase(Locale.ROOT);
        for (CitizenNameStyle style : values()) {
            if (style.name().equals(normalized)) {
                return style;
            }
        }
        return CHINESE;
    }
}
