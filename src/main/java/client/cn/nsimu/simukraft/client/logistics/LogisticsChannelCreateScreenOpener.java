package client.cn.nsimu.simukraft.client.logistics;

import client.cn.nsimu.simukraft.client.toast.ClientInfoToast;
import common.cn.nsimu.simukraft.logistics.LogisticsChannelFilterMode;
import common.cn.nsimu.simukraft.logistics.LogisticsControlBoxService;
import common.cn.nsimu.simukraft.logistics.LogisticsDirection;
import common.cn.nsimu.simukraft.logistics.LogisticsInventoryEntry;
import common.cn.nsimu.simukraft.network.logistics.LogisticsBoxActionPacket;
import common.cn.nsimu.simukraft.network.logistics.LogisticsServerBoxOpenResponsePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.UUID;


@OnlyIn(Dist.CLIENT)
public final class LogisticsChannelCreateScreenOpener {
    private LogisticsChannelCreateScreenOpener() {
    }

    /**
     * open: 打开旧版物流线路创建界面。
     */
    public static void open(LogisticsServerBoxOpenResponsePacket packet, UUID preselectedClientId) {
        open(packet, preselectedClientId, LogisticsDirection.WAREHOUSE_TO_CLIENT);
    }

    /**
     * open: 打开线路创建界面并预选客户端与方向。
     */
    public static void open(LogisticsServerBoxOpenResponsePacket packet, UUID preselectedClientId, LogisticsDirection direction) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.execute(() -> minecraft.setScreen(new LogisticsChannelCreateScreen(packet, preselectedClientId, direction)));
        }
    }

    private static final class LogisticsChannelCreateScreen extends Screen {
        private final LogisticsServerBoxOpenResponsePacket packet;
        private final LogisticsItemFilterGrid itemGrid;
        private UUID selectedClientId;
        private LogisticsDirection direction;
        private LogisticsChannelFilterMode filterMode = LogisticsChannelFilterMode.ITEM;
        private EditBox nameField;
        private EditBox keepSourceField;
        private EditBox keepTargetField;
        private String lastAutoName = "";
        private String keepSourceText = "0";
        private String keepTargetText = "0";
        private int clientScrollOffset = 0;
        private static final int HOTBAR_RESERVE = 58;
        private static final int PANEL_MARGIN = 10;
        private static final int CLIENT_LIST_TOP = 48;
        private static final int CLIENT_ITEM_HEIGHT = 22;
        private static final int RIGHT_COLUMN = 186;

        private LogisticsChannelCreateScreen(LogisticsServerBoxOpenResponsePacket packet, UUID preselectedClientId, LogisticsDirection direction) {
            super(Component.translatable("gui.simukraft.logistics.channel.create_title"));
            this.packet = packet;
            this.selectedClientId = preselectedClientId;
            this.direction = direction != null ? direction : LogisticsDirection.WAREHOUSE_TO_CLIENT;
            this.itemGrid = new LogisticsItemFilterGrid(filterItems());
        }

        /**
         * init: 创建名称输入框、客户端选择和方向按钮。
         */
        @Override
        protected void init() {
            rebuildWidgets("");
        }

        /**
         * renderBackground: 绘制旧版半透明背景。
         */
        @Override
        public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            LogisticsNativeStyle.drawDenseBackdrop(graphics, this.width, this.height);
        }

        /**
         * render: 绘制创建线路表单和物品过滤网格。
         */
        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            renderBackground(graphics, mouseX, mouseY, partialTick);
            Layout layout = layout();
            LogisticsNativeStyle.drawPanel(graphics, layout.x(), layout.y(), layout.width(), layout.height());
            renderClientScrollbar(graphics, layout);
            if (filterMode == LogisticsChannelFilterMode.ITEM) {
                itemGrid.render(graphics, this.font, layout.gridX(), layout.gridY(), mouseX, mouseY);
            } else {
                renderManifestHintPanel(graphics, layout);
            }
            for (var renderable : this.renderables) {
                renderable.render(graphics, mouseX, mouseY, partialTick);
            }
            graphics.drawCenteredString(this.font, this.title, layout.x() + layout.width() / 2, layout.titleY(), LogisticsNativeStyle.TEXT);
            graphics.drawString(this.font, Component.translatable("gui.simukraft.logistics.channel.client"), layout.x() + 12, layout.nameLabelY(), LogisticsNativeStyle.TEXT_WARN);
            graphics.drawString(this.font, Component.translatable("gui.simukraft.logistics.channel.name"), layout.rightX(), layout.nameLabelY(), LogisticsNativeStyle.TEXT_WARN);
            graphics.drawString(this.font, Component.translatable("gui.simukraft.logistics.channel.direction"), layout.rightX(), layout.directionLabelY(), LogisticsNativeStyle.TEXT_WARN);
            graphics.drawString(this.font, Component.translatable("gui.simukraft.logistics.channel.keep_source"), layout.rightX(), layout.keepLabelY(), LogisticsNativeStyle.TEXT_WARN);
            graphics.drawString(this.font, Component.translatable("gui.simukraft.logistics.channel.keep_target"), layout.keepTargetX(), layout.keepLabelY(), LogisticsNativeStyle.TEXT_WARN);
            renderSelectedClientHint(graphics, layout.x() + 12, layout.y() + layout.height() - 18);
            if (filterMode == LogisticsChannelFilterMode.ITEM) {
                graphics.drawString(this.font, Component.translatable(
                        "gui.simukraft.logistics.channel.selected_count", itemGrid.selectedItemIds().size()),
                        layout.actionX(), layout.selectedY(), LogisticsNativeStyle.TEXT, false);
                itemGrid.renderHoverTooltip(graphics, this.font, layout.gridX(), layout.gridY(), mouseX, mouseY);
            } else {
                renderManifestHintText(graphics, layout);
            }
        }

        /**
         * mouseClicked: 先交给按钮，避免确认/返回被物品格抢走点击。
         */
        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (super.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            Layout layout = layout();
            if (filterMode == LogisticsChannelFilterMode.ITEM
                    && itemGrid.mouseClicked(mouseX, mouseY, button, layout.gridX(), layout.gridY())) {
                autoFillNameIfUntouched();
                return true;
            }
            return false;
        }

        /**
         * mouseScrolled: 处理客户端列表和过滤网格滚动。
         */
        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
            Layout layout = layout();
            if (mouseX >= layout.x() && mouseX <= layout.x() + RIGHT_COLUMN - 8
                    && mouseY >= layout.y() + CLIENT_LIST_TOP && mouseY <= layout.y() + layout.height() - 26) {
                int maxOffset = Math.max(0, packet.clients().size() - clientListVisible(layout));
                clientScrollOffset = Math.clamp((int) (clientScrollOffset - verticalAmount), 0, maxOffset);
                rebuildWidgets(nameField != null ? nameField.getValue() : "");
                return true;
            }
            if (filterMode == LogisticsChannelFilterMode.ITEM
                    && itemGrid.mouseScrolled(mouseX, mouseY, verticalAmount, layout.gridX(), layout.gridY())) {
                return true;
            }
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        /**
         * rebuildWidgets: 重建按钮并保留名称输入内容。
         */
        private void rebuildWidgets(String previousName) {
            rememberKeepValues();
            clearWidgets();
            Layout layout = layout();
            boolean autoName = canAutoReplaceName(previousName);
            String name = autoName ? suggestedChannelName() : previousName;
            nameField = new EditBox(this.font, layout.rightX(), layout.nameFieldY(), layout.fieldWidth(), 20, Component.empty());
            nameField.setMaxLength(64);
            nameField.setValue(name);
            if (autoName) {
                lastAutoName = name;
            }
            addRenderableWidget(nameField);
            keepSourceField = keepBox(layout.rightX(), layout.keepFieldY(), keepSourceText, layout.keepFieldWidth());
            keepTargetField = keepBox(layout.keepTargetX(), layout.keepFieldY(), keepTargetText, layout.keepFieldWidth());
            addRenderableWidget(keepSourceField);
            addRenderableWidget(keepTargetField);
            addClientButtons(layout);
            addDirectionButtons(layout);
            addFilterTabButtons(layout);
            addRenderableWidget(LogisticsNativeStyle.button(Component.translatable("gui.simukraft.logistics.channel.confirm"),
                    layout.actionX(), layout.confirmY(), layout.actionWidth(), 20, this::confirm));
            addRenderableWidget(LogisticsNativeStyle.button(Component.translatable("gui.simukraft.logistics.channel.cancel"),
                    layout.actionX(), layout.cancelY(), layout.actionWidth(), 20,
                    () -> LogisticsServerBoxScreenOpener.open(packet)));
        }

        /**
         * addClientButtons: 创建目标客户端选择按钮（支持滚动）。
         */
        private void addClientButtons(Layout layout) {
            List<LogisticsControlBoxService.ClientEntry> clients = packet.clients();
            int maxOffset = Math.max(0, clients.size() - clientListVisible(layout));
            clientScrollOffset = Math.clamp(clientScrollOffset, 0, maxOffset);
            int x = layout.x() + 12;
            int y = layout.y() + CLIENT_LIST_TOP;
            int listBottom = layout.y() + layout.height() - 26;
            for (int i = clientScrollOffset; i < clients.size() && y <= listBottom - CLIENT_ITEM_HEIGHT; i++) {
                LogisticsControlBoxService.ClientEntry client = clients.get(i);
                UUID clientId = client.clientId();
                String prefix = clientId.equals(selectedClientId) ? "> " : "";
                addRenderableWidget(LogisticsNativeStyle.button(Component.literal(prefix + client.name() + " [" + client.portCount() + "]"),
                        x, y, RIGHT_COLUMN - 26, 20, () -> {
                            selectedClientId = clientId;
                            refreshFilterItems();
                            rebuildWidgets(nameField != null ? nameField.getValue() : "");
                        }));
                y += CLIENT_ITEM_HEIGHT;
            }
        }

        /**
         * addDirectionButtons: 创建发送/接收方向按钮。
         */
        private void addDirectionButtons(Layout layout) {
            addRenderableWidget(LogisticsNativeStyle.button(direction == LogisticsDirection.WAREHOUSE_TO_CLIENT
                            ? Component.literal("> ").append(Component.translatable("gui.simukraft.logistics.channel.direction.send"))
                            : Component.translatable("gui.simukraft.logistics.channel.direction.send"),
                    layout.rightX(), layout.directionButtonY(), layout.keepFieldWidth(), 20,
                    () -> setDirection(LogisticsDirection.WAREHOUSE_TO_CLIENT)));
            var receive = addRenderableWidget(LogisticsNativeStyle.button(direction == LogisticsDirection.CLIENT_TO_WAREHOUSE
                            ? Component.literal("> ").append(Component.translatable("gui.simukraft.logistics.channel.direction.receive"))
                            : Component.translatable("gui.simukraft.logistics.channel.direction.receive"),
                    layout.keepTargetX(), layout.directionButtonY(), layout.keepFieldWidth(), 20,
                    () -> setDirection(LogisticsDirection.CLIENT_TO_WAREHOUSE)));
            receive.active = filterMode != LogisticsChannelFilterMode.MANIFEST;
        }

        /**
         * addFilterTabButtons: 切换物品过滤和清单过滤页。
         */
        private void addFilterTabButtons(Layout layout) {
            addRenderableWidget(LogisticsNativeStyle.button(tabLabel(LogisticsChannelFilterMode.ITEM,
                            "gui.simukraft.logistics.channel.items"),
                    layout.rightX(), layout.tabY(), layout.keepFieldWidth(), 20,
                    () -> setFilterMode(LogisticsChannelFilterMode.ITEM)));
            addRenderableWidget(LogisticsNativeStyle.button(tabLabel(LogisticsChannelFilterMode.MANIFEST,
                            "gui.simukraft.logistics.channel.manifest"),
                    layout.keepTargetX(), layout.tabY(), layout.keepFieldWidth(), 20,
                    () -> setFilterMode(LogisticsChannelFilterMode.MANIFEST)));
        }

        /**
         * tabLabel: 当前页签加上选中前缀。
         */
        private Component tabLabel(LogisticsChannelFilterMode mode, String key) {
            Component label = Component.translatable(key);
            return filterMode == mode ? Component.literal("> ").append(label) : label;
        }

        /**
         * setDirection: 切换线路传输方向。
         */
        private void setDirection(LogisticsDirection direction) {
            if (filterMode == LogisticsChannelFilterMode.MANIFEST) {
                this.direction = LogisticsDirection.WAREHOUSE_TO_CLIENT;
            } else {
                this.direction = direction;
            }
            refreshFilterItems();
            rebuildWidgets(nameField != null ? nameField.getValue() : "");
        }

        /**
         * setFilterMode: 切换过滤页签，清单模式强制只发送。
         */
        private void setFilterMode(LogisticsChannelFilterMode mode) {
            this.filterMode = mode != null ? mode : LogisticsChannelFilterMode.ITEM;
            if (this.filterMode == LogisticsChannelFilterMode.MANIFEST) {
                this.direction = LogisticsDirection.WAREHOUSE_TO_CLIENT;
            }
            rebuildWidgets(nameField != null ? nameField.getValue() : "");
        }

        /**
         * renderSelectedClientHint: 绘制当前选择的客户端提示。
         */
        private void renderSelectedClientHint(GuiGraphics graphics, int x, int y) {
            LogisticsControlBoxService.ClientEntry client = selectedClient();
            Component text = client == null
                    ? Component.translatable("gui.simukraft.logistics.channel.need_client")
                    : Component.literal(client.name() + " " + LogisticsNativeStyle.posText(client.boxPos()));
            LogisticsNativeStyle.drawFitString(graphics, this.font, text, x, y, 150, client == null ? LogisticsNativeStyle.TEXT_BAD : LogisticsNativeStyle.TEXT_GOOD);
        }

        /**
         * renderClientScrollbar: 当客户端列表超出可见区时绘制滚动条。
         */
        private void renderClientScrollbar(GuiGraphics graphics, Layout layout) {
            int total = packet.clients().size();
            int visible = clientListVisible(layout);
            if (total <= visible) return;
            int sbX = layout.x() + RIGHT_COLUMN - 10;
            int sbTop = layout.y() + CLIENT_LIST_TOP;
            int sbHeight = layout.y() + layout.height() - 26 - sbTop;
            int thumbH = Math.max(10, sbHeight * visible / total);
            int maxOffset = total - visible;
            int thumbY = sbTop + (sbHeight - thumbH) * clientScrollOffset / maxOffset;
            graphics.fill(sbX, sbTop, sbX + 3, sbTop + sbHeight, 0xFF444444);
            graphics.fill(sbX, thumbY, sbX + 3, thumbY + thumbH, 0xFFAAAAAA);
        }

        /**
         * confirm: 校验客户端和过滤物品后发送创建频道请求。
         */
        private void confirm() {
            if (selectedClientId == null) {
                ClientInfoToast.show(Component.translatable("toast.simukraft.title"), Component.translatable("gui.simukraft.logistics.channel.need_client"), "warning");
                return;
            }
            List<String> filters = filterMode == LogisticsChannelFilterMode.MANIFEST ? List.of() : itemGrid.selectedItemIds();
            if (filterMode == LogisticsChannelFilterMode.ITEM && filters.isEmpty()) {
                ClientInfoToast.show(Component.translatable("toast.simukraft.title"), Component.translatable("gui.simukraft.logistics.channel.need_items"), "warning");
                return;
            }
            String name = nameField != null ? nameField.getValue() : "";
            if (canAutoReplaceName(name)) {
                name = suggestedChannelName();
            }
            rememberKeepValues();
            PacketDistributor.sendToServer(new LogisticsBoxActionPacket(packet.boxPos(),
                    LogisticsBoxActionPacket.Action.ADD_CHANNEL,
                    selectedClientId,
                    null,
                    BlockPos.ZERO,
                    name,
                    direction,
                    BlockPos.ZERO,
                    BlockPos.ZERO,
                    filters,
                    filterMode,
                    parseKeep(keepSourceText),
                    parseKeep(keepTargetText)));
            Minecraft.getInstance().setScreen(null);
        }

        /**
         * selectedClient: 返回当前选中的客户端数据。
         */
        private LogisticsControlBoxService.ClientEntry selectedClient() {
            if (selectedClientId == null) {
                return null;
            }
            for (LogisticsControlBoxService.ClientEntry client : packet.clients()) {
                if (selectedClientId.equals(client.clientId())) return client;
            }
            return null;
        }

        /**
         * refreshFilterItems: 按当前方向和客户端刷新过滤物品来源。
         */
        private void refreshFilterItems() {
            itemGrid.setItems(filterItems());
        }

        /**
         * filterItems: 发送时显示仓库库存，接收时显示选中客户端端口库存。
         */
        private List<LogisticsInventoryEntry> filterItems() {
            if (direction == LogisticsDirection.CLIENT_TO_WAREHOUSE) {
                return selectedClientInventory();
            }
            return packet.inventory();
        }

        /**
         * autoFillNameIfUntouched: 物品选择变化时自动填入客户端翻译名。
         */
        private void autoFillNameIfUntouched() {
            if (nameField == null || !canAutoReplaceName(nameField.getValue())) {
                return;
            }
            lastAutoName = suggestedChannelName();
            nameField.setValue(lastAutoName);
        }

        /**
         * suggestedChannelName: 根据已选过滤物品生成默认线路名。
         */
        private String suggestedChannelName() {
            if (filterMode == LogisticsChannelFilterMode.MANIFEST) {
                return Component.translatable("gui.simukraft.logistics.channel.manifest_name").getString();
            }
            List<String> filters = itemGrid.selectedItemIds();
            if (filters.isEmpty()) {
                return Component.translatable("gui.simukraft.logistics.channel.default_name").getString();
            }
            return LogisticsItemDisplayName.filterText(filters);
        }

        /**
         * canAutoReplaceName: 判断当前名称是否仍属于自动生成内容。
         */
        private boolean canAutoReplaceName(String name) {
            String defaultName = Component.translatable("gui.simukraft.logistics.channel.default_name").getString();
            String manifestName = Component.translatable("gui.simukraft.logistics.channel.manifest_name").getString();
            return name == null || name.isBlank() || name.equals(defaultName) || name.equals(manifestName)
                    || name.equals("物流线路") || name.equals("Logistics Route") || name.equals(lastAutoName);
        }

        /**
         * selectedClientInventory: 查找当前选中客户端的端口库存快照。
         */
        private List<LogisticsInventoryEntry> selectedClientInventory() {
            if (selectedClientId == null) {
                return List.of();
            }
            for (var entry : packet.clientInventories()) {
                if (selectedClientId.equals(entry.clientId())) return entry.inventory();
            }
            return List.of();
        }

        /**
         * layout: 计算表单位置和尺寸。
         */
        /**
         * renderManifestHintPanel: 清单说明底，叠在背景后、文字前。
         */
        private void renderManifestHintPanel(GuiGraphics graphics, Layout layout) {
            int x = layout.gridX();
            int y = layout.gridY();
            graphics.fill(x - 2, y - 2, x + itemGrid.width(), y + itemGrid.height() + 2, 0xFF10101C);
        }

        /**
         * renderManifestHintText: 清单说明文字，叠在按钮之后。
         */
        private void renderManifestHintText(GuiGraphics graphics, Layout layout) {
            int x = layout.gridX();
            int y = layout.gridY();
            graphics.drawString(this.font, Component.translatable("gui.simukraft.logistics.channel.manifest_hint"),
                    x, y + 6, LogisticsNativeStyle.TEXT, false);
            graphics.drawString(this.font, Component.translatable("gui.simukraft.logistics.channel.manifest_hint_build"),
                    x, y + 20, LogisticsNativeStyle.TEXT_DIM, false);
            graphics.drawString(this.font, Component.translatable("gui.simukraft.logistics.channel.manifest_hint_build_2"),
                    x, y + 32, LogisticsNativeStyle.TEXT_DIM, false);
        }

        /**
         * keepBox: 创建保有量数字输入框。
         */
        private EditBox keepBox(int x, int y, String value, int width) {
            EditBox box = new EditBox(this.font, x, y, width, 20, Component.empty());
            box.setMaxLength(7);
            box.setFilter(text -> text != null && text.matches("\\d*"));
            box.setValue(value == null || value.isBlank() ? "0" : value);
            return box;
        }

        /**
         * rememberKeepValues: 重建控件前记下保有量输入。
         */
        private void rememberKeepValues() {
            if (keepSourceField != null) {
                keepSourceText = keepSourceField.getValue();
            }
            if (keepTargetField != null) {
                keepTargetText = keepTargetField.getValue();
            }
        }

        /**
         * parseKeep: 把保有量文本解析成非负整数。
         */
        private static int parseKeep(String value) {
            if (value == null || value.isBlank()) {
                return 0;
            }
            try {
                return Math.max(0, Integer.parseInt(value.trim()));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }

        /**
         * clientListVisible: 当前高度下能显示的客户端行数。
         */
        private static int clientListVisible(Layout layout) {
            int listHeight = layout.y() + layout.height() - 26 - (layout.y() + CLIENT_LIST_TOP);
            return Math.max(1, listHeight / CLIENT_ITEM_HEIGHT);
        }

        /**
         * layout: 右侧一列按 标签→控件 成对排列，全部使用屏幕绝对坐标。
         */
        private Layout layout() {
            int x = PANEL_MARGIN;
            int y = PANEL_MARGIN;
            int width = Math.max(420, this.width - PANEL_MARGIN * 2);
            int rightX = x + RIGHT_COLUMN;
            int fieldWidth = Math.max(196, width - RIGHT_COLUMN - 16);
            int keepFieldWidth = (fieldWidth - 8) / 2;
            int keepTargetX = rightX + keepFieldWidth + 8;
            int titleY = y + 8;
            int nameLabelY = y + 28;
            int nameFieldY = y + 42;
            int directionLabelY = nameFieldY + 26;
            int directionButtonY = directionLabelY + 14;
            int keepLabelY = directionButtonY + 26;
            int keepFieldY = keepLabelY + 14;
            int tabY = keepFieldY + 26;
            int gridY = tabY + 26;
            int actionX = rightX + itemGrid.width() + 8;
            int actionWidth = 78;
            int panelRight = x + width - 12;
            if (actionX + actionWidth > panelRight) {
                actionWidth = Math.max(48, panelRight - actionX);
            }
            int confirmY = gridY;
            int cancelY = gridY + 24;
            int selectedY = cancelY + 24;
            int contentBottom = Math.max(gridY + itemGrid.height(), selectedY + 12);
            int height = Math.max(contentBottom + 12 - y, this.height - y - HOTBAR_RESERVE);
            return new Layout(x, y, width, height, rightX, fieldWidth, keepFieldWidth, keepTargetX,
                    titleY, nameLabelY, nameFieldY, directionLabelY, directionButtonY, keepLabelY, keepFieldY, tabY, gridY,
                    actionX, actionWidth, confirmY, cancelY, selectedY);
        }

        private record Layout(int x, int y, int width, int height, int rightX, int fieldWidth, int keepFieldWidth,
                              int keepTargetX, int titleY, int nameLabelY, int nameFieldY, int directionLabelY,
                              int directionButtonY, int keepLabelY, int keepFieldY, int tabY, int gridY,
                              int actionX, int actionWidth, int confirmY, int cancelY, int selectedY) {
            int gridX() {
                return rightX;
            }
        }
    }
}
