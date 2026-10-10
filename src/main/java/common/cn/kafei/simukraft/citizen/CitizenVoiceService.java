package common.cn.kafei.simukraft.citizen;

import common.cn.kafei.simukraft.entity.CitizenEntity;
import common.cn.kafei.simukraft.registry.ModItems;
import common.cn.kafei.simukraft.registry.ModSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.registries.DeferredHolder;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** NPC 语音：按性别/年龄选事件，同类有冷却，避免建造和闲聊刷屏。 */
public final class CitizenVoiceService {
    public enum Cue {
        HURT,
        PANEL,
        GREET,
        MORNING,
        EVENING,
        SICK,
        HIRE,
        EAT_BURGER,
        EAT_BAKERY,
        FULL,
        PREGNANT,
        BIRTH,
        YAWN,
        SHEARS,
        BUILD,
        FLEE,
        CHAT
    }

    public enum MealKind {
        BURGER,
        BAKERY,
        OTHER
    }

    static final long AMBIENT_LOCK_TICKS = 100L;
    private static final ConcurrentMap<String, Long> COOLDOWNS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, Long> AMBIENT_LOCKS = new ConcurrentHashMap<>();

    private CitizenVoiceService() {
    }

    public static void play(ServerLevel level, CitizenEntity entity, @Nullable CitizenData data, Cue cue) {
        if (level == null || entity == null || cue == null || !entity.isAlive()) {
            return;
        }
        long gameTime = level.getGameTime();
        if (isCoolingDown(entity.getUUID(), cue, gameTime)) {
            return;
        }
        if (isAmbientCue(cue) && !tryAmbientLock(level.dimension().location().toString(), gameTime)) {
            return;
        }
        markCooldown(entity.getUUID(), cue, gameTime);
        SoundEvent sound = resolve(data, cue);
        if (sound == null) {
            return;
        }
        level.playSound(null, entity.blockPosition(), sound, SoundSource.NEUTRAL, volume(cue), 1.0F);
    }

    public static void playAt(ServerLevel level, BlockPos pos, SoundEvent sound) {
        if (level == null || pos == null || sound == null) {
            return;
        }
        level.playSound(null, pos, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    public static void playAt(ServerLevel level, Entity entity, SoundEvent sound) {
        if (level == null || entity == null || sound == null) {
            return;
        }
        level.playSound(null, entity.blockPosition(), sound, SoundSource.NEUTRAL, 1.0F, 1.0F);
    }

    public static Cue panelCue(long dayTime) {
        long time = Math.floorMod(dayTime, 24_000L);
        if (time < 4_000L) {
            return Cue.MORNING;
        }
        if (time >= 12_000L) {
            return Cue.EVENING;
        }
        return Cue.GREET;
    }

    public static MealKind mealKind(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return MealKind.OTHER;
        }
        Item item = stack.getItem();
        if (item == ModItems.HAMBURGER.get() || item == ModItems.FRENCH_FRIES.get() || item == ModItems.CHEESE_BURGER.get()) {
            return MealKind.BURGER;
        }
        if (item == Items.BREAD || item == Items.COOKIE || item == Items.CAKE || item == Items.PUMPKIN_PIE
                || item == ModItems.CHEESE_CHUNK.get()) {
            return MealKind.BAKERY;
        }
        return MealKind.OTHER;
    }

    public static boolean isCheeseFactory(String... names) {
        if (names == null) {
            return false;
        }
        for (String name : names) {
            if (name == null || name.isBlank()) {
                continue;
            }
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.contains("cheese") || name.contains("\u5976\u916a")) {
                return true;
            }
        }
        return false;
    }

    public static boolean isInfant(@Nullable CitizenData data) {
        return data != null && (data.child() || data.age() < 6);
    }

    public static boolean isFemale(@Nullable CitizenData data) {
        return data != null && "female".equalsIgnoreCase(data.gender());
    }

