package client.cn.nsimu.simukraft.client.compat.jei;

import common.cn.nsimu.simukraft.SimuKraft;
import common.cn.nsimu.simukraft.registry.ModBlocks;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

/**
 * SimukraftJeiPlugin: JEI 入口。只有安装了 JEI 时才会被 ServiceLoader 加载。
 */
@JeiPlugin
@OnlyIn(Dist.CLIENT)
public class SimukraftJeiPlugin implements IModPlugin {
    private List<IndustrialJeiRecipe> industrialRecipes = List.of();
    private List<CommercialJeiRecipe> commercialRecipes = List.of();

    /**
     * getPluginUid: JEI 用来区分插件的唯一标识。
     */
    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "jei");
    }

    /**
     * registerCategories: 注册工业配方和商业交易分类。没有可读条目时不显示空分类。
     */
    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        industrialRecipes = IndustrialJeiRecipes.collect();
        commercialRecipes = CommercialJeiRecipes.collect();
        if (!industrialRecipes.isEmpty()) {
            registration.addRecipeCategories(new IndustrialJeiCategory(registration.getJeiHelpers().getGuiHelper(), industrialRecipes));
        }
        if (!commercialRecipes.isEmpty()) {
            registration.addRecipeCategories(new CommercialJeiCategory(registration.getJeiHelpers().getGuiHelper(), commercialRecipes));
        }
    }

    /**
     * registerRecipes: 把工业配方和商业报价交给 JEI。资源重载时重新读取建筑包。
     */
    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        industrialRecipes = IndustrialJeiRecipes.collect();
        commercialRecipes = CommercialJeiRecipes.collect();
        if (!industrialRecipes.isEmpty()) {
            registration.addRecipes(IndustrialJeiCategory.TYPE, industrialRecipes);
            SimuKraft.LOGGER.info("Simukraft: Registered {} industrial recipes with JEI.", industrialRecipes.size());
        }
        if (!commercialRecipes.isEmpty()) {
            registration.addRecipes(CommercialJeiCategory.TYPE, commercialRecipes);
            SimuKraft.LOGGER.info("Simukraft: Registered {} commercial offers with JEI.", commercialRecipes.size());
        }
    }

    /**
     * registerRecipeCatalysts: 控制箱作为分类入口，查看对应物品时能打开配方或交易。
     */
    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        if (industrialRecipes.isEmpty()) {
            industrialRecipes = IndustrialJeiRecipes.collect();
        }
        if (commercialRecipes.isEmpty()) {
            commercialRecipes = CommercialJeiRecipes.collect();
        }
        if (!industrialRecipes.isEmpty()) {
            registration.addRecipeCatalyst(new ItemStack(ModBlocks.INDUSTRIAL_CONTROL_BOX.get()), IndustrialJeiCategory.TYPE);
        }
        if (!commercialRecipes.isEmpty()) {
            registration.addRecipeCatalyst(new ItemStack(ModBlocks.COMMERCIAL_CONTROL_BOX.get()), CommercialJeiCategory.TYPE);
        }
    }
}
