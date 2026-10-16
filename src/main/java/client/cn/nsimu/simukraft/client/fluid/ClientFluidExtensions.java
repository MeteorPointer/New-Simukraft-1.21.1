package client.cn.nsimu.simukraft.client.fluid;

import common.cn.nsimu.simukraft.SimuKraft;
import common.cn.nsimu.simukraft.registry.ModFluidTypes;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

@OnlyIn(Dist.CLIENT)
public final class ClientFluidExtensions {
    private static final ResourceLocation MILK_STILL = ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "block/milk_still");
    private static final ResourceLocation MILK_FLOWING = ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "block/milk_flow");

    private ClientFluidExtensions() {
    }

    public static void register(RegisterClientExtensionsEvent event) {
        event.registerFluidType(new IClientFluidTypeExtensions() {
            @Override
            public ResourceLocation getStillTexture() {
                return MILK_STILL;
            }

            @Override
            public ResourceLocation getFlowingTexture() {
                return MILK_FLOWING;
            }

            @Override
            public int getTintColor() {
                return 0xFFFFFFFF;
            }
        }, ModFluidTypes.MILK);
    }
}
