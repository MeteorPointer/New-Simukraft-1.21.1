package common.cn.kafei.simukraft.clientbridge;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ClientInteractionBridge: common 交互逻辑到客户端界面的线程安全桥接层。
 */
public final class ClientInteractionBridge {
    private static final AtomicReference<ClientInteractionHandler> HANDLER = new AtomicReference<>(ClientInteractionHandler.NOOP);

    private ClientInteractionBridge() {
    }

    /** install: 安装客户端交互实现。 */
    public static void install(ClientInteractionHandler handler) {
        HANDLER.set(Objects.requireNonNullElse(handler, ClientInteractionHandler.NOOP));
    }

    /** reset: 恢复为空实现。 */
    public static void reset() {
        HANDLER.set(ClientInteractionHandler.NOOP);
    }

    /** openBuildBox: 分发建造箱界面打开请求。 */
    public static void openBuildBox(BlockPos pos) {
        HANDLER.get().openBuildBox(pos);
    }

    /** openManifest: 分发材料清单界面打开请求。 */
    public static void openManifest(ItemStack stack, InteractionHand hand) {
        HANDLER.get().openManifest(stack, hand);
    }

    /** openGuideBook: 分发指南书界面打开请求。 */
    public static void openGuideBook(ItemStack stack) {
        HANDLER.get().openGuideBook(stack);
    }
}
