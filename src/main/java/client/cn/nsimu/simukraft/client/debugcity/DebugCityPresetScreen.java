package client.cn.nsimu.simukraft.client.debugcity;

import common.cn.nsimu.simukraft.debugcity.DebugCityChunkGenerator;
import common.cn.nsimu.simukraft.debugcity.DebugCityLayout;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;

/**
 * 调试城市世界类型的自定义页，用来填写居民数量和网格边长。
 */
public final class DebugCityPresetScreen extends Screen {
    private static final int FIELD_WIDTH = 220;
    private static final int FIELD_HEIGHT = 20;
    private final CreateWorldScreen parent;
    private final BiomeSource biomeSource;
    private EditBox citizenCount;
    private EditBox cityScale;

    private DebugCityPresetScreen(CreateWorldScreen parent, BiomeSource biomeSource, int citizens, int scale) {
        super(Component.translatable("screen.simukraft.debug_city.title"));
        this.parent = parent;
        this.biomeSource = biomeSource;
        this.citizenCount = new EditBox(parent.getMinecraft().font, 0, 0, FIELD_WIDTH, FIELD_HEIGHT,
                Component.translatable("config.simukraft.debug_city.citizenCount"));
        this.cityScale = new EditBox(parent.getMinecraft().font, 0, 0, FIELD_WIDTH, FIELD_HEIGHT,
                Component.translatable("config.simukraft.debug_city.cityScale"));
        this.citizenCount.setValue(Integer.toString(citizens));
        this.cityScale.setValue(Integer.toString(scale));
    }

    /**
     * create：从当前世界类型读出已填写的数量，打开自定义页。
     */
    public static Screen create(CreateWorldScreen parent, WorldCreationContext context) {
        ChunkGenerator generator = context.selectedDimensions().overworld();
        int citizens = DebugCityLayout.DEFAULT_CITIZENS;
        int scale = DebugCityLayout.DEFAULT_SCALE;
        if (generator instanceof DebugCityChunkGenerator debugCity) {
            citizens = debugCity.citizenCount();
            scale = debugCity.cityScale();
        }
        return new DebugCityPresetScreen(parent, generator.getBiomeSource(), citizens, scale);
    }

    @Override
    protected void init() {
        int left = (this.width - FIELD_WIDTH) / 2;
        this.citizenCount.setX(left);
        this.citizenCount.setY(72);
        this.citizenCount.setFilter(DebugCityPresetScreen::digitsOnly);
        this.citizenCount.setMaxLength(4);
        this.cityScale.setX(left);
        this.cityScale.setY(116);
        this.cityScale.setFilter(DebugCityPresetScreen::digitsOnly);
        this.cityScale.setMaxLength(3);
        addRenderableWidget(this.citizenCount);
        addRenderableWidget(this.cityScale);
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> apply())
                .bounds(this.width / 2 - 154, this.height - 40, 150, 20)
                .build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(this.width / 2 + 4, this.height - 40, 150, 20)
                .build());
        setInitialFocus(this.citizenCount);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 28, 0xFFFFFF);
        int left = (this.width - FIELD_WIDTH) / 2;
        graphics.drawString(this.font, Component.translatable("config.simukraft.debug_city.citizenCount"), left, 58, 0xA0A0A0, false);
        graphics.drawString(this.font, Component.translatable("config.simukraft.debug_city.cityScale"), left, 102, 0xA0A0A0, false);
        graphics.drawWordWrap(this.font, Component.translatable("screen.simukraft.debug_city.hint"), left, 148, FIELD_WIDTH, 0xA0A0A0);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    private void apply() {
        int citizens = read(this.citizenCount, DebugCityLayout.DEFAULT_CITIZENS, DebugCityLayout::clampCitizens);
        int scale = read(this.cityScale, DebugCityLayout.DEFAULT_SCALE, DebugCityLayout::clampScale);
        DebugCityChunkGenerator generator = new DebugCityChunkGenerator(this.biomeSource, citizens, scale);
        this.parent.getUiState().updateDimensions((registries, dimensions) -> dimensions.replaceOverworldGenerator(registries, generator));
        this.minecraft.setScreen(this.parent);
    }

    private static int read(EditBox box, int fallback, java.util.function.IntUnaryOperator clamp) {
        try {
            return clamp.applyAsInt(Integer.parseInt(box.getValue().trim()));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static boolean digitsOnly(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
