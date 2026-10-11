package client.cn.kafei.simukraft.client.city.map;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

import java.util.Arrays;

/**
 * 城市核心立体沙盘：每格一块完整立方柱，顶面、立面和底边分开着色。
 * 顶点留在方块坐标系里，屏幕投影由调用方的正交矩阵完成。
 */
@OnlyIn(Dist.CLIENT)
public final class SimuMap3DMesh {
    public static final int MAX_CELLS = 80;
    public static final long CACHE_TICKS = 40L;
    public static final int REBUILD_SLACK = 8;
    private static final int SKIRT_DEPTH = 10;
    private static final int BASE_COLOR = 0xFF101418;
    private static final float TOP_SHADE = 0.96F;
    private static final int MAX_GREEDY_SPAN = 8;

    public interface Source {
        int height(int worldX, int worldZ);
        int terrainColor(int worldX, int worldZ);
        int overlay(int worldX, int worldZ);
        boolean core(int worldX, int worldZ);
    }

    private int[] heights = new int[0];
    private int[] colors = new int[0];
    private boolean[] visited = new boolean[0];
    private int cells;
    private int step = 1;
    private int lodStep;
    private int originX;
    private int originZ;
    private int centerX = Integer.MIN_VALUE;
    private int centerZ = Integer.MIN_VALUE;
    private int baseHeight = 64;
    private int skirtBottom = 52;
    private long cachedGameTime = Long.MIN_VALUE;
    private final CellPick pick = new CellPick();

    private float[] fx = new float[0];
    private float[] fy = new float[0];
    private float[] fz = new float[0];
    private int[] fc = new int[0];
    private int faceCount;

    public boolean isEmpty() {
        return faceCount <= 0;
    }

    public int cellCount() {
        return cells;
    }

    public int faceCount() {
        return faceCount;
    }

    public int heightAt(int ix, int iz) {
        if (ix < 0 || iz < 0 || ix >= cells || iz >= cells) {
            return Integer.MIN_VALUE;
        }
        return heights[ix + iz * cells];
    }

    public int colorAt(int ix, int iz) {
        if (ix < 0 || iz < 0 || ix >= cells || iz >= cells) {
            return 0;
        }
        return colors[ix + iz * cells];
    }

    public int worldXOf(int ix) {
        return originX + ix * step;
    }

    public int worldZOf(int iz) {
        return originZ + iz * step;
    }

    public int resolveStep(double zoom) {
        lodStep = lodStepWithHysteresis(zoom, lodStep);
        return lodStep;
    }

    public boolean needsRebuild(int liveCenterX, int liveCenterZ, int step, long gameTime) {
        int slack = Math.max(Math.max(1, step) * REBUILD_SLACK, REBUILD_SLACK);
        return this.step != step
                || cells <= 0
                || faceCount <= 0
                || Math.abs(this.centerX - liveCenterX) > slack
                || Math.abs(this.centerZ - liveCenterZ) > slack
                || gameTime - cachedGameTime >= CACHE_TICKS;
    }

    public void invalidate() {
        centerX = Integer.MIN_VALUE;
        cachedGameTime = Long.MIN_VALUE;
        lodStep = 0;
        faceCount = 0;
    }

    public int baseHeight() {
        return baseHeight;
    }

    public void rebuild(Source source, int centerBlockX, int centerBlockZ, int step, long gameTime) {
        int newStep = Math.max(1, step);
        int newCells = MAX_CELLS;
        int size = newCells * newCells;
        if (heights.length != size) {
            heights = new int[size];
            colors = new int[size];
            visited = new boolean[size];
        }
        this.cells = newCells;
        this.step = newStep;
        this.originX = centerBlockX - (newCells / 2) * newStep;
        this.originZ = centerBlockZ - (newCells / 2) * newStep;
        this.centerX = centerBlockX;
        this.centerZ = centerBlockZ;
        this.cachedGameTime = gameTime;
        Arrays.fill(heights, Integer.MIN_VALUE);
        Arrays.fill(colors, 0);

        int heightSum = 0;
        int heightCount = 0;
        int minHeight = Integer.MAX_VALUE;
        for (int iz = 0; iz < newCells; iz++) {
            for (int ix = 0; ix < newCells; ix++) {
                int worldX = originX + ix * newStep;
                int worldZ = originZ + iz * newStep;
                pickCell(source, worldX, worldZ, newStep, pick);
                int idx = ix + iz * newCells;
                heights[idx] = pick.height;
                if (pick.height == Integer.MIN_VALUE) {
                    continue;
                }
                heightSum += pick.height;
                heightCount++;
                minHeight = Math.min(minHeight, pick.height);
                int albedo = source.terrainColor(pick.worldX, pick.worldZ);
                if (source.core(worldX, worldZ)) {
                    albedo = SimuBlockColors.blendColors(albedo, SimuBlockColors.CORE_HIGHLIGHT_OVERLAY);
                } else {
                    int overlay = source.overlay(worldX, worldZ);
                    if ((overlay >>> 24) != 0) {
                        albedo = SimuBlockColors.blendColors(albedo, overlay);
                    }
                }
                colors[idx] = SimuBlockColors.opaqueTerrainColor(albedo);
            }
        }
        baseHeight = heightCount == 0 ? 64 : Math.round(heightSum / (float) heightCount);
        skirtBottom = (heightCount == 0 ? 64 : minHeight) - SKIRT_DEPTH;
        shadeTops();
        buildFaces();
    }

