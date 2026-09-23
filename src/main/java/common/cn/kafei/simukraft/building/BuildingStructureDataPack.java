package common.cn.kafei.simukraft.building;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import common.cn.kafei.simukraft.SimuKraft;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.AbstractPackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.AddPackFindersEvent;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * 把建筑包里的每一座建筑登记成数据包结构，让 FTB Quests 的结构列表能选到它们。
 * 这些结构没有结构集，不会在世界里自然生成。
 */
public final class BuildingStructureDataPack {
    private static final String PACK_ID = "simukraft/building_structures";
    private static final String STRUCTURE_DIRECTORY = "worldgen/structure/";
    private static final String TAG_DIRECTORY = "tags/worldgen/structure/";
    private static final byte[] STRUCTURE_JSON = """
            {
              "type": "simukraft:placed_building",
              "biomes": "#minecraft:is_overworld",
              "step": "surface_structures",
              "terrain_adaptation": "none",
              "spawn_overrides": {}
            }
            """.getBytes(StandardCharsets.UTF_8);

    private BuildingStructureDataPack() {
    }

    /** onAddPackFinders: 注册始终启用的服务端数据包。 */
    public static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA) {
            return;
        }
        event.addRepositorySource(consumer -> {
            PackLocationInfo location = new PackLocationInfo(
                    PACK_ID,
                    Component.literal("Simukraft Building Structures"),
                    PackSource.FEATURE,
                    Optional.empty()
            );
            Pack pack = Pack.readMetaAndCreate(
                    location,
                    new Pack.ResourcesSupplier() {
                        @Override
                        public PackResources openPrimary(PackLocationInfo packLocation) {
                            return new Resources(packLocation);
                        }

                        @Override
                        public PackResources openFull(PackLocationInfo packLocation, Pack.Metadata metadata) {
                            return openPrimary(packLocation);
                        }
                    },
                    PackType.SERVER_DATA,
                    new PackSelectionConfig(true, Pack.Position.TOP, false)
            );
            if (pack != null) {
                consumer.accept(pack);
            }
        });
    }

    private static Map<String, ResourceLocation> scanBuildings() {
        Map<String, ResourceLocation> structures = new LinkedHashMap<>();
        readZip(BuildingBuiltinResourceService.openOfficialPackage(), "official_building.zip", structures);
        Path root = FMLPaths.GAMEDIR.get().resolve(BuildingPackageCatalog.ROOT_DIR);
        if (!Files.isDirectory(root)) {
            return structures;
        }
        try (var stream = Files.list(root)) {
            List<Path> packages = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .toList();
            for (Path packagePath : packages) {
                try (ZipFile zipFile = new ZipFile(packagePath.toFile(), StandardCharsets.UTF_8)) {
                    var entries = zipFile.entries();
                    while (entries.hasMoreElements()) {
                        addEntry(entries.nextElement().getName(), structures);
                    }
                } catch (IOException exception) {
                    SimuKraft.LOGGER.warn("Simukraft: Failed to read building package {} while exposing structures", packagePath, exception);
                }
            }
        } catch (IOException exception) {
            SimuKraft.LOGGER.warn("Simukraft: Failed to list building packages while exposing structures", exception);
        }
        return structures;
    }

    private static void readZip(InputStream inputStream, String sourceName, Map<String, ResourceLocation> structures) {
        if (inputStream == null) {
            return;
        }
        try (InputStream stream = inputStream; ZipInputStream zip = new ZipInputStream(stream, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                addEntry(entry.getName(), structures);
            }
        } catch (IOException exception) {
            SimuKraft.LOGGER.warn("Simukraft: Failed to read {} while exposing structures", sourceName, exception);
        }
    }

    private static void addEntry(String entryName, Map<String, ResourceLocation> structures) {
        String normalized = entryName == null ? "" : entryName.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (!normalized.startsWith("buildings/") || !normalized.toLowerCase(Locale.ROOT).endsWith(".sk")) {
            return;
        }
        String[] parts = normalized.split("/");
        if (parts.length != 3 || parts[2].isBlank() || parts[2].contains("..")) {
            return;
        }
        ResourceLocation id = BuildingStructureIds.location(parts[1], parts[2]);
        if (id == null) {
            SimuKraft.LOGGER.warn("Simukraft: Skipped building {} because its name is not a valid structure id", normalized);
            return;
        }
        structures.put(id.getPath(), id);
    }

    private static final class Resources implements PackResources {
        private final PackLocationInfo location;
        private Map<String, ResourceLocation> structures;
        private Map<String, byte[]> tags;

        private Resources(PackLocationInfo location) {
            this.location = location;
        }

        private Map<String, ResourceLocation> structures() {
            if (this.structures == null) {
                this.structures = scanBuildings();
                this.tags = buildTags(this.structures);
                SimuKraft.LOGGER.info("Simukraft: Exposed {} building structures for quest detection", this.structures.size());
            }
            return this.structures;
        }

        @Override
        public IoSupplier<InputStream> getRootResource(String... elements) {
            if (elements.length == 1 && PackResources.PACK_META.equals(elements[0])) {
                int packFormat = SharedConstants.getCurrentVersion().getPackVersion(PackType.SERVER_DATA);
                String meta = "{\"pack\":{\"description\":\"Simukraft placed building structures\",\"pack_format\":" + packFormat + "}}";
                return () -> new ByteArrayInputStream(meta.getBytes(StandardCharsets.UTF_8));
            }
            return null;
        }

        @Override
        public IoSupplier<InputStream> getResource(PackType packType, ResourceLocation resourceLocation) {
            if (packType != PackType.SERVER_DATA || !SimuKraft.MOD_ID.equals(resourceLocation.getNamespace())) {
                return null;
            }
            byte[] bytes = resourceBytes(resourceLocation.getPath());
            return bytes == null ? null : () -> new ByteArrayInputStream(bytes);
        }

        @Override
        public void listResources(PackType packType, String namespace, String path, ResourceOutput output) {
            if (packType != PackType.SERVER_DATA || !SimuKraft.MOD_ID.equals(namespace)) {
                return;
            }
            String prefix = path.endsWith("/") ? path : path + "/";
            for (ResourceLocation id : structures().values()) {
                String resourcePath = STRUCTURE_DIRECTORY + id.getPath() + ".json";
                if (resourcePath.equals(path) || resourcePath.startsWith(prefix)) {
                    output.accept(ResourceLocation.fromNamespaceAndPath(namespace, resourcePath), () -> new ByteArrayInputStream(STRUCTURE_JSON));
                }
            }
            for (Map.Entry<String, byte[]> tag : tags.entrySet()) {
                if (tag.getKey().equals(path) || tag.getKey().startsWith(prefix)) {
                    byte[] bytes = tag.getValue();
                    output.accept(ResourceLocation.fromNamespaceAndPath(namespace, tag.getKey()), () -> new ByteArrayInputStream(bytes));
                }
            }
        }

        @Override
        public Set<String> getNamespaces(PackType packType) {
            return packType == PackType.SERVER_DATA ? Set.of(SimuKraft.MOD_ID) : Set.of();
        }

        @Override
        public <T> T getMetadataSection(MetadataSectionSerializer<T> deserializer) throws IOException {
            IoSupplier<InputStream> supplier = getRootResource(PackResources.PACK_META);
            if (supplier == null) {
                return null;
            }
            try (InputStream inputStream = supplier.get()) {
                return AbstractPackResources.getMetadataFromStream(deserializer, inputStream);
            }
        }

        @Override
        public PackLocationInfo location() {
            return location;
        }

        @Override
        public boolean isHidden() {
            return true;
        }

        @Override
        public void close() {
        }

        private byte[] resourceBytes(String path) {
            if (path.startsWith(STRUCTURE_DIRECTORY) && path.endsWith(".json")) {
                String structurePath = path.substring(STRUCTURE_DIRECTORY.length(), path.length() - ".json".length());
                return structures().containsKey(structurePath) ? STRUCTURE_JSON : null;
            }
            structures();
            return tags.get(path);
        }
    }

    private static Map<String, byte[]> buildTags(Map<String, ResourceLocation> structures) {
        Map<String, List<ResourceLocation>> byCategory = new LinkedHashMap<>();
        for (String category : BuildingPackageCatalog.categories()) {
            byCategory.put(category, new ArrayList<>());
        }
        for (ResourceLocation id : structures.values()) {
            String path = id.getPath();
            int slash = path.indexOf('/');
            String category = slash > 0 ? path.substring(0, slash) : "other";
            byCategory.computeIfAbsent(category, ignored -> new ArrayList<>()).add(id);
        }
        Map<String, byte[]> tags = new LinkedHashMap<>();
        tags.put(TAG_DIRECTORY + BuildingStructureIds.ALL_TAG + ".json", tagJson(structures.values()));
        for (Map.Entry<String, List<ResourceLocation>> entry : byCategory.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                tags.put(TAG_DIRECTORY + entry.getKey() + ".json", tagJson(entry.getValue()));
            }
        }
        return tags;
    }

    private static byte[] tagJson(Iterable<ResourceLocation> ids) {
        JsonArray values = new JsonArray();
        for (ResourceLocation id : ids) {
            values.add(id.toString());
        }
        JsonObject root = new JsonObject();
        root.addProperty("replace", false);
        root.add("values", values);
        return root.toString().getBytes(StandardCharsets.UTF_8);
    }
}
