package common.cn.nsimu.simukraft.network.mail;

/** 邮件分类，对应邮件界面左侧的三个标签页。 */
public enum MailCategory {
    CITIZEN,
    SYSTEM,
    CITY;

    /** 系统发件方的 senderKey。 */
    public static final String SYSTEM_KEY = "system";
    public static final String CITIZEN_PREFIX = "citizen:";
    public static final String CITY_PREFIX = "city:";

    public static MailCategory fromName(String name) {
        for (MailCategory category : values()) {
            if (category.name().equalsIgnoreCase(name)) {
                return category;
            }
        }
        return SYSTEM;
    }

    /** 玩家能否在这个分类里回复。 */
    public boolean allowsReply() {
        return this != SYSTEM;
    }
}
