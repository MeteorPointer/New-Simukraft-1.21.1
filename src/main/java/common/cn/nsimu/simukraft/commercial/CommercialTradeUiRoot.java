package common.cn.nsimu.simukraft.commercial;

import com.lowdragmc.lowdraglib2.gui.slot.LocalSlot;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.mojang.blaze3d.systems.RenderSystem;
import common.cn.nsimu.simukraft.economy.EconomyService;
import common.cn.nsimu.simukraft.network.commercial.CommercialTradeOpenResponsePacket;
import common.cn.nsimu.simukraft.network.commercial.CommercialTradePacket;
import common.cn.nsimu.simukraft.network.globalmarket.GlobalMarketListPacket;
import common.cn.nsimu.simukraft.network.globalmarket.GlobalMarketUnlistPacket;
import common.cn.nsimu.simukraft.network.globalmarket.GlobalMarketWarehouseDepositPacket;
import common.cn.nsimu.simukraft.network.globalmarket.GlobalMarketWarehouseWithdrawPacket;
import common.cn.nsimu.simukraft.ui.RecipeBookSearchUi;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;


public final class CommercialTradeUiRoot extends UIElement {
    private static final ResourceLocation VILLAGER_LOCATION = ResourceLocation.withDefaultNamespace("textures/gui/container/villager.png");
    private static final ResourceLocation SCROLLER_SPRITE = ResourceLocation.withDefaultNamespace("container/villager/scroller");
    private static final ResourceLocation SCROLLER_DISABLED_SPRITE = ResourceLocation.withDefaultNamespace("container/villager/scroller_disabled");
    private static final ResourceLocation TRADE_ARROW_SPRITE = ResourceLocation.withDefaultNamespace("container/villager/trade_arrow");
    private static final ResourceLocation TRADE_ARROW_OUT_OF_STOCK_SPRITE = ResourceLocation.withDefaultNamespace("container/villager/trade_arrow_out_of_stock");
    private static final ResourceLocation OUT_OF_STOCK_SPRITE = ResourceLocation.withDefaultNamespace("container/villager/out_of_stock");
    private static final ResourceLocation BUTTON_SPRITE = ResourceLocation.withDefaultNamespace("widget/button");
    private static final ResourceLocation BUTTON_HIGHLIGHTED_SPRITE = ResourceLocation.withDefaultNamespace("widget/button_highlighted");
    private static final Component TRADES_LABEL = Component.translatable("merchant.trades");
    private static final Component INVENTORY_LABEL = Component.translatable("container.inventory");
    private static final int IMAGE_WIDTH = 276;
    private static final int IMAGE_HEIGHT = 166;
    private static final int ROOT_WIDTH = IMAGE_WIDTH;
    private static final int TEXTURE_WIDTH = 512;
    private static final int TEXTURE_HEIGHT = 256;
    private static final int ROW_COUNT = 6;
    private static final int ROW_X = 5;
    private static final int ROW_Y = 51;
    private static final int ROW_WIDTH = 89;
    private static final int ROW_HEIGHT = 18;
    private static final int SCROLL_X = 94;
    private static final int SCROLL_Y = ROW_Y;
    private static final int SCROLL_WIDTH = 6;
    private static final int SCROLL_HEIGHT = ROW_COUNT * ROW_HEIGHT;
    private static final int SCROLLER_HEIGHT = 22;
    private static final int SEARCH_FRAME_X = 5;
    private static final int SEARCH_FRAME_Y = 34;
    private static final int SEARCH_TEXT_X = 25;
    private static final int SEARCH_TEXT_Y = 36;
    private static final int COST_A_X = 136;
    private static final int COST_B_X = 162;
    private static final int RESULT_X = 220;
    private static final int TRADE_SLOT_Y = 37;
    private static final int INVENTORY_X = 108;
    private static final int INVENTORY_Y = 84;
    private static final int SLOT_SIZE = 18;
    private static final int TEXT_COLOR = 4210752;
    private static final int MUTED_COLOR = 0xFF555555;
    private static final int ERROR_COLOR = 0xFF8A2020;
    private static final int LEFT_PANEL_BACKGROUND = 0xFF404040;
    private static final int ACTION_ICON_Y = 65;
    private static final int ACTION_ICON_SIZE = 18;
    private static final int LIST_ICON_X = 232;
    private static final int UNLIST_ICON_X = 252;
    private static final int MIDDLE_MOUSE = 2;
    private static final int SELECTION_COLOR = 0xFFFFAA00;
    private static final int SELECTION_FILL = 0x44FF8800;
    private static final int RESULT_SELECTION_COLOR = 0xFF3DC8FF;
    private static final int RESULT_SELECTION_FILL = 0x443DC8FF;
    private static WeakReference<CommercialTradeUiRoot> activeRoot = new WeakReference<>(null);
    private CommercialTradeOpenResponsePacket packet;
    private final ItemSlot costSlotA;
    private final ItemSlot costSlotB;
    private final ItemSlot resultSlot;
    private final TextField searchField;
    private final TextField priceField;
    private final TextField quantityField;
    private CommercialTradeOfferTab activeTab = CommercialTradeOfferTab.SELL;
    private GlobalMarketModeTabStrip.Mode marketMode = GlobalMarketModeTabStrip.Mode.BUY;
    private String selectedOfferId = "";
    private String searchText = "";
    private String priceText = "1.00";
    private String quantityText = "64";
    private int scrollOff;
    private int warehouseScroll;
    private final Set<ItemSlot> selectedInventorySlots = new LinkedHashSet<>();
    private UIElement warehouseHitbox;
    private UIElement listIconHitbox;
    private UIElement unlistIconHitbox;

    public CommercialTradeUiRoot(CommercialTradeOpenResponsePacket packet) {
        this.packet = packet;
        this.costSlotA = displaySlot(COST_A_X, TRADE_SLOT_Y);
        this.costSlotB = displaySlot(COST_B_X, TRADE_SLOT_Y);
        this.resultSlot = displaySlot(RESULT_X, TRADE_SLOT_Y);
        this.searchField = searchField();
        this.priceField = packet.canList() ? priceField() : null;
        this.quantityField = packet.canList() ? quantityField() : null;
        activeRoot = new WeakReference<>(this);
        layout(layout -> layout.width(ROOT_WIDTH).height(IMAGE_HEIGHT + panelTop()));
        resultSlot.addEventListener(UIEvents.MOUSE_DOWN, this::onResultSlotMouseDown);
        if (packet.globalMarket()) {
            addChild(modeTabHitbox());
        }
        addChild(tabHitbox());
        addChild(offerListHitbox());
        addChild(scrollerHitbox());
        if (packet.globalMarket() && packet.canList()) {
            warehouseHitbox = warehouseHitbox();
            addChild(warehouseHitbox);
        }
        addChild(searchField);
        addChild(costSlotA);
        addChild(costSlotB);
        addChild(resultSlot);
        addChild(playerInventorySlots());
        if (packet.canList()) {
            listIconHitbox = listIconHitbox();
            unlistIconHitbox = unlistIconHitbox();
            addChild(listIconHitbox);
            addChild(unlistIconHitbox);
            addChild(priceField);
            addChild(quantityField);
            refreshListingEditors();
        }
        updateDisplaySlots();
    }

