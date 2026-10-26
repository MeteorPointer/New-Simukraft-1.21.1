package client.cn.nsimu.simukraft.client.auth;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.nio.file.attribute.UserPrincipalNotFoundException;
import java.util.EnumSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

final class SponsorAuthStorage {
    private static final int STORAGE_VERSION = 2;
    private static final System.Logger LOGGER = System.getLogger("SimuKraft SponsorAuth");
    private final Path path;

    SponsorAuthStorage() {
        this(resolvePath());
    }

    SponsorAuthStorage(Path path) {
        this.path = path.toAbsolutePath().normalize();
    }

    synchronized StoredState load() {
        Properties properties = new Properties();
        if (Files.isRegularFile(path)) {
            try (InputStream input = Files.newInputStream(path)) {
                properties.load(input);
            } catch (IOException exception) {
                LOGGER.log(System.Logger.Level.WARNING, "Failed to read sponsor auth state", exception);
            }
        }

        boolean[] protectionError = {false};
        StoredState state = new StoredState();
        state.installId = properties.getProperty("install_id", "").trim();
        if (!isUuid(state.installId)) {
            state.installId = UUID.randomUUID().toString();
        }
        state.deviceToken = secret(properties, "device_token", protectionError);
        state.pendingDeviceToken = secret(properties, "pending_device_token", protectionError);
        state.license = secret(properties, "license", protectionError);
        state.refreshAfter = longValue(properties, "refresh_after");
        state.expiresAt = longValue(properties, "expires_at");
        state.lastOnlineCheck = longValue(properties, "last_online_check");
        state.keyId = properties.getProperty("key_id", "").trim();
        state.keyN = properties.getProperty("key_n", "").trim();
        state.keyE = properties.getProperty("key_e", "").trim();
        state.pairDeviceCode = secret(properties, "pair_device_code", protectionError);
        state.userCode = secret(properties, "user_code", protectionError);
        state.verificationUri = secret(properties, "verification_uri", protectionError);
        state.pairExpiresAt = longValue(properties, "pair_expires_at");
        state.pollInterval = Math.max(5, longValue(properties, "poll_interval"));

        if (protectionError[0]) {
            state.clearAuthorization();
            state.protectionError = true;
            restrictExistingPath();
            return state;
        }

        // Also migrates the v1 plaintext format on first successful read.
        save(state);
        return state;
    }

    synchronized void save(StoredState state) {
        try {
            Properties properties = new Properties();
            put(properties, "storage_version", STORAGE_VERSION);
            put(properties, "install_id", state.installId);
            putSecret(properties, "device_token", state.deviceToken);
            putSecret(properties, "pending_device_token", state.pendingDeviceToken);
            putSecret(properties, "license", state.license);
            put(properties, "refresh_after", state.refreshAfter);
            put(properties, "expires_at", state.expiresAt);
            put(properties, "last_online_check", state.lastOnlineCheck);
            put(properties, "key_id", state.keyId);
            put(properties, "key_n", state.keyN);
            put(properties, "key_e", state.keyE);
            putSecret(properties, "pair_device_code", state.pairDeviceCode);
            putSecret(properties, "user_code", state.userCode);
            putSecret(properties, "verification_uri", state.verificationUri);
            put(properties, "pair_expires_at", state.pairExpiresAt);
            put(properties, "poll_interval", state.pollInterval);

            Path directory = path.getParent();
            Files.createDirectories(directory);
            restrictPermissions(directory, true);
            Path temp = Files.createTempFile(directory, path.getFileName() + ".", ".tmp");
            try {
                try (OutputStream output = Files.newOutputStream(temp)) {
                    properties.store(output, "New: Sim-U-Kraft sponsor authorization");
                }
                restrictPermissions(temp, false);
                try {
                    Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
                }
                restrictPermissions(path, false);
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException | RuntimeException | LinkageError exception) {
            LOGGER.log(System.Logger.Level.WARNING, "Failed to persist protected sponsor auth state", exception);
        }
    }

    private void restrictExistingPath() {
        try {
            if (Files.isDirectory(path.getParent())) restrictPermissions(path.getParent(), true);
            if (Files.isRegularFile(path)) restrictPermissions(path, false);
        } catch (IOException | RuntimeException exception) {
            LOGGER.log(System.Logger.Level.WARNING, "Failed to restrict sponsor auth path", exception);
        }
    }

    private static String secret(
            Properties properties,
            String key,
            boolean[] protectionError
    ) {
        String stored = properties.getProperty(key, "").trim();
        if (stored.isEmpty()) return "";
        try {
            return SponsorSecretProtector.unprotect(stored);
        } catch (RuntimeException | LinkageError exception) {
            protectionError[0] = true;
            LOGGER.log(
                    System.Logger.Level.WARNING,
                    "Sponsor auth secret cannot be decrypted; the file may come from another computer: " + key,
                    exception
            );
            return "";
        }
    }

    private static void putSecret(Properties properties, String key, String value) {
        if (value != null && !value.isBlank()) {
            properties.setProperty(key, SponsorSecretProtector.protect(value));
        }
    }

    private static void restrictPermissions(Path target, boolean directory) throws IOException {
        try {
            Set<PosixFilePermission> ownerOnly = directory
                    ? EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE
            )
                    : EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE
            );
            Files.setPosixFilePermissions(target, ownerOnly);
            return;
        } catch (UnsupportedOperationException ignored) {
            // Windows: apply a DACL below.
        }

