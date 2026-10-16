package common.cn.nsimu.simukraft.medical;

import java.nio.file.Path;

/** 医疗建筑 JSON 的最小业务定义。 */
public record MedicalDefinition(String id, String name, int serviceRangeRings, Path sourcePath) {
    public static final int DEFAULT_SERVICE_RANGE_RINGS = 3;
    public static final int MAX_SERVICE_RANGE_RINGS = 6;

    public MedicalDefinition {
        id = id != null && !id.isBlank() ? id.trim() : "hospital";
        name = name != null && !name.isBlank() ? name.trim() : id;
        serviceRangeRings = Math.clamp(serviceRangeRings, 1, MAX_SERVICE_RANGE_RINGS);
    }

    /** defaultFor：缺少医疗 JSON 时使用的兼容默认定义。 */
    public static MedicalDefinition defaultFor(String id, Path sourcePath) {
        return new MedicalDefinition(id, id, DEFAULT_SERVICE_RANGE_RINGS, sourcePath);
    }
}
