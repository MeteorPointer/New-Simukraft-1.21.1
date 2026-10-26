package client.cn.nsimu.simukraft.client.auth;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.net.URI;

public final class SponsorAuthScreen extends Screen {
    private final Screen parent;
    private final boolean required;
    private Button startButton;
    private Button copyButton;
    private Button openButton;
    private Button refreshButton;
    private Button forgetButton;
    private Button closeButton;

    public SponsorAuthScreen(Screen parent) {
        this(parent, false);
    }

    private SponsorAuthScreen(Screen parent, boolean required) {
        super(Component.translatable("gui.simukraft.auth.title"));
        this.parent = parent;
        this.required = required;
    }

    public static SponsorAuthScreen required(Screen parent) {
        return new SponsorAuthScreen(parent, true);
    }

    @Override
    protected void init() {
        int center = width / 2;
        int y = height / 2 + 28;
        startButton = addRenderableWidget(Button.builder(
                Component.translatable("gui.simukraft.auth.start"),
                button -> SponsorAuthManager.startPairing(Minecraft.getInstance().getUser().getName())
        ).bounds(center - 102, y, 100, 20).build());
        refreshButton = addRenderableWidget(Button.builder(
                Component.translatable("gui.simukraft.auth.refresh"),
                button -> SponsorAuthManager.refreshNow()
        ).bounds(center + 2, y, 100, 20).build());
        copyButton = addRenderableWidget(Button.builder(
                Component.translatable("gui.simukraft.auth.copy_code"),
                button -> copyCode()
        ).bounds(center - 102, y + 24, 100, 20).build());
        openButton = addRenderableWidget(Button.builder(
                Component.translatable("gui.simukraft.auth.open_web"),
                button -> openWebsite()
        ).bounds(center + 2, y + 24, 100, 20).build());
        forgetButton = addRenderableWidget(Button.builder(
                Component.translatable("gui.simukraft.auth.forget"),
                button -> SponsorAuthManager.forgetLocalAuthorization()
        ).bounds(center - 102, y + 48, 100, 20).build());
        closeButton = addRenderableWidget(Button.builder(
                Component.translatable(required ? "gui.simukraft.auth.exit_game" : "gui.button.back"),
                button -> closeOrExit()
        ).bounds(center + 2, y + 48, 100, 20).build());
        updateButtons();
    }

    @Override
    public void tick() {
        super.tick();
        if (required && SponsorAuthManager.hasEntitlement("sponsor")) {
            onClose();
            return;
        }
        updateButtons();
    }

    private void updateButtons() {
        if (startButton == null) return;
        SponsorAuthSnapshot state = SponsorAuthManager.snapshot();
        boolean pairing = state.phase() == SponsorAuthSnapshot.Phase.PAIRING;
        startButton.active = state.phase() != SponsorAuthSnapshot.Phase.REFRESHING;
        copyButton.active = pairing && !state.userCode().isBlank();
        openButton.active = pairing && !state.verificationUri().isBlank();
        refreshButton.active = !pairing;
        forgetButton.active = state.authorized() || state.phase() == SponsorAuthSnapshot.Phase.ERROR;
        closeButton.setMessage(Component.translatable(
                required && !state.authorized() ? "gui.simukraft.auth.exit_game" : "gui.button.back"
        ));
    }

    private void closeOrExit() {
        if (required && !SponsorAuthManager.hasEntitlement("sponsor")) {
            Minecraft.getInstance().stop();
            return;
        }
        onClose();
    }

    private void copyCode() {
        String code = SponsorAuthManager.snapshot().userCode();
        if (!code.isBlank()) Minecraft.getInstance().keyboardHandler.setClipboard(code);
    }

    private void openWebsite() {
        String url = SponsorAuthManager.snapshot().verificationUri();
        if (url.isBlank()) return;
        try {
            Util.getPlatform().openUri(URI.create(url));
        } catch (RuntimeException ignored) {
            // 链接损坏时仍可复制短码手动打开官网，不能让授权页崩溃。
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        // Screen 的控件阶段可能触发/提交背景模糊。自定义文字必须在它之后绘制，
        // 否则 Iris 等渲染组合下会出现“按钮清晰、标题和状态文字发虚”。
        super.render(graphics, mouseX, mouseY, partialTick);
        SponsorAuthSnapshot state = SponsorAuthManager.snapshot();
        graphics.drawCenteredString(font, title, width / 2, 28, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.literal(state.message()), width / 2, 54, 0xCCCCCC);
        if (!state.userCode().isBlank()) {
            graphics.drawCenteredString(font, Component.literal(state.userCode()), width / 2, 82, 0x55FF55);
            graphics.drawCenteredString(
                    font,
                    Component.translatable("gui.simukraft.auth.verify_hint"),
                    width / 2,
                    100,
                    0xAAAAAA
            );
        } else if (state.authorized()) {
            graphics.drawCenteredString(
                    font,
                    Component.translatable("gui.simukraft.auth.authorized", state.tier()),
                    width / 2,
                    82,
                    0x55FF55
            );
        } else {
            graphics.drawCenteredString(
                    font,
                    Component.translatable("gui.simukraft.auth.not_authorized"),
                    width / 2,
                    82,
                    0xFFAA55
            );
        }
        if (required && !state.authorized()) {
            graphics.drawCenteredString(
                    font,
                    Component.translatable("gui.simukraft.auth.required_hint"),
                    width / 2,
                    118,
                    0xFF7777
            );
        }
    }

    @Override
    public void onClose() {
        if (required && !SponsorAuthManager.hasEntitlement("sponsor")) return;
        Minecraft.getInstance().setScreen(parent);
    }
}