        AclFileAttributeView view = Files.getFileAttributeView(target, AclFileAttributeView.class);
        if (view == null) return;
        UserPrincipal owner = currentUserPrincipal(target);
        AclEntry ownerEntry = AclEntry.newBuilder()
                .setType(AclEntryType.ALLOW)
                .setPrincipal(owner)
                .setPermissions(EnumSet.allOf(AclEntryPermission.class))
                .build();
        view.setAcl(List.of(ownerEntry));
    }

    private static UserPrincipal currentUserPrincipal(Path target) throws IOException {
        String username = System.getenv("USERNAME");
        if (username == null || username.isBlank()) username = System.getProperty("user.name", "");
        String domain = System.getenv("USERDOMAIN");
        for (String candidate : new String[]{
                domain == null || domain.isBlank() ? "" : domain + "\\" + username,
                username
        }) {
            if (candidate.isBlank()) continue;
            try {
                return FileSystems.getDefault()
                        .getUserPrincipalLookupService()
                        .lookupPrincipalByName(candidate);
            } catch (UserPrincipalNotFoundException ignored) {
                // Try the next spelling, then fall back to the file owner.
            }
        }
        return Files.getOwner(target);
    }

    private static Path resolvePath() {
        String override = System.getProperty("simukraft.auth.file", "").trim();
        if (!override.isEmpty()) return Path.of(override);
        return Path.of(System.getProperty("user.home"), ".nsuk", "simukraft", "auth.properties");
    }

    private static boolean isUuid(String value) {
        try {
            return UUID.fromString(value).toString().equalsIgnoreCase(value);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static long longValue(Properties properties, String key) {
        try {
            return Long.parseLong(properties.getProperty(key, "0"));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private static void put(Properties properties, String key, String value) {
        if (value != null && !value.isBlank()) properties.setProperty(key, value);
    }

    private static void put(Properties properties, String key, long value) {
        if (value > 0L) properties.setProperty(key, Long.toString(value));
    }

    static final class StoredState {
        String installId = "";
        String deviceToken = "";
        String pendingDeviceToken = "";
        String license = "";
        long refreshAfter;
        long expiresAt;
        long lastOnlineCheck;
        String keyId = "";
        String keyN = "";
        String keyE = "";
        String pairDeviceCode = "";
        String userCode = "";
        String verificationUri = "";
        long pairExpiresAt;
        long pollInterval = 5L;
        boolean protectionError;

        void clearPairing() {
            pendingDeviceToken = "";
            pairDeviceCode = "";
            userCode = "";
            verificationUri = "";
            pairExpiresAt = 0L;
            pollInterval = 5L;
        }

        void clearAuthorization() {
            deviceToken = "";
            license = "";
            refreshAfter = 0L;
            expiresAt = 0L;
            lastOnlineCheck = 0L;
            protectionError = false;
            clearPairing();
        }
    }
}
