package common.cn.kafei.simukraft.block;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.network.geology.GeologicalSurveyHintService;
import common.cn.kafei.simukraft.registry.ModSoundEvents;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;

/** NSUK 灯块：右键播放灯块语音，并用地质锤文本框逐字显示台词。 */
public final class LightBlock extends Block {
    static final ResourceLocation TALKING_LIGHT_ADVANCEMENT =
            ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "story/talking_light");
    static final String TALKING_LIGHT_CRITERION = "use_light";

    public LightBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(1.0F)
                .sound(SoundType.GLASS)
                .lightLevel(state -> 15));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel && player instanceof ServerPlayer serverPlayer) {
            level.playSound(null, pos, ModSoundEvents.LIGHT_USE.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
            GeologicalSurveyHintService.sendTypewriter(
                    serverPlayer,
                    Component.translatable(LightBlockSpeech.KEY),
                    LightBlockSpeech.SPEECH_MILLIS);
            grantTalkingLightAdvancement(serverPlayer);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    static void grantTalkingLightAdvancement(ServerPlayer player) {
        if (player.getServer() == null) {
            return;
        }
        AdvancementHolder advancement = player.getServer().getAdvancements().get(TALKING_LIGHT_ADVANCEMENT);
        if (advancement == null) {
            return;
        }
        if (player.getAdvancements().getOrStartProgress(advancement).isDone()) {
            return;
        }
        player.getAdvancements().award(advancement, TALKING_LIGHT_CRITERION);
    }
}
