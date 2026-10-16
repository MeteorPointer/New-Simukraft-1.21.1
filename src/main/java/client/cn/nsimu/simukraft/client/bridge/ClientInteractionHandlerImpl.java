package client.cn.nsimu.simukraft.client.bridge;

import client.cn.nsimu.simukraft.client.buildbox.BuildBoxScreenOpener;
import client.cn.nsimu.simukraft.client.guidebook.GuideBookScreen;
import client.cn.nsimu.simukraft.client.manifest.ManifestScreen;
import common.cn.nsimu.simukraft.clientbridge.ClientInteractionHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * ClientInteractionHandlerImpl: 客户端交互实现，二次封装具体界面调用。
 */
@OnlyIn(Dist.CLIENT)
public final class ClientInteractionHandlerImpl implements ClientInteractionHandler {
    public static final ClientInteractionHandlerImpl INSTANCE = new ClientInteractionHandlerImpl();

    private ClientInteractionHandlerImpl() {
    }

    @Override
    public void openBuildBox(BlockPos pos) {
        BuildBoxScreenOpener.open(pos);
    }

    @Override
    public void openManifest(ItemStack stack, InteractionHand hand) {
        ManifestScreen.open(stack, hand);
    }

    @Override
    public void openGuideBook(ItemStack stack) {
        ItemStack previewStack = stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
        Minecraft.getInstance().setScreen(new GuideBookScreen(previewStack));
    }
}
