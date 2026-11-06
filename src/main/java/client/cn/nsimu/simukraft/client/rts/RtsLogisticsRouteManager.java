package client.cn.nsimu.simukraft.client.rts;

import client.cn.nsimu.simukraft.client.buildbox.BuildingBoundsRenderer;
import common.cn.nsimu.simukraft.network.rts.RtsLogisticsRoutesRequestPacket;
import common.cn.nsimu.simukraft.network.rts.RtsLogisticsRoutesSyncPacket;
import common.cn.nsimu.simukraft.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * RTS 物流线路：光标停在物流盒上时向服务端要该节点的线路。
 */
@OnlyIn(Dist.CLIENT)
public final class RtsLogisticsRouteManager {
    private static BlockPos requested;
    private static List<RtsLogisticsRoutesSyncPacket.Line> lines = List.of();

    private RtsLogisticsRouteManager() {
    }

    /**
     * tick: 光标换到另一座物流盒，或停在原处时定期刷新线路。
     */
    public static void tick(Minecraft minecraft) {
        if (minecraft == null || minecraft.level == null || minecraft.player == null || minecraft.screen != null) {
            clear();
            return;
        }
        BlockPos target = RtsSelectionManager.cursorTargetPos();
        BlockPos anchor = queryAnchor(minecraft, target);
        if (anchor == null) {
            clear();
            return;
        }
        boolean same = anchor.equals(requested);
        if (!same || minecraft.player.tickCount % 20 == 0) {
            requested = anchor.immutable();
            PacketDistributor.sendToServer(new RtsLogisticsRoutesRequestPacket(requested));
        }
    }

    /**
     * queryAnchor: 物流盒用自身坐标。建筑用边界角，避免在建筑里每格都重发请求。
     */
    private static BlockPos queryAnchor(Minecraft minecraft, BlockPos target) {
        if (target == null) {
            return null;
        }
        if (isLogisticsBox(minecraft, target)) {
            return target;
        }
        BuildingBoundsRenderer.RtsBuildingBounds building = BuildingBoundsRenderer.hoveredPlacedBuilding();
        if (building == null || building.bounds() == null || !building.bounds().contains(net.minecraft.world.phys.Vec3.atCenterOf(target))) {
            return null;
        }
        return BlockPos.containing(building.bounds().minX, building.bounds().minY, building.bounds().minZ);
    }

    /**
     * apply: 只接收当前光标那座物流盒的回包。
     */
    public static void apply(RtsLogisticsRoutesSyncPacket packet) {
        if (packet == null || requested == null || packet.anchor() == null || !requested.equals(packet.anchor())) {
            return;
        }
        lines = packet.lines() == null ? List.of() : List.copyOf(packet.lines());
    }

    public static void clear() {
        requested = null;
        lines = List.of();
    }

    public static List<RtsLogisticsRoutesSyncPacket.Line> lines() {
        return lines;
    }

    private static boolean isLogisticsBox(Minecraft minecraft, BlockPos pos) {
        if (pos == null || minecraft.level == null) {
            return false;
        }
        BlockState state = minecraft.level.getBlockState(pos);
        return state.is(ModBlocks.LOGISTICS_SERVER_BOX.get()) || state.is(ModBlocks.LOGISTICS_CLIENT_BOX.get());
    }
}
