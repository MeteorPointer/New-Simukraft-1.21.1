package common.cn.nsimu.simukraft.debugcity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 调试城市的网格尺寸、岗位轮换和地块坐标。
 */
public final class DebugCityLayout {
    public static final int MAX_CITIZENS = 4096;
    public static final int MAX_SCALE = 128;
    public static final int DEFAULT_CITIZENS = 64;
    public static final int DEFAULT_SCALE = 8;
    public static final int CELL_SIZE = 16;
    public static final int ROAD_MARGIN = 2;
    public static final int SURFACE_Y = 64;
    public static final int PLOT_SIZE = 5;
    public static final int HOUSE_LOCAL_X = 2;
    public static final int HOUSE_LOCAL_Z = 2;
    public static final int WORK_LOCAL_X = 9;
    public static final int WORK_LOCAL_Z = 2;
    public static final int HOUSE_DOOR_DX = 4;
    public static final int HOUSE_DOOR_DZ = 2;
    public static final int WORK_DOOR_DX = 0;
    public static final int WORK_DOOR_DZ = 2;

    private DebugCityLayout() {
    }

    /**
     * clampCitizens：把居民数量限制在可生成范围内。
     */
    public static int clampCitizens(int citizenCount) {
        return Math.max(1, Math.min(MAX_CITIZENS, citizenCount));
    }

    /**
     * clampScale：把城市边长限制在可生成范围内。
     */
    public static int clampScale(int cityScale) {
        return Math.max(1, Math.min(MAX_SCALE, cityScale));
    }

    /**
     * sideLength：网格边长至少能放下城市核心和全部居民地块。
     */
    public static int sideLength(int citizenCount, int cityScale) {
        int citizens = clampCitizens(citizenCount);
        int side = Math.max(clampScale(cityScale), 2);
        while (side < MAX_SCALE && homeCells(side).size() < citizens) {
            side++;
        }
        return side;
    }

    /**
     * workCell：工作地点紧挨住宅，偶数行在东边，奇数行在西边，和住宅交叉铺开。
     */
    public static Cell workCell(Cell home, int side) {
        int row = home.chunkZ() - originChunk(side);
        if (Math.floorMod(row, 2) == 0) {
            return new Cell(home.chunkX() + 1, home.chunkZ());
        }
        return new Cell(home.chunkX() - 1, home.chunkZ());
    }

    /**
     * originChunk：网格西北角的区块坐标。
     */
    public static int originChunk(int side) {
        return -Math.max(1, side) / 2;
    }

    /**
     * centerChunk：城市核心所在区块，固定落在原点附近。
     */
    public static int centerChunk(int side) {
        return originChunk(side) + Math.max(1, side) / 2;
    }

    /**
     * isRoad：区块边缘两格是道路。
     */
    public static boolean isRoad(int worldX, int worldZ) {
        int localX = Math.floorMod(worldX, CELL_SIZE);
        int localZ = Math.floorMod(worldZ, CELL_SIZE);
        return localX < ROAD_MARGIN || localX >= CELL_SIZE - ROAD_MARGIN
                || localZ < ROAD_MARGIN || localZ >= CELL_SIZE - ROAD_MARGIN;
    }

    /**
     * occupiedCells：按离核心的方格距离挑出每个居民的一块地。
     */
    public static List<Cell> occupiedCells(int citizenCount, int cityScale) {
        int citizens = clampCitizens(citizenCount);
        int side = sideLength(citizens, cityScale);
        List<Cell> cells = homeCells(side);
        int center = centerChunk(side);
        cells.sort(Comparator
                .comparingInt((Cell cell) -> Math.max(Math.abs(cell.chunkX() - center), Math.abs(cell.chunkZ() - center)))
                .thenComparingInt(Cell::chunkX)
                .thenComparingInt(Cell::chunkZ));
        if (cells.size() > citizens) {
            return List.copyOf(cells.subList(0, citizens));
        }
        return List.copyOf(cells);
    }

    /**
     * homeCells：网格里可以放住宅的区块。工作区块落在网格外、市中心或另一户住宅上时不用。
     */
    static List<Cell> homeCells(int side) {
        int origin = originChunk(side);
        int center = centerChunk(side);
        List<Cell> cells = new ArrayList<>();
        for (int row = 0; row < side; row++) {
            boolean evenRow = Math.floorMod(row, 2) == 0;
            for (int col = 0; col < side; col++) {
                boolean homeColumn = evenRow ? Math.floorMod(col, 2) == 0 : Math.floorMod(col, 2) == 1;
                if (!homeColumn) {
                    continue;
                }
                int chunkX = origin + col;
                int chunkZ = origin + row;
                if (chunkX == center && chunkZ == center) {
                    continue;
                }
                Cell home = new Cell(chunkX, chunkZ);
                Cell work = workCell(home, side);
                if (work.chunkX() == center && work.chunkZ() == center) {
                    continue;
                }
                if (work.chunkX() < origin || work.chunkZ() < origin
                        || work.chunkX() >= origin + side || work.chunkZ() >= origin + side) {
                    continue;
                }
                cells.add(home);
            }
        }
        return cells;
    }

    /**
     * workplaceKind：居民依次获得工业、商业或公共岗位。
     */
    public static WorkplaceKind workplaceKind(int citizenIndex) {
        int safeIndex = Math.max(0, citizenIndex);
        int group = Math.floorMod(safeIndex, 3);
        if (group == 0) {
            return WorkplaceKind.INDUSTRIAL;
        }
        if (group == 1) {
            return WorkplaceKind.COMMERCIAL;
        }
        return switch (Math.floorMod(safeIndex / 3, 4)) {
            case 0 -> WorkplaceKind.MEDICAL;
            case 1 -> WorkplaceKind.BANK;
            case 2 -> WorkplaceKind.EXCHANGE;
            default -> WorkplaceKind.GLOBAL_MARKET;
        };
    }

    /**
     * worldX：把地块内的局部坐标换成世界坐标。
     */
    public static int worldX(Cell cell, int localX) {
        return cell.chunkX() * CELL_SIZE + localX;
    }

    /**
     * worldZ：把地块内的局部坐标换成世界坐标。
     */
    public static int worldZ(Cell cell, int localZ) {
        return cell.chunkZ() * CELL_SIZE + localZ;
    }

    /**
     * Cell：一个 16×16 的城市地块。
     */
    public record Cell(int chunkX, int chunkZ) {
    }

    /**
     * WorkplaceKind：调试居民的三类岗位，公共岗位再分到医院、银行、交易所和全球市场。
     */
    public enum WorkplaceKind {
        INDUSTRIAL,
        COMMERCIAL,
        MEDICAL,
        BANK,
        EXCHANGE,
        GLOBAL_MARKET
    }
}