    /**
     * refreshActive: 刷新当前打开的商业交易界面快照。
     */
    @OnlyIn(Dist.CLIENT)
    public static void refreshActive(CommercialTradeOpenResponsePacket packet) {
        CommercialTradeUiRoot root = activeRoot.get();
        if (root != null && root.isSameSession(packet)) {
            root.updatePacket(packet);
        }
    }

    /**
     * drawBackgroundAdditional: 绘制原版村民风格交易内容和左侧 Tab。
     */
    @OnlyIn(Dist.CLIENT)
    @Override
    public void drawBackgroundAdditional(GUIContext guiContext) {
        updateDisplaySlots();
        int frameLeft = (int) getPositionX();
        int rootTop = (int) getPositionY();
        int top = rootTop + panelTop();
        Font font = guiContext.mc.font;
        if (packet.globalMarket()) {
            GlobalMarketModeTabStrip.renderUnselected(guiContext, font, frameLeft, rootTop, marketMode, packet.canList());
        }
        guiContext.graphics.blit(VILLAGER_LOCATION, frameLeft, top, 0, 0.0F, 0.0F, IMAGE_WIDTH, IMAGE_HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT);
        if (packet.globalMarket()) {
            GlobalMarketModeTabStrip.renderSelected(guiContext, font, frameLeft, rootTop, marketMode, packet.canList());
        }
        if (isWarehouseMode()) {
            renderWarehouse(guiContext, font, frameLeft, top);
            renderHoveredTooltip(guiContext, font, frameLeft, top);
            return;
        }
        renderLeftPanelBackground(guiContext, frameLeft, top);
        CommercialTradeTabStrip.render(guiContext, font, frameLeft, top, activeTab);
        renderSearchBox(guiContext, frameLeft, top);
        renderLabels(guiContext, font, frameLeft, top);
        renderMarketButtons(guiContext, font, frameLeft, top);
        renderSelectedOffer(guiContext, font, frameLeft, top);
        renderOfferList(guiContext, font, frameLeft, top);
        renderScroller(guiContext, frameLeft, top);
        renderHoveredTooltip(guiContext, font, frameLeft, top);
    }

    /**
     * drawBackgroundOverlay: 上架模式给中键选中的背包槽画选中框。交换的换得物品用蓝色。
     */
    @OnlyIn(Dist.CLIENT)
    @Override
    public void drawBackgroundOverlay(GUIContext guiContext) {
        if (!isListMode()) {
            return;
        }
        pruneSelection();
        ItemSlot resultSlotSelection = barterResultSlot();
        for (ItemSlot slot : selectedInventorySlots) {
            boolean result = slot == resultSlotSelection;
            drawSelectionSlot(guiContext, (int) slot.getPositionX(), (int) slot.getPositionY(), SLOT_SIZE,
                    result ? RESULT_SELECTION_COLOR : SELECTION_COLOR,
                    result ? RESULT_SELECTION_FILL : SELECTION_FILL);
        }
    }

    /**
     * onTabMouseDown: 只在左侧 Tab 命中区切换交易分类。
     */
    @OnlyIn(Dist.CLIENT)
    private void renderLeftPanelBackground(GUIContext guiContext, int left, int top) {
        guiContext.graphics.fill(left + ROW_X, top + CommercialTradeTabStrip.HIT_Y,
                left + CommercialTradeTabStrip.HIT_X + CommercialTradeTabStrip.HIT_WIDTH, top + ROW_Y, LEFT_PANEL_BACKGROUND);
    }

    private void onTabMouseDown(UIEvent event) {
        if (event.button != 0 || isWarehouseMode()) {
            release(event);
            return;
        }
        CommercialTradeOfferTab tab = CommercialTradeTabStrip.hit(event.x, event.y, (int) getPositionX(), panelOriginY());
        if (tab == null) {
            release(event);
            return;
        }
        activeTab = tab;
        selectedOfferId = "";
        selectedInventorySlots.clear();
        scrollOff = 0;
        refreshListingEditors();
        updateDisplaySlots();
        consume(event);
    }

    private void onModeTabMouseDown(UIEvent event) {
        if (event.button != 0 || !packet.globalMarket()) {
            release(event);
            return;
        }
        GlobalMarketModeTabStrip.Mode mode = GlobalMarketModeTabStrip.hit(
                event.x, event.y, (int) getPositionX(), (int) getPositionY(), packet.canList());
        if (mode == null) {
            release(event);
            return;
        }
        marketMode = mode;
        selectedOfferId = "";
        scrollOff = 0;
        warehouseScroll = 0;
        if (!isListMode()) {
            selectedInventorySlots.clear();
        }
        refreshListingEditors();
        updateDisplaySlots();
        consume(event);
    }

    /**
     * onOfferListMouseDown: 只处理报价列表行点击，避免抢占玩家背包槽位。
     */
    private void onOfferListMouseDown(UIEvent event) {
        if (event.button != 0 || isWarehouseMode()) {
            release(event);
            return;
        }
        int rowIndex = hoveredRow(event.x, event.y, (int) getPositionX(), panelOriginY());
        List<CommercialTradeOpenResponsePacket.OfferEntry> offers = filteredOffers();
        if (rowIndex < 0 || rowIndex >= offers.size()) {
            release(event);
            return;
        }
        selectedOfferId = offers.get(rowIndex).id();
        syncPriceFromOffer(offers.get(rowIndex));
        clampScrollToSelection();
        updateDisplaySlots();
        consume(event);
    }

    /**
     * onOfferListMouseWheel: 滚动当前 Tab 的报价列表。
     */
    private void onOfferListMouseWheel(UIEvent event) {
        if (!canScroll()) {
            release(event);
            return;
        }
        scrollOff = Mth.clamp((int) (scrollOff - event.deltaY), 0, maxScroll());
        consume(event);
    }

    /**
     * onScrollerMouseDown: 点击或拖拽滚动条时定位报价列表。
     */
    private void onScrollerMouseDown(UIEvent event) {
        if (event.button != 0 || !canScroll()) {
            release(event);
            return;
        }
        setScrollFromMouse(event.y, panelOriginY());
        event.target.startDrag(null, null);
        consume(event);
    }

    /**
     * onScrollerDragUpdate: 拖动滚动条时同步列表偏移。
     */
    private void onScrollerDragUpdate(UIEvent event) {
        if (!canScroll()) {
            release(event);
            return;
        }
        setScrollFromMouse(event.y, panelOriginY());
        consume(event);
    }

    /**
     * onResultSlotMouseDown: 点击结果槽时执行一次当前报价。
     */
    private void onResultSlotMouseDown(UIEvent event) {
        CommercialTradeOpenResponsePacket.OfferEntry offer = selectedOffer();
        if (event.button != 0 || isWarehouseMode() || offer == null || !canTrade(offer)) {
            return;
        }
        int count = (Screen.hasShiftDown() && isUnitTrade(offer)) ? 64 : 1;
        tradeSelected(true, count);
        event.stopImmediatePropagation();
    }

