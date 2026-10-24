package client.cn.nsimu.simukraft.client.compat.jei;

import common.cn.nsimu.simukraft.registry.ModBlocks;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * IndustrialJeiCategory: JEI 工业配方分类。输入格和输出格会被 JEI 用来查询物品。
 */
public final class IndustrialJeiCategory implements IRecipeCategory<IndustrialJeiRecipe> {
    public static final RecipeType<IndustrialJeiRecipe> TYPE = RecipeType.create("simukraft", "industrial", IndustrialJeiRecipe.class);
    private static final int COLUMNS = 3;
    private static final int SLOT = 18;
    private static final int TEXT_HEIGHT = 24;
    private static final int ARROW_WIDTH = 24;
    private static final int GAP = 4;
    private static final int MAX_ROWS = 6;

    private final IDrawable icon;
    private final IDrawable arrow;
    private final int rows;

    /**
     * IndustrialJeiCategory: 按当前配方的输入输出行数确定界面高度。
     */
    public IndustrialJeiCategory(IGuiHelper guiHelper, List<IndustrialJeiRecipe> recipes) {
        int usedRows = 1;
        for (IndustrialJeiRecipe recipe : recipes) {
            usedRows = Math.max(usedRows, rowCount(recipe.inputs().size()));
            usedRows = Math.max(usedRows, rowCount(recipe.outputs().size()));
        }
        this.rows = Math.min(usedRows, MAX_ROWS);
        this.icon = guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(ModBlocks.INDUSTRIAL_CONTROL_BOX.get()));
        this.arrow = guiHelper.getRecipeArrow();
    }

    @Override
    public RecipeType<IndustrialJeiRecipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.simukraft.industrial");
    }

    /**
     * getWidth: 新版 JEI 用宽高代替背景贴图。
     */
    @Override
    public int getWidth() {
        return outputOrigin() + COLUMNS * SLOT + 1;
    }

    /**
     * getHeight: 随配方行数增高，最多六行。
     */
    @Override
    public int getHeight() {
        return TEXT_HEIGHT + rows * SLOT + 1;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    /**
     * setRecipe: 把配方材料放进输入格、产物放进输出格，JEI 据此建立查询。
     */
    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, IndustrialJeiRecipe recipe, IFocusGroup focuses) {
        for (int i = 0; i < recipe.inputs().size(); i++) {
            IndustrialJeiRecipe.InputSlot slot = recipe.inputs().get(i);
            if (slot.stacks().isEmpty()) {
                continue;
            }
            IRecipeSlotBuilder input = builder.addSlot(RecipeIngredientRole.INPUT, slotX(0, i), slotY(i));
            input.setStandardSlotBackground();
            input.addItemStacks(slot.stacks());
            if (!slot.consume() || slot.alternative() || !slot.tagId().isBlank()) {
                input.addRichTooltipCallback((view, tooltip) -> appendInputTooltip(slot, tooltip));
            }
        }
        for (int i = 0; i < recipe.outputs().size(); i++) {
            IndustrialJeiRecipe.OutputSlot slot = recipe.outputs().get(i);
            if (slot.stack().isEmpty()) {
                continue;
            }
            IRecipeSlotBuilder output = builder.addSlot(RecipeIngredientRole.OUTPUT, slotX(outputOrigin(), i), slotY(i));
            output.setOutputSlotBackground();
            output.addItemStack(slot.stack());
            if (slot.probability() < 0.999D || slot.randomRange() > 1 || slot.ignoreMultiplier()) {
                output.addRichTooltipCallback((view, tooltip) -> appendOutputTooltip(slot, tooltip));
            }
        }
    }

    /**
     * draw: 画出配方名、建筑名和箭头。
     */
    @Override
    public void draw(IndustrialJeiRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics graphics, double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;
        drawLine(graphics, font, recipe.title(), 2, 0xFF404040);
        drawLine(graphics, font, recipe.buildingLine(), 12, 0xFF606060);
        int used = Math.max(rowCount(recipe.inputs().size()), rowCount(recipe.outputs().size()));
        int arrowY = TEXT_HEIGHT + (Math.min(Math.max(used, 1), rows) * SLOT - arrow.getHeight()) / 2;
        arrow.draw(graphics, 1 + COLUMNS * SLOT + GAP, arrowY);
    }

    private void drawLine(GuiGraphics graphics, Font font, Component text, int y, int color) {
        if (text == null || text.getString().isBlank()) {
            return;
        }
        List<FormattedCharSequence> lines = font.split(FormattedText.of(text.getString()), Math.max(1, getWidth() - 4));
        if (!lines.isEmpty()) {
            graphics.drawString(font, lines.getFirst(), 2, y, color, false);
        }
    }

    private static void appendInputTooltip(IndustrialJeiRecipe.InputSlot slot, ITooltipBuilder tooltip) {
        if (!slot.tagId().isBlank()) {
            tooltip.add(Component.translatable("jei.simukraft.industrial.tag", slot.tagId()));
        }
        if (slot.alternative()) {
            tooltip.add(Component.translatable("jei.simukraft.industrial.any_of"));
        }
        if (!slot.consume()) {
            tooltip.add(Component.translatable("jei.simukraft.industrial.not_consumed"));
        }
    }

    private static void appendOutputTooltip(IndustrialJeiRecipe.OutputSlot slot, ITooltipBuilder tooltip) {
        if (slot.probability() < 0.999D) {
            tooltip.add(Component.translatable("jei.simukraft.industrial.chance", Math.round(slot.probability() * 100.0D)));
        }
        if (slot.randomRange() > 1) {
            tooltip.add(Component.translatable("jei.simukraft.industrial.amount_range", slot.baseAmount(), slot.baseAmount() + slot.randomRange() - 1));
        }
        if (slot.ignoreMultiplier()) {
            tooltip.add(Component.translatable("jei.simukraft.industrial.fixed_amount"));
        }
    }

    private static int outputOrigin() {
        return 1 + COLUMNS * SLOT + GAP + ARROW_WIDTH + GAP;
    }

    private static int slotX(int origin, int index) {
        return origin + (index % COLUMNS) * SLOT;
    }

    private static int slotY(int index) {
        return TEXT_HEIGHT + (index / COLUMNS) * SLOT;
    }

    private static int rowCount(int slots) {
        if (slots <= 0) {
            return 1;
        }
        return (slots + COLUMNS - 1) / COLUMNS;
    }
}
