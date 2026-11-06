package client.cn.nsimu.simukraft.client.rts;

import client.cn.nsimu.simukraft.client.buildbox.PreviewBlockData;
import client.cn.nsimu.simukraft.client.buildbox.PreviewMesh;
import client.cn.nsimu.simukraft.client.buildbox.PreviewMeshBuilder;
import client.cn.nsimu.simukraft.client.freecamera.FreeCameraManager;
import common.cn.nsimu.simukraft.SimuKraft;
import common.cn.nsimu.simukraft.building.BuildingBlockData;
import common.cn.nsimu.simukraft.building.BuildingCatalog;
import common.cn.nsimu.simukraft.building.BuildingStructure;
import common.cn.nsimu.simukraft.building.BuildingStructureService;
import common.cn.nsimu.simukraft.network.rts.RtsConstructionPreviewSyncPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * RTS 施工预览：按任务缓存放置预览同款 VBO，进度变化时不重建网格。
 */
@OnlyIn(Dist.CLIENT)
public final class RtsConstructionPreviewManager {
    private static final int PREVIEW_LIGHT = 15728880;
    private static Map<UUID, Site> sites = Map.of();

    private RtsConstructionPreviewManager() {
    }

    /**
     * apply: 用服务端快照增删预览。结构、原点和旋转不变时只更新名称与进度。
     */
    public static void apply(List<RtsConstructionPreviewSyncPacket.Entry> entries) {
        if (!FreeCameraManager.isRtsActive()) {
            clear();
            return;
        }
        Map<UUID, Site> next = new LinkedHashMap<>();
        if (entries != null) {
            for (RtsConstructionPreviewSyncPacket.Entry entry : entries) {
                if (entry == null || entry.taskId() == null || entry.origin() == null) {
                    continue;
                }
                String meshKey = meshKey(entry);
                Site existing = sites.get(entry.taskId());
                if (existing != null && meshKey.equals(existing.meshKey)) {
                    existing.updateLabel(entry);
                    next.put(entry.taskId(), existing);
                    continue;
                }
                try {
                    Site created = createSite(entry, meshKey);
                    if (created != null) {
                        next.put(entry.taskId(), created);
                    }
                } catch (RuntimeException exception) {
                    SimuKraft.LOGGER.warn("SimuKraft: Failed to build RTS construction preview for {}", entry.taskId(), exception);
                }
            }
        }
        for (Site previous : sites.values()) {
            if (!next.containsValue(previous)) {
                previous.close();
            }
        }
        sites = next;
    }

    /**
     * clear: 释放全部施工预览网格。
     */
    public static void clear() {
        for (Site site : sites.values()) {
            site.close();
        }
        sites = Map.of();
    }

    /**
     * meshes: 返回当前需要绘制的预览网格。
     */
    public static List<PreviewMesh> meshes() {
        if (sites.isEmpty()) {
            return List.of();
        }
        ArrayList<PreviewMesh> meshes = new ArrayList<>(sites.size());
        for (Site site : sites.values()) {
            if (site.mesh != null && !site.mesh.isEmpty()) {
                meshes.add(site.mesh);
            }
        }
        return meshes;
    }

    /**
     * hovered: 光标射线先进入施工范围，且没有更近的方块挡住时返回悬浮信息。
     */
    public static HoverLabel hovered() {
        if (sites.isEmpty() || !FreeCameraManager.isRtsActive()) {
            return null;
        }
        RtsSelectionManager.CursorRay ray = RtsSelectionManager.currentCursorRay();
        if (ray == null) {
            return null;
        }
        var blockHit = RtsSelectionManager.currentCursorBlockHit();
        double blockDistance = blockHit != null && blockHit.getType() == HitResult.Type.BLOCK
                ? blockHit.getLocation().distanceTo(ray.from())
                : Double.POSITIVE_INFINITY;
        Site best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (Site site : sites.values()) {
            double entry = entryDistance(site.bounds, ray.from(), ray.to());
            if (entry <= blockDistance + 0.05D && entry < bestDistance) {
                best = site;
                bestDistance = entry;
            }
        }
        if (best == null) {
            return null;
        }
        return new HoverLabel(best.displayName, best.builderName, best.currentBlockIndex, best.totalBlocks, best.bounds);
    }

