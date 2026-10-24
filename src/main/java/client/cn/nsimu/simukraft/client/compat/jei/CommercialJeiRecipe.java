package client.cn.nsimu.simukraft.client.compat.jei;

import common.cn.nsimu.simukraft.building.BuildingLocale;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * CommercialJeiRecipe: 一条可被 JEI 索引的商业报价。
 * 花费在输入格，所得在输出格。
 */
public record CommercialJeiRecipe(String id,
                                  String buildingName,
                                  String buildingNameEn,
                                  String shopName,
                                  List<ItemStack> inputs,
                                  List<ItemStack> outputs,
                                  double costMoney,
                                  double resultMoney,
                                  String materials,
                                  boolean npcOnly) {
    public CommercialJeiRecipe {
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
        outputs = outputs == null ? List.of() : List.copyOf(outputs);
        shopName = shopName == null ? "" : shopName;
        materials = materials == null ? "" : materials;
    }

    /**
     * title: 商店名。加载时已按当时语言选定。
     */
    public Component title() {
        return Component.literal(shopName);
    }

    /**
     * buildingLine: 当前语言下的建筑名。与店名相同时不再重复。
     */
    public Component buildingLine() {
        String name = BuildingLocale.preferEnglish() && buildingNameEn != null && !buildingNameEn.isBlank()
                ? buildingNameEn
                : (buildingName == null ? "" : buildingName);
        if (name.isBlank() || name.equals(shopName)) {
            return Component.empty();
        }
        return Component.translatable("jei.simukraft.commercial.building", name);
    }
}
