package client.cn.nsimu.simukraft.client.auth;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import common.cn.nsimu.simukraft.SimuKraft;
import common.cn.nsimu.simukraft.sponsor.SponsorGate;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class SponsorAuthManager {
    public static final String ISSUER = "https://new.nsimu.cn";
    private static final URI API_BASE = URI.create(ISSUER + "/api/mod/");
    private static final long ONLINE_CHECK_INTERVAL_SECONDS = 6L * 3600L;
    private static final int MAX_RESPONSE_BYTES = 64 * 1024;
    private static final Object LOCK = new Object();
    private static final SponsorAuthStorage STORAGE = new SponsorAuthStorage();
    private static final SponsorAuthStorage.StoredState STATE = STORAGE.load();
    private static final SponsorBuildIdentity BUILD = SponsorBuildIdentity.current();
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean();
    private static final AtomicBoolean IN_FLIGHT = new AtomicBoolean();
    private static final AtomicLong REFRESH_GENERATION = new AtomicLong();
    private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "simukraft-sponsor-auth");
        thread.setDaemon(true);
        return thread;
    });
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .executor(EXECUTOR)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private static volatile SponsorAuthSnapshot snapshot = new SponsorAuthSnapshot(
            SponsorAuthSnapshot.Phase.UNAUTHORIZED,
            "尚未绑定赞助账号",
            "",
            "",
            0L,
            "",
            Set.of(),
            0L
    );

    private SponsorAuthManager() {
    }

    public static void initialize() {
        if (!SponsorEdition.isEnabled()) return;
        if (!INITIALIZED.compareAndSet(false, true)) return;
        SponsorGate.installResolver(SponsorAuthManager::hasEntitlement);
        boolean refresh = false;
        boolean poll = false;
        long refreshDelay = 0L;
        synchronized (LOCK) {
            if (!ensureRuntimeTrusted()) return;
            if (STATE.protectionError) {
                snapshot = snapshotFor(
                        SponsorAuthSnapshot.Phase.ERROR,
                        "本机授权文件无法解密，可能来自其他电脑，请重新绑定",
                        null
                );
                return;
            }
            long now = epochSeconds();
            var verified = cachedLicense(now);
            if (!STATE.pairDeviceCode.isBlank() && STATE.pairExpiresAt > now) {
                snapshot = snapshotFor(
                        SponsorAuthSnapshot.Phase.PAIRING,
                        "等待官网确认绑定",
                        verified
                );
                poll = true;
            } else if (verified != null) {
                snapshot = snapshotFor(SponsorAuthSnapshot.Phase.AUTHORIZED, "赞助授权有效", verified);
                refresh = now >= verified.refreshAfter()
                        || now - STATE.lastOnlineCheck >= ONLINE_CHECK_INTERVAL_SECONDS;
                if (!refresh) {
                    long nextCheck = Math.min(
                            verified.refreshAfter(),
                            STATE.lastOnlineCheck + ONLINE_CHECK_INTERVAL_SECONDS
                    );
                    refreshDelay = Math.max(1L, nextCheck - now);
                }
            } else if (!STATE.deviceToken.isBlank()) {
                snapshot = snapshotFor(
                        SponsorAuthSnapshot.Phase.REFRESHING,
                        "正在后台检查赞助授权",
                        null
                );
                refresh = true;
            }
        }
        if (poll) schedulePoll(1L);
        if (refresh) refreshNow();
        if (refreshDelay > 0L) scheduleRefresh(refreshDelay);
    }

    public static SponsorAuthSnapshot snapshot() {
        initialize();
        return snapshot;
    }

    private static SponsorInjectionGuardBridge.Result inspectRuntime() {
        if (!BUILD.available()) return new SponsorInjectionGuardBridge.Result(false, true, "build_unavailable");
        SponsorInjectionGuardBridge.Result result = SponsorInjectionGuardBridge.inspect(BUILD.artifactPath());
        if (result.blocked()) SimuKraft.LOGGER.warn("Sponsor injection guard blocked: {}", result.summary());
        return result;
    }

    private static boolean ensureRuntimeTrusted() {
        if (!BUILD.available()) {
            snapshot = snapshotFor(
                    SponsorAuthSnapshot.Phase.ERROR,
                    "无法校验当前模组 JAR：" + BUILD.error(),
                    null
            );
            return false;
        }
        SponsorInjectionGuardBridge.Result result = inspectRuntime();
        if (!result.available() || result.blocked()) {
            snapshot = snapshotFor(
                    SponsorAuthSnapshot.Phase.ERROR,
                    "检测到不受支持的代码注入环境：" + result.summary(),
                    null
            );
            return false;
        }
        return true;
    }

    public static boolean hasEntitlement(String entitlement) {
        SponsorAuthSnapshot current = snapshot();
        return current.authorized() && current.entitlements().contains(entitlement);
    }

    public static void startPairing(String minecraftName) {
        if (!SponsorEdition.isEnabled()) return;
        initialize();
        synchronized (LOCK) {
            if (!ensureRuntimeTrusted()) return;
        }
        if (!IN_FLIGHT.compareAndSet(false, true)) return;

        JsonObject request = new JsonObject();
        synchronized (LOCK) {
            BUILD.addTo(request);
            STATE.protectionError = false;
            STATE.clearPairing();
            STATE.pendingDeviceToken = randomSecret();
            STORAGE.save(STATE);
            request.addProperty("mod_id", "simukraft");
            request.addProperty("install_id", STATE.installId);
            request.addProperty("device_name", currentComputerName());
            request.addProperty("minecraft_name", sanitizeLabel(minecraftName, "未登录", 64));
            request.addProperty("credential_hash", sha256Base64Url(STATE.pendingDeviceToken));
            snapshot = snapshotFor(
                    SponsorAuthSnapshot.Phase.REFRESHING,
                    "正在创建一次性绑定码",
                    cachedLicense(epochSeconds())
            );
        }

        post("pair/start", request).whenComplete((response, error) -> {
            IN_FLIGHT.set(false);
            synchronized (LOCK) {
                if (error != null) {
                    SimuKraft.LOGGER.warn("Sponsor auth pair/start network failure: {}", rootMessage(error));
                    STATE.clearPairing();
                    STORAGE.save(STATE);
                    snapshot = snapshotFor(
                            SponsorAuthSnapshot.Phase.ERROR,
                            "无法连接授权服务：" + rootMessage(error),
                            cachedLicense(epochSeconds())
                    );
                    return;
                }
                if (response.status() != 200) {
                    logApiFailure("pair/start", response);
                    STATE.clearPairing();
                    STORAGE.save(STATE);
                    snapshot = snapshotFor(
                            SponsorAuthSnapshot.Phase.ERROR,
                            errorMessage(response.body(), "创建绑定码失败"),
                            cachedLicense(epochSeconds())
                    );
                    return;
                }

                JsonObject body = response.body();
                STATE.pairDeviceCode = string(body, "device_code");
                STATE.userCode = string(body, "user_code");
                String complete = string(body, "verification_uri_complete");
                STATE.verificationUri = complete.isBlank() ? string(body, "verification_uri") : complete;
                STATE.pollInterval = Math.max(5L, number(body, "interval"));
                STATE.pairExpiresAt = epochSeconds() + Math.max(120L, number(body, "expires_in"));
                STORAGE.save(STATE);
                snapshot = snapshotFor(
                        SponsorAuthSnapshot.Phase.PAIRING,
                        "请在官网核对短码并确认绑定",
                        cachedLicense(epochSeconds())
                );
            }
            schedulePoll(STATE.pollInterval);
        });
    }

    public static void refreshNow() {
        if (!SponsorEdition.isEnabled()) return;
        initialize();
        String deviceToken;
        String installId;
        synchronized (LOCK) {
            if (!ensureRuntimeTrusted()) return;
            if (STATE.deviceToken.isBlank()) {
                snapshot = snapshotFor(
                        SponsorAuthSnapshot.Phase.UNAUTHORIZED,
                        "请先使用绑定码绑定账号",
                        cachedLicense(epochSeconds())
                );
                return;
            }
            deviceToken = STATE.deviceToken;
            installId = STATE.installId;
        }
        if (!IN_FLIGHT.compareAndSet(false, true)) return;

        synchronized (LOCK) {
            var verified = cachedLicense(epochSeconds());
            snapshot = snapshotFor(
                    verified == null ? SponsorAuthSnapshot.Phase.REFRESHING : SponsorAuthSnapshot.Phase.AUTHORIZED,
                    "正在后台核验赞助资格",
                    verified
            );
        }

        JsonObject request = new JsonObject();
        request.addProperty("mod_id", "simukraft");
        request.addProperty("install_id", installId);
        request.addProperty("device_token", deviceToken);
        BUILD.addTo(request);
        post("license/refresh", request).whenComplete((response, error) -> {
            IN_FLIGHT.set(false);
            synchronized (LOCK) {
                if (error != null) {
                    SimuKraft.LOGGER.warn("Sponsor auth license/refresh network failure: {}", rootMessage(error));
                    keepCachedOrError("网络检查失败，继续使用有效的离线 License：" + rootMessage(error));
                    return;
                }
                if (response.status() == 200) {
                    try {
                        acceptLicense(response.body(), false);
                    } catch (Exception exception) {
                        SimuKraft.LOGGER.warn("Sponsor auth rejected license/refresh response", exception);
                        keepCachedOrError("服务器返回的 License 无法验证");
                    }
                    return;
                }

                String code = string(response.body(), "error");
                logApiFailure("license/refresh", response);
                if ("invalid_grant".equals(code)
                        || "device_revoked".equals(code)
                        || "access_denied".equals(code)
                        || "unofficial_build".equals(code)) {
                    STATE.clearAuthorization();
                    STORAGE.save(STATE);
                    snapshot = snapshotFor(
                            SponsorAuthSnapshot.Phase.UNAUTHORIZED,
                            errorMessage(response.body(), "赞助授权已失效"),
                            null
                    );
                } else {
                    keepCachedOrError(errorMessage(response.body(), "授权服务暂时不可用"));
                }
            }
        });
    }

    public static void forgetLocalAuthorization() {
        REFRESH_GENERATION.incrementAndGet();
        synchronized (LOCK) {
            STATE.clearAuthorization();
            STORAGE.save(STATE);
            snapshot = snapshotFor(
                    SponsorAuthSnapshot.Phase.UNAUTHORIZED,
                    "已清除本机授权；官网上的设备仍需单独吊销",
                    null
            );
        }
    }

    private static void pollPairing() {
        String deviceCode;
        synchronized (LOCK) {
            if (!ensureRuntimeTrusted()) return;
            if (STATE.pairDeviceCode.isBlank()) return;
            if (STATE.pairExpiresAt <= epochSeconds()) {
                STATE.clearPairing();
                STORAGE.save(STATE);
                snapshot = snapshotFor(
                        SponsorAuthSnapshot.Phase.ERROR,
                        "绑定码已过期，请重新创建",
                        cachedLicense(epochSeconds())
                );
                return;
            }
            deviceCode = STATE.pairDeviceCode;
        }
        if (!IN_FLIGHT.compareAndSet(false, true)) {
            schedulePoll(1L);
            return;
        }

        JsonObject request = new JsonObject();
        request.addProperty("device_code", deviceCode);
        post("pair/poll", request).whenComplete((response, error) -> {
            IN_FLIGHT.set(false);
            long nextPoll = 0L;
            synchronized (LOCK) {
                if (error != null) {
                    SimuKraft.LOGGER.warn("Sponsor auth pair/poll network failure: {}", rootMessage(error));
                    snapshot = snapshotFor(
                            SponsorAuthSnapshot.Phase.PAIRING,
                            "网络暂时不可用，稍后自动重试",
                            cachedLicense(epochSeconds())
                    );
                    nextPoll = Math.min(60L, Math.max(10L, STATE.pollInterval * 2L));
                } else if (response.status() == 200) {
                    try {
                        acceptLicense(response.body(), true);
                    } catch (Exception exception) {
                        SimuKraft.LOGGER.warn("Sponsor auth rejected pair/poll License", exception);
                        snapshot = snapshotFor(
                                SponsorAuthSnapshot.Phase.ERROR,
                                "官网已确认，但 License 验证失败",
                                cachedLicense(epochSeconds())
                        );
                    }
                } else {
                    String code = string(response.body(), "error");
                    if (!"authorization_pending".equals(code)) logApiFailure("pair/poll", response);
                    switch (code) {
                        case "authorization_pending" -> {
                            snapshot = snapshotFor(
                                    SponsorAuthSnapshot.Phase.PAIRING,
                                    "等待官网确认绑定",
                                    cachedLicense(epochSeconds())
                            );
                            nextPoll = STATE.pollInterval;
                        }
                        case "slow_down" -> {
                            STATE.pollInterval = Math.min(60L, STATE.pollInterval + 5L);
                            STORAGE.save(STATE);
                            nextPoll = STATE.pollInterval;
                        }
                        case "temporarily_unavailable" -> {
                            snapshot = snapshotFor(
                                    SponsorAuthSnapshot.Phase.PAIRING,
                                    errorMessage(response.body(), "授权服务暂时不可用"),
                                    cachedLicense(epochSeconds())
                            );
                            nextPoll = Math.min(60L, Math.max(15L, STATE.pollInterval * 2L));
                        }
                        default -> {
                            STATE.clearPairing();
                            STORAGE.save(STATE);
                            snapshot = snapshotFor(
                                    SponsorAuthSnapshot.Phase.ERROR,
                                    errorMessage(response.body(), "绑定被拒绝或已过期"),
                                    cachedLicense(epochSeconds())
                            );
                        }
                    }
                }
            }
            if (nextPoll > 0L) schedulePoll(nextPoll);
        });
    }

    private static void acceptLicense(JsonObject body, boolean fromPairing) throws Exception {
        if (!ensureRuntimeTrusted()) throw new IllegalStateException("Untrusted runtime");
        JsonObject key = body.getAsJsonObject("license_key");
        String license = string(body, "license");
        if (key == null || license.isBlank()) throw new IllegalArgumentException("Missing License response");

        SponsorAuthStorage.StoredState candidate = new SponsorAuthStorage.StoredState();
        candidate.installId = STATE.installId;
        candidate.keyId = string(key, "kid");
        candidate.keyN = string(key, "n");
        candidate.keyE = string(key, "e");
        var verified = SponsorLicenseVerifier.verify(license, candidate, BUILD, epochSeconds(), ISSUER);

        STATE.keyId = candidate.keyId;
        STATE.keyN = candidate.keyN;
        STATE.keyE = candidate.keyE;
        STATE.license = license;
        STATE.refreshAfter = verified.refreshAfter();
        STATE.expiresAt = verified.expiresAt();
        STATE.lastOnlineCheck = epochSeconds();
        if (fromPairing) {
            if (STATE.pendingDeviceToken.isBlank()) {
                throw new IllegalStateException("Pairing credential was not persisted");
            }
            STATE.deviceToken = STATE.pendingDeviceToken;
            STATE.clearPairing();
        }
        STORAGE.save(STATE);
        snapshot = snapshotFor(SponsorAuthSnapshot.Phase.AUTHORIZED, "赞助授权有效", verified);
        long nextCheck = Math.min(
                verified.refreshAfter(),
                STATE.lastOnlineCheck + ONLINE_CHECK_INTERVAL_SECONDS
        );
        scheduleRefresh(Math.max(1L, nextCheck - epochSeconds()));
    }

    private static void keepCachedOrError(String message) {
        if (!ensureRuntimeTrusted()) return;
        var verified = cachedLicense(epochSeconds());
        snapshot = snapshotFor(
                verified == null ? SponsorAuthSnapshot.Phase.ERROR : SponsorAuthSnapshot.Phase.AUTHORIZED,
                message,
                verified
        );
        long retrySeconds = verified == null
                ? 60L
                : Math.max(60L, Math.min(15L * 60L, verified.expiresAt() - epochSeconds()));
        scheduleRefresh(retrySeconds);
    }

    private static SponsorLicenseVerifier.VerifiedLicense cachedLicense(long now) {
        if (STATE.license.isBlank() || STATE.keyId.isBlank() || STATE.keyN.isBlank() || STATE.keyE.isBlank()) {
            return null;
        }
        if (STATE.lastOnlineCheck > 0L && now + 300L < STATE.lastOnlineCheck) {
            return null;
        }
        try {
            var verified = SponsorLicenseVerifier.verify(STATE.license, STATE, BUILD, now, ISSUER);
            STATE.refreshAfter = verified.refreshAfter();
            STATE.expiresAt = verified.expiresAt();
            return verified;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static SponsorAuthSnapshot snapshotFor(
            SponsorAuthSnapshot.Phase phase,
            String message,
            SponsorLicenseVerifier.VerifiedLicense verified
    ) {
        return new SponsorAuthSnapshot(
                phase,
                message,
                STATE.userCode,
                STATE.verificationUri,
                STATE.pairExpiresAt,
                verified == null ? "" : verified.tier(),
                verified == null ? Set.of() : verified.entitlements(),
                verified == null ? 0L : verified.expiresAt()
        );
    }

    private static void schedulePoll(long seconds) {
        EXECUTOR.schedule(SponsorAuthManager::pollPairing, Math.max(1L, seconds), TimeUnit.SECONDS);
    }

    private static void scheduleRefresh(long seconds) {
        long generation = REFRESH_GENERATION.incrementAndGet();
        EXECUTOR.schedule(() -> {
            if (generation == REFRESH_GENERATION.get()) refreshNow();
        }, Math.max(1L, seconds), TimeUnit.SECONDS);
    }

    private static CompletableFuture<ApiResponse> post(String path, JsonObject body) {
        HttpRequest request = HttpRequest.newBuilder(API_BASE.resolve(path))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
                .thenApply(response -> {
                    String responseText;
                    try (InputStream input = response.body()) {
                        byte[] bytes = input.readNBytes(MAX_RESPONSE_BYTES + 1);
                        if (bytes.length > MAX_RESPONSE_BYTES) {
                            throw new IllegalStateException("Authorization response is too large");
                        }
                        responseText = new String(bytes, StandardCharsets.UTF_8);
                    } catch (IOException exception) {
                        throw new CompletionException(exception);
                    }
                    JsonObject json;
                    try {
                        json = JsonParser.parseString(responseText).getAsJsonObject();
                    } catch (RuntimeException exception) {
                        json = new JsonObject();
                        json.addProperty("error_description", "授权服务返回了无效响应");
                    }
                    return new ApiResponse(response.statusCode(), json);
                });
    }

    private static String randomSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String currentComputerName() {
        String name = System.getenv("COMPUTERNAME");
        if (name == null || name.isBlank()) name = System.getenv("HOSTNAME");
        return sanitizeLabel(name, "Minecraft 客户端", 64);
    }

    private static String sanitizeLabel(String value, String fallback, int maxLength) {
        String text = value == null ? "" : value.replaceAll("[\\p{Cntrl}]", "").trim();
        if (text.isEmpty()) text = fallback;
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private static String sha256Base64Url(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String errorMessage(JsonObject body, String fallback) {
        String description = string(body, "error_description");
        if (!description.isBlank()) return description;
        String code = string(body, "error");
        return code.isBlank() ? fallback : fallback + "（" + code + "）";
    }

    private static void logApiFailure(String operation, ApiResponse response) {
        SimuKraft.LOGGER.warn(
                "Sponsor auth {} failed: HTTP {}, code={}, detail={}",
                operation,
                response.status(),
                string(response.body(), "error"),
                string(response.body(), "error_description")
        );
    }

    private static String string(JsonObject object, String key) {
        return object != null && object.has(key) && !object.get(key).isJsonNull()
                ? object.get(key).getAsString()
                : "";
    }

    private static long number(JsonObject object, String key) {
        return object != null && object.has(key) && !object.get(key).isJsonNull()
                ? object.get(key).getAsLong()
                : 0L;
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    private static long epochSeconds() {
        return System.currentTimeMillis() / 1000L;
    }

    private record ApiResponse(int status, JsonObject body) {
    }
}