    private static Site createSite(RtsConstructionPreviewSyncPacket.Entry entry, String meshKey) {
        Optional<BuildingStructure> structure = BuildingCatalog.findBuildingByStructureFile(entry.category(), entry.structureFileName())
                .flatMap(BuildingStructureService::loadStructure);
        if (structure.isEmpty()) {
            structure = BuildingStructureService.loadStructure(entry.category(), entry.buildingFileName());
        }
        if (structure.isEmpty()) {
            return null;
        }
        List<BuildingBlockData> placed = BuildingStructureService.resolvePlacedBlocks(structure.get(), entry.origin(), entry.rotationDegrees());
        ArrayList<PreviewBlockData> blocks = new ArrayList<>(placed.size());
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BuildingBlockData block : placed) {
            if (block.state() == null || block.state().isAir()) {
                continue;
            }
            BlockPos pos = block.relativePos();
            blocks.add(new PreviewBlockData(pos, block.state(), PREVIEW_LIGHT, block.copyBlockEntityData()));
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        if (blocks.isEmpty()) {
            return null;
        }
        PreviewMesh mesh = PreviewMeshBuilder.build(blocks);
        AABB bounds = new AABB(minX, minY, minZ, maxX + 1.0D, maxY + 1.0D, maxZ + 1.0D);
        Site site = new Site(meshKey, mesh, bounds);
        site.updateLabel(entry);
        return site;
    }

    private static String meshKey(RtsConstructionPreviewSyncPacket.Entry entry) {
        BlockPos origin = entry.origin();
        return entry.category() + '\0' + entry.buildingFileName() + '\0' + entry.structureFileName() + '\0'
                + origin.getX() + ',' + origin.getY() + ',' + origin.getZ() + '\0' + entry.rotationDegrees();
    }

    private static String localizedName(RtsConstructionPreviewSyncPacket.Entry entry) {
        String localized = BuildingCatalog.findBuilding(entry.category(), entry.buildingFileName())
                .or(() -> BuildingCatalog.findBuildingByStructureFile(entry.category(), entry.structureFileName()))
                .map(BuildingCatalog.BuildingDefinition::localizedDisplayName)
                .orElse("");
        if (localized != null && !localized.isBlank()) {
            return localized;
        }
        if (entry.displayName() != null && !entry.displayName().isBlank()) {
            return entry.displayName();
        }
        return entry.buildingFileName() == null ? "" : entry.buildingFileName();
    }

    private static double entryDistance(AABB bounds, Vec3 from, Vec3 to) {
        if (bounds.contains(from)) {
            return 0.0D;
        }
        return bounds.clip(from, to).map(hit -> hit.distanceTo(from)).orElse(Double.POSITIVE_INFINITY);
    }

    /**
     * HoverLabel: 悬浮时显示的建筑名、建造者和进度。
     */
    public record HoverLabel(String displayName, String builderName, int currentBlockIndex, int totalBlocks, AABB bounds) {
    }

    private static final class Site {
        private final String meshKey;
        private final PreviewMesh mesh;
        private final AABB bounds;
        private String displayName = "";
        private String builderName = "";
        private int currentBlockIndex;
        private int totalBlocks;

        private Site(String meshKey, PreviewMesh mesh, AABB bounds) {
            this.meshKey = meshKey;
            this.mesh = mesh;
            this.bounds = bounds;
        }

        private void updateLabel(RtsConstructionPreviewSyncPacket.Entry entry) {
            displayName = localizedName(entry);
            builderName = entry.builderName() == null ? "" : entry.builderName();
            currentBlockIndex = Math.max(0, entry.currentBlockIndex());
            totalBlocks = Math.max(0, entry.totalBlocks());
        }

        private void close() {
            if (mesh != null && mesh != PreviewMesh.EMPTY) {
                mesh.close();
            }
        }
    }
}
