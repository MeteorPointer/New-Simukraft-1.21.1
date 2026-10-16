package common.cn.kafei.simukraft.citizen;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CitizenNameLibraryTest {
    @Test
    void englishNameLibraryHasNoChinese() throws Exception {
        try (InputStream stream = CitizenNameLibraryTest.class.getResourceAsStream("/assets/simukraft/names/en_us.json")) {
            assertNotNull(stream);
            JsonObject root = JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            for (String key : new String[] {"male", "female", "family"}) {
                assertTrue(root.getAsJsonArray(key).size() >= 80, key);
                root.getAsJsonArray(key).forEach(element -> {
                    String name = element.getAsString();
                    assertFalse(name.isBlank(), key);
                    assertFalse(CitizenNameLibrary.containsCjk(name), name);
                });
            }
        }
    }
}
