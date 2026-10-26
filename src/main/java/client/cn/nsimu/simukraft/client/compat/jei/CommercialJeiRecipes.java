package client.cn.nsimu.simukraft.client.compat.jei;

import common.cn.nsimu.simukraft.SimuKraft;
import common.cn.nsimu.simukraft.building.BuildingCatalog;
import common.cn.nsimu.simukraft.commercial.*;
import common.cn.nsimu.simukraft.economy.CoinDenominations;
import common.cn.nsimu.simukraft.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * CommercialJeiRecipes: 从商业建筑包收集 JEI 可查询的报价。
 */
public final class CommercialJeiRecipes {
    private CommercialJeiRecipes() {
    }

    /**
     * collect: 读取 commercial 分类里的报价。失败时返回空列表，避免拖垮 JEI。
     */
    public static List<CommercialJeiRecipe> collect() {
        try {
            return collectUnsafely();
        } catch (Exception exception) {
            SimuKraft.LOGGER.warn("Simukraft: Failed to collect commercial offers for JEI.", exception);
            return List.of();
        }
    }

    private static List<CommercialJeiRecipe> collectUnsafely() {
        List<CommercialJeiRecipe> recipes = new ArrayList<>();
        for (BuildingCatalog.BuildingDefinition building : BuildingCatalog.listBuildings("commercial")) {
            CommercialDefinition definition = CommercialDefinitionLoader.loadForCatalog(building).definition();
            if (definition == null || definition.offers().isEmpty()) {
                continue;
            }
            int index = 0;
            for (CommercialOffer offer : definition.offers()) {
                CommercialJeiRecipe view = toRecipe(building, definition, offer, index++);
                if (view != null) {
                    recipes.add(view);
                }
            }
        }
        return List.copyOf(recipes);
    }

    private static CommercialJeiRecipe toRecipe(BuildingCatalog.BuildingDefinition building,
                                                CommercialDefinition definition,
                                                CommercialOffer offer,
                                                int index) {
        if (offer == null || !offer.valid()) {
            return null;
        }
        List<ItemStack> inputs = new ArrayList<>();
        List<ItemStack> outputs = new ArrayList<>();
        double costMoney = appendResources(inputs, offer.cost());
        double resultMoney = appendResources(outputs, offer.result());
        if (inputs.isEmpty() && outputs.isEmpty()) {
            return null;
        }
        String id = building.packageKey() + "|" + building.metaFileName() + "|" + definition.id() + "|" + offer.id() + "|" + index;
        return new CommercialJeiRecipe(
                id,
                firstText(building.displayName(), definition.name(), building.metaFileName()),
                firstText(building.displayNameEn(), ""),
                firstText(definition.name(), building.displayName(), offer.id()),
                inputs,
                outputs,
                costMoney,
                resultMoney,
                materials(offer),
                offer.visibleTo() == CommercialVisibility.NPC
        );
    }

    /**
     * appendResources: 物品直接入格，资金拆成金、银、铜，方便按硬币查询交易。
     */
    private static double appendResources(List<ItemStack> slots, List<CommercialResource> resources) {
        double money = 0.0D;
        for (CommercialResource resource : resources) {
            if (resource.type() == CommercialResource.Type.MONEY) {
                money += resource.money();
                continue;
            }
            ItemStack stack = resource.stack(1);
            if (!stack.isEmpty()) {
                slots.add(stack);
            }
        }
        appendCoins(slots, money);
        return money;
    }

    private static void appendCoins(List<ItemStack> slots, double money) {
        if (money <= 0.0D) {
            return;
        }
        int[] coins = CoinDenominations.breakdown(money);
        addCoin(slots, new ItemStack(ModItems.GOLD_COIN.get()), coins[0]);
        addCoin(slots, new ItemStack(ModItems.SILVER_COIN.get()), coins[1]);
        addCoin(slots, new ItemStack(ModItems.COPPER_COIN.get()), coins[2]);
    }

    private static void addCoin(List<ItemStack> slots, ItemStack stack, int count) {
        if (count <= 0) {
            return;
        }
        stack.setCount(count);
        slots.add(stack);
    }

    private static String materials(CommercialOffer offer) {
        CommercialOffer.StockRule stock = offer.stock();
        if (stock == null || stock.materials().isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (CommercialOffer.MaterialRequirement material : stock.materials()) {
            if (!material.valid()) {
                continue;
            }
            parts.add(Component.translatable(material.item().getDescriptionId()).getString() + " x" + material.count());
        }
        return String.join(", ", parts);
    }

    private static String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
