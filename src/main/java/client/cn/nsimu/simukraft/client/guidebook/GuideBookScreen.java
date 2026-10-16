package client.cn.nsimu.simukraft.client.guidebook;

import common.cn.nsimu.simukraft.SimuKraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 指南书界面。封面点击后展开，页签和目录链接都指向 JSON 里的章节。
 */
@OnlyIn(Dist.CLIENT)
public final class GuideBookScreen extends Screen {
    private static final ResourceLocation COVER_ICON_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            SimuKraft.MOD_ID, "textures/gui/guide_book_cover_icon.png");
    private static final ResourceLocation TAB_CITY_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            SimuKraft.MOD_ID, "textures/gui/guide_book/tab_city.png");
    private static final ResourceLocation TAB_COMMERCE_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            SimuKraft.MOD_ID, "textures/gui/guide_book/tab_commerce.png");
    private static final ResourceLocation TAB_LOGISTICS_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            SimuKraft.MOD_ID, "textures/gui/guide_book/tab_logistics.png");
    private static final ResourceLocation TAB_RETURN_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            SimuKraft.MOD_ID, "textures/gui/guide_book/tab_return.png");
    private static final int COVER_FRONT_BORDER_COLOR = 0x081B38;
    private static final int COVER_FRONT_FILL_COLOR = 0x1B447D;
    private static final int COVER_BACK_FILL_COLOR = 0x173867;
    private static final int COVER_SPINE_COLOR = 0x0E2B55;
    private static final int COVER_DECORATION_COLOR = 0xA9D0FF;
    private static final int BOOK_WIDTH = 332;
    private static final int BOOK_HEIGHT = 214;
    private static final int CLOSED_BOOK_WIDTH = 174;
    private static final int CLOSED_BOOK_HEIGHT = 232;
    private static final int BOOK_MARGIN = 12;
    private static final int PAGE_GAP = 10;
    private static final int OPEN_TICKS = 16;
    private static final int TAB_TEXTURE_WIDTH = 16;
    private static final int TAB_TEXTURE_HEIGHT = 9;
    private static final int RETURN_TEXTURE_WIDTH = 15;
    private static final int RETURN_TEXTURE_HEIGHT = 32;
    private static final int TAB_SCALE = 3;
    private static final float RETURN_SCALE = 1.8F;
    private static final int TAB_WIDTH = TAB_TEXTURE_WIDTH * TAB_SCALE;
    private static final int TAB_HEIGHT = TAB_TEXTURE_HEIGHT * TAB_SCALE;
    private static final int TAB_SPACING = 15;
    private static final int TAB_EXPOSED_WIDTH = 18;
    private static final int TAB_BASE_Y_OFFSET = 20;
    private static final int TAB_HOVER_SHIFT_X = 5;
    private static final int TAB_SELECTED_SHIFT_X = 7;
    private static final int TAB_HOVER_SHIFT_Y = -1;
    private static final int TAB_SELECTED_SHIFT_Y = -2;
    private static final float TAB_REVEAL_START_PROGRESS = 0.80F;
    private static final int TAB_HIDDEN_OFFSET_X = TAB_EXPOSED_WIDTH + 8;
    private static final int RETURN_WIDTH = Math.round(RETURN_TEXTURE_WIDTH * RETURN_SCALE);
    private static final int RETURN_HEIGHT = Math.round(RETURN_TEXTURE_HEIGHT * RETURN_SCALE);
    private static final int RETURN_TOP_OVERLAP = 7;
    private static final int PAGE_BUTTON_WIDTH = 16;
    private static final int PAGE_BUTTON_HEIGHT = 16;
    private static final int PAGE_BUTTON_OFFSET_X = 20;
    private static final int PAGE_BUTTON_OFFSET_Y = 180;

    private final ItemStack previewStack;
    private float alpha;
    private int openTicks;
    private BookState bookState = BookState.COVER;
    private GuideBookPage currentChapter;
    private String currentChapterId = "directory";
    private int currentPageIndex;
    private Button prevPageButton;
    private Button nextPageButton;
    private final List<LinkClickArea> linkClickAreas = new ArrayList<>();

    public GuideBookScreen(ItemStack previewStack) {
        super(Component.literal(GuideBookLang.get("guidebook.item.name")));
        this.previewStack = previewStack == null ? ItemStack.EMPTY : previewStack;
        this.currentChapter = GuideBookLoader.getIndexPage();
        if (this.currentChapter != null) {
            this.currentChapterId = this.currentChapter.getId();
        }
    }

    @Override
    protected void init() {
        super.init();
        if (this.minecraft != null && !GuideBookLang.isLoaded()) {
            GuideBookLang.load(this.minecraft.getResourceManager());
        }
        if (this.minecraft != null && !GuideBookLoader.isLoaded()) {
            GuideBookLoader.loadIfEmpty(this.minecraft.getResourceManager());
        }
        if (this.currentChapter == null) {
            this.currentChapter = GuideBookLoader.getIndexPage();
            if (this.currentChapter != null) {
                this.currentChapterId = this.currentChapter.getId();
            }
        }
        GuideBookImageCache.setOnImageLoadedCallback(url -> {
            if (this.minecraft != null) {
                this.minecraft.execute(this::rebuildWidgets);
            }
        });

        int bookX = this.width / 2 - BOOK_WIDTH / 2;
        int bookY = this.height / 2 - BOOK_HEIGHT / 2;
        this.prevPageButton = Button.builder(Component.literal(GuideBookLang.get("guidebook.button.prev")), button -> turnPage(-1))
                .pos(bookX + PAGE_BUTTON_OFFSET_X, bookY + PAGE_BUTTON_OFFSET_Y)
                .size(PAGE_BUTTON_WIDTH, PAGE_BUTTON_HEIGHT)
                .build();
        this.nextPageButton = Button.builder(Component.literal(GuideBookLang.get("guidebook.button.next")), button -> turnPage(1))
                .pos(bookX + BOOK_WIDTH - PAGE_BUTTON_OFFSET_X - PAGE_BUTTON_WIDTH, bookY + PAGE_BUTTON_OFFSET_Y)
                .size(PAGE_BUTTON_WIDTH, PAGE_BUTTON_HEIGHT)
                .build();
        this.addRenderableWidget(this.prevPageButton);
        this.addRenderableWidget(this.nextPageButton);
        updatePageButtons();
    }

    @Override
    public void onClose() {
        GuideBookImageCache.setOnImageLoadedCallback(null);
        super.onClose();
    }

    @Override
    public void tick() {
        super.tick();
        if (this.bookState == BookState.OPENING && this.openTicks < OPEN_TICKS) {
            this.openTicks++;
            if (this.openTicks >= OPEN_TICKS) {
                this.bookState = BookState.OPENED;
                updatePageButtons();
            }
        }
    }

    /** 书页自己画底，不使用 1.21 菜单打开时的模糊层。 */
    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(@Nonnull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.alpha = Math.min(1.0F, this.alpha + 0.05F);
        guiGraphics.fill(0, 0, this.width, this.height, 0xF0101010);
        if (this.bookState == BookState.COVER) {
            renderClosedCover(guiGraphics);
        } else {
            float openProgress = this.bookState == BookState.OPENED
                    ? 1.0F
                    : Mth.clamp((this.openTicks + partialTick) / OPEN_TICKS, 0.0F, 1.0F);
            float easedProgress = 1.0F - (1.0F - openProgress) * (1.0F - openProgress);
            renderBook(guiGraphics, easedProgress, mouseX, mouseY);
            renderBookText(guiGraphics, easedProgress, mouseX, mouseY);
        }
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        if (this.bookState == BookState.OPENED && this.currentChapter != null && !this.currentChapter.isDirectory()) {
            int[] pageInfo = calculateGlobalPageNumber();
            int centerX = this.width / 2;
            int pageNumberY = this.height / 2 - BOOK_HEIGHT / 2 + BOOK_HEIGHT - 20;
            guiGraphics.drawCenteredString(this.font, pageInfo[0] + " / " + pageInfo[1], centerX, pageNumberY, 0x5A4A3A);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.bookState == BookState.COVER) {
            beginOpenAnimation();
            return true;
        }
        int bookX = this.width / 2 - BOOK_WIDTH / 2;
        int bookY = this.height / 2 - BOOK_HEIGHT / 2;
        if (this.currentChapter != null && !this.currentChapter.isDirectory()
                && isInside(mouseX, mouseY, getReturnButtonX(bookX), getReturnButtonY(bookY), RETURN_WIDTH, RETURN_HEIGHT)) {
            navigateToChapter("directory");
            return true;
        }
        int tabX = getTabX(bookX);
        int baseY = bookY + TAB_BASE_Y_OFFSET;
        for (GuideBookPage page : GuideBookLoader.getChildPages("directory")) {
            if (isInside(mouseX, mouseY, tabX, getTabY(baseY, page.getTabIndex()), TAB_WIDTH, TAB_HEIGHT)) {
                navigateToChapter(page.getId());
                return true;
            }
        }
        if (this.currentChapter != null && this.currentChapter.isDirectory()) {
            for (LinkClickArea link : this.linkClickAreas) {
                if (link.contains((int) mouseX, (int) mouseY)) {
                    navigateToChapter(link.target());
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 && this.bookState == BookState.OPENED && this.currentChapter != null && !this.currentChapter.isDirectory()) {
            navigateToChapter("directory");
            return true;
        }
        if (keyCode == 263) {
            turnPage(-1);
            return true;
        }
        if (keyCode == 262) {
            turnPage(1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void turnPage(int direction) {
        if (this.currentChapter == null) {
            return;
        }
        int newIndex = this.currentPageIndex + direction;
        if (newIndex >= 0 && newIndex < this.currentChapter.getTotalPages()) {
            this.currentPageIndex = newIndex;
            updatePageButtons();
            return;
        }
        if (direction > 0) {
            navigateToAdjacentChapter(true);
        } else {
            navigateToAdjacentChapter(false);
        }
    }

    private void navigateToAdjacentChapter(boolean next) {
        GuideBookPage adjacent = null;
        int currentTabIndex = this.currentChapter.getTabIndex();
        for (GuideBookPage page : GuideBookLoader.getChildPages("directory")) {
            boolean candidate = next ? page.getTabIndex() > currentTabIndex : page.getTabIndex() < currentTabIndex;
            if (!candidate) {
                continue;
            }
            if (adjacent == null
                    || (next && page.getTabIndex() < adjacent.getTabIndex())
                    || (!next && page.getTabIndex() > adjacent.getTabIndex())) {
                adjacent = page;
            }
        }
        if (adjacent == null) {
            return;
        }
        this.currentChapter = adjacent;
        this.currentChapterId = adjacent.getId();
        this.currentPageIndex = next ? 0 : adjacent.getTotalPages() - 1;
        updatePageButtons();
    }

    private void navigateToChapter(String chapterId) {
        GuideBookLoader.getPage(chapterId).ifPresent(chapter -> {
            this.currentChapter = chapter;
            this.currentChapterId = chapterId;
            this.currentPageIndex = 0;
            updatePageButtons();
        });
    }

    private void updatePageButtons() {
        if (this.prevPageButton == null || this.nextPageButton == null || this.currentChapter == null) {
            return;
        }
        List<GuideBookPage> childPages = GuideBookLoader.getChildPages("directory");
        int currentTabIndex = this.currentChapter.getTabIndex();
        boolean hasPrevChapter = childPages.stream().anyMatch(page -> page.getTabIndex() < currentTabIndex);
        boolean hasNextChapter = childPages.stream().anyMatch(page -> page.getTabIndex() > currentTabIndex);
        boolean opened = this.bookState == BookState.OPENED;
        this.prevPageButton.visible = opened && (this.currentPageIndex > 0 || hasPrevChapter);
        this.nextPageButton.visible = opened && (this.currentPageIndex < this.currentChapter.getTotalPages() - 1 || hasNextChapter);
    }

    private void beginOpenAnimation() {
        if (this.bookState != BookState.COVER) {
            return;
        }
        if (this.currentChapter == null) {
            this.currentChapter = GuideBookLoader.getIndexPage();
            if (this.currentChapter != null) {
                this.currentChapterId = this.currentChapter.getId();
            }
        }
        this.bookState = BookState.OPENING;
        this.openTicks = 0;
    }

    private int[] calculateGlobalPageNumber() {
        List<GuideBookPage> childPages = GuideBookLoader.getChildPages("directory");
        int total = 0;
        int current = 0;
        int currentTabIndex = this.currentChapter.getTabIndex();
        for (GuideBookPage page : childPages) {
            total += page.getTotalPages();
            if (page.getTabIndex() < currentTabIndex) {
                current += page.getTotalPages();
            } else if (page.getTabIndex() == currentTabIndex) {
                current += this.currentPageIndex + 1;
            }
        }
        return new int[]{current, total};
    }

    private void renderClosedCover(GuiGraphics guiGraphics) {
        int bookX = this.width / 2 - CLOSED_BOOK_WIDTH / 2;
        int bookY = this.height / 2 - CLOSED_BOOK_HEIGHT / 2;
        int bookRight = bookX + CLOSED_BOOK_WIDTH;
        int bookBottom = bookY + CLOSED_BOOK_HEIGHT;
        guiGraphics.fill(bookX + 8, bookY + 10, bookRight + 10, bookBottom + 12, withAlpha(0x000000, 0.34F * this.alpha));
        drawPanel(guiGraphics, bookX, bookY, CLOSED_BOOK_WIDTH, CLOSED_BOOK_HEIGHT,
                withAlpha(COVER_FRONT_BORDER_COLOR, this.alpha), withAlpha(COVER_FRONT_FILL_COLOR, this.alpha));
        guiGraphics.fill(bookX + 6, bookY + 4, bookX + 24, bookBottom - 4, withAlpha(COVER_SPINE_COLOR, this.alpha));
        guiGraphics.fill(bookX + 11, bookY + 12, bookX + 13, bookBottom - 12, withAlpha(COVER_DECORATION_COLOR, 0.85F * this.alpha));
        guiGraphics.fill(bookX + 26, bookY + 18, bookRight - 20, bookY + 20, withAlpha(COVER_DECORATION_COLOR, 0.70F * this.alpha));
        guiGraphics.fill(bookX + 26, bookBottom - 24, bookRight - 20, bookBottom - 22, withAlpha(COVER_DECORATION_COLOR, 0.70F * this.alpha));
        renderCoverPreview(guiGraphics, bookX + 22, bookY + 10, CLOSED_BOOK_WIDTH - 34, CLOSED_BOOK_HEIGHT - 20, this.alpha);
    }

    private void renderCoverPreview(GuiGraphics guiGraphics, int x, int y, int width, int height, float progress) {
        int centerX = x + width / 2;
        int iconSize = Math.max(20, Math.min(42, width - 24));
        guiGraphics.fill(x + 10, y + 66, x + width - 10, y + 67, withAlpha(COVER_DECORATION_COLOR, progress));
        guiGraphics.fill(x + 10, y + height - 22, x + width - 10, y + height - 21, withAlpha(COVER_DECORATION_COLOR, progress));
        blitScaled(guiGraphics, COVER_ICON_TEXTURE, centerX - iconSize / 2, y + 18, iconSize, iconSize);
        drawCoverTitleLine(guiGraphics, Component.literal(GuideBookLang.get("guidebook.cover.title")), centerX, y + 74, withAlpha(0xF7D44A, progress), 0.90F);
        drawCoverTitleLine(guiGraphics, Component.literal(GuideBookLang.get("guidebook.cover.title_en")), centerX, y + 89, withAlpha(0xFFE48A, progress), 0.82F);
        drawCoverTitleLine(guiGraphics, Component.literal(GuideBookLang.get("guidebook.cover.subtitle")), centerX, y + 102, withAlpha(0xFFE48A, progress), 0.76F);
        if (!this.previewStack.isEmpty()) {
            guiGraphics.renderItem(this.previewStack, x + 10, y + height - 20);
        }
    }

    private void renderBook(GuiGraphics guiGraphics, float progress, int mouseX, int mouseY) {
        int currentWidth = Math.round(Mth.lerp(progress, BOOK_WIDTH * 0.68F, BOOK_WIDTH));
        int currentHeight = Math.round(Mth.lerp(progress, BOOK_HEIGHT * 0.82F, BOOK_HEIGHT));
        int bookX = this.width / 2 - currentWidth / 2;
        int bookY = this.height / 2 - currentHeight / 2;
        int bookRight = bookX + currentWidth;
        int bookBottom = bookY + currentHeight;
        int centerX = this.width / 2;
        guiGraphics.fill(bookX + 6, bookY + 8, bookRight + 8, bookBottom + 10, withAlpha(0x000000, 0.35F * progress));
        drawPanel(guiGraphics, bookX + 8, bookY + 6, currentWidth - 16, currentHeight - 12,
                withAlpha(0xA09274, 0.45F * progress), withAlpha(0xE7DCC3, 0.28F * progress));

        int innerY = bookY + BOOK_MARGIN;
        int innerWidth = currentWidth - BOOK_MARGIN * 2;
        int innerHeight = currentHeight - BOOK_MARGIN * 2;
        int centerGapLeft = centerX - PAGE_GAP / 2;
        int centerGapRight = centerX + PAGE_GAP / 2;
        int pageWidth = Math.max(10, (innerWidth - PAGE_GAP) / 2);
        int visiblePageWidth = Math.max(4, Math.round(pageWidth * progress));
        float coverProgress = Mth.clamp(progress / 0.92F, 0.0F, 1.0F);
        int coverWidth = Math.max(16, Math.round(Mth.lerp(coverProgress, pageWidth + 18.0F, 18.0F)));
        int leftCoverX = Math.round(Mth.lerp(coverProgress, centerGapLeft - coverWidth, bookX - 4));
        int rightCoverX = Math.round(Mth.lerp(coverProgress, centerGapRight, bookRight - coverWidth + 4));
        drawPanel(guiGraphics, leftCoverX, innerY - 4, coverWidth, innerHeight + 8,
                withAlpha(COVER_FRONT_BORDER_COLOR, progress), withAlpha(COVER_BACK_FILL_COLOR, progress));
        guiGraphics.fill(leftCoverX + 6, innerY + 14, leftCoverX + coverWidth - 6, innerY + 16, withAlpha(COVER_DECORATION_COLOR, 0.42F * progress));
        drawPanel(guiGraphics, rightCoverX, innerY - 4, coverWidth, innerHeight + 8,
                withAlpha(COVER_FRONT_BORDER_COLOR, progress), withAlpha(COVER_FRONT_FILL_COLOR, progress));
        guiGraphics.fill(rightCoverX + 8, innerY + 10, rightCoverX + coverWidth - 8, innerY + 12, withAlpha(COVER_DECORATION_COLOR, 0.75F * progress));

        float tabRevealProgress = getTabRevealProgress(progress);
        if (tabRevealProgress > 0.0F) {
            int openedBookX = this.width / 2 - BOOK_WIDTH / 2;
            int openedBookY = this.height / 2 - BOOK_HEIGHT / 2;
            renderTabs(guiGraphics, openedBookX, openedBookY, mouseX, mouseY, tabRevealProgress);
            if (this.currentChapter != null && !this.currentChapter.isDirectory()) {
                renderReturnButton(guiGraphics, openedBookX, openedBookY, mouseX, mouseY);
            }
        }
        guiGraphics.fill(centerX - 3, innerY - 2, centerX + 3, innerY + innerHeight + 2, withAlpha(COVER_SPINE_COLOR, progress));
        guiGraphics.fill(centerX - 1, innerY - 2, centerX + 1, innerY + innerHeight + 2, withAlpha(COVER_DECORATION_COLOR, progress));
        drawPanel(guiGraphics, centerGapLeft - visiblePageWidth, innerY, visiblePageWidth, innerHeight,
                withAlpha(0xC2B79B, progress), withAlpha(0xF4EBD5, progress));
        drawPanel(guiGraphics, centerGapRight, innerY, visiblePageWidth, innerHeight,
                withAlpha(0xC2B79B, progress), withAlpha(0xF7EFD9, progress));
        for (int i = 0; i < 7; i++) {
            int lineY = innerY + 24 + i * 20;
            int lineColor = withAlpha(0xD5C7A8, 0.55F * progress);
            guiGraphics.fill(centerGapLeft - visiblePageWidth + 12, lineY, centerGapLeft - 12, lineY + 1, lineColor);
            guiGraphics.fill(centerGapRight + 12, lineY, centerGapRight + visiblePageWidth - 12, lineY + 1, lineColor);
        }
    }

    private void renderBookText(GuiGraphics guiGraphics, float progress, int mouseX, int mouseY) {
        if (progress < 0.45F || this.currentChapter == null) {
            return;
        }
        int centerX = this.width / 2;
        int bookX = centerX - BOOK_WIDTH / 2;
        int bookY = this.height / 2 - BOOK_HEIGHT / 2;
        int leftPageX = bookX + BOOK_MARGIN + 12;
        int rightPageX = centerX + PAGE_GAP / 2 + 12;
        int pageY = bookY + BOOK_MARGIN + 14;
        int textWidth = (BOOK_WIDTH - BOOK_MARGIN * 2 - PAGE_GAP) / 2 - 24;
        GuideBookPage.BookPage page = this.currentChapter.getPage(this.currentPageIndex);
        this.linkClickAreas.clear();
        guiGraphics.drawString(this.font, page.getLeftTitle(), leftPageX, pageY, 0xFF23406D, false);
        guiGraphics.drawString(this.font, page.getRightTitle(), rightPageX, pageY, 0xFF23406D, false);
        int leftY = pageY + 20;
        for (GuideBookPage.PageElement element : page.getLeftElements()) {
            leftY = renderElement(guiGraphics, element, leftPageX, leftY, textWidth);
        }
        int rightY = pageY + 20;
        for (GuideBookPage.PageElement element : page.getRightElements()) {
            rightY = renderElement(guiGraphics, element, rightPageX, rightY, textWidth);
        }
        Component tooltip = hoveredBookmarkTooltip(bookX, bookY, mouseX, mouseY);
        if (tooltip != null) {
            guiGraphics.renderTooltip(this.font, tooltip, mouseX, mouseY);
        }
    }

    private int renderElement(GuiGraphics guiGraphics, GuideBookPage.PageElement element, int x, int y, int textWidth) {
        if (element.isImage()) {
            return renderImage(guiGraphics, element, x, y, textWidth);
        }
        if (element.isItem() || element.hasItem()) {
            ItemStack stack = element.getItemStack();
            if (!stack.isEmpty()) {
                guiGraphics.renderItem(stack, x, y);
                guiGraphics.renderItemDecorations(this.font, stack, x, y);
                return y + 20 + element.getSpacing();
            }
        }
        if (element.isLink()) {
            return renderLink(guiGraphics, element, x, y);
        }
        int color = element.isHint() ? 0xFF5A7298 : 0xFF3E3427;
        return drawWrappedText(guiGraphics, element.getContent(), x, y, textWidth, color, element.getSpacing());
    }

    private int renderImage(GuiGraphics guiGraphics, GuideBookPage.PageElement element, int x, int y, int maxWidth) {
        String imageUrl = element.getImageUrl();
        if (imageUrl == null || imageUrl.isEmpty()) {
            return y + element.getSpacing();
        }
        GuideBookImageCache.ImageEntry entry = GuideBookImageCache.getOrLoad(imageUrl);
        int drawWidth = element.getImageWidth();
        int drawHeight = element.getImageHeight();
        if (drawWidth > maxWidth) {
            float scale = (float) maxWidth / drawWidth;
            drawWidth = maxWidth;
            drawHeight = Math.round(drawHeight * scale);
        }
        if (entry.loaded && entry.texture != null) {
            blitScaled(guiGraphics, entry.texture, x, y, drawWidth, drawHeight);
        } else {
            guiGraphics.fill(x, y, x + drawWidth, y + drawHeight, 0xFFCCCCCC);
            String loadingText = entry.loaded ? "[图片加载失败]" : "[图片加载中...]";
            guiGraphics.drawString(this.font, loadingText,
                    x + (drawWidth - this.font.width(loadingText)) / 2,
                    y + (drawHeight - this.font.lineHeight) / 2,
                    0xFF666666, false);
        }
        return y + drawHeight + element.getSpacing();
    }

    private int renderLink(GuiGraphics guiGraphics, GuideBookPage.PageElement element, int x, int y) {
        Component text = element.getContent();
        int width = this.font.width(text);
        int height = this.font.lineHeight;
        guiGraphics.drawString(this.font, text, x, y, 0xFF0066CC, false);
        guiGraphics.fill(x, y + height, x + width, y + height + 1, 0xFF0066CC);
        if (element.getTarget() != null && !element.getTarget().isEmpty()) {
            this.linkClickAreas.add(new LinkClickArea(x, y, width, height, element.getTarget()));
        }
        return y + height + element.getSpacing();
    }

    private void renderTabs(GuiGraphics guiGraphics, int bookX, int bookY, int mouseX, int mouseY, float revealProgress) {
        int tabX = getTabX(bookX);
        int baseY = bookY + TAB_BASE_Y_OFFSET;
        for (GuideBookPage page : GuideBookLoader.getChildPages("directory")) {
            if (!page.getId().equals(this.currentChapterId)) {
                renderTab(guiGraphics, tabX, getTabY(baseY, page.getTabIndex()), page, mouseX, mouseY, revealProgress);
            }
        }
        if (this.currentChapter != null && !this.currentChapter.isDirectory()) {
            renderTab(guiGraphics, tabX, getTabY(baseY, this.currentChapter.getTabIndex()), this.currentChapter, mouseX, mouseY, revealProgress);
        }
    }

    private void renderTab(GuiGraphics guiGraphics, int x, int y, GuideBookPage page, int mouseX, int mouseY, float revealProgress) {
        boolean hovered = isInside(mouseX, mouseY, x, y, TAB_WIDTH, TAB_HEIGHT);
        boolean selected = page.getId().equals(this.currentChapterId);
        int drawX = x + Math.round(Mth.lerp(revealProgress, -TAB_HIDDEN_OFFSET_X, 0.0F))
                + (selected ? TAB_SELECTED_SHIFT_X : hovered ? TAB_HOVER_SHIFT_X : 0);
        int drawY = y + (selected ? TAB_SELECTED_SHIFT_Y : hovered ? TAB_HOVER_SHIFT_Y : 0);
        guiGraphics.fill(drawX + 3, drawY + 3, drawX + TAB_WIDTH + 3, drawY + TAB_HEIGHT + 3, withAlpha(0x000000, 0.20F));
        blitScaled(guiGraphics, tabTexture(page), drawX, drawY, TAB_WIDTH, TAB_HEIGHT);
    }

    private void renderReturnButton(GuiGraphics guiGraphics, int bookX, int bookY, int mouseX, int mouseY) {
        int x = getReturnButtonX(bookX);
        int y = getReturnButtonY(bookY);
        int drawY = y + (isInside(mouseX, mouseY, x, y, RETURN_WIDTH, RETURN_HEIGHT) ? 1 : 0);
        guiGraphics.fill(x + 3, drawY + 3, x + RETURN_WIDTH + 3, drawY + RETURN_HEIGHT + 3, withAlpha(0x000000, 0.22F));
        blitScaled(guiGraphics, TAB_RETURN_TEXTURE, x, drawY, RETURN_WIDTH, RETURN_HEIGHT);
    }

    @Nullable
    private Component hoveredBookmarkTooltip(int bookX, int bookY, int mouseX, int mouseY) {
        int tabX = getTabX(bookX);
        int baseY = bookY + TAB_BASE_Y_OFFSET;
        for (GuideBookPage page : GuideBookLoader.getChildPages("directory")) {
            if (isInside(mouseX, mouseY, tabX, getTabY(baseY, page.getTabIndex()), TAB_WIDTH, TAB_HEIGHT)) {
                return Component.literal(GuideBookLang.get("guidebook.tooltip." + page.getId()));
            }
        }
        if (this.currentChapter != null && !this.currentChapter.isDirectory()
                && isInside(mouseX, mouseY, getReturnButtonX(bookX), getReturnButtonY(bookY), RETURN_WIDTH, RETURN_HEIGHT)) {
            return Component.literal(GuideBookLang.get("guidebook.tooltip.return"));
        }
        return null;
    }

    private ResourceLocation tabTexture(GuideBookPage page) {
        String icon = page.getTabIcon();
        if (icon != null && !icon.isEmpty()) {
            return ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "textures/gui/guide_book/" + icon + ".png");
        }
        return switch (page.getId()) {
            case "items" -> TAB_COMMERCE_TEXTURE;
            case "start" -> TAB_LOGISTICS_TEXTURE;
            default -> TAB_CITY_TEXTURE;
        };
    }

    private int getTabX(int bookX) {
        return bookX + BOOK_WIDTH - BOOK_MARGIN - 1 - TAB_EXPOSED_WIDTH;
    }

    private int getTabY(int baseY, int index) {
        return baseY + index * (TAB_HEIGHT + TAB_SPACING);
    }

    private int getReturnButtonX(int bookX) {
        int innerWidth = BOOK_WIDTH - BOOK_MARGIN * 2;
        int pageWidth = (innerWidth - PAGE_GAP) / 2;
        return bookX + BOOK_MARGIN + pageWidth + PAGE_GAP + 10;
    }

    private int getReturnButtonY(int bookY) {
        return bookY + BOOK_MARGIN - RETURN_TOP_OVERLAP;
    }

    private float getTabRevealProgress(float openProgress) {
        float normalized = Mth.clamp((openProgress - TAB_REVEAL_START_PROGRESS) / (1.0F - TAB_REVEAL_START_PROGRESS), 0.0F, 1.0F);
        return normalized * normalized * (3.0F - 2.0F * normalized);
    }

    private void drawCoverTitleLine(GuiGraphics guiGraphics, Component text, int centerX, int y, int color, float scale) {
        int textWidth = this.font.width(text);
        int drawX = Math.round(centerX - textWidth * scale / 2.0F);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(drawX, y, 0);
        guiGraphics.pose().scale(scale, scale, 1.0F);
        guiGraphics.drawString(this.font, text, 0, 0, color, false);
        guiGraphics.pose().popPose();
    }

    private int drawWrappedText(GuiGraphics guiGraphics, Component text, int x, int y, int maxWidth, int color, int spacing) {
        for (FormattedCharSequence line : this.font.split(text, maxWidth)) {
            guiGraphics.drawString(this.font, line, x, y, color, false);
            y += this.font.lineHeight;
        }
        return y + spacing;
    }

    private void blitScaled(GuiGraphics guiGraphics, ResourceLocation texture, int x, int y, int width, int height) {
        guiGraphics.blit(texture, x, y, width, height, 0.0F, 0.0F, width, height, width, height);
    }

    private void drawPanel(GuiGraphics guiGraphics, int x, int y, int width, int height, int borderColor, int fillColor) {
        guiGraphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, fillColor);
        guiGraphics.fill(x, y, x + width, y + 1, borderColor);
        guiGraphics.fill(x, y + height - 1, x + width, y + height, borderColor);
        guiGraphics.fill(x, y, x + 1, y + height, borderColor);
        guiGraphics.fill(x + width - 1, y, x + width, y + height, borderColor);
    }

    private boolean isInside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private int withAlpha(int color, float alpha) {
        int alphaByte = Mth.clamp((int) (alpha * 255.0F), 0, 255);
        return (alphaByte << 24) | (color & 0xFFFFFF);
    }

    private record LinkClickArea(int x, int y, int width, int height, String target) {
        private boolean contains(int mouseX, int mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }
    }

    private enum BookState {
        COVER,
        OPENING,
        OPENED
    }
}
