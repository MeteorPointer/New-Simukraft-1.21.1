package client.cn.nsimu.simukraft.client.auth;

import com.google.gson.JsonObject;
import common.cn.nsimu.simukraft.SimuKraft;
import net.neoforged.fml.ModList;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Properties;

record SponsorBuildIdentity(
        String minecraftVersion,
        String modVersion,
        String artifactSha256,
        Path artifactPath,
        String error
) {
    private static final String BUILD_MARKER = "/META-INF/simukraft-build.properties";

    static SponsorBuildIdentity current() {
        try {
            Properties marker = new Properties();
            try (InputStream stream = SponsorBuildIdentity.class.getResourceAsStream(BUILD_MARKER)) {
                if (stream == null) throw new IllegalStateException("missing build marker");
                marker.load(stream);
            }
            String minecraftVersion = marker.getProperty("minecraft.version", "").trim();
            String modVersion = marker.getProperty("mod.version", "").trim();
            Path artifact = ModList.get()
                    .getModFileById(SimuKraft.MOD_ID)
                    .getFile()
                    .getFilePath()
                    .toAbsolutePath()
                    .normalize();
            if (!Files.isRegularFile(artifact)) throw new IllegalStateException("not running from a packaged JAR");
            return new SponsorBuildIdentity(minecraftVersion, modVersion, sha256(artifact), artifact, "");
        } catch (Exception exception) {
            return new SponsorBuildIdentity("", "", "", null, rootMessage(exception));
        }
    }

    boolean available() {
        return error.isEmpty()
                && !minecraftVersion.isEmpty()
                && !modVersion.isEmpty()
                && artifactPath != null
                && artifactSha256.matches("[0-9a-f]{64}");
    }

    void addTo(JsonObject request) {
        request.addProperty("minecraft_version", minecraftVersion);
        request.addProperty("mod_version", modVersion);
        request.addProperty("artifact_sha256", artifactSha256);
    }

    static String sha256(Path artifact) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new BufferedInputStream(Files.newInputStream(artifact))) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }
}
