package common.cn.nsimu.simukraft.commercial;

import common.cn.nsimu.simukraft.SimuKraft;
import common.cn.nsimu.simukraft.building.BuildingCatalog;

import javax.annotation.Nullable;
import java.util.Locale;

final class CommercialDefinitionSourceResolver {
    private CommercialDefinitionSourceResolver() {
    }

    static void clearCache() {
    }

    /**
     * explicitCommercialFileName: 解析 .sk 中 commercial 字段声明的包内 JSON 文件名。
     */
    @Nullable
    static String explicitCommercialFileName(BuildingCatalog.BuildingDefinition definition) {
        if (definition == null) {
            return null;
        }
        try {
            return definition.readFileText(definition.metaFileName())
                    .flatMap(text -> text.lines()
                            .map(line -> line != null ? line.trim() : "")
                            .filter(line -> line.regionMatches(true, 0, "commercial:", 0, "commercial:".length()))
                            .map(line -> line.substring("commercial:".length()).trim())
                            .filter(fileName -> !fileName.isBlank())
                            .findFirst())
                    .map(fileName -> definition.hasFile(fileName) ? definition.actualFileName(fileName) : null)
                    .orElse(null);
        } catch (Exception exception) {
            SimuKraft.LOGGER.warn("Simukraft: Failed to read commercial entry from {} in {}", definition.metaFileName(), definition.packageName(), exception);
            return null;
        }
    }

    /**
     * siblingCommercialFileName: 按建筑 .sk 同名规则解析相邻商业 JSON。
     */
    @Nullable
    static String siblingCommercialFileName(BuildingCatalog.BuildingDefinition definition) {
        if (definition == null) {
            return null;
        }
        String fileName = stripExtension(definition.metaFileName()) + ".json";
        return definition.hasFile(fileName) ? definition.actualFileName(fileName) : null;
    }

    private static String stripExtension(String fileName) {
        String safeName = fileName != null ? fileName : "";
        int index = safeName.lastIndexOf('.');
        return index > 0 ? safeName.substring(0, index) : safeName.toLowerCase(Locale.ROOT);
    }
}