    static SoundEvent resolve(@Nullable CitizenData data, Cue cue) {
        boolean female = isFemale(data);
        boolean infant = isInfant(data);
        return switch (cue) {
            case HURT -> pick(female, ModSoundEvents.NPC_HURT_FEMALE, ModSoundEvents.NPC_HURT_MALE);
            case PANEL -> pick(female, ModSoundEvents.NPC_PANEL_FEMALE, ModSoundEvents.NPC_PANEL_MALE);
            case GREET -> pick(female, ModSoundEvents.NPC_GREET_FEMALE, ModSoundEvents.NPC_GREET_MALE);
            case MORNING -> pick(female, ModSoundEvents.NPC_MORNING_FEMALE, ModSoundEvents.NPC_MORNING_MALE);
            case EVENING -> pick(female, ModSoundEvents.NPC_EVENING_FEMALE, ModSoundEvents.NPC_EVENING_MALE);
            case SICK -> infant ? ModSoundEvents.NPC_INFANT_SICK.get()
                    : pick(female, ModSoundEvents.NPC_SICK_FEMALE, ModSoundEvents.NPC_SICK_MALE);
            case HIRE -> pick(female, ModSoundEvents.NPC_HIRE_FEMALE, ModSoundEvents.NPC_HIRE_MALE);
            case EAT_BURGER -> pick(female, ModSoundEvents.NPC_EAT_BURGER_FEMALE, ModSoundEvents.NPC_EAT_BURGER_MALE);
            case EAT_BAKERY -> pick(female, ModSoundEvents.NPC_EAT_BAKERY_FEMALE, ModSoundEvents.NPC_EAT_BAKERY_MALE);
            case FULL -> female ? null : ModSoundEvents.NPC_FULL_MALE.get();
            case PREGNANT -> ModSoundEvents.NPC_PREGNANT.get();
            case BIRTH -> ModSoundEvents.NPC_BIRTH.get();
            case YAWN -> ModSoundEvents.NPC_YAWN.get();
            case SHEARS -> ModSoundEvents.NPC_SHEARS.get();
            case BUILD -> ModSoundEvents.NPC_BUILD.get();
            case FLEE -> ModSoundEvents.NPC_FLEE.get();
            case CHAT -> infant ? ModSoundEvents.NPC_CHAT_INFANT.get()
                    : pick(female, ModSoundEvents.NPC_CHAT_FEMALE, ModSoundEvents.NPC_CHAT_MALE);
        };
    }

    private static SoundEvent pick(boolean female,
                                   DeferredHolder<SoundEvent, SoundEvent> femaleSound,
                                   DeferredHolder<SoundEvent, SoundEvent> maleSound) {
        return (female ? femaleSound : maleSound).get();
    }

    static boolean isAmbientCue(Cue cue) {
        return cue == Cue.CHAT || cue == Cue.FLEE;
    }

    static boolean tryAmbientLock(String levelKey, long gameTime) {
        if (levelKey == null || levelKey.isBlank()) {
            return false;
        }
        Long until = AMBIENT_LOCKS.get(levelKey);
        if (until != null && gameTime < until) {
            return false;
        }
        AMBIENT_LOCKS.put(levelKey, gameTime + AMBIENT_LOCK_TICKS);
        return true;
    }

    static void clearLocksForTest() {
        COOLDOWNS.clear();
        AMBIENT_LOCKS.clear();
    }

    private static boolean isCoolingDown(UUID uuid, Cue cue, long gameTime) {
        Long until = COOLDOWNS.get(uuid + ":" + cue.name());
        return until != null && gameTime < until;
    }

    private static void markCooldown(UUID uuid, Cue cue, long gameTime) {
        long duration = switch (cue) {
            case BUILD -> 50L;
            case CHAT, GREET -> 400L;
            case FLEE, SICK -> 80L;
            case PANEL, MORNING, EVENING -> 40L;
            default -> 20L;
        };
        COOLDOWNS.put(uuid + ":" + cue.name(), gameTime + duration);
    }

    private static float volume(Cue cue) {
        return switch (cue) {
            case BUILD, SHEARS -> 0.55F;
            case CHAT, GREET -> 0.7F;
            default -> 1.0F;
        };
    }
}
