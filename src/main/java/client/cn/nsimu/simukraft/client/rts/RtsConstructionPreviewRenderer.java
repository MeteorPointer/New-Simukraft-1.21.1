package client.cn.nsimu.simukraft.client.rts;

import client.cn.nsimu.simukraft.client.buildbox.BuildingBoundsRenderer;
import client.cn.nsimu.simukraft.client.buildbox.BuildingPreviewRenderer;
import client.cn.nsimu.simukraft.client.buildbox.PreviewMesh;
import client.cn.nsimu.simukraft.client.freecamera.FreeCameraManager;
import common.cn.nsimu.simukraft.building.BuildingCatalog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.List;

/**
 * RTS 施工预览渲染：复用放置建筑的分层 VBO，并在光标悬浮时画出名称、建造者和进度。
 */
@OnlyIn(Dist.CLIENT)
public final class RtsConstructionPreviewRenderer {
    private static final float CONSTRUCTION_ALPHA = 0.5F;

    private RtsConstructionPreviewRenderer() {
    }

    /**
     * onRender: RTS 相机开启时用半透明 VBO 绘制全部施工中的建筑预览。
     */
    public static void onRender(RenderLevelStageEvent event) {
        if (!FreeCameraManager.isRtsActive()) {
            return;
        }
        List<PreviewMesh> meshes = RtsConstructionPreviewManager.meshes();
        if (meshes.isEmpty()) {
            return;
        }
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            for (PreviewMesh mesh : meshes) {
                BuildingPreviewRenderer.renderFaded(
                        mesh,
                        event.getModelViewMatrix(),
                        event.getProjectionMatrix(),
                        event.getCamera().getPosition(),
                        CONSTRUCTION_ALPHA,
                        true);
            }
            return;
        }
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
            for (PreviewMesh mesh : meshes) {
                BuildingPreviewRenderer.renderPreviewMesh(mesh, event, true);
            }
        }
    }

    /**
     * renderHover: 在光标旁显示当前指到的施工信息。
     */
    public static void renderHover(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!FreeCameraManager.isRtsActive() || minecraft.screen != null || minecraft.player == null || graphics == null) {
            return;
        }
        RtsConstructionPreviewManager.HoverLabel construction = RtsConstructionPreviewManager.hovered();
        if (construction != null) {
            int percent = construction.totalBlocks() <= 0
                    ? 0
                    : (int) Math.min(100L, construction.currentBlockIndex() * 100L / construction.totalBlocks());
            String builder = construction.builderName() == null || construction.builderName().isBlank() ? "—" : construction.builderName();
            drawCard(graphics, minecraft.font, java.util.List.of(
                    Component.literal(construction.displayName() == null ? "" : construction.displayName()),
                    Component.translatable("gui.simukraft.rts.construction.builder", builder),
                    Component.translatable("gui.simukraft.rts.construction.progress", percent, Math.max(0, construction.currentBlockIndex()), construction.totalBlocks())));
            return;
        }
        BuildingBoundsRenderer.RtsBuildingBounds placed = BuildingBoundsRenderer.hoveredPlacedBuilding();
        if (placed == null) {
            return;
        }
        java.util.ArrayList<Component> lines = new java.util.ArrayList<>(3);
        lines.add(Component.literal(placedName(placed)));
        lines.add(placedType(placed));
        Component detail = placedDetail(placed);
        if (detail != null) {
            lines.add(detail);
        }
        drawCard(graphics, minecraft.font, lines);
    }

    /**
     * drawCard: 在光标旁绘制悬浮卡片。
     */
    private static void drawCard(GuiGraphics graphics, Font font, java.util.List<Component> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        int width = 0;
        for (Component line : lines) {
            width = Math.max(width, font.width(line));
        }
        int lineHeight = font.lineHeight + 2;
        int height = lineHeight * lines.size() - 2;
        Minecraft minecraft = Minecraft.getInstance();
        int mouseX = scaledMouse(minecraft.mouseHandler.xpos(), minecraft.getWindow().getScreenWidth(), graphics.guiWidth());
        int mouseY = scaledMouse(minecraft.mouseHandler.ypos(), minecraft.getWindow().getScreenHeight(), graphics.guiHeight());
        int x = mouseX + 12;
        int y = mouseY + 12;
        if (x + width + 6 > graphics.guiWidth()) {
            x = Math.max(4, mouseX - width - 16);
        }
        if (y + height + 6 > graphics.guiHeight()) {
            y = Math.max(4, graphics.guiHeight() - height - 6);
        }
        graphics.fill(x - 4, y - 4, x + width + 4, y + height + 4, 0xF0101010);
        graphics.fill(x - 4, y - 4, x - 3, y + height + 4, 0xFF5A8F3A);
        graphics.fill(x + width + 3, y - 4, x + width + 4, y + height + 4, 0xFF5A8F3A);
        for (int index = 0; index < lines.size(); index++) {
            int color = index == 1 ? 0xFFE7C8 : 0xFFFFFF;
            graphics.drawString(font, lines.get(index), x, y + index * lineHeight, color, true);
        }
    }

    private static String placedName(BuildingBoundsRenderer.RtsBuildingBounds placed) {
        String localized = definition(placed).map(BuildingCatalog.BuildingDefinition::localizedDisplayName).orElse("");
        if (localized != null && !localized.isBlank()) {
            return localized;
        }
        return placed.displayName() == null || placed.displayName().isBlank() ? placed.buildingFileName() : placed.displayName();
    }

    private static Component placedType(BuildingBoundsRenderer.RtsBuildingBounds placed) {
        Component category = Component.translatable(categoryKey(placed.category()));
        String kindKey = kindKey(definition(placed).orElse(null));
        if (kindKey == null) {
            return Component.translatable("gui.simukraft.rts.building.type", category);
        }
        return Component.translatable("gui.simukraft.rts.building.type_kind", category, Component.translatable(kindKey));
    }

    private static Component placedDetail(BuildingBoundsRenderer.RtsBuildingBounds placed) {
        if (placed.bedCount() > 0) {
            return Component.translatable("gui.simukraft.rts.building.beds", placed.bedCount());
        }
        String amount = definition(placed).map(BuildingCatalog.BuildingDefinition::localizedAmount).orElse("");
        if (amount == null || amount.isBlank()) {
            amount = placed.amount();
        }
        if (amount == null || amount.isBlank()) {
            return null;
        }
        return Component.translatable("gui.simukraft.rts.building.cost", amount);
    }

    private static java.util.Optional<BuildingCatalog.BuildingDefinition> definition(BuildingBoundsRenderer.RtsBuildingBounds placed) {
        return BuildingCatalog.findBuilding(placed.category(), placed.buildingFileName());
    }

    private static String categoryKey(String category) {
        if (category == null) {
            return "gui.category.other";
        }
        return switch (category.toLowerCase(java.util.Locale.ROOT)) {
            case "residential" -> "gui.category.residential";
            case "commercial" -> "gui.category.commercial";
            case "industrial" -> "gui.category.industrial";
            case "public" -> "gui.category.public";
            default -> "gui.category.other";
        };
    }

    private static String kindKey(BuildingCatalog.BuildingDefinition definition) {
        if (definition == null) {
            return null;
        }
        if (definition.isMedical()) {
            return "gui.simukraft.rts.building.kind.medical";
        }
        if (definition.isBank()) {
            return "gui.simukraft.rts.building.kind.bank";
        }
        if (definition.isExchange()) {
            return "gui.simukraft.rts.building.kind.exchange";
        }
        if (definition.isGlobalMarket()) {
            return "gui.simukraft.rts.building.kind.global_market";
        }
        if (definition.isDrillingPlatform()) {
            return "gui.simukraft.rts.building.kind.drilling";
        }
        return null;
    }

    private static int scaledMouse(double pointer, int screenSize, int guiSize) {
        if (screenSize <= 0) {
            return Mth.floor(pointer);
        }
        return Mth.floor(pointer * guiSize / screenSize);
    }
}
