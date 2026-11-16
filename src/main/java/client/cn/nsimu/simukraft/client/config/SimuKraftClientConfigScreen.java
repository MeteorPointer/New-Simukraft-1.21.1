package client.cn.nsimu.simukraft.client.config;

import client.cn.nsimu.simukraft.client.ClientHUDOverlay;
import client.cn.nsimu.simukraft.client.ClientFontSelection;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import common.cn.nsimu.simukraft.config.ClientConfig;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;


public final class SimuKraftClientConfigScreen {
    private static final int WINDOW_WIDTH = 280;
    private static final int WINDOW_HEIGHT = 320;
    private static final int MIN_WINDOW_WIDTH = 220;
    private static final int MIN_WINDOW_HEIGHT = 260;
    private static final int HEADER_HEIGHT = 36;

    private SimuKraftClientConfigScreen() {
    }

    /**
     * create: 创建客户端配置页。
     */
    public static Screen create(Screen parent) {
        return new ModularUIScreen(SimuKraftConfigWidgets.screenUi(createUi(parent)), Component.translatable("gui.simukraft.config.client"));
    }

    /**
     * createUi: 按旧版小窗口样式组装客户端配置。
     */
    private static UIElement createUi(Screen parent) {
        UIElement window = SimuKraftConfigWidgets.window(WINDOW_WIDTH, WINDOW_HEIGHT, MIN_WINDOW_WIDTH, MIN_WINDOW_HEIGHT);
        window.addChild(SimuKraftConfigWidgets.header(Component.translatable("gui.simukraft.config.client"), HEADER_HEIGHT));
        UIElement body = SimuKraftConfigWidgets.column(12, 6);

        body.addChild(SimuKraftConfigWidgets.row(
                Component.translatable("gui.simukraft.config.client.hud_enabled"),
                SimuKraftConfigWidgets.switchControl(ClientConfig.HUD_ENABLED.get(), ClientConfig.HUD_ENABLED::set)));
        body.addChild(SimuKraftConfigWidgets.row(
                Component.translatable("gui.simukraft.config.client.glass_ui"),
                SimuKraftConfigWidgets.switchControl(ClientConfig.GLASS_UI_ENABLED.get(), ClientConfig.GLASS_UI_ENABLED::set)));
        body.addChild(SimuKraftConfigWidgets.row(
                Component.translatable("gui.simukraft.config.client.glass_color"),
                SimuKraftConfigWidgets.textField(String.format("#%06X", ClientConfig.glassColor()), SimuKraftClientConfigScreen::setGlassColor)));
        body.addChild(fontRow(parent));
        body.addChild(openHudEditorRow());
        body.addChild(openToastEditorRow());
        body.addChild(SimuKraftConfigWidgets.row(
                Component.translatable("gui.simukraft.config.client.path_debug_request"),
                SimuKraftConfigWidgets.switchControl(ClientConfig.PATH_DEBUG_REQUEST_ON_TOGGLE.get(), ClientConfig.PATH_DEBUG_REQUEST_ON_TOGGLE::set)));
        body.addChild(SimuKraftConfigWidgets.row(
                Component.translatable("gui.simukraft.config.client.rts.target_simukraft_blocks"),
                SimuKraftConfigWidgets.switchControl(ClientConfig.RTS_TARGET_SIMUKRAFT_BLOCKS.get(), ClientConfig.RTS_TARGET_SIMUKRAFT_BLOCKS::set)));
        body.addChild(SimuKraftConfigWidgets.row(
                Component.translatable("gui.simukraft.config.client.rts.target_vanilla_blocks"),
                SimuKraftConfigWidgets.switchControl(ClientConfig.RTS_TARGET_VANILLA_BLOCKS.get(), ClientConfig.RTS_TARGET_VANILLA_BLOCKS::set)));
        body.addChild(SimuKraftConfigWidgets.row(
                Component.translatable("gui.simukraft.config.client.rts.target_other_mod_blocks"),
                SimuKraftConfigWidgets.switchControl(ClientConfig.RTS_TARGET_OTHER_MOD_BLOCKS.get(), ClientConfig.RTS_TARGET_OTHER_MOD_BLOCKS::set)));
        body.addChild(SimuKraftConfigWidgets.row(
                Component.translatable("gui.simukraft.config.client.rts.move_hold_seconds"),
                SimuKraftConfigWidgets.intField(ClientConfig.RTS_MOVE_HOLD_SECONDS.get(), 1, 10, ClientConfig.RTS_MOVE_HOLD_SECONDS::set)));
        window.addChild(SimuKraftConfigWidgets.scroller(body));
        window.addChild(footer(parent));
        return SimuKraftConfigWidgets.screenRoot(window);
    }