    private static boolean isUnitTrade(CommercialTradeOpenResponsePacket.OfferEntry offer) {
        CommercialTradeOpenResponsePacket.ResourceEntry result = first(offer.result(), 0);
        if (result != null && !CommercialTradeUiSupport.isMoney(result) && result.count() == 1) {
            return true;
        }
        CommercialTradeOpenResponsePacket.ResourceEntry cost = first(offer.cost(), 0);
        return cost != null
                && !CommercialTradeUiSupport.isMoney(cost)
                && cost.count() == 1
                && offer.result().stream().anyMatch(CommercialTradeUiSupport::isMoney);
    }

    @OnlyIn(Dist.CLIENT)
    private void renderLabels(GUIContext guiContext, Font font, int left, int top) {
        drawCenteredStringNoShadow(guiContext, font, CommercialTradeMenuProvider.title(packet), left + 187, top + 6, packet.globalMarket() ? ERROR_COLOR : TEXT_COLOR);
        Component leftTitle = packet.globalMarket()
                ? Component.translatable(isListMode() ? "gui.simukraft.global_market.list" : "gui.simukraft.global_market.trades")
                : TRADES_LABEL;
        int tradesWidth = font.width(leftTitle);
        guiContext.graphics.drawString(font, leftTitle, left + 5 - tradesWidth / 2 + 48, top + 6, packet.globalMarket() ? ERROR_COLOR : TEXT_COLOR, false);
        guiContext.graphics.drawString(font, INVENTORY_LABEL, left + 107, top + 72, TEXT_COLOR, false);
        String balance = Component.translatable("gui.simukraft.commercial.balance", CommercialTradeUiSupport.money(packet.cityBalance())).getString();
        guiContext.graphics.drawString(font, fitText(font, balance, 116), left + 136, top + 18, MUTED_COLOR, false);
    }

    @OnlyIn(Dist.CLIENT)
    private void renderSearchBox(GUIContext guiContext, int left, int top) {
        RecipeBookSearchUi.renderFrame(guiContext, left + SEARCH_FRAME_X, top + SEARCH_FRAME_Y,
                RecipeBookSearchUi.FRAME_WIDTH, RecipeBookSearchUi.FRAME_TEXTURE_WIDTH, RecipeBookSearchUi.FRAME_HEIGHT,
                RecipeBookSearchUi.TEXT_OFFSET_X, RecipeBookSearchUi.TEXT_OFFSET_Y,
                RecipeBookSearchUi.TEXT_WIDTH, RecipeBookSearchUi.TEXT_HEIGHT);
    }

    @OnlyIn(Dist.CLIENT)
    private void renderSelectedOffer(GUIContext guiContext, Font font, int left, int top) {
        CommercialTradeOpenResponsePacket.OfferEntry offer = selectedOffer();
        if (offer == null || isListMode()) {
            return;
        }
        boolean canTrade = canTrade(offer);
        if (!canTrade) {
            guiContext.graphics.blitSprite(OUT_OF_STOCK_SPRITE, left + 182, top + 35, 28, 21);
        }
        String costKey = CommercialTradeUiSupport.costEnough(packet, offer, getModularUI() != null ? getModularUI().player : null, 1)
                ? "gui.simukraft.commercial.cost_ok"
                : "gui.simukraft.commercial.cost_missing";
        guiContext.graphics.drawString(font, fitText(font, Component.translatable(costKey).getString(), 96), left + 136, top + 60, canTrade ? 0xFF2A602A : ERROR_COLOR, false);
        guiContext.graphics.drawString(font, fitText(font, Component.translatable("gui.simukraft.commercial.stock", CommercialTradeUiSupport.stockText(offer)).getString(), 96),
                left + 136, top + 70, CommercialTradeUiSupport.stockColor(offer, 1), false);
    }

    @OnlyIn(Dist.CLIENT)
    private void renderOfferList(GUIContext guiContext, Font font, int left, int top) {
        List<CommercialTradeOpenResponsePacket.OfferEntry> offers = filteredOffers();
        if (offers.isEmpty()) {
            guiContext.graphics.drawCenteredString(font, Component.translatable("gui.simukraft.commercial.no_offers"), left + 49, top + 82, ERROR_COLOR);
            return;
        }
        for (int row = 0; row < ROW_COUNT; row++) {
            int offerIndex = scrollOff + row;
            if (offerIndex >= offers.size()) {
                break;
            }
            renderOfferRow(guiContext, left, top, row, offers.get(offerIndex));
        }
    }

    @OnlyIn(Dist.CLIENT)
    private void renderOfferRow(GUIContext guiContext, int left, int top, int row, CommercialTradeOpenResponsePacket.OfferEntry offer) {
        int rowLeft = left + ROW_X;
        int rowTop = top + ROW_Y + row * ROW_HEIGHT;
        boolean selected = offer.id().equals(selectedOfferId);
        boolean available = isListMode() || canTrade(offer);
        boolean hovered = inside(guiContext.mouseX, guiContext.mouseY, rowLeft, rowTop, ROW_WIDTH, ROW_HEIGHT);
        RenderSystem.enableBlend();
        guiContext.graphics.blitSprite(selected || hovered ? BUTTON_HIGHLIGHTED_SPRITE : BUTTON_SPRITE, rowLeft, rowTop, ROW_WIDTH, ROW_HEIGHT);
        int itemY = rowTop + 1;
        renderResource(guiContext, first(offer.cost(), 0), left + 10, itemY);
        renderResource(guiContext, first(offer.cost(), 1), left + 40, itemY);
        renderTradeArrow(guiContext, available, left + 60, itemY + 3);
        renderResource(guiContext, first(offer.result(), 0), left + 73, itemY);
    }

    @OnlyIn(Dist.CLIENT)
    private void renderTradeArrow(GUIContext guiContext, boolean canTrade, int x, int y) {
        RenderSystem.enableBlend();
        guiContext.graphics.blitSprite(canTrade ? TRADE_ARROW_SPRITE : TRADE_ARROW_OUT_OF_STOCK_SPRITE, x, y, 0, 10, 9);
    }

    @OnlyIn(Dist.CLIENT)
    private void renderScroller(GUIContext guiContext, int left, int top) {
        if (!canScroll()) {
            guiContext.graphics.blitSprite(SCROLLER_DISABLED_SPRITE, left + SCROLL_X, top + SCROLL_Y, SCROLL_WIDTH, SCROLLER_HEIGHT);
            return;
        }
        int steps = filteredOffers().size() + 1 - ROW_COUNT;
        int hidden = SCROLL_HEIGHT - (SCROLLER_HEIGHT + (steps - 1) * SCROLL_HEIGHT / steps);
        int stepHeight = 1 + hidden / steps + SCROLL_HEIGHT / steps;
        int scrollY = Math.min(SCROLL_HEIGHT - SCROLLER_HEIGHT, scrollOff * stepHeight);
        if (scrollOff == maxScroll()) {
            scrollY = SCROLL_HEIGHT - SCROLLER_HEIGHT;
        }
        guiContext.graphics.blitSprite(SCROLLER_SPRITE, left + SCROLL_X, top + SCROLL_Y + scrollY, SCROLL_WIDTH, SCROLLER_HEIGHT);
    }

