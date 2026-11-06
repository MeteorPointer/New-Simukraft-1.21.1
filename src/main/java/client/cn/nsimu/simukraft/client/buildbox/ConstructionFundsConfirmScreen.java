package client.cn.nsimu.simukraft.client.buildbox;

import client.cn.nsimu.simukraft.client.freecamera.CameraMouseLock;
import client.cn.nsimu.simukraft.client.ui.SimuKraftFlexLayout;
import client.cn.nsimu.simukraft.client.ui.SimuKraftUiTheme;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import common.cn.nsimu.simukraft.economy.CoinDenominations;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * ConstructionFundsConfirmScreen: 资金不足时用 LDLib2 对话框询问是否仍要建造。
 */
@OnlyIn(Dist.CLIENT)
public final class ConstructionFundsConfirmScreen extends ModularUIScreen {
    private final Screen preview;
    private boolean proceeded;

    private ConstructionFundsConfirmScreen(Screen preview, ModularUI ui) {
        super(ui, Component.translatable("gui.simukraft.build_box.funds_confirm.title"));
        this.preview = preview;
    }

    /**
     * open: 打开资金不足确认框。确认后执行 onConfirm，取消则回到预览。
     */
    public static void open(Screen preview, double cost, double funds, Runnable onConfirm) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        ConstructionFundsConfirmScreen[] holder = new ConstructionFundsConfirmScreen[1];
        ModularUI ui = createUi(preview, cost, funds, () -> {
            ConstructionFundsConfirmScreen screen = holder[0];
            if (screen != null) {
                screen.proceeded = true;
            }
            onConfirm.run();
        });
        ConstructionFundsConfirmScreen screen = new ConstructionFundsConfirmScreen(preview, ui);
        holder[0] = screen;
        minecraft.setScreen(screen);
    }

    private static ModularUI createUi(Screen preview, double cost, double funds, Runnable onConfirm) {
        SimuKraftFlexLayout.ScreenSize screenSize = SimuKraftFlexLayout.screenSize();
        UIElement root = SimuKraftFlexLayout.root(screenSize);
        ModularUI ui = new ModularUI(SimuKraftUiTheme.createUi(root))
                .shouldCloseOnEsc(true)
                .shouldCloseOnKeyInventory(false);
        String title = Component.translatable("gui.simukraft.build_box.funds_confirm.title").getString();
        String message = Component.translatable(
                "gui.simukraft.build_box.funds_confirm.message",
                CoinDenominations.formatYuan(cost),
                CoinDenominations.formatYuan(funds)).getString();
        Dialog dialog = Dialog.showCheckBox(title, message, confirmed -> {
            if (confirmed) {
                onConfirm.run();
            } else {
                Minecraft.getInstance().setScreen(preview);
            }
        });
        Button confirm = firstButton(dialog, "__confirm-button__");
        Button reject = firstButton(dialog, "__reject-button__");
        if (confirm != null) {
            confirm.setId("funds-confirm-yes");
            confirm.setText(Component.translatable("gui.simukraft.build_box.funds_confirm.still_build"));
        }
        if (reject != null) {
            reject.setId("funds-confirm-no");
            reject.setText(Component.translatable("gui.simukraft.build_box.funds_confirm.cancel"));
        }
        dialog.darkenBackground().show(ui);
        return ui;
    }

    private static Button firstButton(Dialog dialog, String className) {
        for (UIElement child : dialog.buttonContainer.getChildren()) {
            if (child instanceof Button button && child.hasClass(className)) {
                return button;
            }
        }
        return null;
    }

    @Override
    public void init() {
        super.init();
        CameraMouseLock.setLocked(false);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.mouseHandler.isMouseGrabbed()) {
            minecraft.mouseHandler.releaseMouse();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        super.onClose();
        if (proceeded) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Screen previewScreen = preview;
        minecraft.execute(() -> {
            if (minecraft.screen == null) {
                minecraft.setScreen(previewScreen);
            }
        });
    }
}
