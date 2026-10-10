package common.cn.kafei.simukraft.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TypewriterRevealTest {
    @Test
    void reveal_typesOneCharacterAtATime() {
        assertEquals("", TypewriterReveal.reveal(null, 0L, 70));
        assertEquals("", TypewriterReveal.reveal("", 100L, 70));
        assertEquals("你", TypewriterReveal.reveal("你好", 0L, 70));
        assertEquals("你好", TypewriterReveal.reveal("你好", 70L, 70));
        assertEquals("Hello", TypewriterReveal.reveal("Hello", 10_000L, 70));
    }

    @Test
    void millisPerChar_keepsPaceWithSpokenDuration() {
        assertEquals(100, TypewriterReveal.millisPerChar(134, 13_400));
        assertEquals(30, TypewriterReveal.millisPerChar(1_000, 13_400));
        assertEquals(13_400, TypewriterReveal.millisPerChar(0, 13_400));
    }
}
