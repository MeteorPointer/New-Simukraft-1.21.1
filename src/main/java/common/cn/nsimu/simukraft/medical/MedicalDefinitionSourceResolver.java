package common.cn.nsimu.simukraft.medical;

import common.cn.nsimu.simukraft.building.BuildingCatalog;

import java.util.Locale;

/** 医疗 JSON 文件定位，规则与商业/工业建筑的同名定义一致。 */
public final class MedicalDefinitionSourceResolver {
    private MedicalDefinitionSourceResolver() {
    }

    /** explicitMedicalFileName：读取 .sk 中的 medical:<file>.json 声明。 */
    public static String explicitMedicalFileName(BuildingCatalog.BuildingDefinition definition) {
        if (definition == null) {
            return null;
        }
        return definition.readFileText(definition.metaFileName())
                .map(MedicalDefinitionSourceResolver::findMedicalFileName)
                .orElse(null);
    }

    /** siblingMedicalFileName：解析与 .sk 同名的 JSON 文件。 */
    public static String siblingMedicalFileName(BuildingCatalog.BuildingDefinition definition) {
        if (definition == null || definition.metaFileName() == null) {
            return null;
        }
        String metaName = definition.metaFileName();
        int dot = metaName.lastIndexOf('.');
        String candidate = (dot >= 0 ? metaName.substring(0, dot) : metaName) + ".json";
        return definition.hasFile(candidate) ? candidate : null;
    }

    private static String findMedicalFileName(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        for (String rawLine : text.split("\\R")) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (!line.regionMatches(true, 0, "medical:", 0, "medical:".length())) {
                continue;
            }
            String fileName = line.substring("medical:".length()).trim();
            if (fileName.toLowerCase(Locale.ROOT).endsWith(".json")
                    && !fileName.contains("/") && !fileName.contains("\\\\") && !fileName.contains("..")) {
                return fileName;
            }
        }
        return null;
    }
}
