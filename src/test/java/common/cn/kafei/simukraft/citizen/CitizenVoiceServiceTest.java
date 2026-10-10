package common.cn.kafei.simukraft.citizen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class CitizenVoiceServiceTest {
    @Test
    void panelCue_usesMorningGreetAndEvening() {
        assertEquals(CitizenVoiceService.Cue.MORNING, CitizenVoiceService.panelCue(800L));
        assertEquals(CitizenVoiceService.Cue.GREET, CitizenVoiceService.panelCue(6_000L));
        assertEquals(CitizenVoiceService.Cue.EVENING, CitizenVoiceService.panelCue(18_000L));
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
        assertTrue(CitizenVoiceService.isAmbientCue(CitizenVoiceService.Cue.FLEE));
        assertFalse(CitizenVoiceService.isAmbientCue(CitizenVoiceService.Cue.GREET));
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