    @OnlyIn(Dist.CLIENT)
    private void renderResource(GUIContext guiContext, @Nullable CommercialTradeOpenResponsePacket.ResourceEntry resource, int x, int y) {
        if (resource == null) {
            return;
        }
        ItemStack stack = CommercialTradeUiSupport.resourceStack(resource);
        guiContext.graphics.renderFakeItem(stack, x, y);
        if (CommercialTradeUiSupport.isMoney(resource)) {
            drawResourceCount(guiContext, CommercialTradeUiSupport.moneyShort(resource.money()), x, y);
        } else {
            guiContext.graphics.renderItemDecorations(guiContext.mc.font, stack, x, y);
        }
    }

    @OnlyIn(Dist.CLIENT)
    private void renderHoveredTooltip(GUIContext guiContext, Font font, int left, int top) {
        if (getModularUI() != null) {
            if (packet.globalMarket()) {
                Component tabName = GlobalMarketModeTabStrip.hoveredName(
                        guiContext.mouseX, guiContext.mouseY, left, (int) getPositionY(), packet.canList());
                if (tabName != null) {
                    getModularUI().setHoverTooltip(List.of(tabName), ItemStack.EMPTY, font, null);
                    return;
                }
            }
            if (isWarehouseMode()) {
                int index = GlobalMarketWarehousePanel.hitIndex(guiContext.mouseX, guiContext.mouseY, left, top, warehouseScroll, packet.warehouse().size());
                if (index >= 0) {
                    CommercialTradeOpenResponsePacket.WarehouseSlot slot = packet.warehouse().get(index);
                    ItemStack stack = slot.stack();
                    getModularUI().setHoverTooltip(itemHoverTooltip(stack, slot.count()), stack, font, stack.getTooltipImage().orElse(null));
                    return;
                }
                return;
            }
            if (isListMode()) {
                if (inside(guiContext.mouseX, guiContext.mouseY, left + LIST_ICON_X, top + ACTION_ICON_Y, ACTION_ICON_SIZE, ACTION_ICON_SIZE)) {
                    getModularUI().setHoverTooltip(List.of(
                            Component.translatable("gui.simukraft.global_market.list"),
                            Component.translatable("gui.simukraft.global_market.ctrl_list_hint")), ItemStack.EMPTY, font, null);
                    return;
                }
                if (inside(guiContext.mouseX, guiContext.mouseY, left + UNLIST_ICON_X, top + ACTION_ICON_Y, ACTION_ICON_SIZE, ACTION_ICON_SIZE)) {
                    getModularUI().setHoverTooltip(List.of(Component.translatable("gui.simukraft.global_market.unlist")), ItemStack.EMPTY, font, null);
                    return;
                }
            }
        }
        CommercialTradeOpenResponsePacket.ResourceEntry resource = hoveredResource(guiContext.mouseX, guiContext.mouseY, left, top);
        if (resource == null || getModularUI() == null) {
            return;
        }
        ItemStack stack = CommercialTradeUiSupport.resourceStack(resource);
        if (CommercialTradeUiSupport.isMoney(resource)) {
            getModularUI().setHoverTooltip(List.of(Component.translatable("gui.simukraft.commercial.money_resource", CommercialTradeUiSupport.money(resource.money()))), stack, font, null);
        } else {
            getModularUI().setHoverTooltip(itemHoverTooltip(stack, resource.count()), stack, font, stack.getTooltipImage().orElse(null));
        }
    }

    /**
     * itemHoverTooltip: 原版物品提示（品质、附魔、F3+H），数量放在最后一行。
     */
    @OnlyIn(Dist.CLIENT)
    private List<Component> itemHoverTooltip(ItemStack stack, int count) {
        Minecraft minecraft = Minecraft.getInstance();
        Item.TooltipContext context = minecraft.level != null ? Item.TooltipContext.of(minecraft.level) : Item.TooltipContext.EMPTY;
        TooltipFlag flag = minecraft.options.advancedItemTooltips ? TooltipFlag.ADVANCED : TooltipFlag.NORMAL;
        List<Component> lines = new ArrayList<>(stack.getTooltipLines(context, minecraft.player, flag));
        lines.add(Component.literal("x" + Math.max(1, count)));
        return lines;
    }

    @OnlyIn(Dist.CLIENT)
    private void drawResourceCount(GUIContext guiContext, String label, int x, int y) {
        if (label.isBlank()) {
            return;
        }
        Font font = guiContext.mc.font;
        guiContext.pose.pushPose();
        guiContext.pose.translate(0.0F, 0.0F, 200.0F);
        guiContext.graphics.drawString(font, label, x + 19 - 2 - font.width(label), y + 9, 0xFFFFFF, true);
        guiContext.pose.popPose();
    }

    private void tradeSelected(boolean quickMove, int count) {
        CommercialTradeOpenResponsePacket.OfferEntry offer = selectedOffer();
        if (offer == null || !canTrade(offer)) {
            return;
        }
        if (packet.globalMarket()) {
            PacketDistributor.sendToServer(new CommercialTradePacket(packet.boxPos(), packet.workerId(), offer.id(), count, quickMove, true));
            return;
        }
        if (packet.workerId() != null) {
            PacketDistributor.sendToServer(new CommercialTradePacket(packet.boxPos(), packet.workerId(), offer.id(), count, quickMove));
        }
    }

    private boolean canTrade(CommercialTradeOpenResponsePacket.OfferEntry offer) {
        if (!packet.running() || isListMode()) {
            return false;
        }
        if (!packet.globalMarket() && packet.workerId() == null) {
            return false;
        }
        return CommercialTradeUiSupport.costEnough(packet, offer, getModularUI() != null ? getModularUI().player : null, 1)
                && CommercialTradeUiSupport.stockEnough(offer, 1);
    }

    @OnlyIn(Dist.CLIENT)
    private void renderMarketButtons(GUIContext guiContext, Font font, int left, int top) {
        if (!isListMode()) {
            return;
        }
        if (activeTab != CommercialTradeOfferTab.BARTER) {
            guiContext.graphics.drawString(font, Component.translatable("gui.simukraft.global_market.price"), left + 136, top + 58, MUTED_COLOR, false);
        }
        guiContext.graphics.drawString(font, Component.translatable("gui.simukraft.global_market.quantity"), left + 136, top + 70, MUTED_COLOR, false);
        renderActionIcon(guiContext, left + LIST_ICON_X, top + ACTION_ICON_Y, new ItemStack(Items.CHEST), true);
        renderActionIcon(guiContext, left + UNLIST_ICON_X, top + ACTION_ICON_Y, new ItemStack(Items.BARRIER), canUnlistSelected());
    }

