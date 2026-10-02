package common.cn.kafei.simukraft.citizen;

import common.cn.kafei.simukraft.entity.CitizenEntity;
import common.cn.kafei.simukraft.path.CitizenNavigationService;
import common.cn.kafei.simukraft.path.MovementIntent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;


/** 被玩家攻击后复刻原版 PanicGoal：短时冲刺远离攻击者，不传送、不打断后续职业恢复。 */
public final class CitizenPanicService {
    // 原版 LivingEntity.getLastDamageSource 在 40 tick 后清空，PanicGoal.shouldPanic 随之结束。
    static final int PANIC_MEMORY_TICKS = 40;
    // PanicGoal.findRandomPosition：DefaultRandomPos.getPos(mob, 5, 4)
    static final int FLEE_RADIUS = 5;
    static final int FLEE_VERTICAL = 4;
    private static final int MIN_FLEE_DISTANCE = 2;
    private static final int TARGET_ATTEMPTS = 12;

    private CitizenPanicService() {
    }

    /** onPlayerAttack：伤害真正生效且攻击者是玩家时开始/刷新逃跑窗口。 */
    public static void onPlayerAttack(ServerLevel level, CitizenEntity citizen, DamageSource source) {
        if (level == null || citizen == null || citizen.isRemoved() || !citizen.isAlive()) {
            return;
        }
        Player attacker = attackerPlayer(source);
        if (attacker == null) {
            return;
        }
        if (citizen.isSleeping()) {
            CitizenBedSleepService.wakeUp(level, citizen, citizen.position());
        }
        citizen.startPanic(attacker.getUUID(), level.getGameTime() + PANIC_MEMORY_TICKS);
        citizen.setSprinting(true);
        if (!CitizenNavigationService.hasIntent(level, citizen.getUUID(), MovementIntent.FLEE)) {
            requestDash(level, citizen, attacker.position());
        }
    }

    /** tick：窗口内路径跑完就再冲一段；窗口结束后等当前 FLEE 跑完再停冲刺。 */
    public static void tick(ServerLevel level, CitizenEntity citizen) {
        if (level == null || citizen == null || citizen.isRemoved()) {
            return;
        }
        if (!citizen.isPanicking() && citizen.getPanicThreatId() == null) {
            return;
        }
        boolean fleeNav = CitizenNavigationService.hasIntent(level, citizen.getUUID(), MovementIntent.FLEE);
        if (!citizen.isPanicking() && !fleeNav) {
            citizen.clearPanic();
            return;
        }
        if (!citizen.isAlive()) {
            CitizenNavigationService.stopForced(level, citizen.getUUID());
            citizen.clearPanic();
            return;
        }
        if (citizen.isSleeping()) {
            CitizenBedSleepService.wakeUp(level, citizen, citizen.position());
        }
        citizen.setSprinting(true);
        if (citizen.isPanicking() && !fleeNav) {
            requestDash(level, citizen, resolveThreatPos(level, citizen));
        }
    }

    /** isFleeing：逃跑窗口或正在执行 FLEE 路径，供工作和睡觉逻辑让路。 */
    public static boolean isFleeing(ServerLevel level, UUID citizenId) {
        if (level == null || citizenId == null) {
            return false;
        }
        CitizenEntity citizen = CitizenTeleportService.findCitizenEntity(level, citizenId);
        if (citizen != null && citizen.isPanicking()) {
            return true;
        }
        return CitizenNavigationService.hasIntent(level, citizenId, MovementIntent.FLEE);
    }

    public static boolean isFleeing(CitizenEntity citizen) {
        if (citizen == null || !(citizen.level() instanceof ServerLevel level)) {
            return false;
        }
        return citizen.isPanicking() || CitizenNavigationService.hasIntent(level, citizen.getUUID(), MovementIntent.FLEE);
    }

    /** clear：玩家 RTS 调遣等显式指令打断逃跑。 */
    public static void clear(ServerLevel level, CitizenEntity citizen) {
        if (citizen == null) {
            return;
        }
        citizen.clearPanic();
        if (level != null) {
            CitizenNavigationService.stopForced(level, citizen.getUUID());
        }
    }

    static boolean isPlayerAttack(DamageSource source) {
        return attackerPlayer(source) != null;
    }

    /** fleeScore：更远离威胁的落点得分更高；没有威胁时偏向跑开当前坐标。 */
    static double fleeScore(Vec3 origin, @Nullable Vec3 threat, Vec3 candidate) {
        if (origin == null || candidate == null) {
            return Double.NEGATIVE_INFINITY;
        }
        if (threat == null) {
            return origin.distanceToSqr(candidate);
        }
        return candidate.distanceToSqr(threat) - origin.distanceToSqr(threat);
    }

    @Nullable
    static Player attackerPlayer(DamageSource source) {
        if (source == null) {
            return null;
        }
        Entity attacker = source.getEntity();
        if (attacker instanceof Player player && !player.isSpectator()) {
            return player;
        }
        return null;
    }

    private static void requestDash(ServerLevel level, CitizenEntity citizen, @Nullable Vec3 threatPos) {
        Vec3 target = pickFleeTarget(level, citizen, threatPos);
        if (target != null) {
            CitizenNavigationService.requestMove(level, citizen.getUUID(), target, MovementIntent.FLEE);
        }
    }

    @Nullable
    static Vec3 pickFleeTarget(ServerLevel level, CitizenEntity citizen, @Nullable Vec3 threatPos) {
        if (level == null || citizen == null) {
            return null;
        }
        RandomSource random = citizen.getRandom();
        Vec3 origin = citizen.position();
        Vec3 best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        boolean currentlyInWater = citizen.isInWater();
        for (int attempt = 0; attempt < TARGET_ATTEMPTS; attempt++) {
            int dx = random.nextInt(FLEE_RADIUS * 2 + 1) - FLEE_RADIUS;
            int dz = random.nextInt(FLEE_RADIUS * 2 + 1) - FLEE_RADIUS;
            int dy = random.nextInt(FLEE_VERTICAL * 2 + 1) - FLEE_VERTICAL;
            if (dx * dx + dz * dz < MIN_FLEE_DISTANCE * MIN_FLEE_DISTANCE) {
                continue;
            }
            int x = (int) Math.floor(origin.x) + dx;
            int y = (int) Math.floor(origin.y) + dy;
            int z = (int) Math.floor(origin.z) + dz;
            if (!level.hasChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z))) {
                continue;
            }
            BlockPos feet = new BlockPos(x, y, z);
            if (!isStandable(level, feet, currentlyInWater)) {
                continue;
            }
            Vec3 candidate = Vec3.atBottomCenterOf(feet);
            double score = fleeScore(origin, threatPos, candidate);
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    private static boolean isStandable(ServerLevel level, BlockPos feet, boolean allowWater) {
        if (feet == null || !level.isLoaded(feet)) {
            return false;
        }
        if (!level.isEmptyBlock(feet) || !level.isEmptyBlock(feet.above())) {
            return false;
        }
        if (level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty()) {
            return false;
        }
        return allowWater || level.getFluidState(feet).isEmpty();
    }

    @Nullable
    private static Vec3 resolveThreatPos(ServerLevel level, CitizenEntity citizen) {
        UUID threatId = citizen.getPanicThreatId();
        if (threatId == null) {
            return null;
        }
        Player player = level.getPlayerByUUID(threatId);
        return player != null && player.level() == level ? player.position() : null;
    }
}