    /**
     * emit: 按方块坐标写出顶点。深度测试负责遮挡，调用方负责旋转和正交投影。
     */
    public void emit(BufferBuilder buffer, Matrix4f matrix, float shiftX, float shiftZ) {
        if (faceCount <= 0) {
            return;
        }
        for (int i = 0; i < faceCount; i++) {
            int o = i * 4;
            quad(buffer, matrix,
                    fx[o] + shiftX, fy[o], fz[o] + shiftZ, fc[o],
                    fx[o + 1] + shiftX, fy[o + 1], fz[o + 1] + shiftZ, fc[o + 1],
                    fx[o + 2] + shiftX, fy[o + 2], fz[o + 2] + shiftZ, fc[o + 2],
                    fx[o + 3] + shiftX, fy[o + 3], fz[o + 3] + shiftZ, fc[o + 3]);
        }
    }

    public static int lodStep(double zoom) {
        if (zoom >= 3.2D) {
            return 1;
        }
        if (zoom >= 1.8D) {
            return 2;
        }
        if (zoom >= 1.0D) {
            return 4;
        }
        return 8;
    }

    public static int lodStepWithHysteresis(double zoom, int current) {
        int desired = lodStep(zoom);
        if (desired == current || current <= 0) {
            return desired;
        }
        double hold = lodZoom(Math.min(desired, current));
        if (desired < current) {
            return zoom >= hold + 0.18D ? desired : current;
        }
        return zoom <= hold - 0.18D ? desired : current;
    }

    public static int snapCoord(int value, int step) {
        int size = Math.max(1, step);
        return Math.floorDiv(value, size) * size;
    }

    public static int pickCellHeight(Source source, int worldX, int worldZ, int step) {
        CellPick result = new CellPick();
        pickCell(source, worldX, worldZ, step, result);
        return result.height;
    }

    static double lodZoom(int step) {
        return switch (step) {
            case 1 -> 3.2D;
            case 2 -> 1.8D;
            case 4 -> 1.0D;
            default -> 0.55D;
        };
    }

    static void pickCell(Source source, int worldX, int worldZ, int step, CellPick out) {
        int inner = Math.max(1, step);
        int bestH = Integer.MIN_VALUE;
        int bestX = worldX;
        int bestZ = worldZ;
        for (int dz = 0; dz < inner; dz++) {
            for (int dx = 0; dx < inner; dx++) {
                int h = source.height(worldX + dx, worldZ + dz);
                if (h > bestH) {
                    bestH = h;
                    bestX = worldX + dx;
                    bestZ = worldZ + dz;
                }
            }
        }
        out.height = bestH;
        out.worldX = bestX;
        out.worldZ = bestZ;
    }

    private void shadeTops() {
        int n = cells;
        for (int iz = 0; iz < n; iz++) {
            for (int ix = 0; ix < n; ix++) {
                int idx = ix + iz * n;
                int height = heights[idx];
                if (height == Integer.MIN_VALUE) {
                    continue;
                }
                int north = sampleHeight(ix, iz - 1, height);
                int west = sampleHeight(ix - 1, iz, height);
                int south = sampleHeight(ix, iz + 1, height);
                int east = sampleHeight(ix + 1, iz, height);
                float shade = TOP_SHADE + SimuBlockColors.heightOcclusion(height, north, west, south, east);
                colors[idx] = SimuBlockColors.scaleColor(colors[idx], Mth.clamp(shade, 0.52F, 1.05F));
            }
        }
    }

    private void buildFaces() {
        faceCount = 0;
        Arrays.fill(visited, false);
        int n = cells;
        for (int iz = 0; iz < n; iz++) {
            for (int ix = 0; ix < n; ix++) {
                int idx = ix + iz * n;
                if (visited[idx] || heights[idx] == Integer.MIN_VALUE) {
                    continue;
                }
                int h = heights[idx];
                int c = colors[idx];
                int width = 1;
                while (ix + width < n && width < MAX_GREEDY_SPAN) {
                    int next = ix + width + iz * n;
                    if (visited[next] || heights[next] != h || colors[next] != c) {
                        break;
                    }
                    width++;
                }
                int depth = 1;
                expandZ:
                while (iz + depth < n && depth < MAX_GREEDY_SPAN) {
                    for (int dx = 0; dx < width; dx++) {
                        int next = ix + dx + (iz + depth) * n;
                        if (visited[next] || heights[next] != h || colors[next] != c) {
                            break expandZ;
                        }
                    }
                    depth++;
                }
                for (int zz = 0; zz < depth; zz++) {
                    for (int xx = 0; xx < width; xx++) {
                        visited[ix + xx + (iz + zz) * n] = true;
                    }
                }
                float x0 = originX + ix * step - centerX;
                float z0 = originZ + iz * step - centerZ;
                float x1 = x0 + width * step;
                float z1 = z0 + depth * step;
                addQuad(x0, z0, h, c, x1, z0, h, c, x1, z1, h, c, x0, z1, h, c);
            }
        }
        for (int iz = 0; iz < n; iz++) {
            for (int ix = 0; ix < n; ix++) {
                if (heights[ix + iz * n] == Integer.MIN_VALUE) {
                    continue;
                }
                addCliff(ix, iz, ix + 1, iz, 1, 0);
                addCliff(ix, iz, ix - 1, iz, -1, 0);
                addCliff(ix, iz, ix, iz + 1, 0, 1);
                addCliff(ix, iz, ix, iz - 1, 0, -1);
            }
        }
    }