    @OnlyIn(Dist.CLIENT)
    private void renderActionIcon(GUIContext guiContext, int x, int y, ItemStack icon, boolean enabled) {
        blitButton(guiContext, x, y, ACTION_ICON_SIZE, ACTION_ICON_SIZE);
        if (!enabled) {
            guiContext.graphics.fill(x, y, x + ACTION_ICON_SIZE, y + ACTION_ICON_SIZE, 0x66000000);
        }
        guiContext.pose.pushPose();
        guiContext.pose.translate(0.0F, 0.0F, 200.0F);
        guiContext.graphics.renderFakeItem(icon, x + 1, y + 1);
        guiContext.pose.popPose();
    }

    @OnlyIn(Dist.CLIENT)
    private void blitButton(GUIContext guiContext, int x, int y, int width, int height) {
        boolean hovered = inside(guiContext.mouseX, guiContext.mouseY, x, y, width, height);
        RenderSystem.enableBlend();
        guiContext.graphics.blitSprite(hovered ? BUTTON_HIGHLIGHTED_SPRITE : BUTTON_SPRITE, x, y, width, height);
    }

    private boolean canUnlistSelected() {
        CommercialTradeOpenResponsePacket.OfferEntry offer = selectedOffer();
        return isListMode()
                && offer != null
                && packet.marketCityId() != null
                && packet.marketCityId().equals(offer.sellerCityId());
    }

    private void onUnlistMouseDown(UIEvent event) {
        if (event.button != 0 || !canUnlistSelected()) {
            release(event);
            return;
        }
        PacketDistributor.sendToServer(new GlobalMarketUnlistPacket(packet.boxPos(), selectedOfferId));
        consume(event);
    }

    private void updateDisplaySlots() {
        if (isListMode() && !selectedInventorySlots.isEmpty()) {
            previewSelection();
            return;
        }
        CommercialTradeOpenResponsePacket.OfferEntry offer = selectedOffer();
        setSlotResource(costSlotA, offer == null ? null : first(offer.cost(), 0));
        setSlotResource(costSlotB, offer == null ? null : first(offer.cost(), 1));
        setSlotResource(resultSlot, offer == null ? null : first(offer.result(), 0));
    }

    private void previewSelection() {
        List<ItemStack> items = selectedStacks();
        if (activeTab == CommercialTradeOfferTab.BARTER) {
            setSlotStack(costSlotA, items.isEmpty() ? ItemStack.EMPTY : items.getFirst());
            if (items.size() >= 3) {
                setSlotStack(costSlotB, items.get(1));
                setSlotStack(resultSlot, items.getLast());
            } else if (items.size() == 2) {
                setSlotStack(costSlotB, ItemStack.EMPTY);
                setSlotStack(resultSlot, items.getLast());
            } else {
                setSlotStack(costSlotB, ItemStack.EMPTY);
                setSlotStack(resultSlot, ItemStack.EMPTY);
            }
            return;
        }
        ItemStack merged = mergeSameItem(items);
        if (activeTab == CommercialTradeOfferTab.BUY) {
            setSlotStack(costSlotA, merged);
            setSlotStack(costSlotB, ItemStack.EMPTY);
            setSlotStack(resultSlot, ItemStack.EMPTY);
            return;
        }
        setSlotStack(costSlotA, ItemStack.EMPTY);
        setSlotStack(costSlotB, ItemStack.EMPTY);
        setSlotStack(resultSlot, merged);
    }

    private void setSlotStack(ItemSlot slot, ItemStack stack) {
        slot.setItem(stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy(), false);
    }

    private static ItemStack mergeSameItem(List<ItemStack> items) {
        ItemStack first = null;
        int total = 0;
        for (ItemStack item : items) {
            if (item == null || item.isEmpty()) {
                continue;
            }
            if (first == null) {
                first = item;
            } else if (!ItemStack.isSameItemSameComponents(first, item)) {
                return ItemStack.EMPTY;
            }
            total += item.getCount();
        }
        if (first == null || total <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack merged = first.copy();
        merged.setCount(total);
        return merged;
    }

    private void setSlotResource(ItemSlot slot, @Nullable CommercialTradeOpenResponsePacket.ResourceEntry resource) {
        slot.setItem(resource == null ? ItemStack.EMPTY : CommercialTradeUiSupport.resourceStack(resource), false);
    }

    private List<CommercialTradeOpenResponsePacket.OfferEntry> filteredOffers() {
        return packet.offers().stream()
                .filter(this::matchesMarketMode)
                .filter(activeTab::matches)
                .filter(this::matchesSearch)
                .toList();
    }

    private boolean matchesMarketMode(CommercialTradeOpenResponsePacket.OfferEntry offer) {
        if (!isListMode()) {
            return true;
        }
        return packet.marketCityId() != null && packet.marketCityId().equals(offer.sellerCityId());
    }

    @Nullable
    private CommercialTradeOpenResponsePacket.OfferEntry selectedOffer() {
        List<CommercialTradeOpenResponsePacket.OfferEntry> offers = filteredOffers();
        for (CommercialTradeOpenResponsePacket.OfferEntry offer : offers) {
            if (offer.id().equals(selectedOfferId)) {
                return offer;
            }
        }
        return null;
    }

    private boolean isSameSession(CommercialTradeOpenResponsePacket next) {
        return next != null
                && Objects.equals(packet.boxPos(), next.boxPos())
                && Objects.equals(packet.workerId(), next.workerId());
    }

    private void updatePacket(CommercialTradeOpenResponsePacket next) {
        this.packet = next;
        if (selectedOffer() == null) {
            selectedOfferId = "";
        }
        scrollOff = Mth.clamp(scrollOff, 0, maxScroll());
        refreshListingEditors();
        updateDisplaySlots();
    }

    private void onSearchChanged(String text) {
        searchText = text != null ? text.trim().toLowerCase(Locale.ROOT) : "";
        selectedOfferId = "";
        scrollOff = 0;
        updateDisplaySlots();
    }

    private boolean matchesSearch(CommercialTradeOpenResponsePacket.OfferEntry offer) {
        if (searchText.isBlank()) {
            return true;
        }
        if (containsSearch(offer.id()) || containsSearch(offer.stockItem())) {
            return true;
        }
        return offer.cost().stream().anyMatch(this::matchesSearch)
                || offer.result().stream().anyMatch(this::matchesSearch);
    }

    private boolean matchesSearch(CommercialTradeOpenResponsePacket.ResourceEntry resource) {
        if (resource == null) {
            return false;
        }
        if (CommercialTradeUiSupport.isMoney(resource)) {
            return containsSearch(Component.translatable("gui.simukraft.commercial.money_resource", CommercialTradeUiSupport.money(resource.money())).getString())
                    || containsSearch(CommercialTradeUiSupport.money(resource.money()));
        }
        ItemStack stack = CommercialTradeUiSupport.resourceStack(resource);
        return containsSearch(resource.itemId()) || containsSearch(stack.getHoverName().getString());
    }

    private boolean containsSearch(String text) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(searchText);
    }

    private void clampScrollToSelection() {
        int selectedIndex = selectedOfferIndex();
        if (selectedIndex < 0) {
            scrollOff = Mth.clamp(scrollOff, 0, maxScroll());
            return;
        }
        if (selectedIndex < scrollOff) {
            scrollOff = selectedIndex;
        } else if (selectedIndex >= scrollOff + ROW_COUNT) {
            scrollOff = selectedIndex - ROW_COUNT + 1;
        }
        scrollOff = Mth.clamp(scrollOff, 0, maxScroll());
    }

