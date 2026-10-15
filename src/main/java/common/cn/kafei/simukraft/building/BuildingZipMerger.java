package common.cn.kafei.simukraft.building;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** BuildingZipMerger: 把拖进建筑包目录的 zip 解压并合并进官方建筑文件夹。 */
public final class BuildingZipMerger {
    private static final Set<String> CATEGORIES = Set.of("residential", "commercial", "industry", "public", "other");
    private static final String IMPORTED_DIR = ".imported";

    private BuildingZipMerger() {
    }

    /**
     * mergeDroppedZips: 按文件名顺序把根目录里的 zip 合并进 {@code official_building}。
     * 同相对路径的文件用 zip 中的版本覆盖。成功后把 zip 移到 {@code .imported}。
     * 已有的 {@code official_building.zip} 不参与合并，避免旧官方包盖掉目录里的新文件。
     */
    public static MergeResult mergeDroppedZips(Path rootDirectory) {
        if (rootDirectory == null || !Files.isDirectory(rootDirectory)) {
            return MergeResult.EMPTY;
        }
        List<Path> zips;
        try (var stream = Files.list(rootDirectory)) {
            zips = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip"))
                    .filter(path -> !isLegacyOfficialZip(path))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .toList();
        } catch (IOException exception) {
            return MergeResult.EMPTY;
        }
        if (zips.isEmpty()) {
            return MergeResult.EMPTY;
        }
        Path target = rootDirectory.resolve(BuildingPackageCatalog.OFFICIAL_PACKAGE_NAME).normalize();
        if (!target.startsWith(rootDirectory.toAbsolutePath().normalize())) {
            return MergeResult.EMPTY;
        }
        int zipCount = 0;
        int fileCount = 0;
        List<String> names = new ArrayList<>();
        for (Path zip : zips) {
            int copied = mergeOne(zip, target);
            if (copied <= 0) {
                continue;
            }
            if (archiveZip(rootDirectory, zip)) {
                zipCount++;
                fileCount += copied;
                names.add(zip.getFileName().toString());
            }
        }
        return new MergeResult(zipCount, fileCount, List.copyOf(names));
    }

    private static boolean isLegacyOfficialZip(Path zip) {
        return (BuildingPackageCatalog.OFFICIAL_PACKAGE_NAME + ".zip")
                .equalsIgnoreCase(zip.getFileName().toString());
    }

    private static int mergeOne(Path zip, Path target) {
        int copied = 0;
        try (ZipFile zipFile = new ZipFile(zip.toFile(), StandardCharsets.UTF_8)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                Path relative = buildingRelativePath(entry.getName());
                if (relative == null) {
                    continue;
                }
                Path dest = target.resolve(relative).normalize();
                if (!dest.startsWith(target)) {
                    continue;
                }
                Files.createDirectories(dest.getParent());
                try (InputStream inputStream = zipFile.getInputStream(entry)) {
                    Files.copy(inputStream, dest, StandardCopyOption.REPLACE_EXISTING);
                }
                copied++;
            }
        } catch (IOException exception) {
            return 0;
        }
        return copied;
    }

    /** buildingRelativePath: 接受 buildings/分类/文件，也接受直接以分类开头的文件。 */
    static Path buildingRelativePath(String entryName) {
        if (entryName == null || entryName.isBlank()) {
            return null;
        }
        String name = entryName.replace('\\', '/');
        while (name.startsWith("/")) {
            name = name.substring(1);
        }
        if (name.contains("..")) {
            return null;
        }
        String[] parts = name.split("/");
        String category;
        String fileName;
        if (parts.length == 3 && "buildings".equalsIgnoreCase(parts[0])) {
            category = parts[1];
            fileName = parts[2];
        } else if (parts.length == 2) {
            category = parts[0];
            fileName = parts[1];
        } else {
            return null;
        }
        String normalizedCategory = category.toLowerCase(Locale.ROOT);
        if ("commerce".equals(normalizedCategory)) {
            normalizedCategory = "commercial";
        }
        if ("medical".equals(normalizedCategory)) {
            normalizedCategory = "public";
        }
        if (!CATEGORIES.contains(normalizedCategory) || !isBuildingFile(fileName)) {
            return null;
        }
        return Path.of("buildings", normalizedCategory, fileName);
    }

    private static boolean isBuildingFile(String fileName) {
        if (fileName == null || fileName.isBlank() || fileName.contains("/") || fileName.contains("\\")) {
            return false;
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".sk")
                || lower.endsWith(".nbt")
                || lower.endsWith(".json")
                || "_files.txt".equals(lower);
    }

    private static boolean archiveZip(Path rootDirectory, Path zip) {
        try {
            Path imported = rootDirectory.resolve(IMPORTED_DIR);
            Files.createDirectories(imported);
            Path dest = imported.resolve(zip.getFileName().toString()).normalize();
            if (!dest.startsWith(imported.toAbsolutePath().normalize())) {
                return false;
            }
            if (Files.exists(dest)) {
                String stamped = System.currentTimeMillis() + "-" + zip.getFileName();
                dest = imported.resolve(stamped).normalize();
            }
            Files.move(zip, dest, StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (IOException atomicFailed) {
            try {
                Path imported = rootDirectory.resolve(IMPORTED_DIR);
                Files.createDirectories(imported);
                Path dest = imported.resolve(System.currentTimeMillis() + "-" + zip.getFileName()).normalize();
                Files.move(zip, dest);
                return true;
            } catch (IOException exception) {
                return false;
            }
        }
    }

    public record MergeResult(int zipCount, int fileCount, List<String> zipNames) {
        static final MergeResult EMPTY = new MergeResult(0, 0, List.of());

        public MergeResult {
            zipNames = zipNames == null ? List.of() : List.copyOf(zipNames);
        }
    }
}
