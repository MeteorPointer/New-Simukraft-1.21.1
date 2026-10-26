package common.cn.nsimu.simukraft.logistics;

import common.cn.nsimu.simukraft.citizen.CitizenData;
import common.cn.nsimu.simukraft.job.CitizenEmploymentService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * LogisticsMoveService: 建筑移动时保住物流盒，并把仓库、客户端和绑定容器改到新坐标。
 */
public final class LogisticsMoveService {
    private static final ThreadLocal<Set<BlockPos>> PRESERVED_BOXES = new ThreadLocal<>();

    private LogisticsMoveService() {
    }

    /**
     * preserve: 这些旧坐标上的物流盒正在随建筑移动，移除方块时不要删路线。
     */
    public static void preserve(Set<BlockPos> oldPositions) {
        PRESERVED_BOXES.set(oldPositions);
    }

    /**
     * clearPreserve: 移动结束，恢复正常的拆除清理。
     */
    public static void clearPreserve() {
        PRESERVED_BOXES.remove();
    }

    /**
     * isPreserved: 当前移除的物流盒是否属于正在移动的建筑。
     */
    public static boolean isPreserved(BlockPos pos) {
        Set<BlockPos> preserved = PRESERVED_BOXES.get();
        return preserved != null && pos != null && preserved.contains(pos);
    }

    /**
     * relocate: 把落在旧坐标上的仓库、客户端、容器和仓储管理员改到新坐标。路线仍用原来的 id。
     */
    public static void relocate(ServerLevel level, Map<BlockPos, BlockPos> moved) {
        if (level == null || moved == null || moved.isEmpty()) {
            return;
        }
        LogisticsManager manager = LogisticsManager.get(level);
        long now = level.getGameTime();
        List<LogisticsWarehouseData> warehouses = new ArrayList<>();
        for (LogisticsWarehouseData warehouse : List.copyOf(manager.warehouses())) {
            BlockPos nextBox = moved.getOrDefault(warehouse.boxPos(), warehouse.boxPos());
            List<BlockPos> containers = remap(warehouse.containers(), moved);
            if (nextBox.equals(warehouse.boxPos()) && containers.equals(warehouse.containers())) {
                continue;
            }
            warehouses.add(new LogisticsWarehouseData(
                    warehouse.warehouseId(), nextBox, warehouse.cityId(), warehouse.dimensionId(), containers, now));
            if (!nextBox.equals(warehouse.boxPos())) {
                moveStorageWorker(level, warehouse.boxPos(), nextBox);
            }
        }
        List<LogisticsClientData> clients = new ArrayList<>();
        for (LogisticsClientData client : List.copyOf(manager.clients())) {
            if (client.automatic()) {
                continue;
            }
            BlockPos nextBox = moved.getOrDefault(client.boxPos(), client.boxPos());
            List<LogisticsPortData> ports = new ArrayList<>(client.ports().size());
            boolean portMoved = false;
            for (LogisticsPortData port : client.ports()) {
                BlockPos next = moved.getOrDefault(port.pos(), port.pos());
                portMoved |= !next.equals(port.pos());
                ports.add(new LogisticsPortData(port.id(), port.name(), port.kind(), next));
            }
            if (nextBox.equals(client.boxPos()) && !portMoved) {
                continue;
            }
            clients.add(new LogisticsClientData(
                    client.clientId(), nextBox, client.cityId(), client.dimensionId(), client.name(),
                    client.automatic(), client.sourceType(), client.sourceId(), ports, now));
        }
        if (!warehouses.isEmpty() || !clients.isEmpty()) {
            manager.relocate(warehouses, clients);
        }
    }

    private static List<BlockPos> remap(List<BlockPos> positions, Map<BlockPos, BlockPos> moved) {
        List<BlockPos> next = new ArrayList<>(positions.size());
        for (BlockPos pos : positions) {
            next.add(moved.getOrDefault(pos, pos));
        }
        return List.copyOf(next);
    }

    private static void moveStorageWorker(ServerLevel level, BlockPos oldBox, BlockPos newBox) {
        CitizenData worker = LogisticsControlBoxService.findAssignedStorageWorker(level, oldBox);
        if (worker == null) {
            return;
        }
        CitizenEmploymentService.assignForSource(level, worker.uuid(), LogisticsConstants.SERVER_SOURCE_TYPE,
                LogisticsConstants.STORAGE_ROLE, newBox, worker.workStatusType(), worker.statusLabel());
    }
}