    private void addCliff(int ix, int iz, int nix, int niz, int nx, int nz) {
        int h = heights[ix + iz * cells];
        boolean outside = nix < 0 || niz < 0 || nix >= cells || niz >= cells;
        int neighbor = outside ? Integer.MIN_VALUE : heights[nix + niz * cells];
        if (neighbor != Integer.MIN_VALUE && neighbor >= h) {
            return;
        }
        int bottom = neighbor == Integer.MIN_VALUE ? skirtBottom : neighbor;
        int wall = SimuBlockColors.scaleColor(colors[ix + iz * cells], wallLight(nx, nz));
        int foot = neighbor == Integer.MIN_VALUE ? BASE_COLOR : SimuBlockColors.scaleColor(wall, 0.72F);
        float x0;
        float z0;
        float x1;
        float z1;
        if (nx > 0) {
            x0 = originX + (ix + 1) * step - centerX;
            x1 = x0;
            z0 = originZ + iz * step - centerZ;
            z1 = z0 + step;
        } else if (nx < 0) {
            x0 = originX + ix * step - centerX;
            x1 = x0;
            z0 = originZ + (iz + 1) * step - centerZ;
            z1 = originZ + iz * step - centerZ;
        } else if (nz > 0) {
            z0 = originZ + (iz + 1) * step - centerZ;
            z1 = z0;
            x0 = originX + (ix + 1) * step - centerX;
            x1 = originX + ix * step - centerX;
        } else {
            z0 = originZ + iz * step - centerZ;
            z1 = z0;
            x0 = originX + ix * step - centerX;
            x1 = x0 + step;
        }
        addQuad(x0, z0, h, wall, x1, z1, h, wall, x1, z1, bottom, foot, x0, z0, bottom, foot);
    }

    private void addQuad(float x0, float z0, int h0, int c0,
                         float x1, float z1, int h1, int c1,
                         float x2, float z2, int h2, int c2,
                         float x3, float z3, int h3, int c3) {
        ensureFaceCapacity();
        int o = faceCount * 4;
        fx[o] = x0;
        fy[o] = h0;
        fz[o] = z0;
        fc[o] = c0;
        fx[o + 1] = x1;
        fy[o + 1] = h1;
        fz[o + 1] = z1;
        fc[o + 1] = c1;
        fx[o + 2] = x2;
        fy[o + 2] = h2;
        fz[o + 2] = z2;
        fc[o + 2] = c2;
        fx[o + 3] = x3;
        fy[o + 3] = h3;
        fz[o + 3] = z3;
        fc[o + 3] = c3;
        faceCount++;
    }

    private void ensureFaceCapacity() {
        int needed = (faceCount + 1) * 4;
        if (needed <= fx.length) {
            return;
        }
        int cap = Math.max(256, Math.max(needed, fx.length * 2));
        fx = Arrays.copyOf(fx, cap);
        fy = Arrays.copyOf(fy, cap);
        fz = Arrays.copyOf(fz, cap);
        fc = Arrays.copyOf(fc, cap);
    }

    private int sampleHeight(int ix, int iz, int fallback) {
        if (ix < 0 || iz < 0 || ix >= cells || iz >= cells) {
            return fallback;
        }
        int height = heights[ix + iz * cells];
        return height == Integer.MIN_VALUE ? fallback : height;
    }

    private static float wallLight(int nx, int nz) {
        if (nx > 0) {
            return 0.40F;
        }
        if (nx < 0) {
            return 0.78F;
        }
        if (nz > 0) {
            return 0.34F;
        }
        return 0.66F;
    }

    private static void quad(BufferBuilder buffer, Matrix4f matrix,
                             float x0, float y0, float z0, int c0,
                             float x1, float y1, float z1, int c1,
                             float x2, float y2, float z2, int c2,
                             float x3, float y3, float z3, int c3) {
        buffer.addVertex(matrix, x0, y0, z0).setColor(SimuBlockColors.opaqueTerrainColor(c0));
        buffer.addVertex(matrix, x1, y1, z1).setColor(SimuBlockColors.opaqueTerrainColor(c1));
        buffer.addVertex(matrix, x2, y2, z2).setColor(SimuBlockColors.opaqueTerrainColor(c2));
        buffer.addVertex(matrix, x3, y3, z3).setColor(SimuBlockColors.opaqueTerrainColor(c3));
    }

    static final class CellPick {
        int height;
        int worldX;
        int worldZ;
    }
}
