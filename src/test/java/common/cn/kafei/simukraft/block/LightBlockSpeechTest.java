package common.cn.kafei.simukraft.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LightBlockSpeechTest {
    @Test
    void chineseClientsGetTheChineseCaption() {
        assertTrue(LightBlockSpeech.isChineseLanguage("zh_cn"));
        assertTrue(LightBlockSpeech.isChineseLanguage("zh_tw"));
        assertTrue(LightBlockSpeech.isChineseLanguage("ZH_HK"));
        assertEquals(LightBlockSpeech.CHINESE, LightBlockSpeech.textForLanguage("zh_cn"));
        assertEquals(
                "你好，我是灯箱。我给 Minecraft 世界带来光明。谢谢你点击我。从这里看出去风景不错。谢谢你把我放在这儿。祝你今天愉快。感谢收听。",
                LightBlockSpeech.textForLanguage("zh_cn"));
    }

    @Test
    void otherLanguagesGetTheEnglishCaption() {
        assertFalse(LightBlockSpeech.isChineseLanguage("en_us"));
        assertFalse(LightBlockSpeech.isChineseLanguage("ja_jp"));
        assertFalse(LightBlockSpeech.isChineseLanguage(null));
        assertEquals(LightBlockSpeech.ENGLISH, LightBlockSpeech.textForLanguage("en_us"));
        assertEquals(LightBlockSpeech.ENGLISH, LightBlockSpeech.textForLanguage("fr_fr"));
        assertEquals(
                "Hello, I am a lightbox. I bring light into the Minecraft world. Thanks for clicking me. There's a nice view from here. Thanks for placing me here. Have a nice day. Thank you for watching.",
                LightBlockSpeech.textForLanguage("en_gb"));
    }
}
