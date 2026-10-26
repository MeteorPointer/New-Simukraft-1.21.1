package client.cn.nsimu.simukraft.client.auth;

import com.sun.jna.platform.win32.Crypt32Util;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SponsorSecretProtector {
    static final String DPAPI_PREFIX = "dpapi:v1:";
    static final String BOUND_PREFIX = "bound:v1:";
    static final String PLAIN_PREFIX = "plain:v1:";
    private static final int CRYPTPROTECT_UI_FORBIDDEN = 0x1;
    private static final int GCM_TAG_BITS = 128;
    private static final int GCM_NONCE_BYTES = 12;
    private static final byte[] ENTROPY =
            "nsuk.simukraft.sponsor-auth.v1".getBytes(StandardCharsets.UTF_8);
    private static final String OS =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    private static final boolean WINDOWS = OS.contains("win");
    private static final boolean MACOS = OS.contains("mac");
    private static final boolean LINUX = OS.contains("linux");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static volatile byte[] cachedMachineKey;

    private SponsorSecretProtector() {
    }

    static boolean usesOsProtection() {
        return WINDOWS || MACOS || LINUX;
    }

    static String protect(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) return "";
        byte[] bytes = plaintext.getBytes(StandardCharsets.UTF_8);
        if (WINDOWS) {
            byte[] protectedBytes = Crypt32Util.cryptProtectData(
                    bytes,
                    ENTROPY,
                    CRYPTPROTECT_UI_FORBIDDEN,
                    "NSUK sponsor authorization",
                    null
            );
            return DPAPI_PREFIX + Base64.getEncoder().encodeToString(protectedBytes);
        }
        if (MACOS || LINUX) {
            return BOUND_PREFIX + Base64.getEncoder().encodeToString(encryptBound(bytes));
        }
        throw new IllegalStateException("No supported OS credential protection backend");
    }

    static String unprotect(String stored) {
        if (stored == null || stored.isBlank()) return "";
        if (stored.startsWith(DPAPI_PREFIX)) {
            if (!WINDOWS) throw new IllegalStateException("DPAPI data was copied to a non-Windows system");
            byte[] encrypted = Base64.getDecoder().decode(stored.substring(DPAPI_PREFIX.length()));
            byte[] plaintext = Crypt32Util.cryptUnprotectData(
                    encrypted,
                    ENTROPY,
                    CRYPTPROTECT_UI_FORBIDDEN,
                    null
            );
            return new String(plaintext, StandardCharsets.UTF_8);
        }
        if (stored.startsWith(BOUND_PREFIX)) {
            if (!MACOS && !LINUX) {
                throw new IllegalStateException("Machine-bound data was copied to another operating system");
            }
            byte[] encoded = Base64.getDecoder().decode(stored.substring(BOUND_PREFIX.length()));
            return new String(decryptBound(encoded), StandardCharsets.UTF_8);
        }
        if (stored.startsWith(PLAIN_PREFIX)) {
            return new String(
                    Base64.getDecoder().decode(stored.substring(PLAIN_PREFIX.length())),
                    StandardCharsets.UTF_8
            );
        }
        // v1 plaintext format: load once, then SponsorAuthStorage.save migrates it.
        return stored;
    }

    private static byte[] encryptBound(byte[] plaintext) {
        try {
            byte[] nonce = new byte[GCM_NONCE_BYTES];
            RANDOM.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(machineKey(), "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, nonce)
            );
            cipher.updateAAD(ENTROPY);
            byte[] ciphertext = cipher.doFinal(plaintext);
            byte[] output = new byte[nonce.length + ciphertext.length];
            System.arraycopy(nonce, 0, output, 0, nonce.length);
            System.arraycopy(ciphertext, 0, output, nonce.length, ciphertext.length);
            return output;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to encrypt machine-bound sponsor credential", exception);
        }
    }

    private static byte[] decryptBound(byte[] encoded) {
        if (encoded.length <= GCM_NONCE_BYTES) {
            throw new IllegalArgumentException("Machine-bound sponsor credential is truncated");
        }
        try {
            byte[] nonce = java.util.Arrays.copyOfRange(encoded, 0, GCM_NONCE_BYTES);
            byte[] ciphertext = java.util.Arrays.copyOfRange(
                    encoded,
                    GCM_NONCE_BYTES,
                    encoded.length
            );
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    new SecretKeySpec(machineKey(), "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, nonce)
            );
            cipher.updateAAD(ENTROPY);
            return cipher.doFinal(ciphertext);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Failed to decrypt machine-bound sponsor credential",
                    exception
            );
        }
    }

    private static byte[] machineKey() {
        byte[] cached = cachedMachineKey;
        if (cached != null) return cached.clone();
        synchronized (SponsorSecretProtector.class) {
            if (cachedMachineKey == null) {
                try {
                    MessageDigest digest = MessageDigest.getInstance("SHA-256");
                    digest.update(ENTROPY);
                    digest.update((byte) 0);
                    digest.update(machineIdentity().getBytes(StandardCharsets.UTF_8));
                    digest.update((byte) 0);
                    digest.update(
                            System.getProperty("user.name", "").getBytes(StandardCharsets.UTF_8)
                    );
                    cachedMachineKey = digest.digest();
                } catch (Exception exception) {
                    throw new IllegalStateException("Failed to derive machine-bound key", exception);
                }
            }
            return cachedMachineKey.clone();
        }
    }

    private static String machineIdentity() throws Exception {
        if (LINUX) {
            for (Path candidate : new Path[]{
                    Path.of("/etc/machine-id"),
                    Path.of("/var/lib/dbus/machine-id")
            }) {
                if (!Files.isRegularFile(candidate)) continue;
                String value = Files.readString(candidate, StandardCharsets.UTF_8).trim();
                if (value.matches("[A-Fa-f0-9-]{16,}")) return "linux:" + value;
            }
            throw new IllegalStateException("Linux machine-id is unavailable");
        }
        if (MACOS) {
            Process process = new ProcessBuilder(
                    "/usr/sbin/ioreg",
                    "-rd1",
                    "-c",
                    "IOPlatformExpertDevice"
            )
                    .redirectErrorStream(true)
                    .start();
            if (!process.waitFor(3, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("Timed out reading macOS IOPlatformUUID");
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            Matcher matcher = Pattern.compile(
                    "\"IOPlatformUUID\"\\s*=\\s*\"([^\"]+)\""
            ).matcher(output);
            if (matcher.find()) return "macos:" + matcher.group(1);
            throw new IllegalStateException("macOS IOPlatformUUID is unavailable");
        }
        throw new IllegalStateException("Unsupported operating system");
    }
}