    private static UIElement footer(Screen parent) {
        UIElement footer = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.height(28);
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.justifyContent(AlignContent.CENTER);
            layout.gapAll(6);
            layout.flexShrink(0);
        });
        footer.addChild(footerButton("gui.simukraft.config.save", SimuKraftClientConfigScreen::save));
        footer.addChild(footerButton("gui.simukraft.config.reset", () -> reset(parent)));
        footer.addChild(footerButton("gui.button.back", () -> Minecraft.getInstance().setScreen(SimuKraftConfigSelectionScreen.create(parent))));
        return footer;
    }

    /** fontRow: 提供全客户端字体循环选择，英语环境额外显示 Beautiful ES。 */
    private static UIElement fontRow(Screen parent) {
        return SimuKraftConfigWidgets.row(
                Component.translatable("gui.simukraft.config.client.font"),
                SimuKraftConfigWidgets.button(Component.translatable(ClientFontSelection.labelKey()), () -> {
                    ClientConfig.FONT.set(ClientFontSelection.nextName(ClientConfig.fontName()));
                    Minecraft.getInstance().setScreen(create(parent));
                }, true).layout(layout -> {
                    layout.width(120);
                    layout.height(24);
                    layout.flexShrink(0);
                }));
    }

    private static UIElement footerButton(String key, Runnable action) {
        return SimuKraftConfigWidgets.button(Component.translatable(key), action, true).layout(layout -> {
            layout.flex(1);
            layout.height(24);
        });
    }

    /**
     * openHudEditorRow: 提供单一按钮入口，打开旧版拖拽式 HUD 编辑器。
     */
    private static UIElement openHudEditorRow() {
        return SimuKraftConfigWidgets.row(
                Component.translatable("gui.simukraft.config.client.hud_position"),
                SimuKraftConfigWidgets.button(Component.translatable("gui.simukraft.config.open"),
                                () -> Minecraft.getInstance().setScreen(new HUDPositionEditorScreen(Minecraft.getInstance().screen)), true)
                        .layout(layout -> {
                            layout.width(96);
                            layout.height(24);
                            layout.flexShrink(0);
                        }));
    }

    /**
     * openToastEditorRow: 打开独立通知弹窗编辑器。
     */
    private static UIElement openToastEditorRow() {
        return SimuKraftConfigWidgets.row(
                Component.translatable("gui.simukraft.config.client.toast_position"),
                SimuKraftConfigWidgets.button(Component.translatable("gui.simukraft.config.open"),
                                () -> Minecraft.getInstance().setScreen(
                                        new ToastPositionEditorScreen(Minecraft.getInstance().screen)),
                                true)
                        .layout(layout -> {
                            layout.width(96);
                            layout.height(24);
                            layout.flexShrink(0);
                        }));
    }

    /**
     * setGlassColor: 只接受 #RRGGBB，输入过程中的半截文本先不写配置。
     */
    private static void setGlassColor(String value) {
        if (value != null && value.matches("#[0-9A-Fa-f]{6}")) {
            ClientConfig.GLASS_COLOR.set(value.toUpperCase(java.util.Locale.ROOT));
        }
    }

    /**
     * save: 保存客户端配置并清理 HUD 缓存。
     */
    private static void save() {
        ClientConfig.SPEC.save();
        ClientHUDOverlay.resetCache();
        Minecraft.getInstance().reloadResourcePacks();
    }

    /**
     * reset: 恢复客户端配置默认值。
     */
    private static void reset(Screen parent) {
        ClientConfig.HUD_ENABLED.set(true);
        ClientConfig.HUD_ANCHOR.set(ClientConfig.DEFAULT_HUD_ANCHOR);
        ClientConfig.HUD_POS_X.set(ClientConfig.DEFAULT_HUD_POS_X);
        ClientConfig.HUD_POS_Y.set(ClientConfig.DEFAULT_HUD_POS_Y);
        ClientConfig.HUD_MAX_WIDTH.set(ClientConfig.DEFAULT_HUD_MAX_WIDTH);
        ClientConfig.TOAST_ANCHOR.set(ClientConfig.DEFAULT_TOAST_ANCHOR);
        ClientConfig.TOAST_POS_X.set(ClientConfig.DEFAULT_TOAST_POS_X);
        ClientConfig.TOAST_POS_Y.set(ClientConfig.DEFAULT_TOAST_POS_Y);
        ClientConfig.TOAST_WIDTH.set(ClientConfig.DEFAULT_TOAST_WIDTH);
        ClientConfig.TOAST_HEIGHT.set(ClientConfig.DEFAULT_TOAST_HEIGHT);
        ClientConfig.GLASS_UI_ENABLED.set(false);
        ClientConfig.GLASS_COLOR.set(ClientConfig.DEFAULT_GLASS_COLOR);
        ClientConfig.FONT.set(ClientConfig.DEFAULT_FONT);
        ClientConfig.PATH_DEBUG_REQUEST_ON_TOGGLE.set(true);
        ClientConfig.RTS_TARGET_SIMUKRAFT_BLOCKS.set(true);
        ClientConfig.RTS_TARGET_VANILLA_BLOCKS.set(true);
        ClientConfig.RTS_TARGET_OTHER_MOD_BLOCKS.set(true);
        ClientConfig.RTS_MOVE_HOLD_SECONDS.set(ClientConfig.DEFAULT_RTS_MOVE_HOLD_SECONDS);
        save();
        Minecraft.getInstance().setScreen(create(parent));
    }
}
