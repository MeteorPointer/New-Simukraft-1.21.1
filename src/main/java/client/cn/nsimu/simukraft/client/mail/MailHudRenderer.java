package client.cn.nsimu.simukraft.client.mail;

import common.cn.nsimu.simukraft.SimuKraft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/** 在游戏 HUD 左上角绘制邮件图标及未读提示动画。 */
@OnlyIn(Dist.CLIENT)
public final class MailHudRenderer {
    private static final ResourceLocation READ_ICON = ResourceLocation.fromNamespaceAndPath(
            SimuKraft.MOD_ID, "textures/gui/mail.png");
    private static final ResourceLocation UNREAD_ICON = ResourceLocation.fromNamespaceAndPath(
            SimuKraft.MOD_ID, "textures/gui/incoming_mail.png");
    private static final int ICON_WIDTH = 26;
    private static final int ICON_HEIGHT = 21;
    private static final int MARGIN = 5;

    private MailHudRenderer() {
    }

    public static void render(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        int x = MARGIN + Math.round(MailClientState.shakeOffset(System.currentTimeMillis()));
        int y = MARGIN;
        ResourceLocation icon = MailClientState.hasUnread() ? UNREAD_ICON : READ_ICON;
        graphics.blit(icon, x, y, 0.0F, 0.0F, ICON_WIDTH, ICON_HEIGHT, ICON_WIDTH, ICON_HEIGHT);
    }
}
