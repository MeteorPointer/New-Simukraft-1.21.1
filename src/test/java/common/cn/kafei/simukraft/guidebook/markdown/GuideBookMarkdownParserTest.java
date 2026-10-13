package common.cn.kafei.simukraft.guidebook.markdown;

import common.cn.kafei.simukraft.guidebook.markdown.GuideBookMarkdownDocument.Block;
import common.cn.kafei.simukraft.guidebook.markdown.GuideBookMarkdownDocument.Spread;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuideBookMarkdownParserTest {
    @Test
    void parse_readsFrontMatterAndTwoColumns() {
        GuideBookMarkdownDocument document = GuideBookMarkdownParser.parse("""
                ---
                parent: directory
                tab_icon: tab_city
                tab_index: 2
                ---

                # 左标题 | 右标题

                左栏正文{spacing=8}

                :item[simukraft:city_core]

                :::right

                > 这是提示{spacing=0}

                [序章](prologue)
                """);

        assertEquals("directory", document.parentId());
        assertEquals("tab_city", document.tabIcon());
        assertEquals(2, document.tabIndex());
        assertEquals(1, document.spreads().size());

        Spread spread = document.spreads().get(0);
        assertEquals("左标题", spread.leftTitle());
        assertEquals("右标题", spread.rightTitle());
        assertEquals(2, spread.left().size());
        assertEquals(Block.Kind.TEXT, spread.left().get(0).kind());
        assertEquals("左栏正文", spread.left().get(0).content());
        assertEquals(8, spread.left().get(0).spacing());
        assertEquals(Block.Kind.ITEM, spread.left().get(1).kind());
        assertEquals("simukraft:city_core", spread.left().get(1).itemId());
        assertEquals(4, spread.left().get(1).spacing());

        assertEquals(Block.Kind.HINT, spread.right().get(0).kind());
        assertEquals("这是提示", spread.right().get(0).content());
        assertEquals(0, spread.right().get(0).spacing());
        assertEquals(Block.Kind.LINK, spread.right().get(1).kind());
        assertEquals("序章", spread.right().get(1).content());
        assertEquals("prologue", spread.right().get(1).target());
    }

    @Test
    void parse_startsNewSpreadOnHeading() {
        GuideBookMarkdownDocument document = GuideBookMarkdownParser.parse("""
                # 一 | A

                第一页

                # 二 | B

                第二页
                """);

        assertEquals(2, document.spreads().size());
        assertEquals("一", document.spreads().get(0).leftTitle());
        assertEquals("第二页", document.spreads().get(1).left().get(0).content());
    }

    @Test
    void parse_readsImageTitleAndPlayerName() {
        GuideBookMarkdownDocument document = GuideBookMarkdownParser.parse("""
                # 图

                ## 小标题

                $player_name，你好

                ![肉摊](routan.png){width=128 height=74 spacing=4}
                """);

        Spread spread = document.spreads().get(0);
        assertEquals(Block.Kind.TITLE, spread.left().get(0).kind());
        assertEquals("小标题", spread.left().get(0).content());
        assertEquals("$player_name，你好", spread.left().get(1).content());
        Block image = spread.left().get(2);
        assertEquals(Block.Kind.IMAGE, image.kind());
        assertEquals("routan.png", image.imageUrl());
        assertEquals(128, image.imageWidth());
        assertEquals(74, image.imageHeight());
    }

    @Test
    void parse_flattensInlineAndEntities() {
        GuideBookMarkdownDocument document = GuideBookMarkdownParser.parse("""
                # T

                **粗体**和*斜体*还有[链接](ignored)

                &nbsp;
                """);

        assertEquals("粗体和斜体还有链接", document.spreads().get(0).left().get(0).content());
        assertEquals(" ", document.spreads().get(0).left().get(1).content());
    }

    @Test
    void parse_keepsEscapedHeadingAsText() {
        GuideBookMarkdownDocument document = GuideBookMarkdownParser.parse("""
                # T

                \\# 不是标题
                """);

        assertEquals("# 不是标题", document.spreads().get(0).left().get(0).content());
    }

    @Test
    void parse_acceptsCrLfAndHintFence() {
        GuideBookMarkdownDocument document = GuideBookMarkdownParser.parse(
                "---\r\nparent: directory\r\n---\r\n\r\n# A | B\r\n\r\n:::hint\r\n多行\r\n提示{spacing=8}\r\n:::\r\n");

        assertEquals("directory", document.parentId());
        Block hint = document.spreads().get(0).left().get(0);
        assertEquals(Block.Kind.HINT, hint.kind());
        assertEquals("多行\n提示", hint.content());
        assertEquals(8, hint.spacing());
    }

    @Test
    void parse_directoryWithoutParent() {
        GuideBookMarkdownDocument document = GuideBookMarkdownParser.parse("""
                # 目录 | 目录

                欢迎

                :::right

                [开始](start){spacing=4}

                从零开始
                """);

        assertNull(document.parentId());
        assertEquals(-1, document.tabIndex());
        assertEquals("start", document.spreads().get(0).right().get(0).target());
        assertEquals("从零开始", document.spreads().get(0).right().get(1).content());
    }

    @Test
    void parse_emptySourceYieldsEmptySpread() {
        GuideBookMarkdownDocument document = GuideBookMarkdownParser.parse("");
        assertEquals(1, document.spreads().size());
        assertTrue(document.spreads().get(0).left().isEmpty());
        assertTrue(document.spreads().get(0).right().isEmpty());
    }

    @Test
    void parse_officialChineseChapters() throws Exception {
        GuideBookMarkdownDocument start = parseResource("assets/simukraft/guidebook/zh_cn/start.md");
        assertEquals("directory", start.parentId());
        assertEquals("tab_city", start.tabIcon());
        assertEquals(2, start.tabIndex());
        assertEquals(12, start.spreads().size());
        assertEquals("simukraft:city_core", start.spreads().get(0).left().get(1).itemId());
        assertEquals("routan.png", start.spreads().get(5).right().get(1).imageUrl());
        assertEquals(256, start.spreads().get(6).right().get(0).imageWidth());
        assertEquals("simukraft:logistics_server_box", start.spreads().get(8).left().get(3).itemId());

        GuideBookMarkdownDocument items = parseResource("assets/simukraft/guidebook/zh_cn/items.md");
        assertEquals(5, items.spreads().size());
        assertEquals("tab_commerce", items.tabIcon());
        assertEquals("simukraft:guide_book", items.spreads().get(2).right().get(0).itemId());

        GuideBookMarkdownDocument directory = parseResource("assets/simukraft/guidebook/zh_cn/directory.md");
        assertNull(directory.parentId());
        assertEquals("prologue", directory.spreads().get(0).right().get(0).target());
        assertEquals("start", directory.spreads().get(0).right().get(4).target());
    }

    private static GuideBookMarkdownDocument parseResource(String path) throws Exception {
        try (var stream = GuideBookMarkdownParserTest.class.getClassLoader().getResourceAsStream(path)) {
            assertTrue(stream != null, path);
            return GuideBookMarkdownParser.parse(new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        }
    }
}
