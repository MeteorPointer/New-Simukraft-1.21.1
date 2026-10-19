package common.cn.nsimu.simukraft.network.manifest;

import common.cn.nsimu.simukraft.SimuKraft;
import common.cn.nsimu.simukraft.block.entity.ManifestBlockEntity;
import common.cn.nsimu.simukraft.item.ManifestItem;
import common.cn.nsimu.simukraft.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * ManifestBlockTogglePacket: 把贴墙清单的勾选状态写回方块实体。
 */
public record ManifestBlockTogglePacket(BlockPos pos, int index, boolean checked) implements CustomPacketPayload {
    public static final Type<ManifestBlockTogglePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "manifest_block_toggle"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ManifestBlockTogglePacket> STREAM_CODEC =
            StreamCodec.of(ManifestBlockTogglePacket::encode, ManifestBlockTogglePacket::decode);
    private static final int MAX_MATERIAL_INDEX = 4096;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, ManifestBlockTogglePacket packet) {
        buffer.writeBlockPos(packet.pos());
        buffer.writeVarInt(packet.index());
        buffer.writeBoolean(packet.checked());
    }

    public static ManifestBlockTogglePacket decode(RegistryFriendlyByteBuf buffer) {
        return new ManifestBlockTogglePacket(buffer.readBlockPos(), buffer.readVarInt(), buffer.readBoolean());
    }

    public static void handle(ManifestBlockTogglePacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (packet.index() < 0 || packet.index() > MAX_MATERIAL_INDEX) {
            return;
        }
        if (!player.isAlive()
                || player.distanceToSqr(packet.pos().getX() + 0.5D, packet.pos().getY() + 0.5D, packet.pos().getZ() + 0.5D) > 64.0D) {
            return;
        }
        if (!player.level().getBlockState(packet.pos()).is(ModBlocks.MANIFEST.get())) {
            return;
        }
        if (!(player.level().getBlockEntity(packet.pos()) instanceof ManifestBlockEntity manifest)) {
            return;
        }
        ItemStack stack = manifest.toItem();
        if (packet.index() >= ManifestItem.getMaterials(stack).size()) {
            return;
        }
        manifest.setChecked(packet.index(), packet.checked());
    }
}
