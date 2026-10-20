package common.cn.nsimu.simukraft.network.globalmarket;

import common.cn.nsimu.simukraft.SimuKraft;
import common.cn.nsimu.simukraft.city.CityService;
import common.cn.nsimu.simukraft.globalmarket.GlobalMarketService;
import common.cn.nsimu.simukraft.globalmarket.GlobalMarketWarehouseService;
import common.cn.nsimu.simukraft.network.commercial.CommercialTradeOpenResponsePacket;
import common.cn.nsimu.simukraft.network.commercial.CommercialTradePacket;
import common.cn.nsimu.simukraft.network.toast.InfoToastService;
import common.cn.nsimu.simukraft.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * GlobalMarketWarehouseDepositPacket: 把玩家背包物品存进商店仓库。
 */
public record GlobalMarketWarehouseDepositPacket(BlockPos pos, ItemStack stack) implements CustomPacketPayload {
    public static final Type<GlobalMarketWarehouseDepositPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "global_market_warehouse_deposit"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GlobalMarketWarehouseDepositPacket> STREAM_CODEC = StreamCodec.of(GlobalMarketWarehouseDepositPacket::encode, GlobalMarketWarehouseDepositPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, GlobalMarketWarehouseDepositPacket packet) {
        buffer.writeBlockPos(packet.pos());
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, packet.stack() == null ? ItemStack.EMPTY : packet.stack());
    }

    public static GlobalMarketWarehouseDepositPacket decode(RegistryFriendlyByteBuf buffer) {
        return new GlobalMarketWarehouseDepositPacket(buffer.readBlockPos(), ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer));
    }

    public static void handle(GlobalMarketWarehouseDepositPacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (!player.blockPosition().closerThan(packet.pos(), 16.0D)) {
            return;
        }
        if (!level.getBlockState(packet.pos()).is(ModBlocks.GLOBAL_MARKET_CONTROL_BOX.get())
                || !GlobalMarketService.isGlobalMarketBox(level, packet.pos())) {
            return;
        }
        var building = GlobalMarketService.resolveBuilding(level, packet.pos());
        if (building == null || building.cityId() == null || !CityService.canManageCity(level, building.cityId(), player.getUUID())) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.global_market.no_permission"));
            return;
        }
        ItemStack incoming = packet.stack();
        if (incoming == null || incoming.isEmpty()) {
            return;
        }
        java.util.List<ItemStack> before = CommercialTradePacket.snapshotInventory(player);
        ItemStack taken = incoming.copy();
        if (!takeFromPlayer(player, taken)) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.commercial.not_enough_items"));
            return;
        }
        GlobalMarketWarehouseService.insert(level, building.cityId(), taken);
        CommercialTradePacket.syncChangedSlots(player, before);
        PacketDistributor.sendToPlayer(player, CommercialTradeOpenResponsePacket.from(GlobalMarketService.buildView(level, player, packet.pos()), player.registryAccess()));
    }

    private static boolean takeFromPlayer(ServerPlayer player, ItemStack listed) {
        int remaining = listed.getCount();
        int have = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (ItemStack.isSameItemSameComponents(stack, listed)) {
                have += stack.getCount();
            }
        }
        if (have < remaining) {
            return false;
        }
        for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!ItemStack.isSameItemSameComponents(stack, listed)) {
                continue;
            }
            int take = Math.min(remaining, stack.getCount());
            stack.shrink(take);
            remaining -= take;
        }
        return remaining <= 0;
    }
}
