package client.cn.nsimu.simukraft.client.guidebook;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import common.cn.nsimu.simukraft.guidebook.markdown.GuideBookMarkdownDocument;
import common.cn.nsimu.simukraft.guidebook.markdown.GuideBookMarkdownParser;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 指南书一章。一章可以有多页，每页分左右两栏。
 */
public final class GuideBookPage {
    private final String id;
    private final String parentId;
    private final String tabIcon;
    private final int tabIndex;
    private final List<BookPage> pages;

    public GuideBookPage(String id, List<BookPage> pages, @Nullable String parentId, @Nullable String tabIcon, int tabIndex) {
        this.id = id;
        this.pages = List.copyOf(pages);
        this.parentId = parentId;
        this.tabIcon = tabIcon;
        this.tabIndex = tabIndex;
    }

    public static GuideBookPage fromJson(String id, JsonObject json) {
        String parentId = json.has("parent") ? GsonHelper.getAsString(json, "parent") : null;
        String tabIcon = json.has("tab_icon") ? GsonHelper.getAsString(json, "tab_icon") : null;
        int tabIndex = GsonHelper.getAsInt(json, "tab_index", -1);
        List<BookPage> pages = new ArrayList<>();
        if (json.has("pages") && json.get("pages").isJsonArray()) {
            JsonArray pagesArray = json.getAsJsonArray("pages");
            for (int i = 0; i < pagesArray.size(); i++) {
                pages.add(BookPage.fromJson(pagesArray.get(i).getAsJsonObject()));
            }
        } else {
            pages.add(BookPage.fromJson(json));
        }
        if (pages.isEmpty()) {
            pages.add(BookPage.EMPTY);
        }
        return new GuideBookPage(id, pages, parentId, tabIcon, tabIndex);
    }

    public static GuideBookPage fromMarkdown(String id, String source) {
        return fromMarkdown(id, GuideBookMarkdownParser.parse(source));
    }

    public static GuideBookPage fromMarkdown(String id, GuideBookMarkdownDocument document) {
        List<BookPage> pages = new ArrayList<>();
        for (GuideBookMarkdownDocument.Spread spread : document.spreads()) {
            pages.add(new BookPage(
                    spread.leftTitle(),
                    spread.rightTitle(),
                    mapMarkdown(spread.left()),
                    mapMarkdown(spread.right())));
        }
        if (pages.isEmpty()) {
            pages.add(BookPage.EMPTY);
        }
        return new GuideBookPage(id, pages, document.parentId(), document.tabIcon(), document.tabIndex());
    }

    private static List<PageElement> mapMarkdown(List<GuideBookMarkdownDocument.Block> blocks) {
        List<PageElement> elements = new ArrayList<>();
        for (GuideBookMarkdownDocument.Block block : blocks) {
            elements.add(new PageElement(
                    block.kind().wireName(),
                    block.content(),
                    block.spacing(),
                    block.itemId(),
                    block.itemNbt(),
                    block.target(),
                    block.imageUrl(),
                    block.imageWidth(),
                    block.imageHeight()));
        }
        return elements;
    }

    public String getId() {
        return id;
    }

    @Nullable
    public String getParentId() {
        return parentId;
    }

    @Nullable
    public String getTabIcon() {
        return tabIcon;
    }

    public int getTabIndex() {
        return tabIndex;
    }

    public boolean isDirectory() {
        return parentId == null;
    }

    public int getTotalPages() {
        return pages.size();
    }

    public BookPage getPage(int index) {
        if (pages.isEmpty()) {
            return BookPage.EMPTY;
        }
        if (index < 0 || index >= pages.size()) {
            return pages.get(0);
        }
        return pages.get(index);
    }

    public static final class BookPage {
        public static final BookPage EMPTY = new BookPage("", "", List.of(), List.of());

        private final String leftTitleKey;
        private final String rightTitleKey;
        private final List<PageElement> leftElements;
        private final List<PageElement> rightElements;

        public BookPage(String leftTitleKey, String rightTitleKey, List<PageElement> leftElements, List<PageElement> rightElements) {
            this.leftTitleKey = leftTitleKey;
            this.rightTitleKey = rightTitleKey;
            this.leftElements = leftElements;
            this.rightElements = rightElements;
        }

