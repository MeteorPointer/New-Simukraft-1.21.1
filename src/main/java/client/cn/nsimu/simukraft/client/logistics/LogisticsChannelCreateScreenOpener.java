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
        private static final int HOTBAR_RESERVE = 52;
        private static final int CLIENT_LIST_TOP = 48;
        private static final int CLIENT_ITEM_HEIGHT = 20;

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
            LogisticsNativeStyle.drawBackdrop(graphics, this.width, this.height);
        }

        /**
         * render: 绘制创建线路表单和物品过滤网格。
         */
        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            renderBackground(graphics, mouseX, mouseY, partialTick);
            Layout layout = layout();
            LogisticsNativeStyle.drawPanel(graphics, layout.x(), layout.y(), layout.width(), layout.height());
            graphics.drawCenteredString(this.font, this.title, layout.x() + layout.width() / 2, layout.y() + 8, LogisticsNativeStyle.TEXT);
            graphics.drawString(this.font, Component.translatable("gui.simukraft.logistics.channel.client"), layout.x() + 10, layout.y() + 30, LogisticsNativeStyle.TEXT_WARN);
            graphics.drawString(this.font, Component.translatable("gui.simukraft.logistics.channel.name"), layout.x() + 176, layout.y() + 30, LogisticsNativeStyle.TEXT_WARN);
            graphics.drawString(this.font, Component.translatable("gui.simukraft.logistics.channel.direction"), layout.x() + 176, layout.y() + 68, LogisticsNativeStyle.TEXT_WARN);
            graphics.drawString(this.font, Component.translatable("gui.simukraft.logistics.channel.keep_source"), layout.x() + 176, layout.y() + 108, LogisticsNativeStyle.TEXT_WARN);
            graphics.drawString(this.font, Component.translatable("gui.simukraft.logistics.channel.keep_target"), layout.x() + 272, layout.y() + 108, LogisticsNativeStyle.TEXT_WARN);
            renderSelectedClientHint(graphics, layout.x() + 10, layout.y() + layout.height() - 18);
            renderClientScrollbar(graphics, layout);
            if (filterMode == LogisticsChannelFilterMode.ITEM) {
                itemGrid.render(graphics, this.font, layout.gridX(), layout.gridY(), mouseX, mouseY);
            } else {
                renderManifestHint(graphics, layout);
            }
            super.render(graphics, mouseX, mouseY, partialTick);
        }

        /**
         * mouseClicked: 优先处理过滤网格点击。
         */
        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            Layout layout = layout();
            if (filterMode == LogisticsChannelFilterMode.ITEM
                    && itemGrid.mouseClicked(mouseX, mouseY, button, layout.gridX(), layout.gridY())) {
                autoFillNameIfUntouched();
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        /**
         * mouseScrolled: 处理客户端列表和过滤网格滚动。
         */
        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
            Layout layout = layout();
            if (mouseX >= layout.x() && mouseX <= layout.x() + 166
                    && mouseY >= layout.y() + CLIENT_LIST_TOP && mouseY <= layout.y() + clientListBottom(layout)) {
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
            nameField = new EditBox(this.font, layout.x() + 176, layout.y() + 44, 190, 18, Component.empty());
            nameField.setMaxLength(64);
            nameField.setValue(name);
            if (autoName) {
                lastAutoName = name;
            }
            addRenderableWidget(nameField);
            keepSourceField = keepBox(layout.x() + 176, layout.y() + 120, keepSourceText);
            keepTargetField = keepBox(layout.x() + 272, layout.y() + 120, keepTargetText);
            addRenderableWidget(keepSourceField);
            addRenderableWidget(keepTargetField);
            addClientButtons(layout);
            addDirectionButtons(layout);
            addFilterTabButtons(layout);
            int buttonY = layout.y() + layout.height() - 24;
            addRenderableWidget(LogisticsNativeStyle.button(Component.translatable("gui.simukraft.logistics.channel.confirm"),
                    layout.x() + layout.width() - 174, buttonY, 78, 20, this::confirm));
            addRenderableWidget(LogisticsNativeStyle.button(Component.translatable("gui.simukraft.logistics.channel.cancel"),
                    layout.x() + layout.width() - 90, buttonY, 78, 20,
                    () -> LogisticsServerBoxScreenOpener.open(packet)));
        }

        /**
         * addClientButtons: 创建目标客户端选择按钮（支持滚动）。
         */
        private void addClientButtons(Layout layout) {
            List<LogisticsControlBoxService.ClientEntry> clients = packet.clients();
            int maxOffset = Math.max(0, clients.size() - clientListVisible(layout));
            clientScrollOffset = Math.clamp(clientScrollOffset, 0, maxOffset);
            int x = layout.x() + 10;
            int y = layout.y() + CLIENT_LIST_TOP;
            int listBottom = layout.y() + clientListBottom(layout);
            for (int i = clientScrollOffset; i < clients.size() && y <= listBottom - CLIENT_ITEM_HEIGHT; i++) {
                LogisticsControlBoxService.ClientEntry client = clients.get(i);
                UUID clientId = client.clientId();
                String prefix = clientId.equals(selectedClientId) ? "> " : "";
                addRenderableWidget(LogisticsNativeStyle.button(Component.literal(prefix + client.name() + " [" + client.portCount() + "]"),
                        x, y, 150, 20, () -> {
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
                    layout.x() + 176, layout.y() + 82, 88, 18, () -> setDirection(LogisticsDirection.WAREHOUSE_TO_CLIENT)));
            var receive = addRenderableWidget(LogisticsNativeStyle.button(direction == LogisticsDirection.CLIENT_TO_WAREHOUSE
                            ? Component.literal("> ").append(Component.translatable("gui.simukraft.logistics.channel.direction.receive"))
                            : Component.translatable("gui.simukraft.logistics.channel.direction.receive"),
                    layout.x() + 270, layout.y() + 82, 88, 18, () -> setDirection(LogisticsDirection.CLIENT_TO_WAREHOUSE)));
            receive.active = filterMode != LogisticsChannelFilterMode.MANIFEST;
        }

        /**
         * addFilterTabButtons: 切换物品过滤和清单过滤页。
         */
        private void addFilterTabButtons(Layout layout) {
            addRenderableWidget(LogisticsNativeStyle.button(tabLabel(LogisticsChannelFilterMode.ITEM,
                            "gui.simukraft.logistics.channel.items"),
                    layout.x() + 176, layout.y() + 132, 88, 16,
                    () -> setFilterMode(LogisticsChannelFilterMode.ITEM)));
            addRenderableWidget(LogisticsNativeStyle.button(tabLabel(LogisticsChannelFilterMode.MANIFEST,
                            "gui.simukraft.logistics.channel.manifest"),
                    layout.x() + 270, layout.y() + 132, 88, 16,
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
            int sbX = layout.x() + 163;
            int sbTop = layout.y() + CLIENT_LIST_TOP;
            int sbHeight = clientListBottom(layout) - CLIENT_LIST_TOP;
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
         * renderManifestHint: 清单过滤页说明，不占用物品格。
         */
        private void renderManifestHint(GuiGraphics graphics, Layout layout) {
            int x = layout.gridX();
            int y = layout.gridY();
            graphics.fill(x - 2, y - 2, x + 168, y + 72, 0xFF10101C);
            LogisticsNativeStyle.drawFitString(graphics, this.font,
                    Component.translatable("gui.simukraft.logistics.channel.manifest_hint"),
                    x, y + 6, 164, LogisticsNativeStyle.TEXT);
            LogisticsNativeStyle.drawFitString(graphics, this.font,
                    Component.translatable("gui.simukraft.logistics.channel.manifest_hint_build"),
                    x, y + 28, 164, LogisticsNativeStyle.TEXT_DIM);
        }

        /**
         * keepBox: 创建保有量数字输入框。
         */
        private EditBox keepBox(int x, int y, String value) {
            EditBox box = new EditBox(this.font, x, y, 86, 16, Component.empty());
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
         * clientListBottom: 客户端列表相对面板顶部的底边。
         */
        private static int clientListBottom(Layout layout) {
            return layout.height() - 48;
        }

        /**
         * clientListVisible: 当前高度下能显示的客户端行数。
         */
        private static int clientListVisible(Layout layout) {
            return Math.max(1, (clientListBottom(layout) - CLIENT_LIST_TOP) / CLIENT_ITEM_HEIGHT);
        }

        /**
         * layout: 面板上移，给确认按钮和物品栏留出空隙。
         */
        private Layout layout() {
            int width = Math.min(410, this.width - 20);
            int height = Math.min(252, Math.max(200, this.height - HOTBAR_RESERVE - 8));
            int x = (this.width - width) / 2;
            int y = 8;
            return new Layout(x, y, width, height, x + 176, y + 150);
        }

        private record Layout(int x, int y, int width, int height, int gridX, int gridY) {
        }
    }
}
