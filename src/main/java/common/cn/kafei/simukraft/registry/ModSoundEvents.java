package common.cn.kafei.simukraft.registry;

import common.cn.kafei.simukraft.SimuKraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;


public final class ModSoundEvents {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, SimuKraft.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> BUILD_BOX_PLACE = registerSound("block.build_box.place");
    public static final DeferredHolder<SoundEvent, SoundEvent> BUILD_BOX_BREAK = registerSound("block.build_box.break");
    public static final DeferredHolder<SoundEvent, SoundEvent> BUILD_BOX_OPEN = registerSound("ui.build_box.open");
    public static final DeferredHolder<SoundEvent, SoundEvent> CITY_CORE_OPEN = registerSound("ui.city_core.open");
    public static final DeferredHolder<SoundEvent, SoundEvent> FARMLAND_BOX_PLACE = registerSound("block.farmland_box.place");
    public static final DeferredHolder<SoundEvent, SoundEvent> FARMLAND_BOX_BREAK = registerSound("block.farmland_box.break");
    public static final DeferredHolder<SoundEvent, SoundEvent> PLAYER_WAKE_UP = registerSound("player.wake_up");
    // FIRST_DREAM：旧版首次进入世界时播放的梦境音乐。
    public static final DeferredHolder<SoundEvent, SoundEvent> FIRST_DREAM = registerSound("music.first_dream");
    public static final DeferredHolder<SoundEvent, SoundEvent> MONEY_COLLECT = registerSound("money.collect");
    public static final DeferredHolder<SoundEvent, SoundEvent> CONSTRUCTION_COMPLETE = registerSound("construction.complete");
    public static final DeferredHolder<SoundEvent, SoundEvent> NPC_HURT_MALE = registerSound("npc.hurt.male");
    public static final DeferredHolder<SoundEvent, SoundEvent> NPC_HURT_FEMALE = registerSound("npc.hurt.female");
    public static final DeferredHolder<SoundEvent, SoundEvent> NPC_PANEL_MALE = registerSound("npc.panel.male");
    public static final DeferredHolder<SoundEvent, SoundEvent> NPC_PANEL_FEMALE = registerSound("npc.panel.female");
    public static final DeferredHolder<SoundEvent, SoundEvent> NPC_GREET_MALE = registerSound("npc.greet.male");
    public static final DeferredHolder<SoundEvent, SoundEvent> NPC_GREET_FEMALE = registerSound("npc.greet.female");
    public static final DeferredHolder<SoundEvent, SoundEvent> NPC_MORNING_MALE = registerSound("npc.morning.male");
    public static final DeferredHolder<SoundEvent, SoundEvent> NPC_MORNING_FEMALE = registerSound("npc.morning.female");
    public static final DeferredHolder<SoundEvent, SoundEvent> NPC_EVENING_MALE = registerSound("npc.evening.male");
    public static final DeferredHolder<SoundEvent, SoundEvent> NPC_EVENING_FEMALE = registerSound("npc.evening.female");
    public static final DeferredHolder<SoundEvent, SoundEvent> NPC_SICK_MALE = registerSound("npc.sick.male");
    public static final DeferredHolder<SoundEvent, SoundEvent> NPC_SICK_FEMALE = registerSound("npc.sick.female");
    public static final DeferredHolder<SoundEvent, SoundEvent> NPC_HIRE_MALE = registerSound("npc.hire.male");
    public static final DeferredHolder<SoundEvent, SoundEvent> NPC_HIRE_FEMALE = registerSound("npc.hire.female");
    public static final DeferredHolder<SoundEvent, SoundEvent> NPC_EAT_BURGER_MALE = registerSound("npc.eat.burger.male");
    public static final DeferredHolder<SoundEvent, SoundEvent> NPC_EAT_BURGER_FEMALE = registerSound("npc.eat.burger.female");
    public static final DeferredHolder<SoundEvent, SoundEvent> NPC_EAT_BAKERY_MALE = registerSound("npc.eat.bakery.male");
    public static final DeferredHolder<SoundEvent, SoundEvent> NPC_EAT_BAKERY_FEMALE = registerSound("npc.eat.bakery.female");
    public static final DeferredHolder<SoundEvent, SoundEvent> NPC_FULL_MALE = registerSound("npc.full.male");
    public static final DeferredHolder<SoundEvent, SoundEvent> NPC_PREGNANT = registerSound("npc.pregnant");
    public static final DeferredHolder<SoundEvent, SoundEvent> NPC_BIRTH = registerSound("npc.birth");
    public static final DeferredHolder<SoundEvent, SoundEvent> NPC_INFANT_SICK = registerSound("npc.infant.sick");
    public static final DeferredHolder<SoundEvent, SoundEvent> NPC_YAWN = registerSound("npc.yawn");
    public static final DeferredHolder<SoundEvent, SoundEvent> NPC_SHEARS = registerSound("npc.shears");
    public static final DeferredHolder<SoundEvent, SoundEvent> NPC_BUILD = registerSound("npc.build");
    public static final DeferredHolder<SoundEvent, SoundEvent> NPC_FLEE = registerSound("npc.flee");
    public static final DeferredHolder<SoundEvent, SoundEvent> NPC_CHAT_MALE = registerSound("npc.chat.male");
    public static final DeferredHolder<SoundEvent, SoundEvent> NPC_CHAT_FEMALE = registerSound("npc.chat.female");
    public static final DeferredHolder<SoundEvent, SoundEvent> NPC_CHAT_INFANT = registerSound("npc.chat.infant");
    public static final DeferredHolder<SoundEvent, SoundEvent> LIGHT_USE = registerSound("block.light.use");
    public static final DeferredHolder<SoundEvent, SoundEvent> CHEESE_MACHINE_START = registerSound("block.cheese_machine.start");

    private ModSoundEvents() {
    }

    public static void register(IEventBus modEventBus) {
        SOUNDS.register(modEventBus);
    }

    private static DeferredHolder<SoundEvent, SoundEvent> registerSound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, name)));
    }
}
