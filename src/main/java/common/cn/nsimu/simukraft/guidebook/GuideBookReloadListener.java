package common.cn.nsimu.simukraft.guidebook;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import common.cn.nsimu.simukraft.SimuKraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import javax.annotation.Nonnull;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 服务端资源重载占位。书页渲染只在客户端使用，这里保证资源包重载时指南书目录被扫描。
 */
public final class GuideBookReloadListener extends SimplePreparableReloadListener<Integer> {
    public static final GuideBookReloadListener INSTANCE = new GuideBookReloadListener();
    private static final String INDEX_PATH = "guidebook/index.json";

    private GuideBookReloadListener() {
    }

    @Override
    protected @Nonnull Integer prepare(@Nonnull ResourceManager resourceManager, @Nonnull ProfilerFiller profiler) {
        ResourceLocation index = ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, INDEX_PATH);
        int pages = 0;
        var resource = resourceManager.getResource(index);
        if (resource.isPresent()) {
            try (InputStreamReader reader = new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                if (json.has("pages") && json.get("pages").isJsonArray()) {
                    pages = json.getAsJsonArray("pages").size();
                }
            } catch (Exception exception) {
                SimuKraft.LOGGER.warn("Failed to scan guidebook index", exception);
            }
        }
        return pages;
    }

    @Override
    protected void apply(@Nonnull Integer pages, @Nonnull ResourceManager resourceManager, @Nonnull ProfilerFiller profiler) {
        SimuKraft.LOGGER.info("Guidebook index lists {} page files", pages);
    }
}
