package common.cn.nsimu.simukraft.building;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class BuildingZipMergerTest {
    @TempDir
    Path tempDir;

    @Test
    void droppedZipMergesIntoOfficialFolderAndIsArchived() throws Exception {
        Path official = tempDir.resolve("official_building");
        Files.createDirectories(official.resolve("buildings/commercial"));
        Files.writeString(official.resolve("buildings/commercial/bar.sk"), "name:旧酒馆\n", StandardCharsets.UTF_8);
        writeZip(tempDir.resolve("custom_pack.zip"),
                "buildings/commercial/bar.sk", "name:新酒馆\nname_en:New Tavern\n",
                "buildings/residential/cottage.nbt", "nbt",
                "../escape.sk", "nope",
                "buildings/commercial/nested/hidden.sk", "nope");

        BuildingZipMerger.MergeResult result = BuildingZipMerger.mergeDroppedZips(tempDir);

        assertEquals(1, result.zipCount());
        assertEquals(2, result.fileCount());
        assertEquals("name:新酒馆\nname_en:New Tavern\n",
                Files.readString(official.resolve("buildings/commercial/bar.sk"), StandardCharsets.UTF_8));
        assertTrue(Files.exists(official.resolve("buildings/residential/cottage.nbt")));
        assertFalse(Files.exists(tempDir.resolve("custom_pack.zip")));
        assertTrue(Files.exists(tempDir.resolve(".imported/custom_pack.zip")));
        assertFalse(Files.exists(official.resolve("escape.sk")));
    }

    @Test
    void categoryPrefixWithoutBuildingsRootIsAccepted() throws Exception {
        writeZip(tempDir.resolve("flat.zip"), "industry/mill.sk", "name:Mill\n");

        BuildingZipMerger.MergeResult result = BuildingZipMerger.mergeDroppedZips(tempDir);

        assertEquals(1, result.zipCount());
        assertTrue(Files.exists(tempDir.resolve("official_building/buildings/industry/mill.sk")));
    }

    @Test
    void legacyOfficialZipStaysBesideTheFolder() throws Exception {
        writeZip(tempDir.resolve("official_building.zip"), "buildings/commercial/bar.sk", "name:old\n");

        BuildingZipMerger.MergeResult result = BuildingZipMerger.mergeDroppedZips(tempDir);

        assertEquals(0, result.zipCount());
        assertTrue(Files.exists(tempDir.resolve("official_building.zip")));
        assertFalse(Files.exists(tempDir.resolve("official_building/buildings/commercial/bar.sk")));
    }

    private static void writeZip(Path zip, String... namesAndContents) throws Exception {
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            for (int i = 0; i < namesAndContents.length; i += 2) {
                output.putNextEntry(new ZipEntry(namesAndContents[i]));
                output.write(namesAndContents[i + 1].getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
    }
}
