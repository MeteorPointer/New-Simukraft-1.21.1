package client.cn.nsimu.simukraft.client.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SponsorBuildIdentityTest {
    @TempDir
    Path tempDir;

    @Test
    void hashesTheExactArtifactBytes() throws Exception {
        Path artifact = tempDir.resolve("sponsor.jar");
        Files.writeString(artifact, "NSUK official build");
        assertEquals(
                "1937f6981bb105aa3c474464067e08efab12f1014078cf0f2058bb7fd65e179c",
                SponsorBuildIdentity.sha256(artifact)
        );
    }
}
