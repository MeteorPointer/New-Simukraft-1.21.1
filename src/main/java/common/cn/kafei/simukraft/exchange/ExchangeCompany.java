package common.cn.kafei.simukraft.exchange;

import common.cn.kafei.simukraft.building.BuildingLocale;

/** ExchangeCompany: 数据包驱动的上市公司。 */
public record ExchangeCompany(String id, String displayName, String displayNameEn, String sector, double basePrice, double volatility) {
    public ExchangeCompany(String id, String displayName, String sector, double basePrice, double volatility) {
        this(id, displayName, "", sector, basePrice, volatility);
    }

    public ExchangeCompany {
        id = id != null ? id.trim() : "";
        displayName = displayName != null && !displayName.isBlank() ? displayName.trim() : id;
        displayNameEn = displayNameEn != null ? displayNameEn.trim() : "";
        sector = sector != null ? sector.trim() : "other";
        basePrice = Math.max(0.01D, basePrice);
        volatility = Math.max(0.001D, volatility);
    }

    /** localizedName: 英文客户端优先用英文公司名。 */
    public String localizedName() {
        if (BuildingLocale.preferEnglish() && !displayNameEn.isBlank()) {
            return displayNameEn;
        }
        return displayName;
    }
}
