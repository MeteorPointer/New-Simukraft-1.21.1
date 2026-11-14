package client.cn.nsimu.simukraft.client.ui;

import client.cn.nsimu.simukraft.client.city.ClientCityChunkCache;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.SearchComponent;
import com.lowdragmc.lowdraglib2.gui.ui.utils.UIElementProvider;
import com.lowdragmc.lowdraglib2.utils.search.IResultHandler;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;


/**
 * SimuKraftNameSearch: 用 LDLib2 SearchComponent 补全城市名或玩家名，不依赖方块注册表。
 */
@OnlyIn(Dist.CLIENT)
public final class SimuKraftNameSearch {
    public static final int MAX_RESULTS = 12;

    private SimuKraftNameSearch() {
    }

    /**
     * cities: 用客户端已同步的城市核心名称补全，可排除当前城市。
     */
    public static SearchComponent<String> cities(Component placeholder, UUID excludeCityId) {
        List<String> names = new ArrayList<>();
        for (var entry : ClientCityChunkCache.getInstance().getAllCityCores().entrySet()) {
            if (excludeCityId != null && excludeCityId.equals(entry.getKey())) {
                continue;
            }
            String name = entry.getValue().cityName();
            if (name != null && !name.isBlank()) {
                names.add(name);
            }
        }
        return create(placeholder, names);
    }

    /**
     * players: 用给定玩家名列表补全。
     */
    public static SearchComponent<String> players(Component placeholder, Collection<String> names) {
        return create(placeholder, names);
    }

    /**
     * create: 对不可变名称快照做后台过滤。search() 不访问 Minecraft 客户端对象。
     */
    public static SearchComponent<String> create(Component placeholder, Collection<String> names) {
        List<String> snapshot = uniqueSorted(names);
        SearchComponent<String> search = new SearchComponent<>();
        search.setSearchUI(new SearchComponent.ISearchUI<>() {
            @Override
            public void search(String word, IResultHandler<String> handler) {
                String query = word == null ? "" : word.trim().toLowerCase(Locale.ROOT);
                int accepted = 0;
                for (String name : snapshot) {
                    if (Thread.currentThread().isInterrupted()) {
                        return;
                    }
                    if (matches(name, query)) {
                        handler.acceptResult(name);
                        if (++accepted >= MAX_RESULTS) {
                            return;
                        }
                    }
                }
            }

            @Override
            public String resultText(String result) {
                return result == null ? "" : result;
            }

            @Override
            public void onResultSelected(String result) {
                if (result == null) {
                    return;
                }
                search.setSelected(result, false);
                search.textField.setText(result, false);
            }
        });
        search.setCandidateUIProvider((UIElementProvider<String>) SimuKraftNameSearch::candidate);
        search.searchStyle(style -> style.maxItemCount(MAX_RESULTS).closeAfterSelect(true).scrollerViewHeight(96));
        search.textField.setAnyString();
        search.textField.getTextFieldStyle().placeholder(placeholder == null ? Component.empty() : placeholder);
        search.layout(layout -> {
            layout.flex(1);
            layout.heightPercent(100);
            layout.minWidth(60);
        });
        return search;
    }

    /**
     * typed: 优先已选项，否则用输入框原文。
     */
    public static String typed(SearchComponent<String> search) {
        if (search == null) {
            return "";
        }
        String selected = search.getValue();
        if (selected != null && !selected.isBlank()) {
            return selected.trim();
        }
        String typed = search.textField.getValue();
        return typed == null ? "" : typed.trim();
    }

    public static boolean matches(String name, String query) {
        if (name == null || name.isBlank()) {
            return false;
        }
        if (query == null || query.isBlank()) {
            return true;
        }
        return name.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
    }

    private static UIElement candidate(String name) {
        if (name == null) {
            return new UIElement();
        }
        Label label = new Label();
        label.setText(Component.literal(name));
        label.setOverflowVisible(false);
        label.layout(layout -> {
            layout.widthPercent(100);
            layout.height(14);
            layout.paddingLeft(4);
        });
        label.textStyle(style -> style
                .textColor(SimuKraftUiTheme.TEXT_PRIMARY_COLOR)
                .textShadow(true)
                .textWrap(TextWrap.HOVER_ROLL)
                .rollSpeed(0.22F)
                .textAlignHorizontal(Horizontal.LEFT)
                .textAlignVertical(Vertical.CENTER));
        return label;
    }

    private static List<String> uniqueSorted(Collection<String> names) {
        if (names == null || names.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String name : names) {
            if (name != null && !name.isBlank()) {
                unique.add(name.trim());
            }
        }
        List<String> sorted = new ArrayList<>(unique);
        sorted.sort(String.CASE_INSENSITIVE_ORDER);
        return List.copyOf(sorted);
    }
}
