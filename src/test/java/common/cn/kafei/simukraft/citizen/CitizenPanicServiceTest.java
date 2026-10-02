package common.cn.kafei.simukraft.citizen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import common.cn.kafei.simukraft.path.MovementIntent;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;


class CitizenPanicServiceTest {
    @Test
    void fleeScorePrefersPositionsFartherFromTheAttacker() {
        Vec3 origin = new Vec3(0.0D, 64.0D, 0.0D);
        Vec3 threat = new Vec3(2.0D, 64.0D, 0.0D);
        Vec3 away = new Vec3(-5.0D, 64.0D, 0.0D);
        Vec3 toward = new Vec3(5.0D, 64.0D, 0.0D);

        assertTrue(CitizenPanicService.fleeScore(origin, threat, away)
                > CitizenPanicService.fleeScore(origin, threat, toward));
    }

    @Test
    void fleeScoreWithoutThreatPrefersLeavingTheOrigin() {
        Vec3 origin = new Vec3(0.0D, 64.0D, 0.0D);

        assertTrue(CitizenPanicService.fleeScore(origin, null, new Vec3(4.0D, 64.0D, 0.0D))
                > CitizenPanicService.fleeScore(origin, null, new Vec3(1.0D, 64.0D, 0.0D)));
    }

    @Test
    void nullDamageSourceIsNotAPlayerAttack() {
        assertFalse(CitizenPanicService.isPlayerAttack(null));
    }

    @Test
    void fleeIntentNeverTeleportsOnPathFailure() {
        assertFalse(MovementIntent.FLEE.allowsTeleportFallback());
        assertFalse(MovementIntent.WANDER.allowsTeleportFallback());
        assertTrue(MovementIntent.WORK.allowsTeleportFallback());
        assertTrue(MovementIntent.RUN.allowsTeleportFallback());
    }

    @Test
    void panicWindowMatchesVanillaDamageMemory() {
        assertEquals(40, CitizenPanicService.PANIC_MEMORY_TICKS);
        assertEquals(5, CitizenPanicService.FLEE_RADIUS);
        assertEquals(4, CitizenPanicService.FLEE_VERTICAL);
    }
}
