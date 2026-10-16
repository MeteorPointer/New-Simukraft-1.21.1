package common.cn.nsimu.simukraft.building;

import common.cn.nsimu.simukraft.SimuKraft;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class BuildingBuiltinResourceService {
    private static final String RESOURCE_ROOT = "assets/simukraft/building/";
    private static final String PACK_JSON = RESOURCE_ROOT + "pack.json";
    private static final Set<String> COPIED_ROOTS = ConcurrentHashMap.newKeySet();

    private BuildingBuiltinResourceService() {
    }

    /**
     * ensureCopied: 将官方建筑目录复制到建筑包目录，已存在时不覆盖。
     */
    public static void ensureCopied(Path rootDirectory) {
        if (rootDirectory == null) {
            return;
        }
        Path normalizedRoot = rootDirectory.toAbsolutePath().normalize();
        String rootKey = normalizedRoot.toString().toLowerCase(Locale.ROOT);
        if (COPIED_ROOTS.contains(rootKey)) {
            return;
        }
        synchronized (BuildingBuiltinResourceService.class) {
            if (COPIED_ROOTS.contains(rootKey)) {
                return;
            }
            try {
                if (copyOfficialPackage(normalizedRoot)) {
                    COPIED_ROOTS.add(rootKey);
                }
            } catch (RuntimeException exception) {
                SimuKraft.LOGGER.error("Simukraft: Failed to copy official building package into {}", normalizedRoot, exception);
            }
        }
    }

    /**
     * forceOverwrite: 用内置官方建筑目录覆写游戏目录中的同名文件夹。
     */
    public static void forceOverwrite(Path rootDirectory) {
        if (rootDirectory == null) {
            return;
        }
        Path normalizedRoot = rootDirectory.toAbsolutePath().normalize();
        String rootKey = normalizedRoot.toString().toLowerCase(Locale.ROOT);
        synchronized (BuildingBuiltinResourceService.class) {
            try {
                Files.createDirectories(normalizedRoot);
            } catch (IOException exception) {
                SimuKraft.LOGGER.error("Simukraft: Failed to create building package directory {}", normalizedRoot, exception);
                return;
            }
            Path target = officialTarget(normalizedRoot);
            if (target == null) {
                return;
            }
            try {
                deleteRecursively(target);
                copyBuiltin(target);
                SimuKraft.LOGGER.info("Simukraft: Force-overwrote official building package at {}", target);
            } catch (IOException exception) {
                SimuKraft.LOGGER.error("Simukraft: Failed to overwrite official building package at {}", target, exception);
            }
            COPIED_ROOTS.remove(rootKey);
        }
    }

    /**
     * clearCache: 清理已复制目录标记，测试和重载时允许重新检查官方包。
     */
    public static void clearCache() {
        COPIED_ROOTS.clear();
    }

    private static boolean copyOfficialPackage(Path rootDirectory) {
        try {
            Files.createDirectories(rootDirectory);
        } catch (IOException exception) {
            SimuKraft.LOGGER.error("Simukraft: Failed to create building package directory {}", rootDirectory, exception);
            return false;
        }
        Path target = officialTarget(rootDirectory);
        if (target == null) {
            return false;
        }
        Path staging = rootDirectory.resolve(BuildingPackageCatalog.OFFICIAL_PACKAGE_NAME + ".tmp").normalize();
        if (!staging.startsWith(rootDirectory)) {
            return false;
        }
        try {
            deleteRecursively(staging);
            copyBuiltin(staging);
            if (!Files.isDirectory(staging.resolve("buildings"))) {
                deleteRecursively(staging);
                SimuKraft.LOGGER.warn("Simukraft: Built-in building package did not contain a buildings directory");
                return false;
            }
            deleteRecursively(target);
            Files.move(staging, target);
            SimuKraft.LOGGER.info("Simukraft: Installed official building package at {}", target);
            return true;
        } catch (IOException exception) {
            SimuKraft.LOGGER.error("Simukraft: Failed to copy official building package to {}", target, exception);
            try {
                deleteRecursively(staging);
            } catch (IOException cleanup) {
                SimuKraft.LOGGER.warn("Simukraft: Failed to remove incomplete building package {}", staging, cleanup);
            }
            return false;
        }
    }

    private static Path officialTarget(Path rootDirectory) {
        Path target = rootDirectory.resolve(BuildingPackageCatalog.OFFICIAL_PACKAGE_NAME).normalize();
        if (!target.startsWith(rootDirectory)) {
            SimuKraft.LOGGER.error("Simukraft: Refused unsafe official building package path {}", target);
            return null;
        }
        return target;
    }

    private static void copyBuiltin(Path target) throws IOException {
        ClassLoader classLoader = BuildingBuiltinResourceService.class.getClassLoader();
        URL url = classLoader == null
                ? ClassLoader.getSystemResource(PACK_JSON)
                : classLoader.getResource(PACK_JSON);
        if (url == null) {
            SimuKraft.LOGGER.warn("Simukraft: Missing built-in official building package {}", PACK_JSON);
            return;
        }
        Path source = resourceDirectory(url);
        if (source != null) {
            copyTree(source, target);
            return;
        }
        extractJarPrefix(url, target);
    }

    /**
     * resourceDirectory: 开发环境资源在 union 文件系统上，直接走已挂载的 Path。
     * 不能把 union:/C:/... 按 jar: 前缀裁成 n:/...。
     */
    private static Path resourceDirectory(URL url) {
        try {
            Path packJson = Path.of(url.toURI());
            Path source = packJson.getParent();
            if (source != null && Files.isDirectory(source)) {
                return source;
            }
        } catch (URISyntaxException | FileSystemNotFoundException | IllegalArgumentException exception) {
            SimuKraft.LOGGER.debug("Simukraft: Building resource is not a mounted path: {}", url, exception);
        }
        return null;
    }

    private static void copyTree(Path source, Path target) throws IOException {
        if (source == null || !Files.isDirectory(source)) {
            throw new IOException("Missing building directory " + source);
        }
        try (var walk = Files.walk(source)) {
            for (Path file : walk.filter(Files::isRegularFile).toList()) {
                String relative = source.relativize(file).toString().replace('\\', '/');
                if (relative.isBlank() || relative.contains("..")) {
                    continue;
                }
                Path dest = target.resolve(relative).normalize();
                if (!dest.startsWith(target)) {
                    continue;
                }
                Files.createDirectories(dest.getParent());
                Files.copy(file, dest, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static void extractJarPrefix(URL url, Path target) throws IOException {
        String external = url.toExternalForm();
        if (!external.regionMatches(true, 0, "jar:", 0, 4)) {
            throw new IOException("Unrecognized building resource " + external);
        }
        int split = external.indexOf("!/");
        if (split < 5) {
            throw new IOException("Unrecognized building resource " + external);
        }
        Path jarPath;
        try {
            jarPath = Path.of(URI.create(external.substring(4, split)));
        } catch (IllegalArgumentException | FileSystemNotFoundException exception) {
            throw new IOException("Unrecognized building resource " + external, exception);
        }
        try (ZipFile zipFile = new ZipFile(jarPath.toFile(), StandardCharsets.UTF_8)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName().replace('\\', '/');
                if (entry.isDirectory() || !name.startsWith(RESOURCE_ROOT)) {
                    continue;
                }
                String relative = name.substring(RESOURCE_ROOT.length());
                if (relative.isBlank() || relative.contains("..")) {
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
            }
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (var walk = Files.walk(path)) {
            for (Path file : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(file);
            }
        }
    }
}
