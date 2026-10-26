package client.cn.nsimu.simukraft.client.auth;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

final class SponsorLicenseVerifier {
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    private SponsorLicenseVerifier() {
    }

    static VerifiedLicense verify(
            String jwt,
            SponsorAuthStorage.StoredState state,
            SponsorBuildIdentity build,
            long nowEpochSeconds,
            String expectedIssuer
    ) throws Exception {
        return verify(jwt, state, build, nowEpochSeconds, expectedIssuer, SponsorLicenseTrust::isTrusted);
    }

    static VerifiedLicense verify(
            String jwt,
            SponsorAuthStorage.StoredState state,
            SponsorBuildIdentity build,
            long nowEpochSeconds,
            String expectedIssuer,
            KeyTrust keyTrust
    ) throws Exception {
        String[] parts = jwt.split("\\.");
        if (parts.length != 3) throw new IllegalArgumentException("Malformed License JWT");

        JsonObject header = json(parts[0]);
        if (!"RS256".equals(headerString(header, "alg"))
                || !"JWT".equals(headerString(header, "typ"))) {
            throw new IllegalArgumentException("Unsupported License algorithm");
        }
        String kid = headerString(header, "kid");
        if (kid.isBlank() || !kid.equals(state.keyId)) {
            throw new IllegalArgumentException("Unknown License key");
        }
        if (!keyTrust.isTrusted(state.keyId, state.keyN, state.keyE)) {
            throw new IllegalArgumentException("License key is not pinned by this client");
        }

        BigInteger modulus = new BigInteger(1, URL_DECODER.decode(state.keyN));
        BigInteger exponent = new BigInteger(1, URL_DECODER.decode(state.keyE));
        var publicKey = KeyFactory.getInstance("RSA")
                .generatePublic(new RSAPublicKeySpec(modulus, exponent));
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initVerify(publicKey);
        signature.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        if (!signature.verify(URL_DECODER.decode(parts[2]))) {
            throw new IllegalArgumentException("Invalid License signature");
        }

        JsonObject claims = json(parts[1]);
        long iat = claims.get("iat").getAsLong();
        long refreshAfter = claims.get("refresh_after").getAsLong();
        long exp = claims.get("exp").getAsLong();
        if (!expectedIssuer.equals(headerString(claims, "iss"))
                || !"nsuk-mod".equals(headerString(claims, "aud"))
                || !"simukraft".equals(headerString(claims, "mod_id"))
                || claims.get("license_version").getAsInt() != 3
                || !state.installId.equals(headerString(claims, "install_id"))) {
            throw new IllegalArgumentException("License claims do not match this installation");
        }
        if (headerString(claims, "sub").isBlank()
                || headerString(claims, "jti").isBlank()
                || claims.get("credential_version").getAsInt() < 1) {
            throw new IllegalArgumentException("License identity claims are invalid");
        }
        if (!build.available()
                || !build.artifactSha256().equals(headerString(claims, "artifact_sha256"))
                || !build.minecraftVersion().equals(headerString(claims, "minecraft_version"))
                || !build.modVersion().equals(headerString(claims, "mod_version"))
                || headerString(claims, "build_id").isBlank()) {
            throw new IllegalArgumentException("License does not match this official build");
        }
        if (iat > nowEpochSeconds + 300L || refreshAfter < iat || exp < refreshAfter || exp <= nowEpochSeconds) {
            throw new IllegalArgumentException("License time window is invalid or expired");
        }

        Set<String> entitlements = new HashSet<>();
        JsonArray values = claims.getAsJsonArray("entitlements");
        if (values != null) values.forEach(value -> entitlements.add(value.getAsString()));
        if (!entitlements.contains("sponsor")) {
            throw new IllegalArgumentException("License does not grant sponsor entitlement");
        }
        return new VerifiedLicense(
                refreshAfter,
                exp,
                headerString(claims, "tier"),
                Set.copyOf(entitlements)
        );
    }

    private static JsonObject json(String encoded) {
        return JsonParser.parseString(
                new String(URL_DECODER.decode(encoded), StandardCharsets.UTF_8)
        ).getAsJsonObject();
    }

    private static String headerString(JsonObject object, String key) {
        return object.has(key) ? object.get(key).getAsString() : "";
    }

    record VerifiedLicense(long refreshAfter, long expiresAt, String tier, Set<String> entitlements) {
    }

    @FunctionalInterface
    interface KeyTrust {
        boolean isTrusted(String keyId, String modulus, String exponent);
    }
}
