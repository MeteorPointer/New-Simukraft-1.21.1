package common.cn.kafei.simukraft.building;

import common.cn.kafei.simukraft.SimuKraft;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

/**
 * 已建成建筑在原版结构注册表中的 ID。
 * FTB Quests 的“寻找结构”任务只认 {@code ResourceLocation}，这里把建筑包文件名收成稳定路径。
 */
public final class BuildingStructureIds {
    public static final String ALL_TAG = "placed_buildings";

    private BuildingStructureIds() {
    }

    /**
     * location: {@code simukraft:<分类>/<文件名>}。文件名不合法时返回 null。
     */
    public static ResourceLocation location(String category, String fileName) {
        String path = path(category, fileName);
        if (path == null) {
            return null;
        }
        return ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, path);
    }

    /**
     * path: 分类加文件主干。分类会走建筑包的别名归一，文件名只保留结构路径允许的字符。
     */
    public static String path(String category, String fileName) {
        String stem = sanitize(fileStem(fileName));
        if (stem.isEmpty()) {
            return null;
        }
        return BuildingPackageCatalog.normalizeCategory(category) + "/" + stem;
    }

    /**
     * matches: 任务选中的结构 ID 是否就是这座已建成建筑。
     * 建筑元数据和结构文件主干都可以对上，方便 .sk 与 .nbt 不同名的建筑包。
     */
    public static boolean matches(String category, String buildingFileName, String structureFileName, ResourceLocation structureId) {
        if (structureId == null || !SimuKraft.MOD_ID.equals(structureId.getNamespace())) {
            return false;
        }
        String requested = structureId.getPath();
        return requested.equals(path(category, buildingFileName)) || requested.equals(path(category, structureFileName));
    }

    /** fileStem: 去掉最后一个扩展名。.sk / .nbt / .json 都按这个规则。 */
    public static String fileStem(String fileName) {
        if (fileName == null) {
            return "";
        }
        String trimmed = fileName.trim();
        int index = trimmed.lastIndexOf('.');
        return index > 0 ? trimmed.substring(0, index) : trimmed;
    }

    /** sanitize: 小写，并把空格和其他非法字符收成下划线。 */
    public static String sanitize(String stem) {
        if (stem == null || stem.isBlank()) {
            return "";
        }
        String lower = stem.toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder(lower.length());
        boolean pendingSeparator = false;
        for (int i = 0; i < lower.length(); i++) {
            char character = lower.charAt(i);
            if (isPathCharacter(character)) {
                if (pendingSeparator && !builder.isEmpty()) {
                    builder.append('_');
                }
                pendingSeparator = false;
                builder.append(character);
            } else if (!builder.isEmpty()) {
                pendingSeparator = true;
            }
        }
        return builder.toString();
    }

    private static boolean isPathCharacter(char character) {
        return character >= '0' && character <= '9'
                || character >= 'a' && character <= 'z'
                || character == '_'
                || character == '.'
                || character == '-';
    }
}
