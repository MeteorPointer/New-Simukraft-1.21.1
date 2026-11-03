package client.cn.nsimu.simukraft.client.buildbox;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import common.cn.nsimu.simukraft.building.BuildingBlockData;
import common.cn.nsimu.simukraft.building.BuildingStructureService;
import common.cn.nsimu.simukraft.building.PlacedBuildingRecord;
import common.cn.nsimu.simukraft.building.PlacedBuildingService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 控制盒第三档：按已保存的建筑方块对照世界。正确的不画，缺的画更透的原方块。
 */
@OnlyIn(Dist.CLIENT)
final class BuildingBoundsPreview {
    private static final float GHOST_ALPHA = 0.60F;
    private static final int COLOR_WRONG = 0x48FF3333;
    private static final int COLOR_STATE = 0x48FF8800;
    private static final long CHECK_INTERVAL_TICKS = 2L;
    private static final Map<BlockPos, Cache> CACHES = new ConcurrentHashMap<>();

    private BuildingBoundsPreview() {
    }

    /**
     * prepare：切到预览时就在当前帧把网格做好，世界渲染不用再等。
     */
    static void prepare(BlockPos controlBoxPos, AABB bounds) {
        Minecraft minecraft = Minecraft.getInstance();
        if (controlBoxPos == null || bounds == null || minecraft.level == null) {
            return;
        }
        Cache cache = CACHES.computeIfAbsent(controlBoxPos.immutable(), ignored -> new Cache());
        rebuild(minecraft, bounds, cache);
    }

