package common.cn.nsimu.simukraft.building;

import common.cn.nsimu.simukraft.citizen.CitizenData;
import common.cn.nsimu.simukraft.city.group.CityGroupMessageService;
import common.cn.nsimu.simukraft.city.group.CityUserGroup;
import common.cn.nsimu.simukraft.city.group.CityUserGroupService;
import common.cn.nsimu.simukraft.registry.ModSoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;


public final class ConstructionCompletionNotificationService {
    private ConstructionCompletionNotificationService() {
    }

    public static void notifyCompleted(ServerLevel level, CitizenData citizen, BuildingTaskData task) {
        if (level == null || citizen == null || task == null || task.cityId() == null) {
            return;
        }
        Component message = Component.translatable("message.simukraft.construction.completed", citizen.name(), task.displayName());
        CityGroupMessageService.send(level, CityUserGroup.members(task.cityId()),
                Component.translatable("toast.simukraft.construction_title"), message, "success", ItemStack.EMPTY);
        CityUserGroupService.forEach(level, CityUserGroup.mayors(task.cityId()),
                p -> p.playNotifySound(ModSoundEvents.CONSTRUCTION_COMPLETE.get(), SoundSource.PLAYERS, 1.0F, 1.0F));
    }
}
