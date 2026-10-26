package client.cn.nsimu.simukraft.client.auth;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Build-time sponsor edition switch. Public builds default to disabled even when
 * the marker is missing or malformed, so they never start authorization traffic.
 */
public final class SponsorEdition {
    private static final String BUILD_MARKER = "/META-INF/simukraft-build.properties";
    private static final String PRIVATE_GUARD = "cn.nsimu.security.guard.InjectionDetector";
    private static final boolean ENABLED = decide(readMarker(), guardPresent());

    private SponsorEdition() {
    }

    public static boolean isEnabled() {
        return ENABLED;
    }

    static boolean decide(boolean markerEnabled, boolean privateGuardPresent) {
        // 单独篡改 marker 或删除 JiJ 都不能把赞助包降级成普通包；两个信号不一致时
        // 按赞助版处理并交给运行时完整性检查拒绝缺失/异常的 Guard。
        return markerEnabled || privateGuardPresent;
    }

    private static boolean readMarker() {
        try (InputStream stream = SponsorEdition.class.getResourceAsStream(BUILD_MARKER)) {
            if (stream == null) return false;
            Properties properties = new Properties();
            properties.load(stream);
            return Boolean.parseBoolean(properties.getProperty("sponsor.edition", "false"));
        } catch (IOException | RuntimeException ignored) {
            return false;
        }
    }

    private static boolean guardPresent() {
        try {
            Class.forName(PRIVATE_GUARD, false, SponsorEdition.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError ignored) {
            return false;
        }
    }
}
