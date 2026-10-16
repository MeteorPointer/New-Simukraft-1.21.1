package common.cn.nsimu.simukraft.medical;

import java.util.Locale;

/** 可持久化的随机疾病类型。 */
public enum DiseaseType {
    NONE,
    GENERIC,
    COLD,
    FLU,
    FOOD_POISONING;

    /** fromName：兼容旧存档或配置中的疾病名称。 */
    public static DiseaseType fromName(String name) {
        if (name == null || name.isBlank()) {
            return NONE;
        }
        for (DiseaseType type : values()) {
            if (type.name().equalsIgnoreCase(name)) {
                return type;
            }
        }
        return NONE;
    }

    /** translationKey：返回客户端疾病名称翻译键。 */
    public String translationKey() {
        return "disease." + name().toLowerCase(Locale.ROOT);
    }

    /** isActive：判断当前是否需要疾病治疗。 */
    public boolean isActive() {
        return this != NONE;
    }
}
