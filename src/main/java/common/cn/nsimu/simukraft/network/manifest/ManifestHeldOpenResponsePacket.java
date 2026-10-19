package common.cn.nsimu.simukraft.network.manifest;

import common.cn.nsimu.simukraft.SimuKraft;
import common.cn.nsimu.simukraft.network.clientbound.ClientboundNetworkBridge;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * ManifestHeldOpenResponsePacket: 把手持清单刷新后的材料发给客户端打开界面。
 */
public record ManifestHeldOpenResponsePacket(InteractionHand hand, ItemStack stack) implements CustomPacketPayload {
    public static final Type<ManifestHeldOpenResponsePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "manifest_held_open_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ManifestHeldOpenResponsePacket> STREAM_CODEC =
            StreamCodec.of(ManifestHeldOpenResponsePacket::encode, ManifestHeldOpenResponsePacket::decode);

    public ManifestHeldOpenResponsePacket {
        hand = hand != null ? hand : InteractionHand.MAIN_HAND;
        stack = stack == null ? ItemStack.EMPTY : stack.copy();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, ManifestHeldOpenResponsePacket packet) {
        buffer.writeEnum(packet.hand());
        ItemStack.STREAM_CODEC.encode(buffer, packet.stack());
    }

    public static ManifestHeldOpenResponsePacket decode(RegistryFriendlyByteBuf buffer) {
        return new ManifestHeldOpenResponsePacket(buffer.readEnum(InteractionHand.class), ItemStack.STREAM_CODEC.decode(buffer));
    }

    public static void handle(ManifestHeldOpenResponsePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientboundNetworkBridge.handleManifestHeldOpenResponse(packet));
    }
}
