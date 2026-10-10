package common.cn.kafei.simukraft.block;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class TalkingLightAdvancementTest {
    @Test
    void talkingLightAdvancementUsesManualUseLightCriterion() throws Exception {
        try (InputStream in = TalkingLightAdvancementTest.class.getResourceAsStream(
                "/data/simukraft/advancement/story/talking_light.json")) {
            assertNotNull(in);
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(json.contains("\"use_light\""));
            assertTrue(json.contains("\"trigger\": \"minecraft:impossible\""));
            assertTrue(json.contains("advancement.simukraft.talking_light.title"));
            assertTrue(json.contains("advancement.simukraft.talking_light.description"));
            assertTrue(json.contains("simukraft:rainbow_light_block"));
        }
    }
}
