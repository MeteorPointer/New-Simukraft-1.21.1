package common.cn.kafei.simukraft.network.mining;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.job.CitizenEmploymentService;
import common.cn.kafei.simukraft.mining.MiningBoxService;
import common.cn.kafei.simukraft.network.rts.RtsRemoteMenuAccess;
import common.cn.kafei.simukraft.network.toast.InfoToastService;
import common.cn.kafei.simukraft.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** MiningBoxActionPacket: 切换范围、模式、开关，或解雇矿工。 */
public record MiningBoxActionPacket(BlockPos pos, Action action) implements CustomPacketPayload {
    public static final Type<MiningBoxActionPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "mining_box_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MiningBoxActionPacket> STREAM_CODEC =
            StreamCodec.of(MiningBoxActionPacket::encode, MiningBoxActionPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, MiningBoxActionPacket packet) {
        buffer.writeBlockPos(packet.pos());
        buffer.writeEnum(packet.action());
    }

    public static MiningBoxActionPacket decode(RegistryFriendlyByteBuf buffer) {
        return new MiningBoxActionPacket(buffer.readBlockPos(), buffer.readEnum(Action.class));
    }

    public static void handle(MiningBoxActionPacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (!player.blockPosition().closerThan(packet.pos(), 8.0D) && !RtsRemoteMenuAccess.hasAccess(player, packet.pos())) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.mining_box.too_far"));
            return;
        }
        if (!level.getBlockState(packet.pos()).is(ModBlocks.MINING_BOX.get())) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.mining_box.not_found"));
            return;
        }
        switch (packet.action()) {
            case CYCLE_MODE -> MiningBoxService.cycleMode(level, packet.pos());
            case TOGGLE_RUNNING -> MiningBoxService.toggleRunning(level, packet.pos());
            case FIRE -> {
                var miner = MiningBoxService.findMiner(level, packet.pos());
                if (miner != null) {
                    CitizenEmploymentService.fire(level, miner.uuid(),
                            MiningBoxService.HIRE_SOURCE_TYPE, MiningBoxService.HIRE_ROLE, packet.pos(), "miner_fired");
                }
            }
        }
        MiningBoxOpenRequestPacket.openFor(level, player, packet.pos());
    }

    public enum Action {
        CYCLE_MODE,
        TOGGLE_RUNNING,
        FIRE
    }
}
