package client.cn.nsimu.simukraft.client.auth;

import java.lang.reflect.Method;
import java.nio.file.Path;

/** 保持公开源码不依赖私有 JiJ；赞助构建在运行时反射加载并强制要求它存在。 */
final class SponsorInjectionGuardBridge {
    private static final String DETECTOR = "cn.nsimu.security.guard.InjectionDetector";

    private SponsorInjectionGuardBridge() {
    }

    static Result inspect(Path artifact) {
        if (artifact == null) return new Result(false, true, "missing_artifact");
        try {
            Class<?> detector = Class.forName(DETECTOR, true, SponsorInjectionGuardBridge.class.getClassLoader());
            Object report = detector.getMethod("inspect", Path.class).invoke(null, artifact);
            Method blocked = report.getClass().getMethod("blocked");
            Method summary = report.getClass().getMethod("blockingSummary");
            return new Result(true, (Boolean) blocked.invoke(report), String.valueOf(summary.invoke(report)));
        } catch (ReflectiveOperationException | LinkageError exception) {
            return new Result(false, true, "guard_unavailable");
        }
    }

    record Result(boolean available, boolean blocked, String summary) {
        Result {
            summary = summary == null ? "" : summary;
        }
    }
}
