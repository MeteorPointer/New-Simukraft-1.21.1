package common.cn.nsimu.simukraft.sponsor;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

/**
 * 赞助功能统一权限边界。默认拒绝，客户端授权模块初始化后安装 entitlement 解析器。
 */
public final class SponsorGate {
    private static final Predicate<String> DENY_ALL = entitlement -> false;
    private static final AtomicReference<Predicate<String>> RESOLVER = new AtomicReference<>(DENY_ALL);

    private SponsorGate() {
    }

    public static boolean allowed(String entitlement) {
        String normalized = normalize(entitlement);
        return !normalized.isEmpty() && RESOLVER.get().test(normalized);
    }

    public static void require(String entitlement) {
        String normalized = normalize(entitlement);
        if (!allowed(normalized)) throw new SponsorAccessDeniedException(normalized);
    }

    public static void installResolver(Predicate<String> resolver) {
        RESOLVER.set(Objects.requireNonNull(resolver, "resolver"));
    }

    static void resetForTests() {
        RESOLVER.set(DENY_ALL);
    }

    private static String normalize(String entitlement) {
        return entitlement == null ? "" : entitlement.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
