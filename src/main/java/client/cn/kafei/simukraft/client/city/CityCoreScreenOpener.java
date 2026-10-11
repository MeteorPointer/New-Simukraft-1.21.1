package client.cn.kafei.simukraft.client.city;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import client.cn.kafei.simukraft.client.ui.SimuKraftUiTheme;
import client.cn.kafei.simukraft.client.ui.SimuKraftFlexLayout;
import client.cn.kafei.simukraft.client.ui.SimuKraftWindowFrame;
import client.cn.kafei.simukraft.client.city.map.SimuBlockColors;
import client.cn.kafei.simukraft.client.city.map.SimuMap3DCamera;
import client.cn.kafei.simukraft.client.city.map.SimuMap3DMesh;
import client.cn.kafei.simukraft.client.city.map.SimuMapManager;
import client.cn.kafei.simukraft.client.citizen.CitizenAvatarFactory;
import client.cn.kafei.simukraft.client.citizen.CitizenFamilyGraphCanvas;
import client.cn.kafei.simukraft.client.city.map.SimuMapRegion;
import client.cn.kafei.simukraft.client.city.map.SimuMapRegionData;
import common.cn.kafei.simukraft.city.CityPermissionLevel;
import common.cn.kafei.simukraft.city.FinanceTransactionData;
import common.cn.kafei.simukraft.network.city.chunk.CityChunkBatchPurchasePacket;
import common.cn.kafei.simukraft.network.city.chunk.CityChunkBatchReleasePacket;
import common.cn.kafei.simukraft.network.city.chunk.CityChunkPurchasePacket;
import common.cn.kafei.simukraft.network.city.core.CityCoreCreateCityPacket;
import common.cn.kafei.simukraft.network.city.core.CityCoreManageCityPacket;
import common.cn.kafei.simukraft.network.city.core.CityCoreOpenResponsePacket;
import common.cn.kafei.simukraft.network.city.map.CityCoreMapRequestPacket;
import common.cn.kafei.simukraft.network.city.map.CityCoreMapResponsePacket;
import common.cn.kafei.simukraft.network.citizen.manage.CityCitizenFamilyGraphRequestPacket;
import common.cn.kafei.simukraft.network.citizen.manage.CityCitizenFamilyGraphResponsePacket;
import common.cn.kafei.simukraft.network.citizen.manage.CityCitizenManageActionPacket;
import common.cn.kafei.simukraft.network.citizen.manage.CityCitizenManageRequestPacket;
import common.cn.kafei.simukraft.network.citizen.manage.CityCitizenManageResponsePacket;
import common.cn.kafei.simukraft.network.city.member.CityCoreMemberActionPacket;
import common.cn.kafei.simukraft.network.city.member.CityCoreMembersRequestPacket;
import common.cn.kafei.simukraft.network.city.member.CityCoreMembersResponsePacket;

import com.lowdragmc.lowdraglib2.editor.ui.View;
import com.lowdragmc.lowdraglib2.editor.ui.ViewContainer;
import com.lowdragmc.lowdraglib2.gui.ColorPattern;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.Icons;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import common.cn.kafei.simukraft.ui.RecipeBookSearchUi;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Tab;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;


@OnlyIn(Dist.CLIENT)
public final class CityCoreScreenOpener {
    private static final int BUTTON_WIDTH = 120;
    private static final int COLLAPSED_SIDEBAR_WIDTH = 18;
    private static final int BUTTON_HEIGHT = 24;
    private static final int BACK_BUTTON_WIDTH = 52;
    private static final int BACK_BUTTON_HEIGHT = 20;
    private static final AtomicLong WINDOW_SEQUENCE = new AtomicLong();
    private static volatile CityChunkMapElement activeMapElement;
    private static volatile CityCoreWindow activeWindow;
    private static volatile com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen activeScreen;
    private static volatile CityCoreOpenResponsePacket lastSummaryPacket;
    private static volatile UpgradeRefreshExpectation pendingUpgradeRefresh;

    private CityCoreScreenOpener() {
    }

    public static void open(CityCoreOpenResponsePacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            UpgradeRefreshExpectation expectation = takeUpgradeRefresh(packet);
            if (expectation != null) {
                CityCoreWindow window = activeWindow;
                if (!isActiveScreen(minecraft, window)
                        || window.instanceId != expectation.sourceWindowId()
                        || !window.matches(packet)) {
                    return;
                }
            }
            String restoreTab = null;
            if (expectation == null) {
                CityCoreWindow current = activeWindow;
                if (isActiveScreen(minecraft, current) && current.matches(packet)) {
                    restoreTab = restorableTab(current.currentTabId, packet);
                }
            }
            rememberSummary(packet);
            show(minecraft, createUi(packet, expectation != null ? "upgrade" : restoreTab));
        });
    }

    /** expectUpgradeRefresh: 标记一次升级后的定向刷新，避免迟到响应重新弹出已关闭窗口。 */
    static void expectUpgradeRefresh(CityCoreOpenResponsePacket packet) {
        CityCoreWindow window = activeWindow;
        if (packet != null && packet.hasCity() && window != null && window.matches(packet)) {
            pendingUpgradeRefresh = new UpgradeRefreshExpectation(packet.pos(), packet.cityId(), window.instanceId);
        }
    }

    /** 分区列表和编辑页刷新后留在原标签，地图操作仍回到地图。 */
    private static String restorableTab(String tab, CityCoreOpenResponsePacket packet) {
        if (packet == null || !packet.hasCity() || tab == null || "info".equals(tab)) {
            return null;
        }
        if ("districts".equals(tab) && !packet.districtContext() && packet.permissionLevel() == CityPermissionLevel.MAYOR) {
            return "districts";
        }
        if (("edit".equals(tab) || "finance".equals(tab) || "upgrade".equals(tab)) && packet.canManageCity()) {
            return tab;
        }
        return null;
    }

    /** takeUpgradeRefresh: 消费匹配的升级响应；不按时间降级为普通开窗响应。 */
    private static UpgradeRefreshExpectation takeUpgradeRefresh(CityCoreOpenResponsePacket packet) {
        UpgradeRefreshExpectation expectation = pendingUpgradeRefresh;
        if (expectation != null && expectation.matches(packet)) {
            pendingUpgradeRefresh = null;
            return expectation;
        }
        return null;
    }

    public static void openMembers(CityCoreMembersResponsePacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            CityCoreWindow window = activeWindow;
            if (!isActiveScreen(minecraft, window) || !window.matches(packet.pos(), packet.cityId())) {
                return;
            }
            show(minecraft, createUi(packet));
        });
    }

    public static void openMap(CityCoreMapResponsePacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        Set<Long> chunks = packet.chunks().stream()
                .map(chunk -> ChunkPos.asLong(chunk.chunkX(), chunk.chunkZ()))
                .collect(Collectors.toUnmodifiableSet());
        minecraft.execute(() -> {
            CityCoreWindow window = activeWindow;
            if (!isActiveScreen(minecraft, window) || !window.matches(packet.pos(), packet.cityId())) {
                return;
            }
            ClientCityChunkCache.getInstance().updateCurrentCity(packet.cityId(), chunks, packet.pos(), packet.cityName());
            CityChunkMapElement mapElement = activeMapElement;
            if (mapElement != null && mapElement.matches(packet.pos())) {
                mapElement.updatePacket(packet);
                return;
            }
            show(minecraft, createUi(packet));
        });
    }

    /** isActiveScreen: 验证异步响应仍属于当前城市核心窗口。 */
    private static boolean isActiveScreen(Minecraft minecraft, CityCoreWindow window) {
        return window != null
                && activeWindow == window
                && minecraft.screen == activeScreen;
    }

    /** show: 记录并切换城市核心 Screen，供迟到响应进行精确身份校验。 */
    private static void show(Minecraft minecraft, ModularUI ui) {
        com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen screen =
                new com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen(ui, Component.empty());
        activeScreen = screen;
        minecraft.setScreen(screen);
    }

    private static ModularUI createUi(CityCoreMapResponsePacket packet) {
        CityCoreWindow window = new CityCoreWindow(packet);
        UIElement root = createWindowRoot(window);
        return new ModularUI(SimuKraftUiTheme.createUi(root))
                .shouldCloseOnEsc(true)
                .shouldCloseOnKeyInventory(false);
    }

    private static ModularUI createUi(CityCoreMembersResponsePacket packet) {
        CityCoreWindow window = new CityCoreWindow(packet);
        UIElement root = createWindowRoot(window);
        return new ModularUI(SimuKraftUiTheme.createUi(root))
                .shouldCloseOnEsc(true)
                .shouldCloseOnKeyInventory(false);
    }

    private static ModularUI createUi(CityCoreOpenResponsePacket packet, String initialTabId) {
        CityCoreWindow window = new CityCoreWindow(packet, initialTabId);
        UIElement root = createWindowRoot(window);
        return new ModularUI(SimuKraftUiTheme.createUi(root))
                .shouldCloseOnEsc(true)
                .shouldCloseOnKeyInventory(false);
    }

    /** rememberSummary：缓存完整城市核心包，供地图/成员页重建窗口时保留人口和住房容量。 */
    private static void rememberSummary(CityCoreOpenResponsePacket packet) {
        if (packet != null && packet.hasCity()) {
            lastSummaryPacket = packet;
        } else if (packet != null) {
            lastSummaryPacket = null;
        }
    }

    /** cachedSummary：按城市和核心坐标读取最近一次完整统计包。 */
    private static CityCoreOpenResponsePacket cachedSummary(UUID cityId, BlockPos pos) {
        CityCoreWindow window = activeWindow;
        if (window != null && sameSummary(window.packet, cityId, pos)) {
            return window.packet;
        }
        CityCoreOpenResponsePacket cached = lastSummaryPacket;
        return sameSummary(cached, cityId, pos) ? cached : null;
    }

    private static boolean sameSummary(CityCoreOpenResponsePacket packet, UUID cityId, BlockPos pos) {
        return packet != null && packet.hasCity() && packet.cityId().equals(cityId) && packet.pos().equals(pos);
    }

    private static UIElement createWindowRoot(CityCoreWindow window) {
        SimuKraftFlexLayout.ScreenSize screenSize = SimuKraftFlexLayout.screenSize();
        UIElement root = SimuKraftWindowFrame.create(
                screenSize,
                Component.translatable("screen.simukraft.city_core.title"),
                workspace(window),
                CityCoreScreenOpener::close);
        root.addEventListener(UIEvents.REMOVED, event -> {
            if (activeWindow == window) {
                activeWindow = null;
                activeScreen = null;
            }
        });
        return root;
    }

    private static UIElement workspace(CityCoreWindow window) {
        UIElement body = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.heightPercent(100);
            layout.paddingAll(8);
            layout.flexDirection(FlexDirection.ROW);
            layout.gapAll(8);
            layout.alignItems(AlignItems.STRETCH);
        });
        body.addChild(window.sidebarContainer);
        body.addChild(window.rightTabs);
        return body;
    }

    private static ScrollerView scrollable(UIElement child) {
        ScrollerView scroller = new ScrollerView();
        scroller.scrollerStyle(style -> style.mode(ScrollerMode.VERTICAL));
        scroller.layout(layout -> {
            layout.widthPercent(100);
            layout.heightPercent(100);
            layout.flex(1);
        });
        scroller.addScrollViewChild(child);
        return scroller;
    }

    private static UIElement menuColumn(CityCoreOpenResponsePacket packet, CityCoreWindow window) {
        UIElement menu = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.gapAll(5);
            layout.alignItems(AlignItems.STRETCH);
        });
        if (!packet.hasCity()) {
            menu.addChild(menuButton("screen.simukraft.city_core.create", () -> window.openTab("create", "screen.simukraft.city_core.create", createPanel(packet))));
        } else if (packet.canManageCity()) {
            String editMenuKey = packet.districtContext()
                    ? "screen.simukraft.city_core.menu.edit_district"
                    : "screen.simukraft.city_core.menu.edit";
            menu.addChild(menuButton("screen.simukraft.city_core.menu.info", () -> window.openTab("info", "screen.simukraft.city_core.menu.info", scrollable(contentPanel(packet)))));
            menu.addChild(menuButton("screen.simukraft.city_core.map_title", () -> requestMap(packet)));
            if (!packet.districtContext() && packet.permissionLevel() == CityPermissionLevel.MAYOR) {
                menu.addChild(menuButton("screen.simukraft.city_core.menu.districts", () -> window.openOrReplaceTab("districts", "screen.simukraft.city_core.districts.title", districtsPanel(districtManagementPacket(packet)))));
            }
            if (!packet.districtContext() || packet.permissionLevel() == CityPermissionLevel.MAYOR) {
                menu.addChild(menuButton(editMenuKey, () -> window.openTab("edit", editMenuKey, editPanel(packet))));
            }
            menu.addChild(menuButton("screen.simukraft.city_core.menu.upgrade", () -> window.openTab("upgrade", "screen.simukraft.city_core.menu.upgrade", upgradePanel(packet))));
            menu.addChild(menuButton("screen.simukraft.city_core.menu.citizens", () -> PacketDistributor.sendToServer(new CityCitizenManageRequestPacket(packet.pos()))));
            menu.addChild(menuButton("screen.simukraft.city_core.menu.officials", () -> requestMembers(packet)));
            menu.addChild(menuButton("screen.simukraft.city_core.menu.finance", () -> window.openTab("finance", "screen.simukraft.city_core.menu.finance", financePanel(packet))));
        } else {
            menu.addChild(menuButton("screen.simukraft.city_core.menu.info", () -> window.openTab("info", "screen.simukraft.city_core.menu.info", scrollable(contentPanel(packet)))));
            menu.addChild(menuButton("screen.simukraft.city_core.map_title", () -> requestMap(packet)));
        }
        menu.addChild(menuSpacer());
        menu.addChild(closeButton(CityCoreScreenOpener::close));
        return menu;
    }

    private static UIElement createPanel(CityCoreOpenResponsePacket packet) {
        UIElement panel = basePanel();
        addCreateContent(panel, packet);
        return scrollable(panel);
    }

    private static UIElement editPanel(CityCoreOpenResponsePacket packet) {
        UIElement panel = basePanel();
        String prefix = packet.districtContext()
                ? "screen.simukraft.city_core.edit.district."
                : "screen.simukraft.city_core.edit.";
        String displayName = packet.districtContext() ? packet.districtName() : packet.cityName();
        panel.addChild(line(Component.translatable(prefix + "rename_title")));
        TextField renameField = textField(displayName, 200);
        panel.addChild(renameField);
        panel.addChild(contentButton(prefix + "rename", () -> PacketDistributor.sendToServer(new CityCoreManageCityPacket(packet.pos(), CityCoreManageCityPacket.Action.RENAME, renameField.getValue()))));
        panel.addChild(contentSpacer());
        panel.addChild(line(Component.translatable(prefix + "delete_title")));
        panel.addChild(line(Component.translatable(prefix + "delete_tip", displayName)));
        TextField deleteField = textField("", 200);
        deleteField.getTextFieldStyle().placeholder(Component.translatable(prefix + "delete_placeholder"));
        panel.addChild(deleteField);
        panel.addChild(contentButton(prefix + "delete", () -> PacketDistributor.sendToServer(new CityCoreManageCityPacket(packet.pos(), CityCoreManageCityPacket.Action.DELETE, deleteField.getValue()))));
        return scrollable(panel);
    }

    private static UIElement upgradePanel(CityCoreOpenResponsePacket packet) {
        return CityUpgradePanelFactory.create(packet);
    }

    private static UIElement financePanel(CityCoreOpenResponsePacket packet) {
        UIElement panel = basePanel();
        panel.addChild(line(Component.translatable("screen.simukraft.city_core.finance.title")));
        panel.addChild(line(Component.translatable("screen.simukraft.city_core.funds", String.format(Locale.ROOT, "%.2f", packet.funds()))));
        panel.addChild(contentSpacer());
        panel.addChild(line(Component.translatable("screen.simukraft.city_core.finance.recent")));
        if (packet.financeEntries().isEmpty()) {
            panel.addChild(line(Component.translatable("screen.simukraft.city_core.finance.empty")));
        } else {
            for (CityCoreOpenResponsePacket.FinanceEntry entry : packet.financeEntries()) {
                panel.addChild(line(Component.translatable(
                        "screen.simukraft.city_core.finance.row",
                        financeTypeText(entry),
                        financeAmountText(entry),
                        String.format(Locale.ROOT, "%.2f", entry.balanceAfter()),
                        financeReasonText(entry),
                        entry.actorName().isBlank() ? Component.translatable("screen.simukraft.city_core.finance.system").getString() : entry.actorName()
                )));
            }
        }
        return scrollable(panel);
    }

    private static UIElement districtsPanel(CityCoreOpenResponsePacket packet) {
        UIElement panel = basePanel();
        panel.addChild(line(Component.translatable("screen.simukraft.city_core.districts.title")));
        if (packet.districts().isEmpty()) {
            panel.addChild(line(Component.translatable("screen.simukraft.city_core.districts.empty")));
            return scrollable(panel);
        }
        for (CityCoreOpenResponsePacket.DistrictSummary district : packet.districts()) {
            UIElement row = new UIElement().layout(layout -> {
                layout.widthPercent(100);
                layout.flexDirection(FlexDirection.COLUMN);
                layout.gapAll(3);
            });
            row.addChild(line(Component.translatable("screen.simukraft.city_core.districts.row", district.name(), district.chunkCount(), district.coreCount(), district.mayorName().isBlank() ? "-" : district.mayorName())));
            if (district.members().isEmpty()) {
                row.addChild(line(Component.translatable("screen.simukraft.city_core.districts.no_members")));
            } else {
                for (CityCoreOpenResponsePacket.DistrictMemberView member : district.members()) {
                    row.addChild(line(Component.translatable("screen.simukraft.city_core.districts.member_row", member.playerName(), districtRoleText(member.rolePower()))));
                }
            }
            TextField rename = textField(districtBaseName(district.name()), 200);
            rename.getTextFieldStyle().placeholder(Component.translatable("screen.simukraft.city_core.districts.name_placeholder"));
            row.addChild(rename);
            row.addChild(contentButton("screen.simukraft.city_core.districts.rename", () -> PacketDistributor.sendToServer(new common.cn.kafei.simukraft.network.city.DistrictActionPacket(
                    common.cn.kafei.simukraft.network.city.DistrictActionPacket.Action.RENAME, packet.pos(), district.districtId(), rename.getValue(), List.of()))));
            row.addChild(line(Component.translatable("screen.simukraft.city_core.edit.district.delete_tip", district.name())));
            TextField deleteConfirm = textField("", 200);
            deleteConfirm.getTextFieldStyle().placeholder(Component.translatable("screen.simukraft.city_core.edit.district.delete_placeholder"));
            row.addChild(deleteConfirm);
            row.addChild(contentButton("screen.simukraft.city_core.districts.delete", () -> PacketDistributor.sendToServer(new common.cn.kafei.simukraft.network.city.DistrictActionPacket(
                    common.cn.kafei.simukraft.network.city.DistrictActionPacket.Action.DELETE, packet.pos(), district.districtId(), deleteConfirm.getValue(), List.of()))));
            row.addChild(line(Component.translatable("screen.simukraft.city_core.districts.assign")));
            for (CityCoreOpenResponsePacket.CityMemberRef player : districtCandidates(packet, district)) {
                boolean official = district.members().stream().anyMatch(member -> member.playerId().equals(player.playerId()) && member.rolePower() == 1);
                String playerName = player.playerName().isBlank() ? player.playerId().toString() : player.playerName();
                UIElement actions = new UIElement().layout(layout -> {
                    layout.widthPercent(100);
                    layout.flexDirection(FlexDirection.ROW);
                    layout.alignItems(AlignItems.CENTER);
                    layout.gapAll(4);
                });
                Label name = line(Component.literal(playerName));
                name.layout(layout -> layout.flex(1).height(13));
                actions.addChild(name);
                actions.addChild(memberActionButton("screen.simukraft.city_core.districts.set_mayor", 72, () -> PacketDistributor.sendToServer(new common.cn.kafei.simukraft.network.city.DistrictActionPacket(
                        common.cn.kafei.simukraft.network.city.DistrictActionPacket.Action.SET_MAYOR, packet.pos(), district.districtId(), player.playerId(), "", List.of()))));
                if (official) {
                    actions.addChild(memberActionButton("screen.simukraft.city_core.districts.remove_official", 72, () -> PacketDistributor.sendToServer(new common.cn.kafei.simukraft.network.city.DistrictActionPacket(
                            common.cn.kafei.simukraft.network.city.DistrictActionPacket.Action.REMOVE_OFFICIAL, packet.pos(), district.districtId(), player.playerId(), "", List.of()))));
                } else {
                    actions.addChild(memberActionButton("screen.simukraft.city_core.districts.set_official", 72, () -> PacketDistributor.sendToServer(new common.cn.kafei.simukraft.network.city.DistrictActionPacket(
                            common.cn.kafei.simukraft.network.city.DistrictActionPacket.Action.SET_OFFICIAL, packet.pos(), district.districtId(), player.playerId(), "", List.of()))));
                }
                row.addChild(actions);
            }
            panel.addChild(row);
            panel.addChild(contentSpacer());
        }
        return scrollable(panel);
    }

    /** 地图快照可能不带城市成员。分区页优先用完整快照，再补上分区里已有的人，保证区长和官员按钮有人可点。 */
    private static CityCoreOpenResponsePacket districtManagementPacket(CityCoreOpenResponsePacket packet) {
        if (packet == null || !packet.cityMembers().isEmpty()) {
            return packet;
        }
        CityCoreOpenResponsePacket cached = lastSummaryPacket;
        if (cached != null && cached.hasCity() && cached.cityId().equals(packet.cityId()) && !cached.cityMembers().isEmpty()) {
            return new CityCoreOpenResponsePacket(packet.pos(), true, packet.cityId(), packet.cityName(), packet.funds(), packet.cityLevel(), packet.memberCount(), packet.cityPopulation(), packet.housingCapacity(), packet.cityChunkCount(), packet.cityEnclaveCount(), packet.permissionLevel(), packet.canCreateCity(), packet.canManageCity(), packet.financeEntries(), packet.poiStats(), packet.jobStats(), packet.upgradeTargets(), packet.upgradeProgress(), packet.districts().isEmpty() ? cached.districts() : packet.districts(), packet.districtContext(), packet.districtName(), cached.cityMembers());
        }
        return packet;
    }

    private static List<CityCoreOpenResponsePacket.CityMemberRef> districtCandidates(CityCoreOpenResponsePacket packet, CityCoreOpenResponsePacket.DistrictSummary district) {
        java.util.LinkedHashMap<UUID, CityCoreOpenResponsePacket.CityMemberRef> candidates = new java.util.LinkedHashMap<>();
        for (CityCoreOpenResponsePacket.CityMemberRef member : packet.cityMembers()) {
            candidates.putIfAbsent(member.playerId(), member);
        }
        for (CityCoreOpenResponsePacket.DistrictMemberView member : district.members()) {
            candidates.putIfAbsent(member.playerId(), new CityCoreOpenResponsePacket.CityMemberRef(member.playerId(), member.playerName()));
        }
        return List.copyOf(candidates.values());
    }

    private static String districtBaseName(String name) {
        if (name != null && name.endsWith("\u533a")) {
            return name.substring(0, name.length() - 1);
        }
        return name == null ? "" : name;
    }

    private static Component districtRoleText(int rolePower) {
        String key = switch (rolePower) {
            case 2 -> "screen.simukraft.city_core.districts.role.mayor";
            case 1 -> "screen.simukraft.city_core.districts.role.official";
            default -> "screen.simukraft.city_core.districts.role.resident";
        };
        return Component.translatable(key);
    }

    private static String financeTypeText(CityCoreOpenResponsePacket.FinanceEntry entry) {
        return Component.translatable("screen.simukraft.city_core.finance.type." + entry.type().name().toLowerCase(Locale.ROOT)).getString();
    }

    /** financeAmountText：收入显示正数，支出显示负数，避免流水里两种符号同时出现。 */
    private static String financeAmountText(CityCoreOpenResponsePacket.FinanceEntry entry) {
        double amount = entry.amount();
        if (entry.type() == FinanceTransactionData.Type.EXPENSE) {
            amount = -Math.abs(amount);
        } else if (entry.type() == FinanceTransactionData.Type.INCOME) {
            amount = Math.abs(amount);
        }
        return String.format(Locale.ROOT, "%+.2f", amount);
    }

    private static String financeReasonText(CityCoreOpenResponsePacket.FinanceEntry entry) {
        String reason = entry.reason();
        if (reason == null || reason.isBlank()) {
            return Component.translatable("screen.simukraft.city_core.finance.reason.unknown").getString();
        }
        return Component.translatable("screen.simukraft.city_core.finance.reason." + reason).getString();
    }

    public static void openCitizens(CityCitizenManageResponsePacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            CityCoreWindow window = activeWindow;
            if (!isActiveScreen(minecraft, window) || !window.matches(packet.pos())) {
                return;
            }
            window.openTab("citizens", "screen.simukraft.city_core.menu.citizens", citizensPanel(packet));
        });
    }

    /** openFamilyGraph：在城市核心窗口内打开无侧栏内容的五代关系图画布。 */
    public static void openFamilyGraph(CityCitizenFamilyGraphResponsePacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            CityCoreWindow window = activeWindow;
            if (!isActiveScreen(minecraft, window) || !window.matches(packet.pos())) {
                return;
            }
            window.openOrReplaceTab("family_graph", "screen.simukraft.city_core.family_graph", familyGraphPanel(packet));
            window.hideSidebar();
        });
    }

    private static UIElement familyGraphPanel(CityCitizenFamilyGraphResponsePacket packet) {
        UIElement panel = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.heightPercent(100);
            layout.flexDirection(FlexDirection.COLUMN);
        });
        panel.addChild(new CitizenFamilyGraphCanvas(packet.snapshot()));
        return panel;
    }

    private static UIElement citizensPanel(CityCitizenManageResponsePacket packet) {
        UIElement outer = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.heightPercent(100);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.paddingAll(8);
            layout.gapAll(6);
        });

        TextField searchField = textField("", 200);
        searchField.layout(layout -> {
            layout.widthPercent(100);
            layout.height(20);
            layout.flexShrink(0);
        });
        searchField.getTextFieldStyle().placeholder(Component.translatable("screen.simukraft.city_core.citizen_manage.search"));
        outer.addChild(searchField);

        UIElement listPanel = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.gapAll(4);
        });

        Runnable rebuild = () -> {
            listPanel.clearAllChildren();
            String q = searchField.getValue().toLowerCase(Locale.ROOT).trim();
            boolean any = false;
            for (CityCitizenManageResponsePacket.CitizenEntry citizen : packet.citizens()) {
                String n = citizen.name() == null ? "" : citizen.name();
                if (!q.isEmpty() && !n.toLowerCase(Locale.ROOT).contains(q)) continue;
                listPanel.addChild(citizenRow(packet, citizen));
                any = true;
            }
            if (!any) listPanel.addChild(line(Component.translatable("screen.simukraft.city_core.citizen_manage.empty")));
        };

        searchField.setTextResponder(t -> rebuild.run());
        rebuild.run();

        ScrollerView scroller = scrollable(listPanel);
        scroller.layout(layout -> {
            layout.flex(1);
            layout.widthPercent(100);
        });
        outer.addChild(scroller);
        return outer;
    }

    private static UIElement citizenRow(CityCitizenManageResponsePacket packet, CityCitizenManageResponsePacket.CitizenEntry citizen) {
        UIElement row = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.height(44);
            layout.flexDirection(FlexDirection.ROW);
            layout.gapAll(6);
            layout.alignItems(AlignItems.CENTER);
        });
        UIElement avatar = CitizenAvatarFactory.createHead(citizen.skinPath(), 0xFFFFFFFF);
        avatar.layout(layout -> {
            layout.width(32);
            layout.height(32);
            layout.flexShrink(0);
        });
        row.addChild(avatar);
        UIElement info = new UIElement().layout(layout -> {
            layout.flex(1);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.gapAll(3);
        });
        info.addChild(line(Component.translatable("screen.simukraft.city_core.citizen_manage.row_info",
                Component.translatable(citizen.jobKey()),
                Component.translatable(citizen.workStatusKey()),
                String.valueOf(citizen.age()),
                Component.translatable("screen.simukraft.city_core.citizen_manage.gender_" + citizen.gender()))));
        info.addChild(line(Component.literal(citizen.name() == null || citizen.name().isBlank() ? "-" : citizen.name())));
        row.addChild(info);
        row.addChild(memberActionButton("screen.simukraft.city_core.citizen_manage.family_graph", 52,
                () -> PacketDistributor.sendToServer(new CityCitizenFamilyGraphRequestPacket(packet.pos(), citizen.citizenId()))));
        if (packet.canManage()) {
            row.addChild(memberActionButton("screen.simukraft.city_core.citizen_manage.dismiss", 52,
                    () -> { PacketDistributor.sendToServer(new CityCitizenManageActionPacket(packet.pos(), CityCitizenManageActionPacket.Action.DISMISS, citizen.citizenId())); close(); }));
            row.addChild(memberActionButton("screen.simukraft.city_core.citizen_manage.exile", 44,
                    () -> { PacketDistributor.sendToServer(new CityCitizenManageActionPacket(packet.pos(), CityCitizenManageActionPacket.Action.EXILE, citizen.citizenId())); close(); }));
        }
        return row;
    }

    private static void requestMembers(CityCoreOpenResponsePacket packet) {
        PacketDistributor.sendToServer(new CityCoreMembersRequestPacket(packet.pos()));
    }

    private static void requestMap(CityCoreOpenResponsePacket packet) {
        PacketDistributor.sendToServer(new CityCoreMapRequestPacket(packet.pos()));
    }

    private static UIElement cityMapPanel(CityCoreMapResponsePacket packet) {
        UIElement panel = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.heightPercent(100);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.alignItems(AlignItems.CENTER);
            layout.justifyContent(AlignContent.CENTER);
        });
        panel.addChild(new CityChunkMapElement(packet));
        return panel;
    }

    private static UIElement membersPanel(CityCoreMembersResponsePacket packet) {
        UIElement panel = basePanel();
        panel.addChild(line(Component.translatable("screen.simukraft.city_core.members.title")));
        if (packet.viewerPermission().atLeast(CityPermissionLevel.OFFICIAL)) {
            panel.addChild(addMemberPanel(packet));
            panel.addChild(contentSpacer());
        }
        addMemberGroup(panel, packet.members(), CityPermissionLevel.MAYOR, "screen.simukraft.city_core.members.group_mayor", packet.viewerPermission(), packet.pos());
        addMemberGroup(panel, packet.members(), CityPermissionLevel.OFFICIAL, "screen.simukraft.city_core.members.group_officials", packet.viewerPermission(), packet.pos());
        addMemberGroup(panel, packet.members(), CityPermissionLevel.CITIZEN, "screen.simukraft.city_core.members.group_citizens", packet.viewerPermission(), packet.pos());
        return scrollable(panel);
    }

    private static UIElement addMemberPanel(CityCoreMembersResponsePacket packet) {
        UIElement panel = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.gapAll(4);
            layout.alignItems(AlignItems.STRETCH);
        });
        panel.addChild(sectionLabel("screen.simukraft.city_core.members.add_title"));
        panel.addChild(manualAddRow(packet));
        return panel;
    }

    private static UIElement manualAddRow(CityCoreMembersResponsePacket packet) {
        UIElement wrapper = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.gapAll(0);
        });

        UIElement row = addRow();
        UIElement searchContainer = new UIElement() {
            @Override
            public void drawBackgroundAdditional(@Nonnull GUIContext guiContext) {
                int w = Math.round(getSizeWidth());
                int h = Math.round(getSizeHeight());
                RecipeBookSearchUi.renderFrame(guiContext,
                        Math.round(getPositionX()), Math.round(getPositionY()),
                        w, Math.min(RecipeBookSearchUi.FRAME_TEXTURE_WIDTH, w), h,
                        RecipeBookSearchUi.TEXT_OFFSET_X,
                        Math.max(1, (h - RecipeBookSearchUi.TEXT_HEIGHT) / 2),
                        Math.max(RecipeBookSearchUi.TEXT_WIDTH, w - RecipeBookSearchUi.TEXT_OFFSET_X - 4),
                        RecipeBookSearchUi.TEXT_HEIGHT);
            }
        };
        searchContainer.layout(layout -> {
            layout.flex(1);
            layout.height(RecipeBookSearchUi.FRAME_HEIGHT);
        });

        TextField addField = new TextField();
        addField.setAnyString();
        addField.style(s -> s.backgroundTexture(IGuiTexture.EMPTY));
        addField.textFieldStyle(s -> s
                .textColor(0xFFFFFFFF)
                .cursorColor(0xFFFFFFFF)
                .textShadow(false)
                .fontSize(9.0F)
                .placeholder(Component.translatable("screen.simukraft.city_core.members.add_placeholder"))
                .focusOverlay(IGuiTexture.EMPTY));
        addField.layout(layout -> {
            layout.widthPercent(100);
            layout.height(RecipeBookSearchUi.FRAME_HEIGHT);
            layout.paddingLeft(RecipeBookSearchUi.TEXT_OFFSET_X);
            layout.paddingRight(4);
            layout.paddingTop(Math.max(1, (RecipeBookSearchUi.FRAME_HEIGHT - RecipeBookSearchUi.TEXT_HEIGHT) / 2));
        });
        searchContainer.addChild(addField);
        row.addChild(searchContainer);

        if (packet.viewerPermission() == CityPermissionLevel.MAYOR) {
            row.addChild(memberActionButton("screen.simukraft.city_core.members.add_official", 72,
                    () -> sendAddMember(packet.pos(), CityCoreMemberActionPacket.EMPTY_PLAYER_ID, addField.getValue(), CityPermissionLevel.OFFICIAL)));
            row.addChild(memberActionButton("screen.simukraft.city_core.members.add_mayor", 72,
                    () -> sendAddMember(packet.pos(), CityCoreMemberActionPacket.EMPTY_PLAYER_ID, addField.getValue(), CityPermissionLevel.MAYOR)));
        }
        wrapper.addChild(row);

        UIElement suggestionPanel = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.gapAll(1);
            layout.paddingTop(1);
        });

        Runnable rebuildSuggestions = () -> {
            suggestionPanel.clearAllChildren();
            String q = addField.getValue().trim().toLowerCase(java.util.Locale.ROOT);
            List<CityCoreMembersResponsePacket.CandidateEntry> matches = packet.onlineCandidates().stream()
                    .filter(c -> q.isEmpty() || c.playerName().toLowerCase(java.util.Locale.ROOT).contains(q))
                    .limit(3)
                    .toList();
            for (CityCoreMembersResponsePacket.CandidateEntry candidate : matches) {
                suggestionPanel.addChild(candidateSuggestionRow(candidate, addField, suggestionPanel));
            }
        };

        addField.setTextResponder(t -> rebuildSuggestions.run());
        rebuildSuggestions.run();
        wrapper.addChild(suggestionPanel);
        return wrapper;
    }

    private static UIElement candidateSuggestionRow(CityCoreMembersResponsePacket.CandidateEntry candidate, TextField targetField, UIElement panel) {
        UIElement row = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.height(14);
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.gapAll(4);
            layout.paddingLeft(6);
            layout.paddingRight(4);
        });
        row.style(s -> s.backgroundTexture(new ColorRectTexture(0xCC101010)));
        UIElement dot = new UIElement().layout(l -> l.width(4).height(4).flexShrink(0));
        dot.style(s -> s.backgroundTexture(new ColorRectTexture(0xFF888888)));
        row.addChild(dot);
        Label nameLabel = new Label();
        nameLabel.setText(Component.literal(candidate.playerName()));
        nameLabel.layout(l -> l.flex(1).height(9));
        row.addChild(nameLabel);
        row.addEventListener(UIEvents.MOUSE_DOWN, e -> {
            if (e.button == 0) {
                targetField.setValue(candidate.playerName());
                panel.clearAllChildren();
                e.stopPropagation();
            }
        });
        return row;
    }

    private static void addMemberGroup(UIElement panel, List<CityCoreMembersResponsePacket.MemberEntry> members, CityPermissionLevel groupPermission, String titleKey, CityPermissionLevel viewerPermission, BlockPos pos) {
        List<CityCoreMembersResponsePacket.MemberEntry> groupMembers = members.stream()
                .filter(member -> member.permissionLevel() == groupPermission)
                .toList();
        if (groupMembers.isEmpty()) {
            return;
        }
        panel.addChild(sectionLabel(titleKey));
        addMemberRows(panel, groupMembers, viewerPermission, pos);
    }

    private static void addMemberRows(UIElement panel, List<CityCoreMembersResponsePacket.MemberEntry> members, CityPermissionLevel viewerPermission, BlockPos pos) {
        for (CityCoreMembersResponsePacket.MemberEntry member : members) {
            UIElement row = addRow();
            row.addChild(memberLabel(member));
            if (viewerPermission == CityPermissionLevel.MAYOR && member.permissionLevel() != CityPermissionLevel.MAYOR) {
                row.addChild(memberActionButton("screen.simukraft.city_core.members.set_citizen", 56, () -> PacketDistributor.sendToServer(new CityCoreMemberActionPacket(pos, CityCoreMemberActionPacket.Action.SET_PERMISSION, member.playerId(), member.playerName(), CityPermissionLevel.CITIZEN))));
                row.addChild(memberActionButton("screen.simukraft.city_core.members.set_official", 56, () -> PacketDistributor.sendToServer(new CityCoreMemberActionPacket(pos, CityCoreMemberActionPacket.Action.SET_PERMISSION, member.playerId(), member.playerName(), CityPermissionLevel.OFFICIAL))));
                row.addChild(memberActionButton("screen.simukraft.city_core.members.set_mayor", 56, () -> PacketDistributor.sendToServer(new CityCoreMemberActionPacket(pos, CityCoreMemberActionPacket.Action.SET_PERMISSION, member.playerId(), member.playerName(), CityPermissionLevel.MAYOR))));
            }
            if (viewerPermission.atLeast(CityPermissionLevel.OFFICIAL) && member.permissionLevel() != CityPermissionLevel.MAYOR) {
                row.addChild(memberActionButton("screen.simukraft.city_core.members.remove", 48, () -> PacketDistributor.sendToServer(new CityCoreMemberActionPacket(pos, CityCoreMemberActionPacket.Action.REMOVE, member.playerId(), member.playerName(), CityPermissionLevel.CITIZEN))));
            }
            panel.addChild(row);
        }
    }

    private static Label memberLabel(CityCoreMembersResponsePacket.MemberEntry member) {
        Label label = line(Component.translatable("screen.simukraft.city_core.members.row", member.playerName(), permissionText(member.permissionLevel())));
        label.layout(layout -> {
            layout.height(13);
            layout.flex(1);
        });
        return label;
    }

    private static UIElement addRow() {
        return new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.height(24);
            layout.flexDirection(FlexDirection.ROW);
            layout.gapAll(4);
            layout.alignItems(AlignItems.CENTER);
        });
    }

    private static Label sectionLabel(String key) {
        Label label = line(Component.translatable(key));
        label.layout(layout -> {
            layout.widthPercent(100);
            layout.height(14);
        });
        return label;
    }

    private static Button memberActionButton(String key, int width, Runnable action) {
        Button button = baseButton(key, action);
        button.layout(layout -> {
            layout.width(width);
            layout.height(18);
            layout.flexShrink(0);
            layout.flexDirection(FlexDirection.ROW);
            layout.justifyContent(AlignContent.CENTER);
        });
        return button;
    }

    private static void sendAddMember(BlockPos pos, java.util.UUID playerId, String playerName, CityPermissionLevel permissionLevel) {
        PacketDistributor.sendToServer(new CityCoreMemberActionPacket(pos, CityCoreMemberActionPacket.Action.ADD, playerId, playerName, permissionLevel));
    }

    private static String permissionText(CityPermissionLevel permissionLevel) {
        return Component.translatable("permission.simukraft." + permissionLevel.name().toLowerCase(Locale.ROOT)).getString();
    }

    private static UIElement contentPanel(CityCoreOpenResponsePacket packet) {
        UIElement panel = basePanel();
        if (!packet.hasCity()) {
            addCreateContent(panel, packet);
        } else if (packet.canManageCity()) {
            addManageContent(panel, packet);
        } else {
            addReadOnlyContent(panel, packet);
        }
        return panel;
    }

    private static UIElement basePanel() {
        return new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.paddingAll(10);
            layout.gapAll(5);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.alignItems(AlignItems.STRETCH);
        });
    }

    private static void addCreateContent(UIElement panel, CityCoreOpenResponsePacket packet) {
        panel.addChild(line(Component.translatable("screen.simukraft.city_core.no_city")));
        panel.addChild(contentSpacer());
        TextField nameField = textField("", 200);
        nameField.getTextFieldStyle().placeholder(Component.translatable("screen.simukraft.city_core.name_placeholder"));
        panel.addChild(nameField);
        if (packet.canCreateCity()) {
            panel.addChild(contentButton("screen.simukraft.city_core.create", () -> PacketDistributor.sendToServer(new CityCoreCreateCityPacket(packet.pos(), nameField.getValue()))));
        } else {
            panel.addChild(line(Component.translatable("screen.simukraft.city_core.cannot_create")));
        }
        panel.addChild(contentSpacer());
    }

    private static void addManageContent(UIElement panel, CityCoreOpenResponsePacket packet) {
        addCitySummary(panel, packet);
    }

    private static void addReadOnlyContent(UIElement panel, CityCoreOpenResponsePacket packet) {
        panel.addChild(line(Component.translatable("screen.simukraft.city_info.title")));
        addCitySummary(panel, packet);
        panel.addChild(line(Component.translatable("screen.simukraft.city_core.manage_disabled")));
    }

    private static UIElement menuSpacer() {
        return new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.flexGrow(1);
        });
    }

    private static UIElement contentSpacer() {
        return new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.flexGrow(1);
        });
    }

    private static void addCitySummary(UIElement root, CityCoreOpenResponsePacket packet) {
        root.addChildren(
                line(Component.translatable("screen.simukraft.city_core.city_name", packet.cityName())),
                line(Component.translatable("screen.simukraft.city_core.funds", String.format(Locale.ROOT, "%.2f", packet.funds()))),
                line(Component.translatable("screen.simukraft.city_core.level", packet.cityLevel())),
                line(Component.translatable("screen.simukraft.city_core.population", packet.cityPopulation(), packet.housingCapacity())),
                line(Component.translatable("screen.simukraft.city_core.permission", Component.translatable("permission.simukraft." + packet.permissionLevel().name().toLowerCase(Locale.ROOT)).getString())),
                line(Component.translatable("screen.simukraft.city_core.core_pos", packet.pos().getX(), packet.pos().getY(), packet.pos().getZ()))
        );
    }

    private static void close() {
        Minecraft minecraft = Minecraft.getInstance();
        activeWindow = null;
        activeScreen = null;
        minecraft.setScreen(null);
    }

    private record UpgradeRefreshExpectation(BlockPos pos, UUID cityId, long sourceWindowId) {
        private boolean matches(CityCoreOpenResponsePacket packet) {
            return packet != null && pos.equals(packet.pos()) && cityId.equals(packet.cityId());
        }
    }

    private static Label line(Component component) {
        Label label = new Label();
        label.setText(component);
        label.layout(layout -> {
            layout.height(13);
            layout.widthPercent(100);
        });
        return label;
    }

    private static UIElement sidebarHeader(boolean collapsed, Runnable action) {
        UIElement header = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.height(20);
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.justifyContent(AlignContent.FLEX_START);
            layout.paddingLeft(2);
        });
        header.addChild(sidebarToggleButton(collapsed, action));
        return header;
    }

    private static UIElement sidebarToggleButton(boolean collapsed, Runnable action) {
        Button button = new Button().noText();
        button.addChild(new UIElement()
                .layout(layout -> layout.width(10).height(10))
                .style(style -> style.backgroundTexture(collapsed ? Icons.EXPAND_HORIZONTAL : Icons.COLLAPSE_HORIZONTAL)));
        button.setOnClick(event -> action.run());
        button.layout(layout -> {
            layout.width(14);
            layout.height(14);
            layout.alignItems(AlignItems.CENTER);
            layout.justifyContent(AlignContent.CENTER);
        });
        return button;
    }

    private static Button menuButton(String key, Runnable action) {
        Button button = baseButton(key, action);
        button.layout(layout -> {
            layout.height(BUTTON_HEIGHT);
            layout.widthPercent(100);
            layout.flexDirection(FlexDirection.ROW);
            layout.justifyContent(AlignContent.CENTER);
        });
        return button;
    }

    private static Button closeButton(Runnable action) {
        Button button = baseButton("screen.simukraft.city_core.close", action);
        button.layout(layout -> {
            layout.width(BACK_BUTTON_WIDTH);
            layout.height(BACK_BUTTON_HEIGHT);
            layout.flexDirection(FlexDirection.ROW);
            layout.justifyContent(AlignContent.CENTER);
        });
        return button;
    }

    private static Button contentButton(String key, Runnable action) {
        Button button = baseButton(key, action);
        button.layout(layout -> {
            layout.width(100);
            layout.height(20);
            layout.flexDirection(FlexDirection.ROW);
            layout.justifyContent(AlignContent.CENTER);
        });
        return button;
    }

    private static Button contentButton(Component text, Runnable action) {
        Button button = new Button();
        button.setText(text);
        button.setOnClick(event -> action.run());
        button.layout(layout -> {
            layout.width(170);
            layout.height(20);
            layout.flexDirection(FlexDirection.ROW);
            layout.justifyContent(AlignContent.CENTER);
        });
        return button;
    }

    private static TextField textField(String value, int width) {
        TextField field = new TextField();
        field.setText(value == null ? "" : value);
        field.layout(layout -> {
            layout.width(width);
            layout.height(20);
        });
        return field;
    }

    private static Button baseButton(String key, Runnable action) {
        Button button = new Button();
        button.setText(Component.translatable(key));
        button.setOnClick(event -> action.run());
        return button;
    }

    private static final class CityCoreWindow {
        private final long instanceId = WINDOW_SEQUENCE.incrementAndGet();
        private final CityCoreOpenResponsePacket packet;
        private final CityCoreMembersResponsePacket membersPacket;
        private final CityCoreMapResponsePacket mapPacket;
        private final String initialTabId;
        private String currentTabId;
        private final ViewContainer rightTabs = new ViewContainer();
        private final Map<String, View> openedTabs = new ConcurrentHashMap<>();
        private final UIElement sidebarContainer = new UIElement();
        private boolean sidebarCollapsed;

        private CityCoreWindow(CityCoreOpenResponsePacket packet) {
            this(packet, null);
        }

        private CityCoreWindow(CityCoreOpenResponsePacket packet, String initialTabId) {
            this(packet, null, null, initialTabId);
        }

        private CityCoreWindow(CityCoreMembersResponsePacket membersPacket) {
            this(summaryPacket(membersPacket), membersPacket, null, null);
        }

        private CityCoreWindow(CityCoreMapResponsePacket mapPacket) {
            this(summaryPacket(mapPacket), null, mapPacket, null);
        }

        private CityCoreWindow(CityCoreOpenResponsePacket packet,
                               CityCoreMembersResponsePacket membersPacket,
                               CityCoreMapResponsePacket mapPacket,
                               String initialTabId) {
            CityCoreScreenOpener.activeWindow = this;
            this.packet = packet;
            this.membersPacket = membersPacket;
            this.mapPacket = mapPacket;
            this.initialTabId = initialTabId;
            rightTabs.layout(layout -> {
                layout.flex(1);
                layout.heightPercent(100);
                layout.widthPercent(100);
            });
            rebuildSidebar();
            openDefaultTabs();
        }

        /** summaryPacket：成员页响应不带统计字段时，复用最近一次城市核心统计。 */
        private static CityCoreOpenResponsePacket summaryPacket(CityCoreMembersResponsePacket packet) {
            CityCoreOpenResponsePacket cached = cachedSummary(packet.cityId(), packet.pos());
            int population = cached != null ? cached.cityPopulation() : 0;
            int housingCapacity = cached != null ? cached.housingCapacity() : 0;
            int cityChunkCount = cached != null ? cached.cityChunkCount() : 0;
            int cityEnclaveCount = cached != null ? cached.cityEnclaveCount() : 0;
            List<CityCoreOpenResponsePacket.FinanceEntry> finances = cached != null ? cached.financeEntries() : List.of();
            List<CityCoreOpenResponsePacket.PoiStat> poiStats = cached != null ? cached.poiStats() : List.of();
            List<CityCoreOpenResponsePacket.JobStat> jobStats = cached != null ? cached.jobStats() : List.of();
            List<CityCoreOpenResponsePacket.UpgradeTarget> upgradeTargets = cached != null ? cached.upgradeTargets() : List.of();
            CityCoreOpenResponsePacket.UpgradeProgress upgradeProgress = cached != null ? cached.upgradeProgress() : CityCoreOpenResponsePacket.UpgradeProgress.NONE;
            List<CityCoreOpenResponsePacket.DistrictSummary> districts = cached != null ? cached.districts() : List.of();
            List<CityCoreOpenResponsePacket.CityMemberRef> cityMembers = cached != null ? cached.cityMembers() : List.of();
            return new CityCoreOpenResponsePacket(packet.pos(), true, packet.cityId(), packet.cityName(), packet.funds(), packet.cityLevel(), packet.members().size(), population, housingCapacity, cityChunkCount, cityEnclaveCount, packet.viewerPermission(), false, packet.canManageCity(), finances, poiStats, jobStats, upgradeTargets, upgradeProgress, districts, cityMembers);
        }

        /** summaryPacket：地图响应不带统计字段时，复用最近一次城市核心统计。分区列表必须留下，否则管理页会显示暂无分区。 */
        private static CityCoreOpenResponsePacket summaryPacket(CityCoreMapResponsePacket packet) {
            CityCoreOpenResponsePacket cached = cachedSummary(packet.cityId(), packet.pos());
            int population = cached != null ? cached.cityPopulation() : 0;
            int housingCapacity = cached != null ? cached.housingCapacity() : 0;
            int cityChunkCount = cached != null ? cached.cityChunkCount() : 0;
            int cityEnclaveCount = cached != null ? cached.cityEnclaveCount() : 0;
            List<CityCoreOpenResponsePacket.FinanceEntry> finances = cached != null ? cached.financeEntries() : List.of();
            List<CityCoreOpenResponsePacket.PoiStat> poiStats = cached != null ? cached.poiStats() : List.of();
            List<CityCoreOpenResponsePacket.JobStat> jobStats = cached != null ? cached.jobStats() : List.of();
            List<CityCoreOpenResponsePacket.UpgradeTarget> upgradeTargets = cached != null ? cached.upgradeTargets() : List.of();
            CityCoreOpenResponsePacket.UpgradeProgress upgradeProgress = cached != null ? cached.upgradeProgress() : CityCoreOpenResponsePacket.UpgradeProgress.NONE;
            List<CityCoreOpenResponsePacket.CityMemberRef> cityMembers = cached != null ? cached.cityMembers() : List.of();
            return new CityCoreOpenResponsePacket(packet.pos(), true, packet.cityId(), packet.cityName(), packet.funds(), packet.cityLevel(), packet.memberCount(), population, housingCapacity, cityChunkCount, cityEnclaveCount, packet.permissionLevel(), false, packet.canManageCity(), finances, poiStats, jobStats, upgradeTargets, upgradeProgress, mergeDistricts(cached, packet), cityMembers);
        }

        /** 地图包里的分区是刚从服务端拿到的，不能被缺少分区字段的统计快照覆盖成空列表。 */
        private static List<CityCoreOpenResponsePacket.DistrictSummary> mergeDistricts(CityCoreOpenResponsePacket cached, CityCoreMapResponsePacket map) {
            Map<UUID, CityCoreOpenResponsePacket.DistrictSummary> merged = new LinkedHashMap<>();
            if (cached != null) {
                for (CityCoreOpenResponsePacket.DistrictSummary district : cached.districts()) {
                    merged.put(district.districtId(), district);
                }
            }
            if (map != null) {
                for (CityCoreMapResponsePacket.DistrictEntry entry : map.districts()) {
                    CityCoreOpenResponsePacket.DistrictSummary existing = merged.get(entry.districtId());
                    int chunks = entry.chunks().size();
                    if (existing == null) {
                        merged.put(entry.districtId(), new CityCoreOpenResponsePacket.DistrictSummary(entry.districtId(), entry.name(), entry.color(), chunks, 0, "", List.of()));
                    } else if (existing.chunkCount() != chunks || !existing.name().equals(entry.name())) {
                        merged.put(entry.districtId(), new CityCoreOpenResponsePacket.DistrictSummary(existing.districtId(), entry.name(), entry.color(), chunks, existing.coreCount(), existing.mayorName(), existing.members()));
                    }
                }
            }
            return List.copyOf(merged.values());
        }

        private void rebuildSidebar() {
            sidebarContainer.clearAllChildren();
            sidebarContainer.layout(layout -> {
                layout.width(sidebarCollapsed ? COLLAPSED_SIDEBAR_WIDTH : BUTTON_WIDTH);
                layout.heightPercent(100);
                layout.flexShrink(0);
                layout.flexDirection(FlexDirection.COLUMN);
                layout.alignItems(AlignItems.STRETCH);
            });
            sidebarContainer.addChild(sidebarHeader(sidebarCollapsed, this::toggleSidebar));
            if (!sidebarCollapsed) {
                sidebarContainer.addChild(scrollable(menuColumn(packet, this)));
            }
        }

        private void toggleSidebar() {
            sidebarCollapsed = !sidebarCollapsed;
            rebuildSidebar();
        }

        /** hideSidebar：关系图画布占满内容区，不保留左侧菜单。 */
        private void hideSidebar() {
            if (!sidebarCollapsed) {
                sidebarCollapsed = true;
                rebuildSidebar();
            }
        }

        private void openDefaultTabs() {
            if ("upgrade".equals(initialTabId) && packet.hasCity() && packet.canManageCity()) {
                openTab("info", "screen.simukraft.city_core.menu.info", scrollable(contentPanel(packet)));
                openTab("upgrade", "screen.simukraft.city_core.menu.upgrade", upgradePanel(packet));
            } else if ("districts".equals(initialTabId) && packet.hasCity() && !packet.districtContext() && packet.permissionLevel() == CityPermissionLevel.MAYOR) {
                openTab("districts", "screen.simukraft.city_core.districts.title", districtsPanel(packet));
            } else if ("edit".equals(initialTabId) && packet.hasCity() && packet.canManageCity()) {
                String editMenuKey = packet.districtContext()
                        ? "screen.simukraft.city_core.menu.edit_district"
                        : "screen.simukraft.city_core.menu.edit";
                openTab("edit", editMenuKey, editPanel(packet));
            } else if ("finance".equals(initialTabId) && packet.hasCity() && packet.canManageCity()) {
                openTab("finance", "screen.simukraft.city_core.menu.finance", financePanel(packet));
            } else if (mapPacket != null) {
                openTab("info", "screen.simukraft.city_core.menu.info", scrollable(contentPanel(packet)));
                openTab("map", "screen.simukraft.city_core.map_title", cityMapPanel(mapPacket));
            } else if (membersPacket != null) {
                openTab("members", "screen.simukraft.city_core.members.title", membersPanel(membersPacket));
            } else if (packet.hasCity()) {
                openTab("info", "screen.simukraft.city_core.menu.info", scrollable(contentPanel(packet)));
                requestMap(packet);
            } else {
                openTab("create", "screen.simukraft.city_core.create", createPanel(packet));
            }
        }

        /** openOrReplaceTab：同一标签换内容，避免关系图停留在上一个市民。 */
        private void openOrReplaceTab(String id, String titleKey, UIElement content) {
            View existing = openedTabs.remove(id);
            if (existing != null && rightTabs.hasView(existing)) {
                existing.removeSelf();
            }
            openTab(id, titleKey, content);
        }

        private void openTab(String id, String titleKey, UIElement content) {
            currentTabId = id;
            View existing = openedTabs.get(id);
            if (existing != null && rightTabs.hasView(existing)) {
                rightTabs.selectView(existing);
                return;
            }
            BrowserTabView view = new BrowserTabView(titleKey, () -> openedTabs.remove(id));
            view.addChild(content);
            openedTabs.put(id, view);
            rightTabs.addView(view);
            rightTabs.selectView(view);
        }

        private boolean matches(CityCoreOpenResponsePacket other) {
            return other != null && packet.cityId().equals(other.cityId()) && packet.pos().equals(other.pos());
        }

        /** matches: 校验带城市标识的异步响应归属。 */
        private boolean matches(BlockPos pos, UUID cityId) {
            return packet.pos().equals(pos) && packet.cityId().equals(cityId);
        }

        /** matches: 校验仅携带核心坐标的异步响应归属。 */
        private boolean matches(BlockPos pos) {
            return packet.pos().equals(pos);
        }

    }

    private static final class CityChunkMapElement extends UIElement {
        private static final double MIN_ZOOM = 0.5D;
        private static final double MAX_ZOOM = 10.0D;
        private static final double ZOOM_STEP = 0.5D;
        private static final int MAP_SIDE_PADDING = 4;
        private static final int MAP_TOP_PADDING = 4;
        private static final int CURRENT_BORDER_COLOR = 0xCC00DD00;
        private static final int OTHER_BORDER_COLOR = 0xCCFF8800;
        private static final int CURRENT_CHUNK_FILL_COLOR = 0x5500DD00;
        private static final int OTHER_CHUNK_FILL_COLOR = 0x55FF8800;
        private static final int BATCH_CLAIM_PREVIEW_COLOR = 0x66FFFF55;
        private static final int BATCH_CLAIM_BOX_BORDER_COLOR = 0xEEFFFF00;
        private static final int GRID_COLOR = 0x40000000;
        private static final int CORE_MARKER_COLOR = 0xFF4080FF;
        private static final int THREE_D_CURRENT_BORDER = 0x882E6B32;
        private static final int THREE_D_OTHER_BORDER = 0x88B35A1A;
        private static final int MAX_BATCH_CLAIM_CHUNKS = 256;
        private static final String BATCH_CLAIM_DRAG_MARKER = "city_chunk_batch_claim";
        private volatile CityCoreMapResponsePacket packet;
        private final ClientCityChunkCache cache = ClientCityChunkCache.getInstance();
        private final SimuMapManager mapManager = SimuMapManager.getInstance();
        private final LinkedHashSet<Long> batchClaimChunks = new LinkedHashSet<>();
        private boolean batchClaimDragging;
        private int batchClaimStartChunkX;
        private int batchClaimStartChunkZ;
        private int batchBoxMinX;
        private int batchBoxMaxX;
        private int batchBoxMinZ;
        private int batchBoxMaxZ;
        private int batchClaimEndChunkX = Integer.MIN_VALUE;
        private int batchClaimEndChunkZ = Integer.MIN_VALUE;
        private double zoomLevel = 4.0D;
        private double offsetX;
        private double offsetY;
        private int contextMenuChunkX;
        private int contextMenuChunkZ;
        private double contextMenuX;
        private double contextMenuY;
        private int contextMenuDrawX;
        private int contextMenuDrawY;
        private int contextMenuWidth;
        private int contextMenuHeight;
        private boolean contextMenuVisible;
        private boolean mapConsumerReleased;
        private boolean threeDView;
        private float threeYaw = 0.8F;
        private float threeYawDragStart;
        private double threeZoom = 4.0D;
        private final SimuMap3DMesh threeMesh = new SimuMap3DMesh();
        private int modeButtonX;
        private int modeButtonY;
        private int modeButtonW;
        private int modeButtonH;
        private static final String THREE_D_DRAG = "city_map_3d_orbit";
        private static final String THREE_D_PAN = "city_map_3d_pan";
        private double threePanOffsetX;
        private double threePanOffsetY;
        private final float[] threePanDelta = new float[2];

        private CityChunkMapElement(CityCoreMapResponsePacket packet) {
            this.packet = packet;
            // 地图控件打开时才激活地图系统，避免常驻扫描占用客户端性能。
            mapManager.init();
            mapManager.acquireConsumer();
            forceInitialMapScan();
            centerMapOnCityCore();
            layout(layout -> {
                layout.widthPercent(100);
                layout.heightPercent(100);
                layout.flex(1);
            });
            addEventListener(UIEvents.MOUSE_WHEEL, this::onMouseWheel);
            addEventListener(UIEvents.MOUSE_DOWN, this::onMouseDown);
            addEventListener(UIEvents.MOUSE_UP, this::onMouseUp);
            addEventListener(UIEvents.DRAG_SOURCE_UPDATE, this::onDragUpdate);
            addEventListener(UIEvents.DRAG_END, this::onDragEnd);
            activeMapElement = this;
        }

        @Override
        protected void onRemoved() {
            if (activeMapElement == this) {
                activeMapElement = null;
            }
            releaseMapConsumer();
            super.onRemoved();
        }

        private boolean matches(BlockPos pos) {
            return packet.pos().equals(pos);
        }

        private void updatePacket(CityCoreMapResponsePacket packet) {
            this.packet = packet;
        }

        @Override
        public void drawBackgroundAdditional(@Nonnull GUIContext guiContext) {
            int x = Math.round(getPositionX());
            int y = Math.round(getPositionY());
            int canvasWidth = Math.round(getSizeWidth());
            int canvasHeight = Math.round(getSizeHeight());
            if (canvasWidth <= MAP_SIDE_PADDING * 2 || canvasHeight <= MAP_TOP_PADDING + MAP_SIDE_PADDING) {
                return;
            }
            guiContext.graphics.fill(x, y, x + canvasWidth, y + canvasHeight, 0x80000000);
            int mapStartX = x + MAP_SIDE_PADDING;
            int mapStartY = y + MAP_TOP_PADDING;
            int mapWidth = canvasWidth - MAP_SIDE_PADDING * 2;
            int mapHeight = canvasHeight - MAP_TOP_PADDING - MAP_SIDE_PADDING;
            guiContext.graphics.fill(mapStartX - 2, mapStartY - 2, mapStartX + mapWidth + 2, mapStartY + mapHeight + 2, 0xFFFFFFFF);
            guiContext.graphics.fill(mapStartX - 1, mapStartY - 1, mapStartX + mapWidth + 1, mapStartY + mapHeight + 1, 0x80000000);
            guiContext.graphics.flush();
            guiContext.enableScissor(mapStartX, mapStartY, mapWidth, mapHeight);
            renderMap(guiContext, mapStartX, mapStartY, mapWidth, mapHeight);
            guiContext.graphics.flush();
            guiContext.disableScissor();
            renderModeButton(guiContext, mapStartX, mapStartY, mapWidth);
        }

        private void renderMap(GUIContext guiContext, int startX, int startY, int width, int height) {
            double centerX = startX + width / 2.0D;
            double centerY = startY + height / 2.0D;
            double chunkSize = 16.0D * zoomLevel;
            // 视口坐标先转成 chunk 范围，只处理屏幕可见区域。
            int visibleChunksX = (int) Math.ceil(width / chunkSize) + 2;
            int visibleChunksY = (int) Math.ceil(height / chunkSize) + 2;
            int startChunkX = (int) Math.floor((-offsetX - width / 2.0D) / chunkSize);
            int startChunkZ = (int) Math.floor((-offsetY - height / 2.0D) / chunkSize);
            int endChunkX = startChunkX + visibleChunksX;
            int endChunkZ = startChunkZ + visibleChunksY;
            if (threeDView) {
                render3DMap(guiContext, startX, startY, width, height, centerX, centerY);
                return;
            }
            renderWorldMapTerrain(guiContext, startX, startY, width, height, centerX, centerY);
            renderGridOverlay(guiContext, startX, startY, width, height, centerX, centerY, chunkSize, startChunkX, startChunkZ);
            renderHoveredChunk(guiContext, startX, startY, width, height, centerX, centerY, chunkSize);
            renderOwnedChunkBorders(guiContext, startX, startY, width, height, centerX, centerY, chunkSize, startChunkX, endChunkX, startChunkZ, endChunkZ);
            renderBatchClaimPreview(guiContext, startX, startY, width, height, centerX, centerY, chunkSize);
            renderCoreMarker(guiContext, startX, startY, width, height, centerX, centerY, chunkSize);
            renderHoverBox(guiContext, startX, startY, width, height, centerX, centerY, chunkSize);
            renderContextMenu(guiContext, startX, startY, width, height);
        }

        private void renderWorldMapTerrain(GUIContext guiContext, int startX, int startY, int width, int height, double centerX, double centerY) {
            guiContext.graphics.fill(startX, startY, startX + width, startY + height, 0xFF1F1F1F);
            if (!SimuMapManager.isAvailable()) {
                return;
            }
            guiContext.graphics.flush();
            Collection<SimuMapRegion> regions = mapManager.getAllRegions();
            for (SimuMapRegion region : regions) {
                if (!region.isImageLoaded() && !region.hasData()) {
                    continue;
                }
                int textureId = region.getTextureId();
                if (textureId == -1) {
                    continue;
                }
                double regionWorldX = region.regionX * 512.0D;
                double regionWorldZ = region.regionZ * 512.0D;
                double screenX = centerX + offsetX + regionWorldX * zoomLevel;
                double screenY = centerY + offsetY + regionWorldZ * zoomLevel;
                double regionSize = 512.0D * zoomLevel;
                if (screenX + regionSize < startX || screenX > startX + width || screenY + regionSize < startY || screenY > startY + height) {
                    continue;
                }
                drawRegionTexture(guiContext, textureId, screenX, screenY, regionSize);
            }
        }

        private void drawRegionTexture(GUIContext guiContext, int textureId, double screenX, double screenY, double regionSize) {
            RenderSystem.setShaderTexture(0, textureId);
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.enableBlend();
            Matrix4f matrix = guiContext.graphics.pose().last().pose();
            BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            float x0 = (float) screenX;
            float y0 = (float) screenY;
            float x1 = (float) (screenX + regionSize);
            float y1 = (float) (screenY + regionSize);
            buffer.addVertex(matrix, x0, y1, 0).setUv(0, 1);
            buffer.addVertex(matrix, x1, y1, 0).setUv(1, 1);
            buffer.addVertex(matrix, x1, y0, 0).setUv(1, 0);
            buffer.addVertex(matrix, x0, y0, 0).setUv(0, 0);
            BufferUploader.drawWithShader(buffer.buildOrThrow());
            RenderSystem.disableBlend();
        }

        private void forceInitialMapScan() {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null) {
                return;
            }
            int chunkX = minecraft.player.chunkPosition().x;
            int chunkZ = minecraft.player.chunkPosition().z;
            mapManager.forceScanArea(chunkX, chunkZ, Math.min(12, mapManager.getEffectiveScanRadius()));
            mapManager.forceRenderAll();
        }

        private void releaseMapConsumer() {
            if (mapConsumerReleased) {
                return;
            }
            mapConsumerReleased = true;
            mapManager.releaseConsumer();
        }

        private void renderGridOverlay(GUIContext guiContext, int startX, int startY, int width, int height, double centerX, double centerY, double chunkSize, int startChunkX, int startChunkZ) {
            int verticalLines = (int) Math.ceil(width / chunkSize) + 2;
            int horizontalLines = (int) Math.ceil(height / chunkSize) + 2;
            for (int i = 0; i <= verticalLines; i++) {
                int drawX = (int) Math.round(centerX + offsetX + (startChunkX + i) * chunkSize);
                if (drawX >= startX && drawX <= startX + width) {
                    guiContext.graphics.fill(drawX, startY, drawX + 1, startY + height, GRID_COLOR);
                }
            }
            for (int i = 0; i <= horizontalLines; i++) {
                int drawY = (int) Math.round(centerY + offsetY + (startChunkZ + i) * chunkSize);
                if (drawY >= startY && drawY <= startY + height) {
                    guiContext.graphics.fill(startX, drawY, startX + width, drawY + 1, GRID_COLOR);
                }
            }
        }

        private void renderOwnedChunkBorders(GUIContext guiContext, int startX, int startY, int width, int height, double centerX, double centerY, double chunkSize, int startChunkX, int endChunkX, int startChunkZ, int endChunkZ) {
            for (int chunkX = startChunkX; chunkX <= endChunkX; chunkX++) {
                for (int chunkZ = startChunkZ; chunkZ <= endChunkZ; chunkZ++) {
                    long chunkLong = ChunkPos.asLong(chunkX, chunkZ);
                    if (!cache.isChunkOwned(chunkLong)) {
                        continue;
                    }
                    boolean currentCityChunk = cache.isChunkInCurrentCity(chunkLong);
                    int fillColor = districtFillColor(chunkLong, currentCityChunk);
                    drawChunkFill(guiContext, startX, startY, width, height, centerX, centerY, chunkSize, chunkX, chunkZ, fillColor);
                    // 只绘制领地外边缘，不画相邻已占领区块之间的内部线。
                    drawChunkOwnershipBorder(guiContext, startX, startY, width, height, centerX, centerY, chunkSize, chunkX, chunkZ, currentCityChunk ? CURRENT_BORDER_COLOR : OTHER_BORDER_COLOR);
                }
            }
        }

        private int districtFillColor(long chunkLong, boolean currentCityChunk) {
            if (currentCityChunk) {
                for (CityCoreMapResponsePacket.DistrictEntry district : packet.districts()) {
                    if (district.chunks().stream().anyMatch(chunk -> ChunkPos.asLong(chunk.chunkX(), chunk.chunkZ()) == chunkLong)) {
                        return (district.color() & 0x00FFFFFF) | 0x55000000;
                    }
                }
            }
            return currentCityChunk ? CURRENT_CHUNK_FILL_COLOR : OTHER_CHUNK_FILL_COLOR;
        }

        private void drawChunkFill(GUIContext guiContext, int startX, int startY, int width, int height, double centerX, double centerY, double chunkSize, int chunkX, int chunkZ, int fillColor) {
            double screenX = centerX + offsetX + chunkX * chunkSize;
            double screenY = centerY + offsetY + chunkZ * chunkSize;
            int drawX = Math.max((int) Math.floor(screenX), startX);
            int drawY = Math.max((int) Math.floor(screenY), startY);
            int drawWidth = Math.min((int) Math.ceil(screenX + chunkSize), startX + width) - drawX;
            int drawHeight = Math.min((int) Math.ceil(screenY + chunkSize), startY + height) - drawY;
            if (drawWidth > 0 && drawHeight > 0) {
                guiContext.graphics.fill(drawX, drawY, drawX + drawWidth, drawY + drawHeight, fillColor);
            }
        }

        private void drawChunkOwnershipBorder(GUIContext guiContext, int startX, int startY, int width, int height, double centerX, double centerY, double chunkSize, int chunkX, int chunkZ, int borderColor) {
            double screenX = centerX + offsetX + chunkX * chunkSize;
            double screenY = centerY + offsetY + chunkZ * chunkSize;
            int drawX = Math.max((int) Math.floor(screenX), startX);
            int drawY = Math.max((int) Math.floor(screenY), startY);
            int drawWidth = Math.min((int) Math.ceil(screenX + chunkSize), startX + width) - drawX;
            int drawHeight = Math.min((int) Math.ceil(screenY + chunkSize), startY + height) - drawY;
            if (drawWidth <= 0 || drawHeight <= 0) {
                return;
            }
            int borderThickness = Math.max(1, Math.min(2, drawWidth / 4));
            if (!cache.isChunkOwned(ChunkPos.asLong(chunkX, chunkZ - 1))) {
                guiContext.graphics.fill(drawX, drawY, drawX + drawWidth, drawY + borderThickness, borderColor);
            }
            if (!cache.isChunkOwned(ChunkPos.asLong(chunkX, chunkZ + 1))) {
                guiContext.graphics.fill(drawX, drawY + drawHeight - borderThickness, drawX + drawWidth, drawY + drawHeight, borderColor);
            }
            if (!cache.isChunkOwned(ChunkPos.asLong(chunkX - 1, chunkZ))) {
                guiContext.graphics.fill(drawX, drawY, drawX + borderThickness, drawY + drawHeight, borderColor);
            }
            if (!cache.isChunkOwned(ChunkPos.asLong(chunkX + 1, chunkZ))) {
                guiContext.graphics.fill(drawX + drawWidth - borderThickness, drawY, drawX + drawWidth, drawY + drawHeight, borderColor);
            }
        }

        private void renderBatchClaimPreview(GUIContext guiContext, int startX, int startY, int width, int height, double centerX, double centerY, double chunkSize) {
            if (batchClaimChunks.isEmpty()) {
                return;
            }
            double screenX = centerX + offsetX + batchBoxMinX * chunkSize;
            double screenY = centerY + offsetY + batchBoxMinZ * chunkSize;
            double boxWidth = (batchBoxMaxX - batchBoxMinX + 1) * chunkSize;
            double boxHeight = (batchBoxMaxZ - batchBoxMinZ + 1) * chunkSize;
            drawClippedFill(guiContext, startX, startY, width, height, screenX, screenY, boxWidth, boxHeight, BATCH_CLAIM_PREVIEW_COLOR);
            int thickness = Math.max(1, (int) Math.round(Math.min(2.0D, chunkSize / 8.0D)));
            drawClippedFill(guiContext, startX, startY, width, height, screenX, screenY, boxWidth, thickness, BATCH_CLAIM_BOX_BORDER_COLOR);
            drawClippedFill(guiContext, startX, startY, width, height, screenX, screenY + boxHeight - thickness, boxWidth, thickness, BATCH_CLAIM_BOX_BORDER_COLOR);
            drawClippedFill(guiContext, startX, startY, width, height, screenX, screenY, thickness, boxHeight, BATCH_CLAIM_BOX_BORDER_COLOR);
            drawClippedFill(guiContext, startX, startY, width, height, screenX + boxWidth - thickness, screenY, thickness, boxHeight, BATCH_CLAIM_BOX_BORDER_COLOR);
        }

        private void drawClippedFill(GUIContext guiContext, int startX, int startY, int width, int height,
                                     double screenX, double screenY, double screenWidth, double screenHeight, int color) {
            int drawX = Math.max((int) Math.floor(screenX), startX);
            int drawY = Math.max((int) Math.floor(screenY), startY);
            int drawWidth = Math.min((int) Math.ceil(screenX + screenWidth), startX + width) - drawX;
            int drawHeight = Math.min((int) Math.ceil(screenY + screenHeight), startY + height) - drawY;
            if (drawWidth > 0 && drawHeight > 0) {
                guiContext.graphics.fill(drawX, drawY, drawX + drawWidth, drawY + drawHeight, color);
            }
        }

        private void renderCoreMarker(GUIContext guiContext, int startX, int startY, int width, int height, double centerX, double centerY, double chunkSize) {
            double markerScreenX = centerX + offsetX + packet.centerChunkX() * chunkSize + (packet.pos().getX() & 15) * zoomLevel;
            double markerScreenY = centerY + offsetY + packet.centerChunkZ() * chunkSize + (packet.pos().getZ() & 15) * zoomLevel;
            if (markerScreenX >= startX && markerScreenX <= startX + width && markerScreenY >= startY && markerScreenY <= startY + height) {
                guiContext.graphics.fill((int) markerScreenX - 3, (int) markerScreenY - 3, (int) markerScreenX + 3, (int) markerScreenY + 3, 0xFF0000FF);
                guiContext.graphics.fill((int) markerScreenX - 2, (int) markerScreenY - 2, (int) markerScreenX + 2, (int) markerScreenY + 2, CORE_MARKER_COLOR);
            }
        }

        private void renderHoveredChunk(GUIContext guiContext, int startX, int startY, int width, int height, double centerX, double centerY, double chunkSize) {
            if (batchClaimDragging) {
                return;
            }
            Minecraft minecraft = Minecraft.getInstance();
            double mouseX = minecraft.mouseHandler.xpos() * minecraft.getWindow().getGuiScaledWidth() / minecraft.getWindow().getScreenWidth();
            double mouseY = minecraft.mouseHandler.ypos() * minecraft.getWindow().getGuiScaledHeight() / minecraft.getWindow().getScreenHeight();
            if (isOutside(mouseX, mouseY, startX, startY, width, height)) {
                return;
            }
            int chunkX = screenToChunk(mouseX, centerX, offsetX, chunkSize);
            int chunkZ = screenToChunk(mouseY, centerY, offsetY, chunkSize);
            double screenX = centerX + offsetX + chunkX * chunkSize;
            double screenY = centerY + offsetY + chunkZ * chunkSize;
            int drawX = Math.max((int) Math.floor(screenX), startX);
            int drawY = Math.max((int) Math.floor(screenY), startY);
            int drawWidth = Math.min((int) Math.ceil(screenX + chunkSize), startX + width) - drawX;
            int drawHeight = Math.min((int) Math.ceil(screenY + chunkSize), startY + height) - drawY;
            if (drawWidth > 0 && drawHeight > 0) {
                guiContext.graphics.fill(drawX, drawY, drawX + drawWidth, drawY + drawHeight, 0x40FFFFFF);
            }
        }

        private void renderHoverBox(GUIContext guiContext, int startX, int startY, int width, int height, double centerX, double centerY, double chunkSize) {
            if (contextMenuVisible || batchClaimDragging) {
                return;
            }
            Minecraft minecraft = Minecraft.getInstance();
            double mouseX = minecraft.mouseHandler.xpos() * minecraft.getWindow().getGuiScaledWidth() / minecraft.getWindow().getScreenWidth();
            double mouseY = minecraft.mouseHandler.ypos() * minecraft.getWindow().getGuiScaledHeight() / minecraft.getWindow().getScreenHeight();
            if (isOutside(mouseX, mouseY, startX, startY, width, height)) {
                return;
            }
            int chunkX = screenToChunk(mouseX, centerX, offsetX, chunkSize);
            int chunkZ = screenToChunk(mouseY, centerY, offsetY, chunkSize);
            long chunkLong = ChunkPos.asLong(chunkX, chunkZ);
            Component ownerText = cache.isChunkInCurrentCity(chunkLong)
                    ? Component.translatable("screen.simukraft.city_core.map.current_city")
                    : cache.isChunkOwned(chunkLong) ? Component.translatable("screen.simukraft.city_core.map.other_city")
                    : Component.translatable("screen.simukraft.city_core.map.unclaimed");
            List<Component> lines = List.of(
                    Component.translatable("screen.simukraft.city_core.map.tooltip.chunk", chunkX, chunkZ),
                    ownerText
            );
            int tooltipWidth = Math.max(minecraft.font.width(lines.get(0)), minecraft.font.width(lines.get(1)));
            int tooltipHeight = lines.size() * 10;
            int tooltipX = (int) mouseX + 10;
            int tooltipY = (int) mouseY - tooltipHeight - 8;
            if (tooltipX + tooltipWidth + 8 > startX + width) {
                tooltipX = (int) mouseX - tooltipWidth - 12;
            }
            if (tooltipY < startY) {
                tooltipY = (int) mouseY + 12;
            }
            tooltipX = Math.max(startX + 4, Math.min(tooltipX, startX + width - tooltipWidth - 8));
            tooltipY = Math.max(startY + 4, Math.min(tooltipY, startY + height - tooltipHeight - 8));
            guiContext.graphics.renderComponentTooltip(minecraft.font, lines, tooltipX, tooltipY);
        }

        private void renderContextMenu(GUIContext guiContext, int startX, int startY, int width, int height) {
            if (!contextMenuVisible) {
                return;
            }
            Minecraft minecraft = Minecraft.getInstance();
            boolean hasBatch = !batchClaimChunks.isEmpty();
            Component title = hasBatch
                    ? Component.translatable("screen.simukraft.city_core.map.menu.batch_selected", batchClaimChunks.size())
                    : Component.translatable("screen.simukraft.city_core.map.menu.chunk", contextMenuChunkX, contextMenuChunkZ);
            long singleChunkLong = ChunkPos.asLong(contextMenuChunkX, contextMenuChunkZ);
            boolean singleOwned = !hasBatch && cache.isChunkOwned(singleChunkLong);
            boolean singleCurrentCity = !hasBatch && cache.isChunkInCurrentCity(singleChunkLong);
            boolean canClaim = packet.canManageCity() && (hasBatch || !singleOwned);
            boolean canAbandon = packet.canManageCity() && (hasBatch
                    ? batchClaimChunks.stream().anyMatch(cache::isChunkInCurrentCity)
                    : singleCurrentCity);
            Component claimAction = !packet.canManageCity()
                    ? Component.translatable("screen.simukraft.city_core.map.menu.no_permission")
                    : singleOwned ? Component.translatable("screen.simukraft.city_core.map.menu.claim_unavailable")
                    : Component.translatable("screen.simukraft.city_core.map.menu.claim");
            Component abandonAction = Component.translatable("screen.simukraft.city_core.map.menu.abandon");
            Component districtAction = Component.translatable("screen.simukraft.city_core.map.menu.district");
            contextMenuWidth = Math.max(Math.max(minecraft.font.width(title), minecraft.font.width(districtAction)),
                    Math.max(minecraft.font.width(claimAction), minecraft.font.width(abandonAction))) + 16;
            contextMenuHeight = 70;
            int menuX = (int) contextMenuX;
            int menuY = (int) contextMenuY;
            if (menuX + contextMenuWidth > startX + width) {
                menuX = startX + width - contextMenuWidth;
            }
            if (menuY + contextMenuHeight > startY + height) {
                menuY = startY + height - contextMenuHeight;
            }
            menuX = Math.max(startX + 2, menuX);
            menuY = Math.max(startY + 2, menuY);
            contextMenuDrawX = menuX;
            contextMenuDrawY = menuY;
            guiContext.graphics.fill(menuX, menuY, menuX + contextMenuWidth, menuY + contextMenuHeight, 0xEE202020);
            guiContext.graphics.fill(menuX, menuY, menuX + contextMenuWidth, menuY + 1, 0xFFFFFFFF);
            guiContext.graphics.fill(menuX, menuY + 19, menuX + contextMenuWidth, menuY + 20, 0x80FFFFFF);
            guiContext.graphics.fill(menuX, menuY + 35, menuX + contextMenuWidth, menuY + 36, 0x80FFFFFF);
            guiContext.graphics.fill(menuX, menuY + 51, menuX + contextMenuWidth, menuY + 52, 0x80FFFFFF);
            guiContext.graphics.drawString(minecraft.font, title, menuX + 6, menuY + 6, 0xFFFFFFFF, false);
            guiContext.graphics.drawString(minecraft.font, claimAction, menuX + 6, menuY + 24, canClaim ? 0xFFFFFF55 : 0xFFAAAAAA, false);
            guiContext.graphics.drawString(minecraft.font, districtAction, menuX + 6, menuY + 40, packet.canManageCity() && !batchClaimChunks.isEmpty() ? 0xFF55FFFF : 0xFFAAAAAA, false);
            guiContext.graphics.drawString(minecraft.font, abandonAction, menuX + 6, menuY + 56, canAbandon ? 0xFFFF5555 : 0xFFAAAAAA, false);
        }

        private boolean handleContextMenuClick(double mouseX, double mouseY) {
            if (!contextMenuVisible) {
                return false;
            }
            if (isOutside(mouseX, mouseY, contextMenuDrawX, contextMenuDrawY, contextMenuWidth, contextMenuHeight)) {
                contextMenuVisible = false;
                return true;
            }
            if (mouseY < contextMenuDrawY + 20) {
                return true;
            }
            contextMenuVisible = false;
            if (mouseY < contextMenuDrawY + 36) {
                // 认领
                if (!batchClaimChunks.isEmpty() && packet.canManageCity()) {
                    List<CityChunkBatchPurchasePacket.ChunkEntry> chunks = batchClaimChunks.stream()
                            .map(ChunkPos::new)
                            .map(chunkPos -> new CityChunkBatchPurchasePacket.ChunkEntry(chunkPos.x, chunkPos.z))
                            .toList();
                    batchClaimChunks.clear();
                    PacketDistributor.sendToServer(new CityChunkBatchPurchasePacket(packet.pos(), chunks));
                } else if (batchClaimChunks.isEmpty() && packet.canManageCity()) {
                    long chunkLong = ChunkPos.asLong(contextMenuChunkX, contextMenuChunkZ);
                    if (!cache.isChunkOwned(chunkLong)) {
                        PacketDistributor.sendToServer(new CityChunkPurchasePacket(packet.pos(), contextMenuChunkX, contextMenuChunkZ));
                    }
                }
            } else if (mouseY < contextMenuDrawY + 52) {
                if (packet.canManageCity() && !batchClaimChunks.isEmpty()) {
                    String name = "District " + packet.districts().size();
                    PacketDistributor.sendToServer(new common.cn.kafei.simukraft.network.city.DistrictActionPacket(
                            common.cn.kafei.simukraft.network.city.DistrictActionPacket.Action.CREATE,
                            packet.pos(), null, name, List.copyOf(batchClaimChunks)));
                    batchClaimChunks.clear();
                }
            } else {
                // 放弃区块
                if (packet.canManageCity()) {
                    long coreChunkLong = ChunkPos.asLong(packet.centerChunkX(), packet.centerChunkZ());
                    if (!batchClaimChunks.isEmpty()) {
                        List<Long> districtChunks = batchClaimChunks.stream()
                                .filter(this::isDistrictChunk)
                                .filter(c -> c != coreChunkLong)
                                .toList();
                        if (!districtChunks.isEmpty()) {
                            PacketDistributor.sendToServer(new common.cn.kafei.simukraft.network.city.DistrictActionPacket(
                                    common.cn.kafei.simukraft.network.city.DistrictActionPacket.Action.MOVE_TO_CITY,
                                    packet.pos(), null, "", districtChunks));
                        }
                        List<CityChunkBatchReleasePacket.ChunkEntry> releaseChunks = batchClaimChunks.stream()
                                .filter(c -> !isDistrictChunk(c))
                                .filter(cache::isChunkInCurrentCity)
                                .filter(c -> c != coreChunkLong)
                                .map(ChunkPos::new)
                                .map(cp -> new CityChunkBatchReleasePacket.ChunkEntry(cp.x, cp.z))
                                .toList();
                        batchClaimChunks.clear();
                        if (!releaseChunks.isEmpty()) {
                            PacketDistributor.sendToServer(new CityChunkBatchReleasePacket(packet.pos(), releaseChunks));
                        }
                    } else {
                        long singleChunk = ChunkPos.asLong(contextMenuChunkX, contextMenuChunkZ);
                        if (singleChunk != coreChunkLong && isDistrictChunk(singleChunk)) {
                            PacketDistributor.sendToServer(new common.cn.kafei.simukraft.network.city.DistrictActionPacket(
                                    common.cn.kafei.simukraft.network.city.DistrictActionPacket.Action.MOVE_TO_CITY,
                                    packet.pos(), null, "", List.of(singleChunk)));
                        } else if (singleChunk != coreChunkLong && cache.isChunkInCurrentCity(singleChunk)) {
                            PacketDistributor.sendToServer(new CityChunkBatchReleasePacket(packet.pos(),
                                    List.of(new CityChunkBatchReleasePacket.ChunkEntry(contextMenuChunkX, contextMenuChunkZ))));
                        }
                    }
                }
            }
            return true;
        }

        private boolean isDistrictChunk(long chunkLong) {
            return packet.districts().stream().anyMatch(district -> district.chunks().stream()
                    .anyMatch(chunk -> ChunkPos.asLong(chunk.chunkX(), chunk.chunkZ()) == chunkLong));
        }

        private void beginBatchClaimBox(double mouseX, double mouseY) {
            batchClaimDragging = true;
            double clampedX = clampToMapX(mouseX);
            double clampedY = clampToMapY(mouseY);
            batchClaimStartChunkX = screenToChunk(clampedX, mapCenterX(), offsetX, 16.0D * zoomLevel);
            batchClaimStartChunkZ = screenToChunk(clampedY, mapCenterY(), offsetY, 16.0D * zoomLevel);
            batchClaimEndChunkX = Integer.MIN_VALUE;
            batchClaimEndChunkZ = Integer.MIN_VALUE;
            updateBatchClaimBox(mouseX, mouseY);
        }

        private void updateBatchClaimBox(double mouseX, double mouseY) {
            double clampedX = clampToMapX(mouseX);
            double clampedY = clampToMapY(mouseY);
            int endChunkX = screenToChunk(clampedX, mapCenterX(), offsetX, 16.0D * zoomLevel);
            int endChunkZ = screenToChunk(clampedY, mapCenterY(), offsetY, 16.0D * zoomLevel);
            if (endChunkX == batchClaimEndChunkX && endChunkZ == batchClaimEndChunkZ) {
                return;
            }
            batchClaimEndChunkX = endChunkX;
            batchClaimEndChunkZ = endChunkZ;
            fillBatchClaimRectangle(batchClaimStartChunkX, batchClaimStartChunkZ, endChunkX, endChunkZ);
        }

        private void fillBatchClaimRectangle(int startX, int startZ, int endX, int endZ) {
            int dx = endX - startX;
            int dz = endZ - startZ;
            int boxWidth = Math.abs(dx) + 1;
            int boxHeight = Math.abs(dz) + 1;
            if ((long) boxWidth * (long) boxHeight > MAX_BATCH_CLAIM_CHUNKS) {
                double scale = Math.sqrt((double) MAX_BATCH_CLAIM_CHUNKS / ((double) boxWidth * (double) boxHeight));
                boxWidth = Math.max(1, (int) Math.floor(boxWidth * scale));
                boxHeight = Math.max(1, (int) Math.floor(boxHeight * scale));
                while ((long) boxWidth * (long) boxHeight > MAX_BATCH_CLAIM_CHUNKS) {
                    if (boxWidth >= boxHeight) {
                        boxWidth--;
                    } else {
                        boxHeight--;
                    }
                }
                endX = startX + Integer.signum(dx) * (boxWidth - 1);
                endZ = startZ + Integer.signum(dz) * (boxHeight - 1);
            }
            batchBoxMinX = Math.min(startX, endX);
            batchBoxMaxX = Math.max(startX, endX);
            batchBoxMinZ = Math.min(startZ, endZ);
            batchBoxMaxZ = Math.max(startZ, endZ);
            batchClaimChunks.clear();
            for (int chunkX = batchBoxMinX; chunkX <= batchBoxMaxX; chunkX++) {
                for (int chunkZ = batchBoxMinZ; chunkZ <= batchBoxMaxZ; chunkZ++) {
                    batchClaimChunks.add(ChunkPos.asLong(chunkX, chunkZ));
                }
            }
        }

        private void finishBatchClaim() {
            batchClaimDragging = false;
        }

        private double clampToMapX(double mouseX) {
            double mapStartX = getPositionX() + MAP_SIDE_PADDING;
            double mapWidth = getSizeWidth() - MAP_SIDE_PADDING * 2.0D;
            return Math.max(mapStartX, Math.min(mouseX, mapStartX + Math.max(0.0D, mapWidth) - 0.001D));
        }

        private double clampToMapY(double mouseY) {
            double mapStartY = getPositionY() + MAP_TOP_PADDING;
            double mapHeight = getSizeHeight() - MAP_TOP_PADDING - MAP_SIDE_PADDING;
            return Math.max(mapStartY, Math.min(mouseY, mapStartY + Math.max(0.0D, mapHeight) - 0.001D));
        }

        private int screenToChunk(double screenValue, double centerValue, double offsetValue, double chunkSize) {
            return (int) Math.floor((screenValue - centerValue - offsetValue) / chunkSize);
        }

        private boolean isMouseOutsideMap(double mouseX, double mouseY) {
            double mapStartX = getPositionX() + MAP_SIDE_PADDING;
            double mapStartY = getPositionY() + MAP_TOP_PADDING;
            double mapWidth = getSizeWidth() - MAP_SIDE_PADDING * 2.0D;
            double mapHeight = getSizeHeight() - MAP_TOP_PADDING - MAP_SIDE_PADDING;
            return isOutside(mouseX, mouseY, mapStartX, mapStartY, mapWidth, mapHeight);
        }

        private double mapCenterX() {
            return getPositionX() + MAP_SIDE_PADDING + (getSizeWidth() - MAP_SIDE_PADDING * 2.0D) / 2.0D;
        }

        private double mapCenterY() {
            return getPositionY() + MAP_TOP_PADDING + (getSizeHeight() - MAP_TOP_PADDING - MAP_SIDE_PADDING) / 2.0D;
        }

        private boolean isOutside(double mouseX, double mouseY, double x, double y, double width, double height) {
            return mouseX < x || mouseX > x + width || mouseY < y || mouseY > y + height;
        }

        private void renderModeButton(GUIContext guiContext, int mapStartX, int mapStartY, int mapWidth) {
            Minecraft minecraft = Minecraft.getInstance();
            Component label = Component.translatable(threeDView
                    ? "screen.simukraft.city_core.map.mode_2d"
                    : "screen.simukraft.city_core.map.mode_3d");
            modeButtonW = minecraft.font.width(label) + 12;
            modeButtonH = 14;
            modeButtonX = mapStartX + mapWidth - modeButtonW - 4;
            modeButtonY = mapStartY + 4;
            guiContext.graphics.fill(modeButtonX, modeButtonY, modeButtonX + modeButtonW, modeButtonY + modeButtonH, threeDView ? 0xEE1E6B3A : 0xEE202020);
            guiContext.graphics.fill(modeButtonX, modeButtonY, modeButtonX + modeButtonW, modeButtonY + 1, 0xFFFFFFFF);
            guiContext.graphics.drawString(minecraft.font, label, modeButtonX + 6, modeButtonY + 3, 0xFFFFFFFF, false);
        }

        private boolean isInsideModeButton(double mouseX, double mouseY) {
            return modeButtonW > 0 && !isOutside(mouseX, mouseY, modeButtonX, modeButtonY, modeButtonW, modeButtonH);
        }

        /** 用已扫描的高度和颜色画成立体方块。正交投影保留真实高度，深度测试处理遮挡。 */
        private void render3DMap(GUIContext guiContext, int startX, int startY, int width, int height, double centerX, double centerY) {
            guiContext.graphics.fill(startX, startY, startX + width, startY + height, 0xFF101418);
            int step = threeMesh.resolveStep(threeZoom);
            int liveCenterX = (int) Math.floor(-offsetX / zoomLevel);
            int liveCenterZ = (int) Math.floor(-offsetY / zoomLevel);
            float scale = (float) threeZoom;
            Minecraft minecraft = Minecraft.getInstance();
            long gameTime = minecraft.level == null ? 0L : minecraft.level.getGameTime();
            if (threeMesh.needsRebuild(liveCenterX, liveCenterZ, step, gameTime)) {
                threeMesh.rebuild(
                        threeDSource(step),
                        SimuMap3DMesh.snapCoord(liveCenterX, step),
                        SimuMap3DMesh.snapCoord(liveCenterZ, step),
                        step,
                        gameTime);
            }
            if (!threeMesh.isEmpty()) {
                guiContext.graphics.flush();
                drawThreeDMesh(centerX, centerY, scale, liveCenterX, liveCenterZ);
            }
            guiContext.graphics.drawString(minecraft.font, Component.translatable("screen.simukraft.city_core.map.mode_3d_hint"), startX + 6, startY + height - 12, 0xFFDDDDDD, false);
        }

        /**
         * drawThreeDMesh: 顶点已经是界面像素。投影必须用整屏范围，不能再乘缩放。
         * 投影范围如果跟着 threeZoom 变大，滚轮缩放会被抵消。
         */
        private void drawThreeDMesh(double centerX, double centerY, float scale, int liveCenterX, int liveCenterZ) {
            var window = Minecraft.getInstance().getWindow();
            float guiWidth = (float) (window.getWidth() / window.getGuiScale());
            float guiHeight = (float) (window.getHeight() / window.getGuiScale());
            Matrix4f projection = new Matrix4f().setOrtho(0.0F, guiWidth, guiHeight, 0.0F, -8000.0F, 8000.0F);
            Matrix4f model = SimuMap3DCamera.modelMatrix(
                    (float) centerX,
                    (float) centerY,
                    threeYaw,
                    scale,
                    threeMesh.baseHeight());
            RenderSystem.backupProjectionMatrix();
            Matrix4fStack modelView = RenderSystem.getModelViewStack();
            modelView.pushMatrix();
            try {
                RenderSystem.setProjectionMatrix(projection, VertexSorting.ORTHOGRAPHIC_Z);
                modelView.identity();
                RenderSystem.applyModelViewMatrix();
                RenderSystem.enableDepthTest();
                RenderSystem.depthMask(true);
                RenderSystem.clear(256, Minecraft.ON_OSX);
                RenderSystem.disableCull();
                RenderSystem.disableBlend();
                RenderSystem.setShader(GameRenderer::getPositionColorShader);
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
                threeMesh.emit(
                        buffer,
                        model,
                        threeMesh.worldXOf(threeMesh.cellCount() / 2) - liveCenterX,
                        threeMesh.worldZOf(threeMesh.cellCount() / 2) - liveCenterZ);
                BufferUploader.drawWithShader(buffer.buildOrThrow());
            } finally {
                modelView.popMatrix();
                RenderSystem.applyModelViewMatrix();
                RenderSystem.restoreProjectionMatrix();
                RenderSystem.enableCull();
                RenderSystem.disableDepthTest();
            }
        }

        private SimuMap3DMesh.Source threeDSource(int step) {
            return new SimuMap3DMesh.Source() {
                @Override
                public int height(int worldX, int worldZ) {
                    return sampleMapHeight(worldX, worldZ);
                }

                @Override
                public int terrainColor(int worldX, int worldZ) {
                    return sampleMapColor(worldX, worldZ);
                }

                @Override
                public int overlay(int worldX, int worldZ) {
                    return threeDOwnershipOverlay(worldX, worldZ, step);
                }

                @Override
                public boolean core(int worldX, int worldZ) {
                    return columnContainsCore(worldX, worldZ, step);
                }
            };
        }

        private int threeDOwnershipOverlay(int worldX, int worldZ, int step) {
            int key = ownershipKey(worldX, worldZ);
            if (key == 0) {
                return 0;
            }
            if (ownershipKey(worldX, worldZ - step) == key
                    && ownershipKey(worldX, worldZ + step) == key
                    && ownershipKey(worldX - step, worldZ) == key
                    && ownershipKey(worldX + step, worldZ) == key) {
                return 0;
            }
            return key == 1 ? THREE_D_CURRENT_BORDER : THREE_D_OTHER_BORDER;
        }

        private int ownershipKey(int worldX, int worldZ) {
            long chunkLong = ChunkPos.asLong(worldX >> 4, worldZ >> 4);
            if (!cache.isChunkOwned(chunkLong)) {
                return 0;
            }
            return cache.isChunkInCurrentCity(chunkLong) ? 1 : 2;
        }

        private boolean columnContainsCore(int worldX, int worldZ, int step) {
            int coreX = packet.pos().getX();
            int coreZ = packet.pos().getZ();
            return coreX >= worldX && coreX < worldX + step && coreZ >= worldZ && coreZ < worldZ + step;
        }
        private int sampleMapHeight(int worldX, int worldZ) {
            SimuMapRegionData data = mapColumnData(worldX, worldZ);
            if (data == null) {
                return Integer.MIN_VALUE;
            }
            int regionX = Math.floorDiv(worldX, SimuMapRegionData.SIZE);
            int regionZ = Math.floorDiv(worldZ, SimuMapRegionData.SIZE);
            short height = data.getHeight(worldX - regionX * SimuMapRegionData.SIZE, worldZ - regionZ * SimuMapRegionData.SIZE);
            return height == SimuMapRegionData.HEIGHT_UNKNOWN ? Integer.MIN_VALUE : height;
        }

        private int sampleMapColor(int worldX, int worldZ) {
            SimuMapRegionData data = mapColumnData(worldX, worldZ);
            if (data == null) {
                return 0xFF6E8B5A;
            }
            int regionX = Math.floorDiv(worldX, SimuMapRegionData.SIZE);
            int regionZ = Math.floorDiv(worldZ, SimuMapRegionData.SIZE);
            int color = data.getColor(worldX - regionX * SimuMapRegionData.SIZE, worldZ - regionZ * SimuMapRegionData.SIZE);
            return color == 0 ? 0xFF6E8B5A : color;
        }

        private SimuMapRegionData mapColumnData(int worldX, int worldZ) {
            SimuMapRegion region = mapManager.getRegion(Math.floorDiv(worldX, SimuMapRegionData.SIZE), Math.floorDiv(worldZ, SimuMapRegionData.SIZE));
            return region == null ? null : region.getData();
        }

        private void onMouseDown(com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent event) {
            if (event.button == 0 && isInsideModeButton(event.x, event.y)) {
                threeDView = !threeDView;
                threeMesh.invalidate();
                contextMenuVisible = false;
                batchClaimChunks.clear();
                event.stopPropagation();
                return;
            }
            if (threeDView && !isMouseOutsideMap(event.x, event.y)) {
                if (event.button == 0) {
                    threeYawDragStart = threeYaw;
                    event.target.startDrag(THREE_D_DRAG, null);
                    event.stopPropagation();
                    return;
                }
                if (event.button == 1) {
                    threePanOffsetX = offsetX;
                    threePanOffsetY = offsetY;
                    event.target.startDrag(THREE_D_PAN, null);
                    event.stopPropagation();
                    return;
                }
                if (event.button == 2) {
                    event.stopPropagation();
                    return;
                }
            }
            if (contextMenuVisible && event.button == 0 && handleContextMenuClick(event.x, event.y)) {
                event.stopPropagation();
                return;
            }
            if (isMouseOutsideMap(event.x, event.y)) {
                contextMenuVisible = false;
                return;
            }
            if (event.button == 0) {
                if (!batchClaimChunks.isEmpty()) {
                    batchClaimChunks.clear();
                    event.stopPropagation();
                    return;
                }
                event.target.startDrag(new Vector2f((float) offsetX, (float) offsetY), null);
                event.stopPropagation();
                return;
            }
            if (event.button == 2) {
                contextMenuVisible = false;
                batchClaimChunks.clear();
                beginBatchClaimBox(event.x, event.y);
                event.target.startDrag(BATCH_CLAIM_DRAG_MARKER, null);
                event.stopPropagation();
                return;
            }
            if (event.button == 1) {
                contextMenuChunkX = screenToChunk(event.x, mapCenterX(), offsetX, 16.0D * zoomLevel);
                contextMenuChunkZ = screenToChunk(event.y, mapCenterY(), offsetY, 16.0D * zoomLevel);
                contextMenuX = event.x;
                contextMenuY = event.y;
                contextMenuVisible = true;
                event.stopPropagation();
            }
        }

        private void onMouseUp(com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent event) {
            if (event.button == 2) {
                finishBatchClaim();
                event.stopPropagation();
            }
        }

        private void onDragEnd(com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent event) {
            finishBatchClaim();
        }

        private void onDragUpdate(com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent event) {
            contextMenuVisible = false;
            if (THREE_D_DRAG.equals(event.dragHandler.getDraggingObject())) {
                threeYaw = threeYawDragStart + (float) (event.x - event.dragStartX) * 0.012F;
                event.stopPropagation();
                return;
            }
            if (THREE_D_PAN.equals(event.dragHandler.getDraggingObject())) {
                float scale = (float) Math.max(threeZoom, 0.6D);
                SimuMap3DCamera.pan(
                        (float) (event.x - event.dragStartX),
                        (float) (event.y - event.dragStartY),
                        threeYaw,
                        scale,
                        threePanDelta);
                offsetX = threePanOffsetX + threePanDelta[0] * zoomLevel;
                offsetY = threePanOffsetY + threePanDelta[1] * zoomLevel;
                event.stopPropagation();
                return;
            }
            if (BATCH_CLAIM_DRAG_MARKER.equals(event.dragHandler.getDraggingObject())) {
                updateBatchClaimBox(event.x, event.y);
                event.stopPropagation();
                return;
            }
            if (event.dragHandler.getDraggingObject() instanceof Vector2f startOffset) {
                offsetX = startOffset.x + event.x - event.dragStartX;
                offsetY = startOffset.y + event.y - event.dragStartY;
                event.stopPropagation();
            }
        }

        private void onMouseWheel(com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent event) {
            contextMenuVisible = false;
            if (threeDView) {
                if (event.deltaY > 0) {
                    threeZoom = Math.min(threeZoom * 1.12D, 10.0D);
                } else {
                    threeZoom = Math.max(threeZoom / 1.12D, 0.55D);
                }
                event.stopPropagation();
                return;
            }
            double oldZoom = zoomLevel;
            if (event.deltaY > 0) {
                zoomLevel = Math.min(zoomLevel + ZOOM_STEP, MAX_ZOOM);
            } else {
                zoomLevel = Math.max(zoomLevel - ZOOM_STEP, MIN_ZOOM);
            }
            if (oldZoom != zoomLevel) {
                double mapStartX = getPositionX() + MAP_SIDE_PADDING;
                double mapStartY = getPositionY() + MAP_TOP_PADDING;
                double mapWidth = getSizeWidth() - MAP_SIDE_PADDING * 2.0D;
                double mapHeight = getSizeHeight() - MAP_TOP_PADDING - MAP_SIDE_PADDING;
                double centerX = mapStartX + mapWidth / 2.0D;
                double centerY = mapStartY + mapHeight / 2.0D;
                double mouseOffsetX = event.x - centerX;
                double mouseOffsetY = event.y - centerY;
                double scaleFactor = zoomLevel / oldZoom;
                offsetX = mouseOffsetX - (mouseOffsetX - offsetX) * scaleFactor;
                offsetY = mouseOffsetY - (mouseOffsetY - offsetY) * scaleFactor;
            }
            event.stopPropagation();
        }

        private void centerMapOnCityCore() {
            double chunkSize = 16.0D * zoomLevel;
            offsetX = -packet.centerChunkX() * chunkSize;
            offsetY = -packet.centerChunkZ() * chunkSize;
        }
    }

    private static final class BrowserTabView extends View {
        private final Runnable removeListener;

        private BrowserTabView(String name, Runnable removeListener) {
            super(name, IGuiTexture.EMPTY);
            this.removeListener = removeListener;
            setCanRemove(true);
        }

        @Override
        public Tab createTab() {
            Tab tab = new Tab();
            tab.setText(Component.translatable(getName()));
            tab.addChild(new Button().setOnClick(event -> {
                if (event.button == 0) {
                    onClose();
                    event.stopPropagation();
                }
            }).noText().buttonStyle(buttonStyle -> buttonStyle.baseTexture(Icons.CLOSE)
                    .hoverTexture(Icons.CLOSE.copy().setColor(ColorPattern.LIGHT_GRAY.color))
                    .pressedTexture(Icons.CLOSE.copy().setColor(ColorPattern.GRAY.color))).layout(layout -> {
                layout.heightPercent(100);
                layout.setAspectRatio(1f);
            }));
            return tab;
        }

        @Override
        protected void onClose() {
            removeListener.run();
            removeSelf();
        }
    }
}
