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
    static final int ICON_WIDTH = 26;
    static final int ICON_HEIGHT = 21;
    static final int MARGIN = 5;

    private MailHudRenderer() {
    }

    public static void render(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        // 教程界面需要透过遮罩露出 HUD 上的邮箱，其余界面打开时不画。
        if (minecraft.player == null || (minecraft.screen != null && !(minecraft.screen instanceof MailTutorialScreen))) {
            return;
        }
        drawIcon(event.getGuiGraphics(), MailClientState.hasUnread());
    }

    /**
     * 画左上角邮箱图标。教程界面在遮罩之后会再调一次，并固定画来信图标，
     * 保证聚光灯里露出的是“收到邮件”的样子，而不是被黑色遮罩压暗或被换成普通图标。
     */
    static void drawIcon(GuiGraphics graphics, boolean unread) {
        int x = MARGIN + Math.round(MailClientState.shakeOffset(System.currentTimeMillis()));
        int y = MARGIN;
        ResourceLocation icon = unread ? UNREAD_ICON : READ_ICON;
        graphics.blit(icon, x, y, 0.0F, 0.0F, ICON_WIDTH, ICON_HEIGHT, ICON_WIDTH, ICON_HEIGHT);
    }
}
