package common.cn.kafei.simukraft.guidebook.markdown;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;

/**
 * 指南书 Markdown 解析结果。一章若干跨页，每页左右两栏，元素类型与 JSON 书页一致。
 */
public final class GuideBookMarkdownDocument {
    private final String parentId;
    private final String tabIcon;
    private final int tabIndex;
    private final List<Spread> spreads;

    public GuideBookMarkdownDocument(
            @Nullable String parentId,
            @Nullable String tabIcon,
            int tabIndex,
            List<Spread> spreads) {
        this.parentId = parentId;
        this.tabIcon = tabIcon;
        this.tabIndex = tabIndex;
        this.spreads = List.copyOf(spreads);
    }

    @Nullable
    public String parentId() {
        return parentId;
    }

    @Nullable
    public String tabIcon() {
        return tabIcon;
    }

    public int tabIndex() {
        return tabIndex;
    }

    public List<Spread> spreads() {
        return spreads;
    }

    public static final class Spread {
        private final String leftTitle;
        private final String rightTitle;
        private final List<Block> left;
        private final List<Block> right;

        public Spread(String leftTitle, String rightTitle, List<Block> left, List<Block> right) {
            this.leftTitle = leftTitle == null ? "" : leftTitle;
            this.rightTitle = rightTitle == null ? "" : rightTitle;
            this.left = List.copyOf(left);
            this.right = List.copyOf(right);
        }

        public String leftTitle() {
            return leftTitle;
        }

        public String rightTitle() {
            return rightTitle;
        }

        public List<Block> left() {
            return left;
        }

        public List<Block> right() {
            return right;
        }
    }

    public static final class Block {
        public enum Kind {
            TEXT("text"),
            HINT("hint"),
            ITEM("item"),
            LINK("link"),
            IMAGE("image"),
            TITLE("title");

            private final String wireName;

            Kind(String wireName) {
                this.wireName = wireName;
            }

            public String wireName() {
                return wireName;
            }

            public static Kind fromWire(String type) {
                if (type == null || type.isEmpty()) {
                    return TEXT;
                }
                for (Kind kind : values()) {
                    if (kind.wireName.equals(type)) {
                        return kind;
                    }
                }
                return TEXT;
            }
        }

        private final Kind kind;
        private final String content;
        private final int spacing;
        private final String itemId;
        private final String itemNbt;
        private final String target;
        private final String imageUrl;
        private final int imageWidth;
        private final int imageHeight;

        public Block(
                Kind kind,
                String content,
                int spacing,
                @Nullable String itemId,
                @Nullable String itemNbt,
                @Nullable String target,
                @Nullable String imageUrl,
                int imageWidth,
                int imageHeight) {
            this.kind = Objects.requireNonNullElse(kind, Kind.TEXT);
            this.content = content == null ? "" : content;
            this.spacing = spacing;
            this.itemId = itemId;
            this.itemNbt = itemNbt;
            this.target = target;
            this.imageUrl = imageUrl;
            this.imageWidth = Math.max(1, imageWidth);
            this.imageHeight = Math.max(1, imageHeight);
        }

        public static Block text(String content, int spacing) {
            return new Block(Kind.TEXT, content, spacing, null, null, null, null, 64, 64);
        }

        public static Block hint(String content, int spacing) {
            return new Block(Kind.HINT, content, spacing, null, null, null, null, 64, 64);
        }

        public static Block title(String content, int spacing) {
            return new Block(Kind.TITLE, content, spacing, null, null, null, null, 64, 64);
        }

        public static Block item(String itemId, int spacing) {
            return new Block(Kind.ITEM, "", spacing, itemId, null, null, null, 64, 64);
        }

        public static Block item(String itemId, @Nullable String itemNbt, int spacing) {
            return new Block(Kind.ITEM, "", spacing, itemId, itemNbt, null, null, 64, 64);
        }

        public static Block link(String content, String target, int spacing) {
            return new Block(Kind.LINK, content, spacing, null, null, target, null, 64, 64);
        }

        public static Block image(String imageUrl, int width, int height, int spacing) {
            return new Block(Kind.IMAGE, "", spacing, null, null, null, imageUrl, width, height);
        }

        public Kind kind() {
            return kind;
        }

        public String content() {
            return content;
        }

        public int spacing() {
            return spacing;
        }

        @Nullable
        public String itemId() {
            return itemId;
        }

        @Nullable
        public String itemNbt() {
            return itemNbt;
        }

        @Nullable
        public String target() {
            return target;
        }

        @Nullable
        public String imageUrl() {
            return imageUrl;
        }

        public int imageWidth() {
            return imageWidth;
        }

        public int imageHeight() {
            return imageHeight;
        }
    }
}