    static void render(RenderLevelStageEvent event, Map<BlockPos, BuildingBoundsRenderer.DisplayedBuildingBounds> displayed) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        long gameTime = minecraft.level.getGameTime();
        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPos = event.getCamera().getPosition();
        for (var entry : displayed.entrySet()) {
            BuildingBoundsRenderer.DisplayedBuildingBounds shown = entry.getValue();
            if (shown.mode() != BuildingBoundsRenderer.BuildingBoundsMode.PREVIEW) {
                continue;
            }
            if (!inView(minecraft, cameraPos, shown.bounds())) {
                continue;
            }
            Cache cache = CACHES.computeIfAbsent(entry.getKey(), ignored -> new Cache());
            if (cache.mesh == null) {
                rebuild(minecraft, shown.bounds(), cache);
            } else if (gameTime - cache.checkedAt >= CHECK_INTERVAL_TICKS && signatureChanged(minecraft.level, cache)) {
                rebuild(minecraft, shown.bounds(), cache);
            } else if (gameTime - cache.checkedAt >= CHECK_INTERVAL_TICKS) {
                cache.checkedAt = gameTime;
            }
            BuildingPreviewRenderer.renderFaded(cache.mesh, event.getModelViewMatrix(), event.getProjectionMatrix(), cameraPos, GHOST_ALPHA);
            renderMarks(poseStack, cameraPos, cache.marks, cache.covered, minecraft.level);
        }
    }

    static void clear(BlockPos controlBoxPos) {
        Cache removed = controlBoxPos == null ? null : CACHES.remove(controlBoxPos.immutable());
        if (removed != null) {
            closeMesh(removed);
        }
    }

    static void clearAll() {
        CACHES.values().forEach(BuildingBoundsPreview::closeMesh);
        CACHES.clear();
    }

    private static void closeMesh(Cache cache) {
        if (cache.mesh != null && cache.mesh != PreviewMesh.EMPTY) {
            cache.mesh.close();
        }
        cache.mesh = PreviewMesh.EMPTY;
    }

    private static void rebuild(Minecraft minecraft, AABB bounds, Cache cache) {
        closeMesh(cache);
        cache.checkedAt = minecraft.level == null ? 0L : minecraft.level.getGameTime();
        cache.marks = List.of();
        cache.mesh = PreviewMesh.EMPTY;
        PlacedBuildingRecord building = findBuilding(minecraft, bounds);
        if (building == null || minecraft.level == null) {
            return;
        }
        List<BuildingBlockData> expected = cache.expected == null || cache.expected.isEmpty()
                ? List.copyOf(resolveExpected(building))
                : cache.expected;
        List<Mark> marks = new ArrayList<>();
        List<PreviewBlockData> ghosts = new ArrayList<>();
        Set<Long> covered = new HashSet<>();
        for (BuildingBlockData block : expected) {
            if (block.state() == null || block.state().isAir()) {
                continue;
            }
            BlockPos pos = block.relativePos();
            BlockState world = minecraft.level.getBlockState(pos);
            if (world.equals(block.state())) {
                continue;
            }
            covered.add(pos.asLong());
            if (world.getBlock() == block.state().getBlock()) {
                marks.add(new Mark(pos, COLOR_STATE));
                continue;
            }
            if (!world.isAir()) {
                marks.add(new Mark(pos, COLOR_WRONG));
                continue;
            }
            ghosts.add(new PreviewBlockData(pos, block.state(), 15728880, block.copyBlockEntityData()));
        }
        cache.expected = expected;
        cache.marks = List.copyOf(marks);
        cache.covered = covered;
        cache.signature = signature(minecraft.level, cache.expected);
        cache.mesh = PreviewMeshBuilder.build(ghosts, occluders(minecraft.level, ghosts, covered));
    }

    private static boolean signatureChanged(net.minecraft.world.level.Level level, Cache cache) {
        return cache.expected != null && signature(level, cache.expected) != cache.signature;
    }

    private static long signature(net.minecraft.world.level.Level level, List<BuildingBlockData> expected) {
        long hash = 1L;
        for (BuildingBlockData block : expected) {
            hash = hash * 31L + Block.getId(level.getBlockState(block.relativePos()));
        }
        return hash;
    }

    private static boolean inView(Minecraft minecraft, Vec3 cameraPos, AABB bounds) {
        int chunks = minecraft.options.getEffectiveRenderDistance();
        double limit = chunks * 16.0D;
        double dx = clamp(cameraPos.x, bounds.minX, bounds.maxX) - cameraPos.x;
        double dy = clamp(cameraPos.y, bounds.minY, bounds.maxY) - cameraPos.y;
        double dz = clamp(cameraPos.z, bounds.minZ, bounds.maxZ) - cameraPos.z;
        return dx * dx + dy * dy + dz * dz <= limit * limit;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static List<BuildingBlockData> resolveExpected(PlacedBuildingRecord building) {
        List<BuildingBlockData> resolved = new ArrayList<>();
        for (BuildingBlockData block : expectedBlocks(building)) {
            if (block.state() == null || block.state().isAir()) {
                continue;
            }
            BlockPos pos = worldPos(building, block.relativePos());
            resolved.add(pos.equals(block.relativePos())
                    ? block
                    : new BuildingBlockData(pos, block.state(), block.originalStructurePos(), block.copyBlockEntityData()));
        }
        return resolved;
    }

    private static List<BuildingBlockData> expectedBlocks(PlacedBuildingRecord building) {
        if (building.blocks() != null && !building.blocks().isEmpty()) {
            return building.blocks();
        }
        return BuildingStructureService.loadStructure(building.category(), building.buildingFileName())
                .map(structure -> BuildingStructureService.resolvePlacedBlocks(structure, building.worldOrigin(), rotation(building.facing())))
                .orElse(List.of());
    }

    private static PlacedBuildingRecord findBuilding(Minecraft minecraft, AABB bounds) {
        if (!(minecraft.level instanceof ClientLevel clientLevel) || minecraft.getSingleplayerServer() == null) {
            return null;
        }
        var serverLevel = minecraft.getSingleplayerServer().getLevel(clientLevel.dimension());
        if (serverLevel == null) {
            return null;
        }
        for (PlacedBuildingRecord building : PlacedBuildingService.getBuildings(serverLevel)) {
            if (bounds.equals(buildingBounds(building))) {
                return building;
            }
        }
        return null;
    }

    private static AABB buildingBounds(PlacedBuildingRecord building) {
        return new AABB(building.minPos().getX(), building.minPos().getY(), building.minPos().getZ(),
                building.maxPos().getX() + 1, building.maxPos().getY() + 1, building.maxPos().getZ() + 1);
    }

    private static BlockPos worldPos(PlacedBuildingRecord building, BlockPos stored) {
        BlockPos min = building.minPos();
        BlockPos max = building.maxPos();
        if (stored.getX() >= Math.min(min.getX(), max.getX()) && stored.getX() <= Math.max(min.getX(), max.getX())
                && stored.getY() >= Math.min(min.getY(), max.getY()) && stored.getY() <= Math.max(min.getY(), max.getY())
                && stored.getZ() >= Math.min(min.getZ(), max.getZ()) && stored.getZ() <= Math.max(min.getZ(), max.getZ())) {
            return stored;
        }
        return building.worldOrigin().offset(stored);
    }

    private static int rotation(String facing) {
        String normalized = facing == null ? "" : facing.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "east" -> 90;
            case "south" -> 180;
            case "west" -> 270;
            default -> 0;
        };
    }

    private static Map<Long, BlockState> occluders(net.minecraft.world.level.Level level, List<PreviewBlockData> ghosts, Set<Long> covered) {
        Map<Long, BlockState> occluders = new HashMap<>();
        Set<Long> ghostKeys = new HashSet<>();
        for (PreviewBlockData ghost : ghosts) {
            ghostKeys.add(ghost.pos().asLong());
        }
        for (long key : covered) {
            if (!ghostKeys.contains(key)) {
                occluders.put(key, level.getBlockState(BlockPos.of(key)));
            }
        }
        for (PreviewBlockData ghost : ghosts) {
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = ghost.pos().relative(direction);
                long key = neighbor.asLong();
                if (covered.contains(key) || occluders.containsKey(key)) {
                    continue;
                }
                BlockState world = level.getBlockState(neighbor);
                if (!world.isAir()) {
                    occluders.put(key, world);
                }
            }
        }
        return occluders;
    }

    private static void renderMarks(PoseStack poseStack, Vec3 cameraPos, List<Mark> marks, Set<Long> covered, net.minecraft.world.level.Level level) {
        if (marks.isEmpty() || !hasOpenFace(marks, covered, level)) {
            return;
        }
        Matrix4f matrix = poseStack.last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        // 略微拉到实体方块前面，避免和方块表面抢同一层深度。
        RenderSystem.enablePolygonOffset();
        RenderSystem.polygonOffset(-3.0F, -3.0F);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (Mark mark : marks) {
            addBox(buffer, matrix, cameraPos, mark.pos(), mark.color(), covered, level);
        }
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.polygonOffset(0.0F, 0.0F);
        RenderSystem.disablePolygonOffset();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void addBox(BufferBuilder buffer, Matrix4f matrix, Vec3 cameraPos, BlockPos pos, int color, Set<Long> covered, net.minecraft.world.level.Level level) {
        float r = ((color >> 16) & 0xFF) / 255.0F;
        float g = ((color >> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;
        float a = ((color >> 24) & 0xFF) / 255.0F;
        double x0 = pos.getX() - cameraPos.x - 0.002D;
        double y0 = pos.getY() - cameraPos.y - 0.002D;
        double z0 = pos.getZ() - cameraPos.z - 0.002D;
        double x1 = x0 + 1.004D;
        double y1 = y0 + 1.004D;
        double z1 = z0 + 1.004D;
        if (faceOpen(level, covered, pos, Direction.NORTH)) {
            quad(buffer, matrix, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, r, g, b, a);
        }
        if (faceOpen(level, covered, pos, Direction.SOUTH)) {
            quad(buffer, matrix, x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, r, g, b, a);
        }
        if (faceOpen(level, covered, pos, Direction.WEST)) {
            quad(buffer, matrix, x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, r, g, b, a);
        }
        if (faceOpen(level, covered, pos, Direction.EAST)) {
            quad(buffer, matrix, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, r, g, b, a);
        }
        if (faceOpen(level, covered, pos, Direction.UP)) {
            quad(buffer, matrix, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, r, g, b, a);
        }
        if (faceOpen(level, covered, pos, Direction.DOWN)) {
            quad(buffer, matrix, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, r, g, b, a);
        }
    }

    private static boolean hasOpenFace(List<Mark> marks, Set<Long> covered, net.minecraft.world.level.Level level) {
        for (Mark mark : marks) {
            for (Direction direction : Direction.values()) {
                if (faceOpen(level, covered, mark.pos(), direction)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * faceOpen：贴着另一块预览或实心方块的面不画。
     */
    private static boolean faceOpen(net.minecraft.world.level.Level level, Set<Long> covered, BlockPos pos, Direction direction) {
        BlockPos neighbor = pos.relative(direction);
        if (covered.contains(neighbor.asLong())) {
            return false;
        }
        return !level.getBlockState(neighbor).canOcclude();
    }

    private static void quad(BufferBuilder buffer, Matrix4f matrix, double x1, double y1, double z1, double x2, double y2, double z2, double x3, double y3, double z3, double x4, double y4, double z4, float r, float g, float b, float a) {
        buffer.addVertex(matrix, (float) x1, (float) y1, (float) z1).setColor(r, g, b, a);
        buffer.addVertex(matrix, (float) x2, (float) y2, (float) z2).setColor(r, g, b, a);
        buffer.addVertex(matrix, (float) x3, (float) y3, (float) z3).setColor(r, g, b, a);
        buffer.addVertex(matrix, (float) x4, (float) y4, (float) z4).setColor(r, g, b, a);
    }

    private static final class Cache {
        private long checkedAt = Long.MIN_VALUE;
        private long signature;
        private List<BuildingBlockData> expected = List.of();
        private List<Mark> marks = List.of();
        private Set<Long> covered = Set.of();
        private PreviewMesh mesh = PreviewMesh.EMPTY;
    }

    private record Mark(BlockPos pos, int color) {
    }
}
