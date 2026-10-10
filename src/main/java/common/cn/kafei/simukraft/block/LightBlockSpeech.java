package common.cn.kafei.simukraft.block;

import java.util.Locale;

/** LightBlockSpeech: 灯块右键台词，按玩家语言选中文或英文。 */
public final class LightBlockSpeech {
    public static final String KEY = "message.simukraft.light_block.speech";
    public static final String KEY_EN = "message.simukraft.light_block.speech.en";
    public static final String CHINESE = "你好，我是灯箱。我给 Minecraft 世界带来光明。谢谢你点击我。从这里看出去风景不错。谢谢你把我放在这儿。祝你今天愉快。感谢收听。";
    public static final String ENGLISH = "Hello, I am a lightbox. I bring light into the Minecraft world. Thanks for clicking me. There's a nice view from here. Thanks for placing me here. Have a nice day. Thank you for watching.";
    public static final int SPEECH_MILLIS = 13_400;

    private LightBlockSpeech() {
    }

    public static boolean isChineseLanguage(String languageCode) {
        return languageCode != null && languageCode.toLowerCase(Locale.ROOT).startsWith("zh");
    }

    public static String textForLanguage(String languageCode) {
        return isChineseLanguage(languageCode) ? CHINESE : ENGLISH;
    }
}
