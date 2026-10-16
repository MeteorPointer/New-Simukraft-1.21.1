package client.cn.kafei.simukraft.client.mining;

import client.cn.kafei.simukraft.client.hire.NpcHireScreen;
import client.cn.kafei.simukraft.client.selection.TwoPointSelectionScreen;
import client.cn.kafei.simukraft.client.ui.SimuKraftUiTheme;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import common.cn.kafei.simukraft.mining.MiningBoxMode;
import common.cn.kafei.simukraft.mining.MiningBoxService;
import common.cn.kafei.simukraft.network.mining.MiningBoxActionPacket;
import common.cn.kafei.simukraft.network.mining.MiningBoxOpenResponsePacket;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

/** MiningBoxScreenOpener: 挖矿盒的雇佣、范围、模式和开关。 */
@OnlyIn(Dist.CLIENT)
public final class MiningBoxScreenOpener {
    private static final int GOLD = 0xFFF0C040;
    private static final int GOLD_TEXT = 0xFF3A2A08;

    private MiningBoxScreenOpener() {
    }

    public static void open(MiningBoxOpenResponsePacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.execute(() -> minecraft.setScreen(new ModularUIScreen(createUi(packet), Component.empty())));
        }
    }

    private static ModularUI createUi(MiningBoxOpenResponsePacket packet) {
        UIElement root = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.heightPercent(100);
            layout.alignItems(AlignItems.CENTER);
            layout.justifyContent(AlignContent.CENTER);
        });
        root.addChild(SimuKraftUiTheme.createShellPanel(
                Minecraft.getInstance().getWindow().getGuiScaledWidth(),
                Minecraft.getInstance().getWindow().getGuiScaledHeight()));

        UIElement panel = new UIElement().layout(layout -> {
            layout.width(220);
            layout.paddingAll(12);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.alignItems(AlignItems.STRETCH);
            layout.gapAll(8);
        });
        panel.style(style -> style.backgroundTexture(new ColorRectTexture(0xFFF7F7F7)));

        panel.addChild(title(Component.translatable("gui.simukraft.mining_box.title")));
        panel.addChild(title(Component.translatable("gui.simukraft.mining_box.miner",
                packet.hasMiner() ? packet.minerName() : Component.translatable("gui.simukraft.mining_box.none").getString())));
        panel.addChild(goldButton(Component.translatable("gui.simukraft.mining_box.hire"),
                () -> NpcHireScreen.request(packet.pos(), MiningBoxService.HIRE_SOURCE_TYPE, MiningBoxService.HIRE_ROLE),
                !packet.hasMiner(), 196));
        panel.addChild(goldButton(Component.translatable("gui.simukraft.mining_box.fire"),
                () -> send(packet, MiningBoxActionPacket.Action.FIRE), packet.hasMiner(), 196));
        panel.addChild(goldButton(rangeText(packet),
                () -> TwoPointSelectionScreen.openMining(packet.pos()), true, 196));

        UIElement lastRow = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.flexDirection(FlexDirection.ROW);
            layout.gapAll(8);
        });
        lastRow.addChild(goldButton(Component.translatable("gui.simukraft.mining_box.mode",
                Component.translatable(MiningBoxMode.fromName(packet.mode()).translationKey())),
                () -> send(packet, MiningBoxActionPacket.Action.CYCLE_MODE), true, 94));
        lastRow.addChild(goldButton(Component.translatable(packet.running()
                        ? "gui.simukraft.mining_box.pause"
                        : "gui.simukraft.mining_box.start"),
                () -> send(packet, MiningBoxActionPacket.Action.TOGGLE_RUNNING), true, 94));
        panel.addChild(lastRow);
        root.addChild(panel);
        return new ModularUI(SimuKraftUiTheme.createUi(root)).shouldCloseOnEsc(true).shouldCloseOnKeyInventory(false);
    }

    private static Label title(Component text) {
        Label label = new Label();
        label.setText(text);
        label.layout(layout -> {
            layout.widthPercent(100);
            layout.height(14);
        });
        label.textStyle(style -> style.textColor(0xFF222222).textShadow(false).textWrap(TextWrap.HOVER_ROLL)
                .textAlignHorizontal(Horizontal.LEFT).textAlignVertical(Vertical.CENTER));
        return label;
    }

    private static Button goldButton(Component text, Runnable action, boolean active, int width) {
        Button button = new Button();
        button.setText(text);
        button.setActive(active);
        button.style(style -> style.backgroundTexture(new ColorRectTexture(active ? GOLD : 0xFFB8B8B8)));
        button.textStyle(style -> style.textColor(GOLD_TEXT).textShadow(false));
        if (active) {
            button.setOnClick(event -> action.run());
        }
        button.layout(layout -> {
            layout.width(width);
            layout.height(22);
            layout.flexShrink(0);
        });
        return button;
    }

    private static Component rangeText(MiningBoxOpenResponsePacket packet) {
        if (!packet.hasArea()) {
            return Component.translatable("gui.simukraft.mining_box.range_unset");
        }
        return Component.translatable("gui.simukraft.mining_box.range_set", packet.width(), packet.depth(), packet.topY());
    }

    private static void send(MiningBoxOpenResponsePacket packet, MiningBoxActionPacket.Action action) {
        PacketDistributor.sendToServer(new MiningBoxActionPacket(packet.pos(), action));
    }
}
