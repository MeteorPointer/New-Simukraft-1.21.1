package common.cn.nsimu.simukraft.path;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * PathExtraCosts: 寻路只加总外部登记的代价，不认识具体玩法。
 */
public final class PathExtraCosts {
    private static final List<Source> SOURCES = new CopyOnWriteArrayList<>();
    private static final ThreadLocal<Boolean> CITY_BOUND = new ThreadLocal<>();
    private static final ThreadLocal<UUID> BOUND_CITY = new ThreadLocal<>();

    private PathExtraCosts() {
    }

    /**
     * callWithCity: 在一次搜索里固定市民所属城市，避免每条边都回查市民。
     */
    public static <T> T callWithCity(UUID cityId, Supplier<T> work) {
        Boolean previousBound = CITY_BOUND.get();
        UUID previousCity = BOUND_CITY.get();
        CITY_BOUND.set(Boolean.TRUE);
        BOUND_CITY.set(cityId);
        try {
            return work.get();
        } finally {
            if (previousBound == null) {
                CITY_BOUND.remove();
                BOUND_CITY.remove();
            } else {
                CITY_BOUND.set(previousBound);
                BOUND_CITY.set(previousCity);
            }
        }
    }

    /**
     * hasBoundCity: 当前线程是否已经在搜索开始时解析过城市。
     */
    public static boolean hasBoundCity() {
        return Boolean.TRUE.equals(CITY_BOUND.get());
    }

    /**
     * boundCity: 解析结果。没有城市时是 null。
     */
    public static UUID boundCity() {
        return BOUND_CITY.get();
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
