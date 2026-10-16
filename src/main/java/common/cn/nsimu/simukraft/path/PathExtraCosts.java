package common.cn.nsimu.simukraft.path;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/** PathExtraCosts: 寻路只加总外部登记的代价，不认识具体玩法。 */
public final class PathExtraCosts {
    private static final List<Source> SOURCES = new CopyOnWriteArrayList<>();

    private PathExtraCosts() {
    }

    public static void register(Source source) {
        if (source != null && !SOURCES.contains(source)) {
            SOURCES.add(source);
        }
    }

    public static double total(UUID citizenId, ResourceLocation dimension, BlockPos pos) {
        double extra = 0.0D;
        for (Source source : SOURCES) {
            extra += source.extra(citizenId, dimension, pos);
        }
        return extra;
    }

    @FunctionalInterface
    public interface Source {
        double extra(UUID citizenId, ResourceLocation dimension, BlockPos pos);
    }
}
