package common.cn.kafei.simukraft.guidebook.markdown;

import common.cn.kafei.simukraft.guidebook.markdown.GuideBookMarkdownDocument.Block;
import common.cn.kafei.simukraft.guidebook.markdown.GuideBookMarkdownDocument.Spread;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 指南书 Markdown 引擎。把一章 md 解析成与 JSON 书页相同的左右栏结构。
 *
 * <pre>
 * ---
 * parent: directory
 * tab_icon: tab_city
 * tab_index: 0
 * ---
 *
 * # 左标题 | 右标题
 *
 * 段落文字{spacing=8}
 *
 * :item[simukraft:city_core]
 *
 * ## 栏内小标题
 *
 * :::right
 *
 * &gt; 提示
 *
 * [序章](prologue)
 *
 * ![图](routan.png){width=128 height=74}
 * </pre>
 *
 * {@code #} 开启新的左右跨页；{@code :::right}/{@code :::left} 切换栏；
 * 引用是 hint；{@code ##} 是 title 元素。默认 spacing 为 4。
 */
public final class GuideBookMarkdownParser {
    public static final int DEFAULT_SPACING = 4;
    public static final int DEFAULT_IMAGE_SIZE = 64;
    public static final int DEFAULT_TAB_INDEX = -1;

    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.*)$");
    private static final Pattern ITEM = Pattern.compile("^:item\\[([^\\]]+)]\\s*(?:\\{([^}]*)})?\\s*$");
    private static final Pattern IMAGE = Pattern.compile("^!\\[([^\\]]*)]\\(([^)]+)\\)\\s*(?:\\{([^}]*)})?\\s*$");
    private static final Pattern LINK = Pattern.compile("^\\[([^\\]]+)]\\(([^)]+)\\)\\s*(?:\\{([^}]*)})?\\s*$");
    private static final Pattern FENCE = Pattern.compile("^:::\\s*(\\S+)?\\s*$");
    private static final Pattern ATTRS_TAIL = Pattern.compile("^(.*)\\{([^{}]+)}\\s*$");

    private GuideBookMarkdownParser() {
    }

    public static GuideBookMarkdownDocument parse(@Nullable String source) {
        String normalized = normalize(source);
        FrontMatter frontMatter = FrontMatter.read(normalized);
        List<Spread> spreads = parseBody(frontMatter.body);
        if (spreads.isEmpty()) {
            spreads.add(new Spread("", "", List.of(), List.of()));
        }
        return new GuideBookMarkdownDocument(
                frontMatter.parentId,
                frontMatter.tabIcon,
                frontMatter.tabIndex,
                spreads);
    }

    private static String normalize(@Nullable String source) {
        if (source == null || source.isEmpty()) {
            return "";
        }
        String text = source;
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static List<Spread> parseBody(String body) {
        String[] lines = body.split("\n", -1);
        List<SpreadBuilder> spreads = new ArrayList<>();
        SpreadBuilder current = null;
        boolean right = false;
        int index = 0;
        while (index < lines.length) {
            String raw = lines[index];
            String trimmed = raw.trim();
            if (trimmed.isEmpty() || isHtmlComment(trimmed)) {
                index++;
                continue;
            }
            if (trimmed.startsWith("\\")) {
                if (current == null) {
                    current = newSpread(spreads, "", "");
                }
                Paragraph paragraph = readParagraph(lines, index);
                column(current, right).add(Block.text(paragraph.text, paragraph.spacing));
                index = paragraph.nextIndex;
                continue;
            }
            if ("---".equals(trimmed)) {
                current = null;
                right = false;
                index++;
                continue;
            }
            Matcher fence = FENCE.matcher(trimmed);
            if (fence.matches()) {
                String name = fence.group(1);
                if (name == null || name.isEmpty()) {
                    index++;
                    continue;
                }
                String directive = name.toLowerCase(Locale.ROOT);
                if ("right".equals(directive)) {
                    if (current == null) {
                        current = newSpread(spreads, "", "");
                    }
                    right = true;
                    index++;
                    continue;
                }
                if ("left".equals(directive)) {
                    if (current == null) {
                        current = newSpread(spreads, "", "");
                    }
                    right = false;
                    index++;
                    continue;
                }
                if ("hint".equals(directive)) {
                    if (current == null) {
                        current = newSpread(spreads, "", "");
                    }
                    HintBlock hint = readHintFence(lines, index + 1);
                    column(current, right).add(Block.hint(hint.text, hint.spacing));
                    index = hint.nextIndex;
                    continue;
                }
                index++;
                continue;
            }
            Matcher heading = HEADING.matcher(trimmed);
            if (heading.matches()) {
                int level = heading.group(1).length();
                Attrs attrs = Attrs.parse(heading.group(2));
                String titleText = unescapeInline(attrs.text).stripTrailing();
                if (level == 1) {
                    TitlePair titles = TitlePair.split(titleText);
                    current = newSpread(spreads, titles.left, titles.right);
                    right = false;
                    index++;
                    continue;
                }
                if (current == null) {
                    current = newSpread(spreads, "", "");
                }
                column(current, right).add(Block.title(titleText, attrs.spacing(DEFAULT_SPACING)));
                index++;
                continue;
            }
            if (current == null) {
                current = newSpread(spreads, "", "");
            }
            Matcher item = ITEM.matcher(trimmed);
            if (item.matches()) {
                Attrs attrs = Attrs.fromBody(item.group(2));
                column(current, right).add(Block.item(item.group(1).trim(), attrs.value("nbt"), attrs.spacing(DEFAULT_SPACING)));
                index++;
                continue;
            }
            Matcher image = IMAGE.matcher(trimmed);
            if (image.matches()) {
                Attrs attrs = Attrs.fromBody(image.group(3));
                column(current, right).add(Block.image(
                        image.group(2).trim(),
                        attrs.intValue("width", DEFAULT_IMAGE_SIZE),
                        attrs.intValue("height", DEFAULT_IMAGE_SIZE),
                        attrs.spacing(DEFAULT_SPACING)));
                index++;
                continue;
            }
            Matcher link = LINK.matcher(trimmed);
            if (link.matches()) {
                Attrs attrs = Attrs.fromBody(link.group(3));
                column(current, right).add(Block.link(
                        unescapeInline(link.group(1)),
                        normalizeTarget(link.group(2).trim()),
                        attrs.spacing(DEFAULT_SPACING)));
                index++;
                continue;
            }
            if (trimmed.startsWith(">")) {
                QuoteBlock quote = readQuote(lines, index);
                column(current, right).add(Block.hint(quote.text, quote.spacing));
                index = quote.nextIndex;
                continue;
            }
            Paragraph paragraph = readParagraph(lines, index);
            column(current, right).add(Block.text(paragraph.text, paragraph.spacing));
            index = paragraph.nextIndex;
        }
        List<Spread> result = new ArrayList<>(spreads.size());
        for (SpreadBuilder builder : spreads) {
            result.add(builder.build());
        }
        return result;
    }

    private static SpreadBuilder newSpread(List<SpreadBuilder> spreads, String leftTitle, String rightTitle) {
        SpreadBuilder builder = new SpreadBuilder(leftTitle, rightTitle);
        spreads.add(builder);
        return builder;
    }

    private static List<Block> column(SpreadBuilder current, boolean right) {
        return right ? current.right : current.left;
    }

    private static HintBlock readHintFence(String[] lines, int start) {
        StringBuilder text = new StringBuilder();
        int index = start;
        int spacing = DEFAULT_SPACING;
        while (index < lines.length) {
            String trimmed = lines[index].trim();
            Matcher fence = FENCE.matcher(trimmed);
            if (fence.matches() && (fence.group(1) == null || fence.group(1).isEmpty())) {
                index++;
                break;
            }
            Attrs attrs = Attrs.parse(stripEscape(trimmed));
            if (text.length() > 0) {
                text.append('\n');
            }
            text.append(unescapeInline(attrs.text));
            spacing = attrs.spacing(spacing);
            index++;
        }
        return new HintBlock(text.toString(), spacing, index);
    }

    private static QuoteBlock readQuote(String[] lines, int start) {
        StringBuilder text = new StringBuilder();
        int index = start;
        int spacing = DEFAULT_SPACING;
        while (index < lines.length) {
            String trimmed = stripEscape(lines[index].trim());
            if (!trimmed.startsWith(">")) {
                break;
            }
            String body = trimmed.substring(1);
            if (body.startsWith(" ")) {
                body = body.substring(1);
            }
            Attrs attrs = Attrs.parse(body);
            if (text.length() > 0) {
                text.append('\n');
            }
            text.append(unescapeInline(attrs.text));
            spacing = attrs.spacing(spacing);
            index++;
        }
        return new QuoteBlock(text.toString(), spacing, index);
    }

    private static Paragraph readParagraph(String[] lines, int start) {
        StringBuilder text = new StringBuilder();
        int index = start;
        int spacing = DEFAULT_SPACING;
        while (index < lines.length) {
            String rawTrim = lines[index].trim();
            if (rawTrim.isEmpty()) {
                break;
            }
            boolean escaped = rawTrim.startsWith("\\");
            if (index > start && !escaped && isSpecialLine(rawTrim)) {
                break;
            }
            Attrs attrs = Attrs.parse(stripEscape(rawTrim));
            if (text.length() > 0) {
                text.append('\n');
            }
            text.append(unescapeInline(attrs.text));
            spacing = attrs.spacing(spacing);
            index++;
        }
        return new Paragraph(text.toString(), spacing, Math.max(index, start + 1));
    }

    private static boolean isSpecialLine(String trimmed) {
        if ("---".equals(trimmed) || isHtmlComment(trimmed)) {
            return true;
        }
        if (FENCE.matcher(trimmed).matches() || HEADING.matcher(trimmed).matches()) {
            return true;
        }
        if (ITEM.matcher(trimmed).matches() || IMAGE.matcher(trimmed).matches() || LINK.matcher(trimmed).matches()) {
            return true;
        }
        return trimmed.startsWith(">");
    }

    private static boolean isHtmlComment(String trimmed) {
        return trimmed.startsWith("<!--") && trimmed.endsWith("-->");
    }

    private static String stripEscape(String trimmed) {
        if (trimmed.startsWith("\\") && trimmed.length() > 1) {
            char next = trimmed.charAt(1);
            if (next == '#' || next == '>' || next == ':' || next == '-' || next == '!' || next == '[') {
                return trimmed.substring(1);
            }
        }
        return trimmed;
    }

    private static String normalizeTarget(String target) {
        if (target.startsWith("chapter:")) {
            return target.substring("chapter:".length());
        }
        return target;
    }

    static String unescapeInline(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String resolved = decodeEntities(text);
        StringBuilder out = new StringBuilder(resolved.length());
        int i = 0;
        while (i < resolved.length()) {
            char c = resolved.charAt(i);
            if (c == '\\' && i + 1 < resolved.length()) {
                out.append(resolved.charAt(i + 1));
                i += 2;
                continue;
            }
            if (c == '*' || c == '_') {
                boolean doubled = i + 1 < resolved.length() && resolved.charAt(i + 1) == c;
                String token = doubled ? String.valueOf(c) + c : String.valueOf(c);
                int close = resolved.indexOf(token, i + token.length());
                if (close >= i + token.length()) {
                    out.append(unescapeInline(resolved.substring(i + token.length(), close)));
                    i = close + token.length();
                    continue;
                }
            }
            if (c == '`') {
                int close = resolved.indexOf('`', i + 1);
                if (close > i) {
                    out.append(resolved, i + 1, close);
                    i = close + 1;
                    continue;
                }
            }
            if (c == '[' && resolved.indexOf("](", i) > i) {
                int labelEnd = resolved.indexOf("](", i);
                int urlEnd = resolved.indexOf(')', labelEnd + 2);
                if (labelEnd > i && urlEnd > labelEnd) {
                    out.append(resolved, i + 1, labelEnd);
                    i = urlEnd + 1;
                    continue;
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    private static String decodeEntities(String text) {
        return text
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&#10;", "\n");
    }

    private static final class SpreadBuilder {
        private final String leftTitle;
        private final String rightTitle;
        private final List<Block> left = new ArrayList<>();
        private final List<Block> right = new ArrayList<>();

        private SpreadBuilder(String leftTitle, String rightTitle) {
            this.leftTitle = leftTitle;
            this.rightTitle = rightTitle;
        }

        private Spread build() {
            return new Spread(leftTitle, rightTitle, left, right);
        }
    }

    private static final class FrontMatter {
        private final String parentId;
        private final String tabIcon;
        private final int tabIndex;
        private final String body;

        private FrontMatter(@Nullable String parentId, @Nullable String tabIcon, int tabIndex, String body) {
            this.parentId = parentId;
            this.tabIcon = tabIcon;
            this.tabIndex = tabIndex;
            this.body = body;
        }

        private static FrontMatter read(String source) {
            String trimmedStart = trimStart(source);
            if (!trimmedStart.startsWith("---")) {
                return new FrontMatter(null, null, DEFAULT_TAB_INDEX, source);
            }
            String[] lines = source.split("\n", -1);
            int first = 0;
            while (first < lines.length && lines[first].trim().isEmpty()) {
                first++;
            }
            if (first >= lines.length || !lines[first].trim().equals("---")) {
                return new FrontMatter(null, null, DEFAULT_TAB_INDEX, source);
            }
            int end = -1;
            for (int i = first + 1; i < lines.length; i++) {
                if (lines[i].trim().equals("---")) {
                    end = i;
                    break;
                }
            }
            if (end < 0) {
                return new FrontMatter(null, null, DEFAULT_TAB_INDEX, source);
            }
            String parentId = null;
            String tabIcon = null;
            int tabIndex = DEFAULT_TAB_INDEX;
            for (int i = first + 1; i < end; i++) {
                String line = lines[i].trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int colon = line.indexOf(':');
                if (colon <= 0) {
                    continue;
                }
                String key = line.substring(0, colon).trim();
                String value = unquote(line.substring(colon + 1).trim());
                switch (key) {
                    case "parent" -> parentId = emptyToNull(value);
                    case "tab_icon" -> tabIcon = emptyToNull(value);
                    case "tab_index" -> tabIndex = parseInt(value, DEFAULT_TAB_INDEX);
                    default -> {
                    }
                }
            }
            StringBuilder body = new StringBuilder();
            for (int i = end + 1; i < lines.length; i++) {
                if (body.length() > 0) {
                    body.append('\n');
                }
                body.append(lines[i]);
            }
            return new FrontMatter(parentId, tabIcon, tabIndex, body.toString());
        }

        private static String trimStart(String source) {
            int i = 0;
            while (i < source.length() && (source.charAt(i) == ' ' || source.charAt(i) == '\n' || source.charAt(i) == '\t')) {
                i++;
            }
            return source.substring(i);
        }
    }

    static final class Attrs {
        private final String text;
        private final Map<String, String> values;

        private Attrs(String text, Map<String, String> values) {
            this.text = text;
            this.values = values;
        }

        static Attrs parse(String line) {
            if (line == null || line.isEmpty()) {
                return new Attrs("", Map.of());
            }
            Matcher matcher = ATTRS_TAIL.matcher(line);
            if (!matcher.matches()) {
                return new Attrs(line, Map.of());
            }
            return new Attrs(matcher.group(1).stripTrailing(), parseBody(matcher.group(2)));
        }

        static Attrs fromBody(@Nullable String body) {
            return new Attrs("", parseBody(body));
        }

        private static Map<String, String> parseBody(@Nullable String body) {
            Map<String, String> values = new LinkedHashMap<>();
            if (body == null || body.isBlank()) {
                return values;
            }
            for (String part : body.trim().split("\\s+")) {
                int eq = part.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                values.put(part.substring(0, eq), unquote(part.substring(eq + 1)));
            }
            return values;
        }

        int spacing(int fallback) {
            return intValue("spacing", fallback);
        }

        int intValue(String key, int fallback) {
            return parseInt(values.get(key), fallback);
        }

        @Nullable
        String value(String key) {
            return emptyToNull(values.get(key));
        }
    }

    private record TitlePair(String left, String right) {
        private static TitlePair split(String title) {
            int index = title.indexOf(" | ");
            if (index < 0) {
                return new TitlePair(title, "");
            }
            return new TitlePair(title.substring(0, index).stripTrailing(), title.substring(index + 3).stripLeading());
        }
    }

    private record HintBlock(String text, int spacing, int nextIndex) {
    }

    private record QuoteBlock(String text, int spacing, int nextIndex) {
    }

    private record Paragraph(String text, int spacing, int nextIndex) {
    }

    private static int parseInt(@Nullable String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    @Nullable
    private static String emptyToNull(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    private static String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }
}
