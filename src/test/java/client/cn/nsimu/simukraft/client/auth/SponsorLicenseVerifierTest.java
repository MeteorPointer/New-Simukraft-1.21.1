package client.cn.nsimu.simukraft.client.auth;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SponsorLicenseVerifierTest {
    private static final Base64.Encoder URL = Base64.getUrlEncoder().withoutPadding();

    @Test
    void verifiesSignedLicenseAndRejectsAnotherInstallation() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        var publicKey = (RSAPublicKey) pair.getPublic();

        long now = 1_800_000_000L;
        JsonObject claims = new JsonObject();
        claims.addProperty("iss", "https://new.nsimu.cn");
        claims.addProperty("aud", "nsuk-mod");
        claims.addProperty("sub", "user");
        claims.addProperty("jti", "license");
        claims.addProperty("iat", now - 10);
        claims.addProperty("refresh_after", now + 100);
        claims.addProperty("exp", now + 200);
        claims.addProperty("mod_id", "simukraft");
        claims.addProperty("license_version", 3);
        claims.addProperty("install_id", "11111111-1111-4111-8111-111111111111");
        claims.addProperty("credential_version", 1);
        claims.addProperty("build_id", "simukraft-test");
        claims.addProperty("artifact_sha256", "a".repeat(64));
        claims.addProperty("minecraft_version", "1.21.1");
        claims.addProperty("mod_version", "test");
        claims.addProperty("tier", "sponsor");
        JsonArray entitlements = new JsonArray();
        entitlements.add("sponsor");
        claims.add("entitlements", entitlements);

        JsonObject header = new JsonObject();
        header.addProperty("alg", "RS256");
        header.addProperty("typ", "JWT");
        header.addProperty("kid", "test-key");
        String signingInput = encode(header.toString()) + "." + encode(claims.toString());
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(pair.getPrivate());
        signer.update(signingInput.getBytes(StandardCharsets.US_ASCII));
        String jwt = signingInput + "." + URL.encodeToString(signer.sign());

        SponsorAuthStorage.StoredState state = new SponsorAuthStorage.StoredState();
        state.installId = "11111111-1111-4111-8111-111111111111";
        state.keyId = "test-key";
        state.keyN = unsigned(publicKey.getModulus().toByteArray());
        state.keyE = unsigned(publicKey.getPublicExponent().toByteArray());

        var build = new SponsorBuildIdentity("1.21.1", "test", "a".repeat(64), Path.of("test.jar"), "");
        var verified = SponsorLicenseVerifier.verify(
                jwt,
                state,
                build,
                now,
                "https://new.nsimu.cn",
                (keyId, modulus, exponent) -> true
        );
        assertEquals("sponsor", verified.tier());
        assertTrue(verified.entitlements().contains("sponsor"));

        state.installId = "22222222-2222-4222-8222-222222222222";
        assertThrows(
                IllegalArgumentException.class,
                () -> SponsorLicenseVerifier.verify(
                        jwt,
                        state,
                        build,
                        now,
                        "https://new.nsimu.cn",
                        (keyId, modulus, exponent) -> true
                )
        );
    }

    @Test
    void pinsTheProductionSigningKey() {
        assertTrue(SponsorLicenseTrust.isTrusted(
                "nsuk-rs256-1",
                "t-K8EzdkbpXWumVS2eC9kwr700W-SVHmEw3bu7dw2fdD9fiX4FM1Zvpaa7f0NKJvHMOEhnkNuN9c-HT8v3S7NGIyXeY9IAOcRO6abuLtyfYvqsjQ8k-uFf1cMn_Duc4bBsTnou8luoAaEDwN35TkKnXlZtyQQyoqonaTOqpIjZJUr00RLd9GKwAMpj9zqpETgILl5qfjFoWdIpnT5KfbdUd1zp75i_8RD_Fzqn3E2060QgVxngWqZUYSMqpqC4rdQuMS0sR7lEfYgwrzMsj544JgLIe94r63JEqxQyQk1ksGrUakeCIRUNJMHAl-zfJoPWplJAT0TR3DhvsicyQEvw",
                "AQAB"
        ));
        assertFalse(SponsorLicenseTrust.isTrusted("nsuk-rs256-1", "modified", "AQAB"));
    }

    private static String encode(String value) {
        return URL.encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String unsigned(byte[] value) {
        int start = value.length > 1 && value[0] == 0 ? 1 : 0;
        byte[] normalized = java.util.Arrays.copyOfRange(value, start, value.length);
        return URL.encodeToString(normalized);
    }
}
