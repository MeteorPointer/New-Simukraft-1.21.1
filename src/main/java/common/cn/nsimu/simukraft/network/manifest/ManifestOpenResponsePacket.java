package common.cn.nsimu.simukraft.network.manifest;

import common.cn.nsimu.simukraft.SimuKraft;
import common.cn.nsimu.simukraft.block.entity.ManifestBlockEntity;
import common.cn.nsimu.simukraft.item.ManifestItem;
import common.cn.nsimu.simukraft.network.clientbound.ClientboundNetworkBridge;
import common.cn.nsimu.simukraft.registry.ModBlocks;
import common.cn.nsimu.simukraft.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * ManifestOpenResponsePacket: 把贴墙清单的当前数据发给客户端打开界面。
 */
public record ManifestOpenResponsePacket(BlockPos pos, ItemStack stack) implements CustomPacketPayload {
    public static final Type<ManifestOpenResponsePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "manifest_open_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ManifestOpenResponsePacket> STREAM_CODEC =
            StreamCodec.of(ManifestOpenResponsePacket::encode, ManifestOpenResponsePacket::decode);

    public ManifestOpenResponsePacket {
        stack = stack == null ? ItemStack.EMPTY : stack.copy();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, ManifestOpenResponsePacket packet) {
        buffer.writeBlockPos(packet.pos());
        ItemStack.STREAM_CODEC.encode(buffer, packet.stack());
    }

    public static ManifestOpenResponsePacket decode(RegistryFriendlyByteBuf buffer) {
        return new ManifestOpenResponsePacket(buffer.readBlockPos(), ItemStack.STREAM_CODEC.decode(buffer));
    }

    public static void handle(ManifestOpenResponsePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientboundNetworkBridge.handleManifestOpenResponse(packet));
    }

    /**
     * openFor: 校验距离后把清单快照发给玩家。
     */
    public static void openFor(ServerLevel level, ServerPlayer player, BlockPos pos) {
        if (!level.getBlockState(pos).is(ModBlocks.MANIFEST.get())) {
            return;
        }
        if (!player.isAlive() || player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) > 64.0D) {
            return;
        }
        ItemStack stack;
        if (level.getBlockEntity(pos) instanceof ManifestBlockEntity manifest) {
            stack = manifest.toItem();
            if (ManifestItem.refreshFromLinkedSource(stack, level)) {
                manifest.loadFromItem(stack);
            }
        } else {
            stack = new ItemStack(ModItems.MANIFEST.get());
        }
        PacketDistributor.sendToPlayer(player, new ManifestOpenResponsePacket(pos, stack));
    }
}
