package client.cn.nsimu.simukraft.client.mail;

import common.cn.nsimu.simukraft.SimuKraft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** 邮件最小占位界面，打开时邮件已由服务端确认已读。 */
public final class MailScreen extends Screen {
    private static final ResourceLocation BACKGROUND = ResourceLocation.fromNamespaceAndPath(
            SimuKraft.MOD_ID, "textures/gui/mail_background.png");
    private static final int BACKGROUND_WIDTH = 1920;
    private static final int BACKGROUND_HEIGHT = 1080;

    public MailScreen() {
        super(Component.translatable("screen.simukraft.mail.title"));
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.simukraft.mail.close"), button -> onClose())
                .bounds(width / 2 - 50, height - 38, 100, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.blit(BACKGROUND, 0, 0, 0.0F, 0.0F, width, height,
                BACKGROUND_WIDTH, BACKGROUND_HEIGHT);
        int panelWidth = Math.min(560, Math.max(240, width - 40));
        int panelHeight = Math.min(300, Math.max(150, height - 90));
        int left = (width - panelWidth) / 2;
        int top = (height - panelHeight) / 2 - 8;
        graphics.fill(left, top, left + panelWidth, top + panelHeight, 0xCC101010);
        graphics.drawCenteredString(font, title, width / 2, top + 20, 0xFFFFFF);
        Component content = MailClientState.unreadCount() > 0
                ? Component.translatable("screen.simukraft.mail.unread", MailClientState.unreadCount())
                : Component.translatable("screen.simukraft.mail.empty");
        graphics.drawCenteredString(font, content, width / 2, top + 64, 0xE0E0E0);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
