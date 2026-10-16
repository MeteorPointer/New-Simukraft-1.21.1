package common.cn.nsimu.simukraft.network.commercial;

import common.cn.nsimu.simukraft.SimuKraft;
import common.cn.nsimu.simukraft.commercial.CommercialControlBoxService;
import common.cn.nsimu.simukraft.commercial.CommercialTradeAccessValidator;
import common.cn.nsimu.simukraft.commercial.CommercialTradeService;
import common.cn.nsimu.simukraft.network.rts.RtsRemoteCitizenAccess;
import common.cn.nsimu.simukraft.network.toast.InfoToastService;
import common.cn.nsimu.simukraft.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;
import java.util.UUID;


public record CommercialTradePacket(BlockPos pos, UUID workerId, String offerId, int count,
                                    boolean quickMove) implements CustomPacketPayload {
    public static final Type<CommercialTradePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "commercial_trade"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CommercialTradePacket> STREAM_CODEC = StreamCodec.of(CommercialTradePacket::encode, CommercialTradePacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public CommercialTradePacket(BlockPos pos, UUID workerId, String offerId, int count) {
        this(pos, workerId, offerId, count, true);
    }

    /**
     * encode: 写入玩家商业交易请求。
     */
    public static void encode(RegistryFriendlyByteBuf buffer, CommercialTradePacket packet) {
        buffer.writeBlockPos(packet.pos());
        buffer.writeUUID(packet.workerId());
        buffer.writeUtf(packet.offerId(), 256);
        buffer.writeVarInt(packet.count());
        buffer.writeBoolean(packet.quickMove());
    }

    /**
     * decode: 读取玩家商业交易请求。
     */
    public static CommercialTradePacket decode(RegistryFriendlyByteBuf buffer) {
        return new CommercialTradePacket(buffer.readBlockPos(), buffer.readUUID(), buffer.readUtf(256), buffer.readVarInt(), buffer.readBoolean());
    }

    /**
     * handle: 在服务端执行 NPC 商业交易并刷新交易视图。
     */
    public static void handle(CommercialTradePacket packet, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            if (!CommercialTradeAccessValidator.isValidWorker(level, packet.pos(), packet.workerId())
                    || (!CommercialTradeAccessValidator.isTradeReachable(level, player, packet.pos(), packet.workerId())
                    && !RtsRemoteCitizenAccess.hasTradeAccess(player, packet.pos(), packet.workerId()))) {
                InfoToastService.warning(player, Component.translatable("message.simukraft.commercial_control_box.too_far"));
                return;
            }
            if (!level.getBlockState(packet.pos()).is(ModBlocks.COMMERCIAL_CONTROL_BOX.get())) {
                InfoToastService.warning(player, Component.translatable("message.simukraft.commercial_control_box.not_found"));
                return;
            }
            List<ItemStack> before = snapshotInventory(player);
            CommercialTradeService.TradeResult result = CommercialTradeService.executePlayerTrade(level, player, packet.pos(), packet.offerId(), packet.count(), packet.quickMove());
            if (result.success()) {
                InfoToastService.success(player, result.message());
                if (!result.carriedStack().isEmpty()) {
                    player.containerMenu.setCarried(result.carriedStack().copy());
                }
                syncChangedSlots(player, before);
                PacketDistributor.sendToPlayer(player, CommercialTradeOpenResponsePacket.from(CommercialControlBoxService.buildTradeView(level, packet.pos(), packet.workerId(), player)));
            } else {
                InfoToastService.warning(player, result.message());
            }
        }
    }

    /**
     * snapshotInventory: 快照玩家背包所有槽位用于交易后对比。
     */
    private static List<ItemStack> snapshotInventory(ServerPlayer player) {
        List<ItemStack> snapshot = new java.util.ArrayList<>(player.getInventory().getContainerSize());
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            snapshot.add(player.getInventory().getItem(i).copy());
        }
        return snapshot;
    }


    static final int PLAYER_INVENTORY_CONTAINER_ID = -2;

    /**
     * syncChangedSlots: 商店界面没有玩家背包槽，按当前菜单槽号发包会被客户端丢掉。
     * 容器号改成-2 直接写入玩家物品栏，快捷栏、主背包和副手都会立刻显示,不然有些时候会出现物品栏不同步的情况。
     */
    private static void syncChangedSlots(ServerPlayer player, List<ItemStack> before) {
        int size = Math.min(before.size(), player.getInventory().getContainerSize());
        for (int slot = 0; slot < size; slot++) {
            ItemStack current = player.getInventory().getItem(slot);
            if (!ItemStack.matches(before.get(slot), current)) {
                sendPlayerInventorySlot(player, slot, current);
            }
        }
    }

    static int[] inventorySlotSync(int inventorySlot) {
        return new int[]{PLAYER_INVENTORY_CONTAINER_ID, inventorySlot};
    }

    private static void sendPlayerInventorySlot(ServerPlayer player, int slot, ItemStack stack) {
        int[] target = inventorySlotSync(slot);
        player.connection.send(new ClientboundContainerSetSlotPacket(target[0], 0, target[1], stack.copy()));
    }
}
