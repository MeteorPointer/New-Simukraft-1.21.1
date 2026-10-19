package common.cn.nsimu.simukraft.logistics;

/**
 * LogisticsChannelFilterMode: 线路过滤方式，物品格点选或读取客户端表面清单。
 */
public enum LogisticsChannelFilterMode {
    ITEM,
    MANIFEST;

    /**
     * fromName: 解析过滤模式，未知值回退到物品过滤。
     */
    public static LogisticsChannelFilterMode fromName(String value) {
        if (value != null && value.equalsIgnoreCase(MANIFEST.name())) {
            return MANIFEST;
        }
        return ITEM;
    }
}
