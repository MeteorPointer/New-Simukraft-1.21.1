package common.cn.kafei.simukraft.network.mining;

import common.cn.kafei.simukraft.SimuKraft;
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

/** MiningBoxSetAreaPacket: 保存玩家框选的两个角点。 */
public record MiningBoxSetAreaPacket(BlockPos pos, BlockPos point1, BlockPos point2) implements CustomPacketPayload {
    public static final Type<MiningBoxSetAreaPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "mining_box_set_area"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MiningBoxSetAreaPacket> STREAM_CODEC =
            StreamCodec.of(MiningBoxSetAreaPacket::encode, MiningBoxSetAreaPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, MiningBoxSetAreaPacket packet) {
        buffer.writeBlockPos(packet.pos());
        buffer.writeBlockPos(packet.point1());
        buffer.writeBlockPos(packet.point2());
    }

    public static MiningBoxSetAreaPacket decode(RegistryFriendlyByteBuf buffer) {
        return new MiningBoxSetAreaPacket(buffer.readBlockPos(), buffer.readBlockPos(), buffer.readBlockPos());
    }

    public static void handle(MiningBoxSetAreaPacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (!player.blockPosition().closerThan(packet.pos(), 16.0D) && !RtsRemoteMenuAccess.hasAccess(player, packet.pos())) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.mining_box.too_far"));
            return;
        }
        if (!level.getBlockState(packet.pos()).is(ModBlocks.MINING_BOX.get())) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.mining_box.not_found"));
            return;
        }
        if (!MiningBoxService.setArea(level, packet.pos(), packet.point1(), packet.point2())) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.mining_box.area_invalid"));
        }
        MiningBoxOpenRequestPacket.openFor(level, player, packet.pos());
    }
}
