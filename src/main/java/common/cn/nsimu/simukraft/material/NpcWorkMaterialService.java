package common.cn.nsimu.simukraft.material;

import common.cn.nsimu.simukraft.citizen.CitizenData;
import common.cn.nsimu.simukraft.citizen.CitizenService;
import common.cn.nsimu.simukraft.citizen.CitizenWorkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

public final class NpcWorkMaterialService {
    public static final String MISSING_MATERIAL_STATUS_PREFIX = "simukraft:missing_material:";

    private NpcWorkMaterialService() {
    }

    /**
     * tryConsume: 按工作材料请求消耗一份可接受物品。
     */
    public static WorkMaterialResult tryConsume(ServerLevel level, WorkMaterialCache materialCache, WorkMaterialRequest request) {
        if (level == null || materialCache == null || request == null || request.isEmpty()) {
            return WorkMaterialResult.available(ItemStack.EMPTY);
        }
        if (materialCache.tryConsumeOne(level, request) == WorkMaterialCache.ConsumeResult.CONSUMED) {
            return WorkMaterialResult.available(request.displayStack());
        }
        return WorkMaterialResult.missing(request.displayStack(), request.acceptedItems());
    }

    /**
     * markMissing: 标记 NPC 当前缺少的材料，状态里只保存稳定物品 ID。
     */
    public static void markMissing(ServerLevel level, CitizenData citizen, String workContext, WorkMaterialResult result) {
        if (level == null || citizen == null || result == null || result.available()) {
            return;
        }
        String materialId = result.materialId();
        String statusLabel = missingMaterialStatus(materialId);
        String needDetail = (workContext == null || workContext.isBlank() ? "work" : workContext) + ":missing=" + materialId;
        if (citizen.workStatusType() == CitizenWorkStatus.WORKING
                && statusLabel.equals(citizen.statusLabel())
                && needDetail.equals(citizen.workNeedDetail())) {
            return;
        }
        citizen.setWorkStatus(CitizenWorkStatus.WORKING);
        citizen.setStatusLabel(statusLabel);
        citizen.setWorkNeedDetail(needDetail);
        CitizenService.save(level, citizen.uuid());
    }

    /**
     * describe: 返回材料物品 ID，避免在服务端固化客户端语言。
     */
    public static String describe(ItemStack stack) {
        return new WorkMaterialResult(false, stack).materialId();
    }

    /**
     * missingMaterialStatus: 生成客户端可解析的缺材料状态标签。
     */
    public static String missingMaterialStatus(String itemId) {
        return MISSING_MATERIAL_STATUS_PREFIX + (itemId == null || itemId.isBlank() ? "unknown" : itemId);
    }
}
