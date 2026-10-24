package client.cn.nsimu.simukraft.client.compat.jei;

import common.cn.nsimu.simukraft.economy.CoinDenominations;
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
 * CommercialJeiCategory: JEI 商业交易分类。花费和所得会被 JEI 用来查询物品。
 */
public final class CommercialJeiCategory implements IRecipeCategory<CommercialJeiRecipe> {
    public static final RecipeType<CommercialJeiRecipe> TYPE = RecipeType.create("simukraft", "commercial", CommercialJeiRecipe.class);
    private static final int COLUMNS = 3;
    private static final int SLOT = 18;
    private static final int TEXT_HEIGHT = 24;
    private static final int ARROW_WIDTH = 24;
    private static final int GAP = 4;
    private static final int MAX_ROWS = 4;

    private final IDrawable icon;
    private final IDrawable arrow;
    private final int rows;

    /**
     * CommercialJeiCategory: 按报价两侧的格子行数确定界面高度。
     */
    public CommercialJeiCategory(IGuiHelper guiHelper, List<CommercialJeiRecipe> recipes) {
        int usedRows = 1;
        for (CommercialJeiRecipe recipe : recipes) {
            usedRows = Math.max(usedRows, rowCount(recipe.inputs().size()));
            usedRows = Math.max(usedRows, rowCount(recipe.outputs().size()));
        }
        this.rows = Math.min(usedRows, MAX_ROWS);
        this.icon = guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(ModBlocks.COMMERCIAL_CONTROL_BOX.get()));
        this.arrow = guiHelper.getRecipeArrow();
    }

    @Override
    public RecipeType<CommercialJeiRecipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.simukraft.commercial");
    }

    /**
     * getWidth: 新版 JEI 用宽高代替背景贴图。
     */
    @Override
    public int getWidth() {
        return outputOrigin() + COLUMNS * SLOT + 1;
    }

    /**
     * getHeight: 随报价行数增高，最多四行。
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
     * setRecipe: 花费放进输入格，所得放进输出格。
     */
    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, CommercialJeiRecipe recipe, IFocusGroup focuses) {
        addSlots(builder, RecipeIngredientRole.INPUT, 0, recipe.inputs(), recipe);
        addSlots(builder, RecipeIngredientRole.OUTPUT, outputOrigin(), recipe.outputs(), recipe);
    }

    /**
     * draw: 画出店名、建筑名和箭头。
     */
    @Override
    public void draw(CommercialJeiRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics graphics, double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;
        drawLine(graphics, font, recipe.title(), 2, 0xFF404040);
        drawLine(graphics, font, recipe.buildingLine(), 12, 0xFF606060);
        int used = Math.max(rowCount(recipe.inputs().size()), rowCount(recipe.outputs().size()));
        int arrowY = TEXT_HEIGHT + (Math.min(Math.max(used, 1), rows) * SLOT - arrow.getHeight()) / 2;
        arrow.draw(graphics, 1 + COLUMNS * SLOT + GAP, arrowY);
    }

    /**
     * getTooltip: 空白处显示金额、补货材料和仅 NPC 的报价。
     */
    @Override
    public void getTooltip(ITooltipBuilder tooltip, CommercialJeiRecipe recipe, IRecipeSlotsView recipeSlotsView, double mouseX, double mouseY) {
        appendInfo(recipe, tooltip);
    }

    private void addSlots(IRecipeLayoutBuilder builder, RecipeIngredientRole role, int origin, List<ItemStack> stacks, CommercialJeiRecipe recipe) {
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            if (stack.isEmpty()) {
                continue;
            }
            IRecipeSlotBuilder slot = builder.addSlot(role, origin + (i % COLUMNS) * SLOT, TEXT_HEIGHT + (i / COLUMNS) * SLOT);
            if (role == RecipeIngredientRole.OUTPUT) {
                slot.setOutputSlotBackground();
            } else {
                slot.setStandardSlotBackground();
            }
            slot.addItemStack(stack);
            slot.addRichTooltipCallback((view, tooltip) -> appendInfo(recipe, tooltip));
        }
    }

    private static void appendInfo(CommercialJeiRecipe recipe, ITooltipBuilder tooltip) {
        if (recipe.costMoney() > 0.0D) {
            tooltip.add(Component.translatable("jei.simukraft.commercial.cost", CoinDenominations.formatYuan(recipe.costMoney())));
        }
        if (recipe.resultMoney() > 0.0D) {
            tooltip.add(Component.translatable("jei.simukraft.commercial.pay", CoinDenominations.formatYuan(recipe.resultMoney())));
        }
        if (!recipe.materials().isBlank()) {
            tooltip.add(Component.translatable("jei.simukraft.commercial.materials", recipe.materials()));
        }
        if (recipe.npcOnly()) {
            tooltip.add(Component.translatable("jei.simukraft.commercial.npc_only"));
        }
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

    private static int outputOrigin() {
        return 1 + COLUMNS * SLOT + GAP + ARROW_WIDTH + GAP;
    }

    private static int rowCount(int slots) {
        if (slots <= 0) {
            return 1;
        }
        return (slots + COLUMNS - 1) / COLUMNS;
    }
}