        public static BookPage fromJson(JsonObject json) {
            String leftTitleKey = GsonHelper.getAsString(json, "left_title", "");
            String rightTitleKey = GsonHelper.getAsString(json, "right_title", "");
            return new BookPage(
                    leftTitleKey,
                    rightTitleKey,
                    parseElements(GsonHelper.getAsJsonArray(json, "left_content", null)),
                    parseElements(GsonHelper.getAsJsonArray(json, "right_content", null)));
        }

        public Component getLeftTitle() {
            return translate(leftTitleKey);
        }

        public Component getRightTitle() {
            return translate(rightTitleKey);
        }

        public List<PageElement> getLeftElements() {
            return leftElements;
        }

        public List<PageElement> getRightElements() {
            return rightElements;
        }

        private static List<PageElement> parseElements(@Nullable JsonArray array) {
            List<PageElement> elements = new ArrayList<>();
            if (array == null) {
                return elements;
            }
            for (int i = 0; i < array.size(); i++) {
                JsonObject object = array.get(i).getAsJsonObject();
                elements.add(new PageElement(
                        GsonHelper.getAsString(object, "type", "text"),
                        GsonHelper.getAsString(object, "content", ""),
                        GsonHelper.getAsInt(object, "spacing", 4),
                        object.has("item_id") ? GsonHelper.getAsString(object, "item_id") : null,
                        object.has("item_nbt") ? GsonHelper.getAsString(object, "item_nbt") : null,
                        object.has("target") ? GsonHelper.getAsString(object, "target") : null,
                        object.has("image_url") ? GsonHelper.getAsString(object, "image_url") : null,
                        GsonHelper.getAsInt(object, "image_width", 64),
                        GsonHelper.getAsInt(object, "image_height", 64)));
            }
            return elements;
        }

        private static Component translate(String key) {
            if (key == null || key.isEmpty()) {
                return Component.empty();
            }
            if (GuideBookLang.has(key) || looksLikeLangKey(key)) {
                String translated = GuideBookLang.get(key);
                if (GuideBookLang.has(key) || !translated.equals(key)) {
                    return Component.literal(translated);
                }
                return Component.translatable(key);
            }
            return Component.literal(GuideBookLang.get(key));
        }

        private static boolean looksLikeLangKey(String key) {
            return key.startsWith("guidebook.") || key.startsWith("start.");
        }
    }

    public static final class PageElement {
        private final String type;
        private final String contentKey;
        private final int spacing;
        private final String itemId;
        private final String itemNbt;
        private final String target;
        private final String imageUrl;
        private final int imageWidth;
        private final int imageHeight;
        private ItemStack cachedItem;

        public PageElement(String type, String contentKey, int spacing,
                           @Nullable String itemId, @Nullable String itemNbt, @Nullable String target,
                           @Nullable String imageUrl, int imageWidth, int imageHeight) {
            this.type = type;
            this.contentKey = contentKey;
            this.spacing = spacing;
            this.itemId = itemId;
            this.itemNbt = itemNbt;
            this.target = target;
            this.imageUrl = imageUrl;
            this.imageWidth = Math.max(1, imageWidth);
            this.imageHeight = Math.max(1, imageHeight);
        }

        public int getSpacing() {
            return spacing;
        }

        public Component getContent() {
            return BookPage.translate(contentKey);
        }

        public boolean isHint() {
            return "hint".equals(type);
        }

        public boolean isItem() {
            return "item".equals(type);
        }

        public boolean isLink() {
            return "link".equals(type);
        }

        public boolean isImage() {
            return "image".equals(type);
        }

        public boolean hasItem() {
            return itemId != null && !itemId.isEmpty();
        }

        @Nullable
        public String getTarget() {
            return target;
        }

        @Nullable
        public String getImageUrl() {
            return imageUrl;
        }

        public int getImageWidth() {
            return imageWidth;
        }

        public int getImageHeight() {
            return imageHeight;
        }

        public ItemStack getItemStack() {
            if (cachedItem != null) {
                return cachedItem;
            }
            if (itemId == null || itemId.isEmpty()) {
                return ItemStack.EMPTY;
            }
            ResourceLocation id = ResourceLocation.tryParse(itemId);
            var item = id == null ? Items.AIR : BuiltInRegistries.ITEM.get(id);
            ItemStack stack = new ItemStack(item);
            if (itemNbt != null && !itemNbt.isEmpty()) {
                try {
                    CompoundTag tag = TagParser.parseTag(itemNbt);
                    CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
                } catch (Exception ignored) {
                    // 书页里的 NBT 只用于展示，解析失败时仍显示基础物品。
                }
            }
            cachedItem = stack;
            return stack;
        }
    }
}
