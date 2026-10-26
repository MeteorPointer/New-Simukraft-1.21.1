package client.cn.nsimu.simukraft.client.auth;

import java.util.Set;

public record SponsorAuthSnapshot(
        Phase phase,
        String message,
        String userCode,
        String verificationUri,
        long pairingExpiresAt,
        String tier,
        Set<String> entitlements,
        long licenseExpiresAt
) {
    public SponsorAuthSnapshot {
        message = message == null ? "" : message;
        userCode = userCode == null ? "" : userCode;
        verificationUri = verificationUri == null ? "" : verificationUri;
        tier = tier == null ? "" : tier;
        entitlements = entitlements == null ? Set.of() : Set.copyOf(entitlements);
    }

    public boolean authorized() {
        return !entitlements.isEmpty() && licenseExpiresAt > System.currentTimeMillis() / 1000L;
    }

    public enum Phase {
        UNAUTHORIZED,
        PAIRING,
        REFRESHING,
        AUTHORIZED,
        ERROR
    }
}
