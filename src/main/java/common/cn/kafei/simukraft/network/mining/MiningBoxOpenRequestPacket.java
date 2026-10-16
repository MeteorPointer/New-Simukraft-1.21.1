package common.cn.kafei.simukraft.network.mining;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.mining.MiningBoxService;
import common.cn.kafei.simukraft.mining.MiningBoxState;
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
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** MiningBoxOpenRequestPacket: 打开挖矿盒。 */
public record MiningBoxOpenRequestPacket(BlockPos pos) implements CustomPacketPayload {
    public static final Type<MiningBoxOpenRequestPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "mining_box_open_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MiningBoxOpenRequestPacket> STREAM_CODEC =
            StreamCodec.of(MiningBoxOpenRequestPacket::encode, MiningBoxOpenRequestPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, MiningBoxOpenRequestPacket packet) {
        buffer.writeBlockPos(packet.pos());
    }

    public static MiningBoxOpenRequestPacket decode(RegistryFriendlyByteBuf buffer) {
        return new MiningBoxOpenRequestPacket(buffer.readBlockPos());
    }

    public static void handle(MiningBoxOpenRequestPacket packet, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            openFor(level, player, packet.pos());
        }
    }

    public static void openFor(ServerLevel level, ServerPlayer player, BlockPos pos) {
        if (!player.blockPosition().closerThan(pos, 8.0D) && !RtsRemoteMenuAccess.hasAccess(player, pos)) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.mining_box.too_far"));
            return;
        }
        if (!level.getBlockState(pos).is(ModBlocks.MINING_BOX.get())) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.mining_box.not_found"));
            return;
        }
        PacketDistributor.sendToPlayer(player, snapshot(level, pos));
    }

    public static MiningBoxOpenResponsePacket snapshot(ServerLevel level, BlockPos pos) {
        CitizenData miner = MiningBoxService.findMiner(level, pos);
        MiningBoxState state = MiningBoxService.state(level, pos);
        return new MiningBoxOpenResponsePacket(
                pos,
                miner != null,
                miner != null ? miner.uuid() : null,
                miner != null ? miner.name() : "",
                state.hasArea(),
                state.point1(),
                state.point2(),
                state.topY(),
                state.width(),
                state.depth(),
                state.mode().name(),
                state.running());
    }
}