    private int selectedOfferIndex() {
        List<CommercialTradeOpenResponsePacket.OfferEntry> offers = filteredOffers();
        for (int i = 0; i < offers.size(); i++) {
            if (offers.get(i).id().equals(selectedOfferId)) {
                return i;
            }
        }
        return -1;
    }

    private int hoveredRow(float mouseX, float mouseY, int left, int top) {
        int row = hoveredVisibleRow(mouseX, mouseY, left, top);
        int index = row < 0 ? -1 : scrollOff + row;
        return index >= 0 && index < filteredOffers().size() ? index : -1;
    }

    private int hoveredVisibleRow(float mouseX, float mouseY, int left, int top) {
        if (!inside(mouseX, mouseY, left + ROW_X, top + ROW_Y, ROW_WIDTH, ROW_HEIGHT * ROW_COUNT)) {
            return -1;
        }
        int row = ((int) mouseY - (top + ROW_Y)) / ROW_HEIGHT;
        return row >= 0 && row < ROW_COUNT && scrollOff + row < filteredOffers().size() ? row : -1;
    }

    @Nullable
    private CommercialTradeOpenResponsePacket.ResourceEntry hoveredResource(float mouseX, float mouseY, int left, int top) {
        CommercialTradeOpenResponsePacket.OfferEntry selected = selectedOffer();
        if (selected != null) {
            CommercialTradeOpenResponsePacket.ResourceEntry hovered = hoveredInOffer(selected, mouseX, mouseY, left, top, true, 0);
            if (hovered != null) {
                return hovered;
            }
        }
        int row = hoveredVisibleRow(mouseX, mouseY, left, top);
        if (row < 0) {
            return null;
        }
        List<CommercialTradeOpenResponsePacket.OfferEntry> offers = filteredOffers();
        if (scrollOff + row >= offers.size()) {
            return null;
        }
        return hoveredInOffer(offers.get(scrollOff + row), mouseX, mouseY, left, top, false, row);
    }

    @Nullable
    private CommercialTradeOpenResponsePacket.ResourceEntry hoveredInOffer(CommercialTradeOpenResponsePacket.OfferEntry offer, float mouseX, float mouseY, int left, int top, boolean detail, int row) {
        int y = detail ? top + TRADE_SLOT_Y : top + ROW_Y + row * ROW_HEIGHT;
        int costAX = detail ? left + COST_A_X : left + 10;
        int costBX = detail ? left + COST_B_X : left + 40;
        int resultX = detail ? left + RESULT_X : left + 73;
        if (inside(mouseX, mouseY, costAX, y, SLOT_SIZE, SLOT_SIZE)) {
            return first(offer.cost(), 0);
        }
        if (inside(mouseX, mouseY, costBX, y, SLOT_SIZE, SLOT_SIZE)) {
            return first(offer.cost(), 1);
        }
        if (inside(mouseX, mouseY, resultX, y, SLOT_SIZE, SLOT_SIZE)) {
            return first(offer.result(), 0);
        }
        return null;
    }

    private void setScrollFromMouse(float mouseY, int top) {
        int max = maxScroll();
        float factor = (mouseY - (top + SCROLL_Y) - SCROLLER_HEIGHT / 2.0F) / (SCROLL_HEIGHT - SCROLLER_HEIGHT);
        scrollOff = Mth.clamp((int) (factor * max + 0.5F), 0, max);
    }

    private boolean canScroll() {
        return filteredOffers().size() > ROW_COUNT;
    }

    private int maxScroll() {
        return Math.max(0, filteredOffers().size() - ROW_COUNT);
    }

    private UIElement listIconHitbox() {
        UIElement hitbox = interactionHitbox(LIST_ICON_X, ACTION_ICON_Y, ACTION_ICON_SIZE, ACTION_ICON_SIZE);
        hitbox.style(style -> style.backgroundTexture(IGuiTexture.EMPTY).zIndex(35));
        hitbox.addEventListener(UIEvents.MOUSE_DOWN, this::onListIconMouseDown);
        return hitbox;
    }

    private UIElement unlistIconHitbox() {
        UIElement hitbox = interactionHitbox(UNLIST_ICON_X, ACTION_ICON_Y, ACTION_ICON_SIZE, ACTION_ICON_SIZE);
        hitbox.style(style -> style.backgroundTexture(IGuiTexture.EMPTY).zIndex(35));
        hitbox.addEventListener(UIEvents.MOUSE_DOWN, this::onUnlistMouseDown);
        return hitbox;
    }

