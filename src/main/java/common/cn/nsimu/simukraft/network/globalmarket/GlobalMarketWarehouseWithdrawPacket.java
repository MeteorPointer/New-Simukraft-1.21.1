package common.cn.nsimu.simukraft.network.globalmarket;

import common.cn.nsimu.simukraft.SimuKraft;
import common.cn.nsimu.simukraft.city.CityService;
import common.cn.nsimu.simukraft.globalmarket.GlobalMarketControlBoxService;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * GlobalMarketWarehouseWithdrawPacket: 从商店仓库取出物品到玩家背包。
 */
public record GlobalMarketWarehouseWithdrawPacket(BlockPos pos, ItemStack template,
                                                  int count) implements CustomPacketPayload {
    public static final Type<GlobalMarketWarehouseWithdrawPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "global_market_warehouse_withdraw"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GlobalMarketWarehouseWithdrawPacket> STREAM_CODEC = StreamCodec.of(GlobalMarketWarehouseWithdrawPacket::encode, GlobalMarketWarehouseWithdrawPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, GlobalMarketWarehouseWithdrawPacket packet) {
        buffer.writeBlockPos(packet.pos());
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, packet.template() == null ? ItemStack.EMPTY : packet.template());
        buffer.writeVarInt(Math.max(0, packet.count()));
    }

    public static GlobalMarketWarehouseWithdrawPacket decode(RegistryFriendlyByteBuf buffer) {
        return new GlobalMarketWarehouseWithdrawPacket(buffer.readBlockPos(), ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer), buffer.readVarInt());
    }

    public static void handle(GlobalMarketWarehouseWithdrawPacket packet, IPayloadContext context) {
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
        if (building == null || building.cityId() == null || building.buildingId() == null
                || !CityService.canManageCity(level, building.cityId(), player.getUUID())) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.global_market.no_permission"));
            return;
        }
        java.util.List<ItemStack> before = CommercialTradePacket.snapshotInventory(player);
        GlobalMarketControlBoxService.prepareWarehouse(level, building);
        ItemStack given = GlobalMarketWarehouseService.extract(level, building.buildingId(), packet.template(), packet.count());
        if (given.isEmpty()) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.global_market.warehouse_not_enough"));
            return;
        }
        giveToPlayer(player, given);
        CommercialTradePacket.syncChangedSlots(player, before);
        PacketDistributor.sendToPlayer(player, CommercialTradeOpenResponsePacket.from(GlobalMarketService.buildView(level, player, packet.pos()), player.registryAccess()));
    }

    private static void giveToPlayer(ServerPlayer player, ItemStack given) {
        int remaining = given.getCount();
        ItemStack template = given.copy();
        int max = Math.max(1, template.getMaxStackSize());
        while (remaining > 0) {
            ItemStack stack = template.copy();
            int n = Math.min(remaining, max);
            stack.setCount(n);
            if (!player.getInventory().add(stack) && !stack.isEmpty()) {
                player.level().addFreshEntity(new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), stack));
            }
            remaining -= n;
        }
    }
}
