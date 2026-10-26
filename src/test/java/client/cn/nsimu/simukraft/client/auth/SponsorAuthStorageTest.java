package client.cn.nsimu.simukraft.client.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SponsorAuthStorageTest {
    @TempDir
    Path tempDirectory;

    @Test
    void migratesPlaintextSecretsAndRoundTripsProtectedState() throws Exception {
        Path file = tempDirectory.resolve("auth.properties");
        Files.writeString(
                file,
                "install_id=11111111-1111-4111-8111-111111111111\n"
                        + "device_token=device-secret\n"
                        + "pending_device_token=pending-secret\n"
                        + "license=signed-license\n"
                        + "pair_device_code=device-code\n"
                        + "user_code=ABCD-EFGH\n"
                        + "verification_uri=https\\://example.invalid/pair?code\\=ABCD-EFGH\n"
        );

        SponsorAuthStorage storage = new SponsorAuthStorage(file);
        SponsorAuthStorage.StoredState migrated = storage.load();
        assertEquals("device-secret", migrated.deviceToken);
        assertEquals("signed-license", migrated.license);
        assertFalse(migrated.protectionError);

        String persisted = Files.readString(file);
        assertTrue(persisted.contains("storage_version=2"));
        assertFalse(persisted.contains("device-secret"));
        assertFalse(persisted.contains("signed-license"));
        if (SponsorSecretProtector.usesOsProtection()) {
            Properties encoded = new Properties();
            try (var input = Files.newInputStream(file)) {
                encoded.load(input);
            }
            assertTrue(encoded.values().stream().anyMatch(
                    value -> String.valueOf(value).startsWith(SponsorSecretProtector.DPAPI_PREFIX)
            ));
        }

        SponsorAuthStorage.StoredState reloaded = new SponsorAuthStorage(file).load();
        assertEquals(migrated.installId, reloaded.installId);
        assertEquals("device-secret", reloaded.deviceToken);
        assertEquals("pending-secret", reloaded.pendingDeviceToken);
        assertEquals("signed-license", reloaded.license);
        assertEquals("device-code", reloaded.pairDeviceCode);
        assertEquals("ABCD-EFGH", reloaded.userCode);
        assertEquals("https://example.invalid/pair?code=ABCD-EFGH", reloaded.verificationUri);
        assertFalse(reloaded.protectionError);
    }

    @Test
    void rejectsCredentialsThatCannotBeDecryptedOnThisMachine() throws Exception {
        Path file = tempDirectory.resolve("copied-auth.properties");
        Properties copied = new Properties();
        copied.setProperty("install_id", "22222222-2222-4222-8222-222222222222");
        copied.setProperty("device_token", SponsorSecretProtector.DPAPI_PREFIX + "AAAA");
        try (var output = Files.newOutputStream(file)) {
            copied.store(output, "simulated copied credential");
        }

        SponsorAuthStorage.StoredState state = new SponsorAuthStorage(file).load();
        assertTrue(state.protectionError);
        assertTrue(state.deviceToken.isEmpty());
        assertTrue(state.license.isEmpty());
    }
}