    private UIElement modeTabHitbox() {
        UIElement hitbox = new UIElement();
        hitbox.layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE)
                .left(GlobalMarketModeTabStrip.HIT_X)
                .top(GlobalMarketModeTabStrip.HIT_Y)
                .width(GlobalMarketModeTabStrip.hitWidth(packet.canList()))
                .height(GlobalMarketModeTabStrip.TAB_HEIGHT));
        hitbox.style(style -> style.backgroundTexture(IGuiTexture.EMPTY).zIndex(40));
        hitbox.addEventListener(UIEvents.MOUSE_DOWN, this::onModeTabMouseDown);
        return hitbox;
    }

    private UIElement tabHitbox() {
        UIElement hitbox = interactionHitbox(CommercialTradeTabStrip.HIT_X, CommercialTradeTabStrip.HIT_Y, CommercialTradeTabStrip.HIT_WIDTH, CommercialTradeTabStrip.HIT_HEIGHT);
        hitbox.addEventListener(UIEvents.MOUSE_DOWN, this::onTabMouseDown);
        return hitbox;
    }

    private UIElement offerListHitbox() {
        UIElement hitbox = interactionHitbox(ROW_X, ROW_Y, ROW_WIDTH, ROW_HEIGHT * ROW_COUNT);
        hitbox.addEventListener(UIEvents.MOUSE_DOWN, this::onOfferListMouseDown);
        hitbox.addEventListener(UIEvents.MOUSE_WHEEL, this::onOfferListMouseWheel);
        return hitbox;
    }

    private UIElement scrollerHitbox() {
        UIElement hitbox = interactionHitbox(SCROLL_X, SCROLL_Y, SCROLL_WIDTH, SCROLL_HEIGHT + 1);
        hitbox.addEventListener(UIEvents.MOUSE_DOWN, this::onScrollerMouseDown);
        hitbox.addEventListener(UIEvents.MOUSE_WHEEL, this::onOfferListMouseWheel);
        hitbox.addEventListener(UIEvents.DRAG_UPDATE, this::onScrollerDragUpdate);
        return hitbox;
    }

    private UIElement interactionHitbox(int x, int y, int width, int height) {
        UIElement hitbox = new UIElement();
        hitbox.layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE).left(x).top(contentY(y)).width(width).height(height));
        hitbox.style(style -> style.backgroundTexture(IGuiTexture.EMPTY).zIndex(20));
        return hitbox;
    }

    private static void consume(UIEvent event) {
        event.stopPropagation();
    }

    @OnlyIn(Dist.CLIENT)
    private static void drawCenteredStringNoShadow(GUIContext guiContext, Font font, Component text, int centerX, int y, int color) {
        guiContext.graphics.drawString(font, text, centerX - font.width(text) / 2, y, color, false);
    }

    private static void release(UIEvent event) {
        event.hasHandler = false;
    }

    private boolean isListMode() {
        return packet.globalMarket() && packet.canList() && marketMode == GlobalMarketModeTabStrip.Mode.LIST;
    }

    private boolean isWarehouseMode() {
        return packet.globalMarket() && packet.canList() && marketMode == GlobalMarketModeTabStrip.Mode.WAREHOUSE;
    }

    @OnlyIn(Dist.CLIENT)
    private void renderWarehouse(GUIContext guiContext, Font font, int left, int top) {
        guiContext.graphics.fill(left + ROW_X, top + 16, left + 103, top + 160, LEFT_PANEL_BACKGROUND);
        Component title = Component.translatable("gui.simukraft.global_market.warehouse");
        guiContext.graphics.drawString(font, title, left + 5 - font.width(title) / 2 + 48, top + 6, ERROR_COLOR, false);
        drawCenteredStringNoShadow(guiContext, font, CommercialTradeMenuProvider.title(packet), left + 187, top + 6, ERROR_COLOR);
        guiContext.graphics.drawString(font, fitText(font, Component.translatable("gui.simukraft.global_market.warehouse_hint").getString(), 90), left + 8, top + 24, MUTED_COLOR, false);
        guiContext.graphics.drawString(font, fitText(font, Component.translatable("gui.simukraft.global_market.warehouse_hint_take").getString(), 90), left + 8, top + 36, MUTED_COLOR, false);
        guiContext.graphics.drawString(font, fitText(font, Component.translatable("gui.simukraft.global_market.warehouse_hint_all").getString(), 90), left + 8, top + 48, MUTED_COLOR, false);
        guiContext.graphics.drawString(font, INVENTORY_LABEL, left + 107, top + 72, TEXT_COLOR, false);
        List<CommercialTradeOpenResponsePacket.WarehouseSlot> items = packet.warehouse();
        warehouseScroll = Mth.clamp(warehouseScroll, 0, GlobalMarketWarehousePanel.maxScroll(items.size()));
        GlobalMarketWarehousePanel.render(guiContext, font, left, top, items, warehouseScroll);
        if (items.isEmpty()) {
            guiContext.graphics.drawCenteredString(font, Component.translatable("gui.simukraft.global_market.warehouse_empty"),
                    left + GlobalMarketWarehousePanel.X + GlobalMarketWarehousePanel.COLS * GlobalMarketWarehousePanel.SLOT / 2,
                    top + GlobalMarketWarehousePanel.Y + 22, ERROR_COLOR);
        }
    }

    private UIElement warehouseHitbox() {
        UIElement hitbox = interactionHitbox(GlobalMarketWarehousePanel.X, GlobalMarketWarehousePanel.Y,
                GlobalMarketWarehousePanel.COLS * GlobalMarketWarehousePanel.SLOT,
                GlobalMarketWarehousePanel.ROWS * GlobalMarketWarehousePanel.SLOT);
        hitbox.style(style -> style.backgroundTexture(IGuiTexture.EMPTY).zIndex(30));
        hitbox.addEventListener(UIEvents.MOUSE_DOWN, this::onWarehouseMouseDown);
        hitbox.addEventListener(UIEvents.MOUSE_WHEEL, this::onWarehouseMouseWheel);
        return hitbox;
    }

    private void onWarehouseMouseDown(UIEvent event) {
        if (!isWarehouseMode() || event.button != 0) {
            release(event);
            return;
        }
        int index = GlobalMarketWarehousePanel.hitIndex(event.x, event.y, (int) getPositionX(), panelOriginY(), warehouseScroll, packet.warehouse().size());
        if (index < 0) {
            release(event);
            return;
        }
        CommercialTradeOpenResponsePacket.WarehouseSlot slot = packet.warehouse().get(index);
        int amount = Screen.hasShiftDown() ? slot.count() : Math.min(64, slot.count());
        PacketDistributor.sendToServer(new GlobalMarketWarehouseWithdrawPacket(packet.boxPos(), slot.stack(), amount));
        consume(event);
    }

    private void onWarehouseMouseWheel(UIEvent event) {
        if (!isWarehouseMode()) {
            release(event);
            return;
        }
        warehouseScroll = Mth.clamp((int) (warehouseScroll - event.deltaY), 0, GlobalMarketWarehousePanel.maxScroll(packet.warehouse().size()));
        consume(event);
    }

    private int panelTop() {
        return packet.globalMarket() ? GlobalMarketModeTabStrip.TAB_TOP_OFFSET : 0;
    }

    private int panelOriginY() {
        return (int) getPositionY() + panelTop();
    }

    private int contentY(int y) {
        return y + panelTop();
    }

    private void refreshListingEditors() {
        boolean warehouse = isWarehouseMode();
        boolean list = isListMode();
        searchField.setDisplay(!warehouse);
        costSlotA.setDisplay(!warehouse);
        costSlotB.setDisplay(!warehouse);
        resultSlot.setDisplay(!warehouse);
        if (priceField != null) {
            priceField.setDisplay(list && activeTab != CommercialTradeOfferTab.BARTER);
        }
        if (quantityField != null) {
            quantityField.setDisplay(list);
        }
        if (listIconHitbox != null) {
            listIconHitbox.setDisplay(list);
        }
        if (unlistIconHitbox != null) {
            unlistIconHitbox.setDisplay(list);
        }
        if (warehouseHitbox != null) {
            warehouseHitbox.setDisplay(warehouse);
        }
    }

    /**
     * onInventoryListMouseDown: 上架模式下鼠标中键切换选中。交换最多两个付出加一个换得。
     */
    private void onInventoryListMouseDown(UIEvent event, ItemSlot slot) {
        if (isWarehouseMode() && event.button == 0 && Screen.hasShiftDown()) {
            ItemStack stack = slot.getSlot() != null ? slot.getSlot().getItem() : ItemStack.EMPTY;
            if (!stack.isEmpty()) {
                PacketDistributor.sendToServer(new GlobalMarketWarehouseDepositPacket(packet.boxPos(), stack.copy()));
            }
            event.stopImmediatePropagation();
            return;
        }
        if (event.button != MIDDLE_MOUSE || !isListMode()) {
            return;
        }
        ItemStack stack = slot.getSlot() != null ? slot.getSlot().getItem() : ItemStack.EMPTY;
        if (stack.isEmpty()) {
            selectedInventorySlots.remove(slot);
            event.stopImmediatePropagation();
            return;
        }
        if (selectedInventorySlots.contains(slot)) {
            selectedInventorySlots.remove(slot);
            event.stopImmediatePropagation();
            return;
        }
        if (activeTab == CommercialTradeOfferTab.BARTER) {
            if (selectedInventorySlots.size() >= 3) {
                toast("message.simukraft.global_market.need_barter_selection");
                event.stopImmediatePropagation();
                return;
            }
        } else if (!selectionMatches(stack)) {
            toast("message.simukraft.global_market.one_item_type");
            event.stopImmediatePropagation();
            return;
        }
        selectedInventorySlots.add(slot);
        event.stopImmediatePropagation();
    }

    private boolean selectionMatches(ItemStack stack) {
        for (ItemSlot selected : selectedInventorySlots) {
            ItemStack current = selected.getSlot() != null ? selected.getSlot().getItem() : ItemStack.EMPTY;
            if (!current.isEmpty()) {
                return ItemStack.isSameItemSameComponents(current, stack);
            }
        }
        return true;
    }

    @Nullable
    private ItemSlot barterResultSlot() {
        if (activeTab != CommercialTradeOfferTab.BARTER || selectedInventorySlots.size() < 2) {
            return null;
        }
        ItemSlot last = null;
        for (ItemSlot slot : selectedInventorySlots) {
            last = slot;
        }
        return last;
    }

    private void onListIconMouseDown(UIEvent event) {
        if (event.button != 0 || !isListMode()) {
            release(event);
            return;
        }
        pruneSelection();
        List<ItemStack> items = selectedStacks();
        if (items.isEmpty()) {
            toast("message.simukraft.global_market.need_selection");
            consume(event);
            return;
        }
        if (activeTab == CommercialTradeOfferTab.BARTER && (items.size() < 2 || items.size() > 3)) {
            toast("message.simukraft.global_market.need_barter_selection");
            consume(event);
            return;
        }
        double price = EconomyService.normalizeAmount(EconomyService.parseAmount(priceText));
        if (activeTab != CommercialTradeOfferTab.BARTER && price < 0.01D) {
            toast("message.simukraft.global_market.price_too_low");
            consume(event);
            return;
        }
        int quantity = parseQuantity();
        if (quantity < 1) {
            toast("message.simukraft.global_market.need_quantity");
            consume(event);
            return;
        }
        PacketDistributor.sendToServer(new GlobalMarketListPacket(
                packet.boxPos(),
                activeTab.name(),
                items,
                price,
                quantity));
        selectedInventorySlots.clear();
        consume(event);
    }

    private void toast(String key) {
        if (getModularUI() != null && getModularUI().player != null) {
            getModularUI().player.displayClientMessage(Component.translatable(key), true);
        }
    }

    private List<ItemStack> selectedStacks() {
        List<ItemStack> items = new ArrayList<>();
        for (ItemSlot slot : selectedInventorySlots) {
            ItemStack stack = slot.getSlot() != null ? slot.getSlot().getItem() : ItemStack.EMPTY;
            if (!stack.isEmpty()) {
                items.add(stack.copy());
            }
        }
        return items;
    }

    private void pruneSelection() {
        selectedInventorySlots.removeIf(slot -> slot.getSlot() == null || slot.getSlot().getItem().isEmpty());
    }

    @OnlyIn(Dist.CLIENT)
    private static void drawSelectionSlot(GUIContext guiContext, int x, int y, int size, int border, int fill) {
        guiContext.graphics.fill(x, y, x + size, y + size, fill);
        guiContext.graphics.fill(x, y, x + size, y + 2, border);
        guiContext.graphics.fill(x, y + size - 2, x + size, y + size, border);
        guiContext.graphics.fill(x, y, x + 2, y + size, border);
        guiContext.graphics.fill(x + size - 2, y, x + size, y + size, border);
    }

    private void syncPriceFromOffer(CommercialTradeOpenResponsePacket.OfferEntry offer) {
        if (priceField == null || offer == null) {
            return;
        }
        double money = moneyAmount(offer.cost());
        if (money <= 0.0D) {
            money = moneyAmount(offer.result());
        }
        if (money <= 0.0D) {
            return;
        }
        priceText = CommercialTradeUiSupport.money(money);
        priceField.setText(priceText, false);
    }

    private static double moneyAmount(List<CommercialTradeOpenResponsePacket.ResourceEntry> resources) {
        return resources.stream()
                .filter(CommercialTradeUiSupport::isMoney)
                .mapToDouble(CommercialTradeOpenResponsePacket.ResourceEntry::money)
                .findFirst()
                .orElse(0.0D);
    }

    private TextField priceField() {
        TextField field = RecipeBookSearchUi.createField(168, contentY(56), 48, 13, priceText, text -> priceText = text);
        field.textFieldStyle(style -> style.textColor(0xFF2A2A2A)
                .cursorColor(0xFF2A2A2A)
                .textShadow(false)
                .fontSize(9.0F)
                .placeholder(Component.translatable("gui.simukraft.global_market.price"))
                .focusOverlay(IGuiTexture.EMPTY));
        return field;
    }

    private TextField quantityField() {
        TextField field = RecipeBookSearchUi.createField(168, contentY(68), 48, 13, quantityText, text -> quantityText = text);
        field.textFieldStyle(style -> style.textColor(0xFF2A2A2A)
                .cursorColor(0xFF2A2A2A)
                .textShadow(false)
                .fontSize(9.0F)
                .placeholder(Component.translatable("gui.simukraft.global_market.quantity"))
                .focusOverlay(IGuiTexture.EMPTY));
        return field;
    }

    private int parseQuantity() {
        try {
            return Math.max(0, Integer.parseInt(quantityText.replaceAll("[^0-9]", "")));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private ItemSlot displaySlot(int x, int y) {
        ItemSlot slot = new ItemSlot(new LocalSlot() {
            @Override
            public boolean mayPickup(Player player) {
                return false;
            }

            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
        slot.layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE).left(x - 1).top(contentY(y) - 1).width(SLOT_SIZE).height(SLOT_SIZE));
        slot.style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
        slot.slotStyle(style -> style.slotOverlay(IGuiTexture.EMPTY).showItemTooltips(true));
        return slot;
    }

    private InventorySlots playerInventorySlots() {
        InventorySlots slots = new InventorySlots();
        slots.layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE).left(INVENTORY_X - 1).top(contentY(INVENTORY_Y) - 1).width(162).height(76));
        slots.hotbar.layout(layout -> layout.marginTop(4));
        slots.apply(slot -> {
            slot.style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
            if (packet.globalMarket() && packet.canList()) {
                slot.addEventListener(UIEvents.MOUSE_DOWN, event -> onInventoryListMouseDown(event, slot), true);
            }
        });
        return slots;
    }

    private TextField searchField() {
        return RecipeBookSearchUi.createField(SEARCH_TEXT_X, contentY(SEARCH_TEXT_Y), "", this::onSearchChanged);
    }

    @OnlyIn(Dist.CLIENT)
    private static String fitText(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        return font.plainSubstrByWidth(text, Math.max(1, maxWidth - font.width(ellipsis))) + ellipsis;
    }

    @Nullable
    private static CommercialTradeOpenResponsePacket.ResourceEntry first(List<CommercialTradeOpenResponsePacket.ResourceEntry> resources, int index) {
        return index >= 0 && index < resources.size() ? resources.get(index) : null;
    }

    private static boolean inside(float mouseX, float mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
