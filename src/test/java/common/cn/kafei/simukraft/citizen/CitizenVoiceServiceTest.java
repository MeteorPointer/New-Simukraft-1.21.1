package common.cn.kafei.simukraft.citizen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class CitizenVoiceServiceTest {
    @Test
    void nearbyTalkCue_greetsBeforeChatting() {
        CitizenVoiceService.clearLocksForTest();
        UUID id = UUID.randomUUID();
        assertEquals(CitizenVoiceService.Cue.GREET, CitizenVoiceService.nearbyTalkCue(id, 0L));
    }

    @Test
    void shopKind_usesBakeryAndKfcFolderNames() {
        assertEquals(CitizenVoiceService.MealKind.BAKERY, CitizenVoiceService.shopKind("bakery_stall"));
        assertEquals(CitizenVoiceService.MealKind.BAKERY, CitizenVoiceService.shopKind("\u9762\u5305\u5e97"));
        assertEquals(CitizenVoiceService.MealKind.BURGER, CitizenVoiceService.shopKind("kfc_shop"));
        assertEquals(CitizenVoiceService.MealKind.BURGER, CitizenVoiceService.shopKind("\u80af\u6253\u9e21"));
        assertEquals(CitizenVoiceService.MealKind.OTHER, CitizenVoiceService.shopKind("grocery"));
    }

    @Test
    void isCheeseFactory_matchesEnglishAndChineseNames() {
        assertTrue(CitizenVoiceService.isCheeseFactory("cheese_factory_1"));
        assertTrue(CitizenVoiceService.isCheeseFactory("NSUK Cheese Plant"));
        assertTrue(CitizenVoiceService.isCheeseFactory("\u5976\u916a\u5de5\u5382"));
        assertFalse(CitizenVoiceService.isCheeseFactory("bakery"));
        assertFalse(CitizenVoiceService.isCheeseFactory((String) null));
    }

    @Test
    void ambientLock_allowsOnlyOneSpeakerPerLevel() {
        CitizenVoiceService.clearLocksForTest();
        assertTrue(CitizenVoiceService.isAmbientCue(CitizenVoiceService.Cue.CHAT));
        assertTrue(CitizenVoiceService.isAmbientCue(CitizenVoiceService.Cue.GREET));
        assertTrue(CitizenVoiceService.isAmbientCue(CitizenVoiceService.Cue.MORNING));
        assertTrue(CitizenVoiceService.isAmbientCue(CitizenVoiceService.Cue.EVENING));
        assertFalse(CitizenVoiceService.isAmbientCue(CitizenVoiceService.Cue.PANEL));
        assertFalse(CitizenVoiceService.isAmbientCue(CitizenVoiceService.Cue.HURT));

        assertTrue(CitizenVoiceService.tryAmbientLock("overworld", 0L));
        assertFalse(CitizenVoiceService.tryAmbientLock("overworld", 10L));
        assertTrue(CitizenVoiceService.tryAmbientLock("the_nether", 10L));
        assertTrue(CitizenVoiceService.tryAmbientLock("overworld", CitizenVoiceService.AMBIENT_LOCK_TICKS));
    }

    @Test
    void infantAndFemale_followCitizenData() {
        CitizenData child = new CitizenData(UUID.randomUUID());
        child.setGender("male");
        child.setChild(true);
        child.setAge(1);
        assertTrue(CitizenVoiceService.isInfant(child));
        assertFalse(CitizenVoiceService.isFemale(child));

        CitizenData woman = new CitizenData(UUID.randomUUID());
        woman.setGender("female");
        woman.setAge(22);
        assertTrue(CitizenVoiceService.isFemale(woman));
        assertFalse(CitizenVoiceService.isInfant(woman));
    }
}
